"""Tool CLI contracts only: no ADB, emulator, HTTP server or real credential is touched."""
import importlib
import json
import subprocess
import sys
import time

import pytest

from backend.smoke import ORIGIN, DESTINATION

TOKEN = "synthetic-private-b6-token-00000000"
TOOLS = ["dev", "nav_poc", "gps_replay", "active_replay"]


def setup_tool(monkeypatch, name, serial="emulator-5556", fail_at=None, http_error=False):
    module = importlib.import_module("backend." + name)
    args = {"dev": ["provision-android"], "nav_poc": ["--request-public-route"],
            "gps_replay": ["--public-route-prefix", "--scenario", "hold-start"],
            "active_replay": ["--public-session", "--scenario", "hold", "--count", "1"]}[name]
    monkeypatch.setattr(sys, "argv", [name, *args, *([] if serial is None else ["--serial", serial])])
    monkeypatch.setenv("ANDROID_SERIAL", "emulator-9998")  # Must not redirect an omitted or failed target.
    config = {"base_url": "http://10.0.2.2:8765", "token": TOKEN, "expires_at_ms": int(time.time()*1000)+60000}
    if hasattr(module, "SESSION"):
        class Session:
            def read_text(self, **kwargs):
                return json.dumps(config)
        monkeypatch.setattr(module, "SESSION", Session())
    data = dict(origin=ORIGIN, destination=DESTINATION, trip_origin=ORIGIN, trip_destination=DESTINATION,
                development_public_replay=True, source="KAKAO_DIRECTIONS", measured_at_epoch_ms=int(time.time()*1000),
                distance_meters=6000, duration_seconds=600,
                geometry=[[ORIGIN["longitude"], ORIGIN["latitude"]], [DESTINATION["longitude"], DESTINATION["latitude"]]],
                guidance={"segments": [{"geometry": [[ORIGIN["longitude"], ORIGIN["latitude"]],
                                                       [DESTINATION["longitude"], DESTINATION["latitude"]]]}],
                          "maneuvers": [{"type": 100}, {"type": 101}]})
    calls, inputs, http = [], [], []

    def run(command, **kwargs):
        calls.append(command)
        assert TOKEN not in " ".join(command)
        assert kwargs["capture_output"] is True and kwargs["timeout"] == 10
        if fail_at == "first" or fail_at == "verify" and "wc" in command:
            return subprocess.CompletedProcess(command, 1, b"", TOKEN.encode())
        if "exec-in" in command:
            inputs.append(kwargs["input"])
            return subprocess.CompletedProcess(command, 0, b"", b"")
        if "wc" in command:
            return subprocess.CompletedProcess(command, 0, str(len(inputs[-1])).encode(), b"")
        if "id" in command:
            return subprocess.CompletedProcess(command, 0, b"1010123\n", b"")
        if "getprop" in command:
            return subprocess.CompletedProcess(command, 0, b"Connect on x86_64 emulator\n", b"")
        if "cat" in command:
            return subprocess.CompletedProcess(command, 0, json.dumps(data).encode(), b"")
        assert "emu" in command and "geo" in command
        return subprocess.CompletedProcess(command, 0, b"OK", b"")

    monkeypatch.setattr(module.subprocess, "run", run)
    monkeypatch.setattr(module.time, "sleep", lambda _: None)
    if name == "nav_poc":
        class Client:
            def __init__(self, **kwargs):
                assert kwargs == dict(trust_env=False, timeout=15, follow_redirects=False)
            def __enter__(self):
                return self
            def __exit__(self, *args):
                pass
            def post(self, url, **kwargs):
                http.append(url)
                assert kwargs["headers"]["Authorization"] == "Bearer " + TOKEN
                class Response:
                    status_code = 401 if http_error else 200
                    def json(self):
                        return {"error": TOKEN} if http_error else data
                return Response()
        monkeypatch.setattr(module.httpx, "Client", Client)
    return module, calls, inputs, http


@pytest.mark.parametrize("name", TOOLS)
def test_explicit_serial_is_used_for_every_call_without_device_discovery(monkeypatch, capsys, name):
    module, calls, inputs, _ = setup_tool(monkeypatch, name)
    assert module.main() == 0
    assert calls and all(c[1:3] == ["-s", "emulator-5556"] for c in calls)
    assert not any("devices" in c or "install" in c or "clear" in c for c in calls)
    if name in ("dev", "nav_poc"):
        transfer = [c for c in calls if "exec-in" in c or "wc" in c]
        assert len(transfer) == 2
        package = "com.surpride.app" if name == "dev" else "com.surpride.navhostpoc"
        assert f"run-as {package} --user 10" in transfer[0][-1]
        assert transfer[1][transfer[1].index("run-as")+1:][:3] == [package, "--user", "10"]
        assert len(inputs) == 1 and TOKEN in inputs[0].decode()
    captured = capsys.readouterr()
    assert TOKEN not in captured.out + captured.err
    assert "emulator-5556" in captured.out


@pytest.mark.parametrize("name", TOOLS)
def test_omitted_serial_keeps_the_fixed_legacy_target_and_ignores_android_serial(monkeypatch, name):
    module, calls, _, _ = setup_tool(monkeypatch, name, serial=None)
    assert module.main() == 0
    assert calls and all(c[1:3] == ["-s", "emulator-5554"] for c in calls)


@pytest.mark.parametrize("name", TOOLS)
@pytest.mark.parametrize("serial", ["", "physical-device", "emulator-5556;other"])
def test_invalid_serial_fails_before_any_adb_or_http(monkeypatch, capsys, name, serial):
    module, calls, _, http = setup_tool(monkeypatch, name, serial=serial)
    with pytest.raises(SystemExit) as error:
        module.main()
    assert error.value.code == 2
    assert calls == [] and http == []
    captured = capsys.readouterr()
    assert TOKEN not in captured.out + captured.err


@pytest.mark.parametrize("name", TOOLS)
def test_absent_or_offline_serial_stops_at_first_failure_without_fallback(monkeypatch, capsys, name):
    module, calls, _, http = setup_tool(monkeypatch, name, fail_at="first")
    assert module.main() == 1
    assert len(calls) == 1 and calls[0][1:3] == ["-s", "emulator-5556"]
    assert http == []
    captured = capsys.readouterr()
    assert TOKEN not in captured.out + captured.err


@pytest.mark.parametrize("name", ["dev", "nav_poc"])
def test_failed_byte_verification_never_retargets_or_prints_private_stderr(monkeypatch, capsys, name):
    module, calls, _, _ = setup_tool(monkeypatch, name, fail_at="verify")
    assert module.main() == 1
    assert all(c[1:3] == ["-s", "emulator-5556"] for c in calls)
    assert "wc" in calls[-1]
    captured = capsys.readouterr()
    assert TOKEN not in captured.out + captured.err


def test_untrusted_proxy_error_body_is_not_printed(monkeypatch, capsys):
    module, calls, inputs, http = setup_tool(monkeypatch, "nav_poc", http_error=True)
    assert module.main() == 1
    assert len(http) == 1 and not inputs
    assert not any("exec-in" in c for c in calls)
    captured = capsys.readouterr()
    assert TOKEN not in captured.out + captured.err


@pytest.mark.parametrize("serial", ["emulator-5555", "emulator-99999", "emulator-0000"])
def test_direct_provision_rejects_invalid_console_port_before_reading_credentials(monkeypatch, serial):
    from argparse import ArgumentTypeError
    from backend import dev
    monkeypatch.setattr(dev, "SESSION", None)
    with pytest.raises(ArgumentTypeError):
        dev.provision("adb", serial)
