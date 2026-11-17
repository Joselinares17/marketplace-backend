"""Retain test outcomes without stdout, properties, stack traces or rejected values."""
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
REQUIRED = {
    "ModuleArchitectureTests": 4, "ApiExceptionHandlerTests": 15, "SecurityHttpIT": 20,
    "RuntimeConfigurationTests": 21, "LoggingConfigurationTests": 11,
    "MigrationsIT": 2, "DurableEventsIT": 3, "MarketplaceBackendApplicationIT": 2,
}


def collect(root, destination):
    destination.mkdir(parents=True, exist_ok=True)
    summaries = []
    observed = {}
    for directory in ("surefire-reports", "failsafe-reports"):
        for path in sorted((root / "marketplace-backend" / "target" / directory).glob("TEST-*.xml")):
            suite = ET.parse(path).getroot()
            counts = {key: int(suite.get(key, "0")) for key in ("tests", "failures", "errors", "skipped")}
            name = suite.get("name", "").rsplit(".", 1)[-1]
            observed[name] = counts
            summaries.append({"suite": name, **counts})
            clean = ET.Element("testsuite", name=name, **{k: str(v) for k, v in counts.items()})
            for case in suite.findall("testcase"):
                # Drop parameter values from display names.
                test = ET.SubElement(clean, "testcase", name=case.get("name", "").split("(")[0],
                                     classname=suite.get("name", ""))
                for kind in ("failure", "error", "skipped"):
                    if case.find(kind) is not None:
                        ET.SubElement(test, kind, message="Outcome retained; diagnostic payload omitted.")
            ET.ElementTree(clean).write(destination / path.name, encoding="utf-8", xml_declaration=True)
    (destination / "tests.json").write_text(json.dumps(summaries, indent=2) + "\n")
    valid = all(name in observed and observed[name]["tests"] >= minimum
                and all(observed[name][key] == 0 for key in ("failures", "errors", "skipped"))
                for name, minimum in REQUIRED.items())
    return valid and all(not (s["failures"] or s["errors"] or s["skipped"]) for s in summaries)


if __name__ == "__main__":
    sys.exit(0 if collect(ROOT, ROOT / "quality-evidence") else 1)
