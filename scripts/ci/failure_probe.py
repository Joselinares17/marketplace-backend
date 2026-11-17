"""Demonstrate a real test failure without editing the working tree."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]


def main():
    with tempfile.TemporaryDirectory(prefix="quality-failure-probe-") as temp:
        project = Path(temp) / "backend"
        project.mkdir()
        for name in ("pom.xml", "mvnw", "mvnw.cmd", ".mvn", "src"):
            source = ROOT / "marketplace-backend" / name
            if source.is_dir():
                shutil.copytree(source, project / name)
            else:
                shutil.copyfile(source, project / name)
        fixture = project / "src/test/java/example/quality/FailurePropagationTests.java"
        fixture.parent.mkdir(parents=True)
        fixture.write_text('''package example.quality;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
class FailurePropagationTests {
    @Test void rejectsTheCandidate() { Assertions.fail("Synthetic assertion failure"); }
}
''')
        wrapper = project / ("mvnw.cmd" if os.name == "nt" else "mvnw")
        wrapper.chmod(0o755)
        environment = dict(os.environ, SPRING_PROFILES_ACTIVE="ci")
        with (project / "probe.log").open("w") as log:
            result = subprocess.run([str(wrapper), "-B", "-ntp", "-o",
                                     "-Dtest=FailurePropagationTests", "test"],
                                    cwd=project, env=environment, stdout=log,
                                    stderr=subprocess.STDOUT, timeout=180)
        report = project / "target/surefire-reports/TEST-example.quality.FailurePropagationTests.xml"
        if not report.exists():
            raise RuntimeError("Probe did not execute its test; compilation failure is not sufficient.")
        suite = ET.parse(report).getroot()
        proven = result.returncode != 0 and suite.get("tests") == "1" and suite.get("failures") == "1"
        if not proven:
            raise RuntimeError("Expected test-failure propagation was not demonstrated.")
        destination = ROOT / "quality-evidence"
        destination.mkdir(exist_ok=True)
        (destination / "failure-probe.json").write_text(json.dumps({
            "test_executed": True, "tests": 1, "failures": 1,
            "maven_exit_code": result.returncode, "working_tree_modified": False
        }, indent=2) + "\n")
        print("Isolated JUnit assertion failed and Maven returned a failure, as required.")


if __name__ == "__main__":
    main()
