import json
from contextlib import redirect_stdout
from io import StringIO
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

import evidence
import policy
import scan


class PromotionTests(unittest.TestCase):
    def event(self, source="dev", destination="test", fork=False):
        return {"pull_request": {
            "head": {"ref": source, "sha": "a"*40,
                     "repo": {"full_name": "someone/marketplace-backend" if fork else policy.REPOSITORY}},
            "base": {"ref": destination, "sha": "b"*40, "repo": {"full_name": policy.REPOSITORY}}}}

    def test_valid_promotions(self):
        for source, target in (("dev","test"), ("test","main")):
            with self.subTest(source=source):
                self.assertEqual(policy.validate("pull_request", self.event(source,target), policy.REPOSITORY,
                                                 "refs/pull/1/merge", "c"*40), "b"*40)

    def test_wrong_sources_and_reverse_sync_are_rejected(self):
        for source, target in (("main","test"), ("dev","main"), ("test","dev"), ("feature","test")):
            with self.subTest(source=source, target=target), self.assertRaises(ValueError):
                policy.validate("pull_request", self.event(source,target), policy.REPOSITORY,
                                "refs/pull/1/merge", "c"*40)

    def test_same_branch_name_from_fork_is_rejected(self):
        with self.assertRaises(ValueError):
            policy.validate("pull_request", self.event(fork=True), policy.REPOSITORY, "refs/pull/1/merge", "c"*40)

    def test_push_is_bound_to_dev_and_exact_sha(self):
        event = {"repository": {"full_name": policy.REPOSITORY}, "before":"a"*40, "after":"b"*40}
        self.assertEqual(policy.validate("push", event, policy.REPOSITORY, "refs/heads/dev", "b"*40), "a"*40)
        with self.assertRaises(ValueError):
            policy.validate("push", event, policy.REPOSITORY, "refs/heads/test", "b"*40)
        with self.assertRaises(ValueError):
            policy.validate("push", event, policy.REPOSITORY, "refs/heads/dev", "c"*40)


class EvidenceTests(unittest.TestCase):
    def test_missing_reports_cannot_pass(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.assertFalse(evidence.collect(root, root / "evidence"))

    def test_failed_test_is_retained_without_diagnostic_secrets(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            reports = root / "marketplace-backend/target/surefire-reports"
            reports.mkdir(parents=True)
            (reports / "TEST-example.xml").write_text('''<testsuite name="example" tests="1" failures="1">
                <properties><property name="secret" value="SYNTHETIC_PRIVATE_VALUE"/></properties>
                <testcase name="fails(SYNTHETIC_PRIVATE_VALUE)" classname="example">
                <failure>SYNTHETIC_PRIVATE_VALUE</failure></testcase>
                <system-out>SYNTHETIC_PRIVATE_VALUE</system-out></testsuite>''')
            output = root / "evidence"
            self.assertFalse(evidence.collect(root, output))
            retained = (output / "TEST-example.xml").read_text()
            self.assertNotIn("SYNTHETIC_PRIVATE_VALUE", retained)
            self.assertIsNotNone(ET.fromstring(retained).find("testcase/failure"))

    def test_workflow_has_no_unpinned_actions_or_privileged_events(self):
        workflow = (Path(__file__).resolve().parents[2] / ".github/workflows/ci.yml").read_text()
        import re
        actions = re.findall(r"uses: (\S+)", workflow)
        self.assertEqual(len(actions), 3)
        for action in actions:
            self.assertRegex(action, r"@[0-9a-f]{40}$")
        self.assertNotIn("pull_request_target:", workflow)
        self.assertNotIn("continue-on-error:", workflow)
        self.assertNotIn("id-token: write", workflow)


class ScannerTests(unittest.TestCase):
    def check_report(self, payload, exit_code):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            output = root / "quality-evidence"
            output.mkdir()
            (output / "candidate.json").write_text(json.dumps({"scan_range": "a"*40}))

            def run(command, **kwargs):
                if payload is not None:
                    report = next(arg.split("=", 1)[1] for arg in command if arg.startswith("--report-path="))
                    Path(report).write_text(json.dumps(payload))
                from subprocess import CompletedProcess
                return CompletedProcess(command, exit_code)

            with patch.object(scan, "ROOT", root), patch.object(scan.subprocess, "run", side_effect=run), redirect_stdout(StringIO()):
                result = scan.scan("secrets")
            return result, (output / "secrets.json").read_text()

    def test_findings_fail_even_if_tool_returns_success_without_printing_payload(self):
        result, summary = self.check_report([{"Secret": "SYNTHETIC_PRIVATE_VALUE"}], 0)
        self.assertEqual(result, 1)
        self.assertNotIn("SYNTHETIC_PRIVATE_VALUE", summary)
        self.assertEqual(json.loads(summary)["findings"], 1)

    def test_missing_report_or_tool_error_cannot_pass(self):
        for payload, code in ((None, 0), ([], 2)):
            with self.subTest(payload=payload, code=code):
                result, _ = self.check_report(payload, code)
                self.assertEqual(result, 1)


if __name__ == "__main__":
    unittest.main()
