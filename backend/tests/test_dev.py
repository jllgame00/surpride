import json
import subprocess
import time
from backend import dev


def test_debug_provision_uses_correct_user_order_stdin_and_verifies_bytes(monkeypatch, capsys):
    config={"base_url":"http://10.0.2.2:8765", "token":"synthetic-private-dev-token-00000000", "expires_at_ms":int(time.time()*1000)+60000}
    class Session:
        def read_text(self, **kwargs):
            return json.dumps(config)
    monkeypatch.setattr(dev,"SESSION",Session())
    calls=[]
    def run(command, **kwargs):
        calls.append(command)
        assert config["token"] not in " ".join(command)
        if "exec-in" in command:
            assert "run-as com.surpride.app --user 10" in command[-1]
            assert json.loads(kwargs["input"])==config
            return subprocess.CompletedProcess(command,0,b"",b"")
        return subprocess.CompletedProcess(command,0,str(len(json.dumps(config).encode())).encode(),b"")
    monkeypatch.setattr(dev.subprocess,"run",run)
    dev.provision("adb")
    assert len(calls)==2
    assert config["token"] not in capsys.readouterr().out
