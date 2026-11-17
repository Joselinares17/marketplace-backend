"""Install verified upstream CLI binaries; no third-party Python dependencies."""
import hashlib
import io
import json
import os
from pathlib import Path
import platform
import sys
import tarfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[2]
DESTINATION = ROOT / ".ci-tools"
PINS = json.loads(Path(__file__).with_name("tools.json").read_text())


def install():
    system = platform.system().lower()
    if system not in {"windows", "linux"} or platform.machine().lower() not in {"amd64", "x86_64"}:
        raise RuntimeError("Tool installer supports Linux/Windows amd64 only.")
    DESTINATION.mkdir(exist_ok=True)
    for name, pin in PINS.items():
        url, expected = pin[system]
        request = urllib.request.Request(url, headers={"User-Agent": "backend-quality"})
        with urllib.request.urlopen(request, timeout=120) as response:
            payload = response.read()
        if hashlib.sha256(payload).hexdigest() != expected:
            raise RuntimeError("Tool checksum mismatch: " + name)
        binary = name + (".exe" if system == "windows" else "")
        if url.endswith(".zip"):
            with zipfile.ZipFile(io.BytesIO(payload)) as archive:
                matches = [p for p in archive.namelist() if Path(p).name == binary]
                if len(matches) != 1:
                    raise RuntimeError("Unexpected tool archive layout.")
                payload = archive.read(matches[0])
        elif url.endswith(".tar.gz"):
            with tarfile.open(fileobj=io.BytesIO(payload), mode="r:gz") as archive:
                matches = [m for m in archive.getmembers() if Path(m.name).name == binary and m.isfile()]
                if len(matches) != 1:
                    raise RuntimeError("Unexpected tool archive layout.")
                payload = archive.extractfile(matches[0]).read()
        path = DESTINATION / binary
        path.write_bytes(payload)
        path.chmod(0o755)
        print(name + " " + pin["version"] + " verified")


if __name__ == "__main__":
    install()
