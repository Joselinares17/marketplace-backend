"""Run open-source security scanners; publish only safe outcome summaries."""
import json
import os
from pathlib import Path
import subprocess
import shutil
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]


def executable(name):
    return str(ROOT / ".ci-tools" / (name + (".exe" if os.name == "nt" else "")))


def scan(kind):
    output = ROOT / "quality-evidence"
    output.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="quality-scan-") as temp:
        report = Path(temp) / "report.json"
        if kind == "secrets":
            candidate = json.loads((output / "candidate.json").read_text())
            command = [executable("gitleaks"), "git", "--no-banner", "--redact=100",
                       "--report-format=json", "--report-path=" + str(report),
                       "--log-opts=" + candidate["scan_range"], "."]
        elif kind == "snapshot":
            snapshot = Path(temp) / "snapshot"
            snapshot.mkdir()
            paths = subprocess.check_output(
                ["git", "ls-files", "-z", "--cached", "--others", "--exclude-standard"], cwd=ROOT
            ).decode().split("\0")
            for name in set(paths) - {""}:
                source = ROOT / name
                destination = snapshot / name
                if source.is_symlink():
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    destination.write_text(os.readlink(source))
                elif source.is_file():
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copyfile(source, destination)
            command = [executable("gitleaks"), "dir", "--no-banner", "--redact=100",
                       "--report-format=json", "--report-path=" + str(report), str(snapshot)]
        elif kind == "dependencies":
            command = [executable("osv-scanner"), "scan", "source",
                       "--lockfile=marketplace-backend/target/dependency-bom.cdx.json", "--no-ignore",
                       "--format=json", "--output-file=" + str(report)]
        else:
            raise ValueError("Unknown scanner.")
        result = subprocess.run(command, cwd=ROOT, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                                timeout=600)
        count = None
        packages = None
        advisories = []
        if report.exists():
            data = json.loads(report.read_text())
            if kind in {"secrets", "snapshot"}:
                count = len(data)
            else:
                if not isinstance(data.get("results"), list):
                    raise ValueError("Unexpected scanner report structure.")
                bom = json.loads((ROOT / "marketplace-backend/target/dependency-bom.cdx.json").read_text())
                packages = len(bom.get("components", []))
                if not packages or not all(component.get("purl") for component in bom["components"]):
                    raise ValueError("Dependency evidence requires resolved components with package URLs.")
                count = sum(len(package.get("vulnerabilities", []))
                            for group in data.get("results", []) for package in group.get("packages", []))
                advisories = sorted({vulnerability["id"] for group in data["results"]
                                     for package in group.get("packages", [])
                                     for vulnerability in package.get("vulnerabilities", [])})
        (output / (kind + ".json")).write_text(json.dumps({
            "control": kind, "exit_code": result.returncode, "findings": count,
            "resolved_components": packages, "advisory_ids": advisories,
            "status": "passed" if result.returncode == 0 and count == 0 else "failed"
        }, indent=2) + "\n")
        if result.returncode != 0 or count != 0:
            print(kind + " check failed; inspect the sanitized summary. No diagnostic payload was printed.")
            return 1
        print(kind + " check passed.")
        return 0


if __name__ == "__main__":
    sys.exit(scan(sys.argv[1]))
