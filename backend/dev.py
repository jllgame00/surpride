"""Local runner / debug provisioning. Tokens travel via private files and stdin, never argv/output."""
import argparse
import asyncio
import json
import os
import re
from pathlib import Path
import secrets
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
SESSION = ROOT / ".gradle" / "route-proxy" / "session.json"
STOP = SESSION.with_name("stop")
DEFAULT_SERIAL = "emulator-5554"


def adb_serial(value):
    if not re.fullmatch(r"emulator-[0-9]{4,5}", value) or not 1024 <= int(value[9:]) <= 65534 or int(value[9:]) % 2:
        raise argparse.ArgumentTypeError("Specify an emulator serial with an even console port (e.g. emulator-5556)")
    return value


def load_env():
    path = ROOT / "backend" / ".env"
    if not path.exists():
        return
    if path.stat().st_size > 8192:
        raise ValueError("Invalid local environment file")
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        name, separator, value = line.partition("=")
        if separator and name.strip() in {"KAKAO_REST_API_KEY", "SURPRIDE_PROXY_TOKEN", "SURPRIDE_PROXY_MODE"}:
            value = value.strip()
            if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                value = value[1:-1]
            if value:
                os.environ.setdefault(name.strip(), value)


async def serve(mode):
    import uvicorn
    from backend.app import Settings, create_app
    load_env()
    token = os.environ.get("SURPRIDE_PROXY_TOKEN") or secrets.token_urlsafe(32)
    settings = Settings(api_key=os.environ.get("KAKAO_REST_API_KEY", "").strip(), token=token,
                        mode=mode or os.environ.get("SURPRIDE_PROXY_MODE", "live"))
    app = create_app(settings)
    SESSION.parent.mkdir(parents=True, exist_ok=True)
    STOP.unlink(missing_ok=True)
    config = {"base_url": "http://10.0.2.2:8765", "token": token, "expires_at_ms": settings.expires_at_ms}
    SESSION.write_text(json.dumps(config), encoding="utf-8")
    server = uvicorn.Server(uvicorn.Config(app, host="127.0.0.1", port=8765, workers=1,
                                         access_log=False, log_level="critical", server_header=False))

    async def watch():
        while not STOP.exists() and int(time.time() * 1000) < settings.expires_at_ms:
            await asyncio.sleep(0.5)
        server.should_exit = True

    watcher = asyncio.create_task(watch())
    print("Local proxy: 127.0.0.1:8765 / mode=" + settings.mode +
          " / provider key configured=" + str(bool(settings.api_key)), flush=True)
    try:
        await server.serve()
    finally:
        watcher.cancel()
        SESSION.unlink(missing_ok=True)
        STOP.unlink(missing_ok=True)


def provision(adb, serial=DEFAULT_SERIAL):
    # Explicit serial/user/package; no other device, data directory or app is touched.
    serial = adb_serial(serial)
    config = json.loads(SESSION.read_text(encoding="utf-8"))
    if config["expires_at_ms"] <= int(time.time() * 1000):
        raise ValueError("Session expired")
    command = [adb, "-s", serial, "exec-in",
               "run-as com.surpride.app --user 10 sh -c 'cat > cache/route-proxy-session.json'"]
    result = subprocess.run(command, input=json.dumps(config).encode(), capture_output=True, timeout=10)
    if result.returncode or result.stderr:
        raise RuntimeError("Debug provisioning failed; check installed debuggable app and user 10")
    check = subprocess.run([adb, "-s", serial, "exec-out", "run-as", "com.surpride.app", "--user", "10",
                            "wc", "-c", "cache/route-proxy-session.json"], capture_output=True, timeout=10)
    if check.returncode or not check.stdout or int(check.stdout.split()[0]) != len(json.dumps(config).encode()):
        raise RuntimeError("Debug provisioning byte count verification failed")
    print("Provisioned debug app private cache; serial=" + serial + "; credential output suppressed.")


def main():
    parser = argparse.ArgumentParser(description="SURPRIDE local proxy (not a production server)")
    commands = parser.add_subparsers(dest="command", required=True)
    run = commands.add_parser("serve")
    run.add_argument("--mode", choices=("live", "mock"))
    device = commands.add_parser("provision-android")
    device.add_argument("--adb", default="adb")
    device.add_argument("--serial", type=adb_serial, default=DEFAULT_SERIAL)
    commands.add_parser("stop")
    args = parser.parse_args()
    try:
        if args.command == "serve":
            asyncio.run(serve(args.mode))
        elif args.command == "stop":
            STOP.parent.mkdir(parents=True, exist_ok=True)
            STOP.touch()
            print("Stop requested for local proxy.")
        else:
            provision(args.adb, args.serial)
    except KeyboardInterrupt:
        pass
    except Exception:
        print("Local proxy operation failed; check dependencies, environment, port or debug installation. No secrets printed.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
