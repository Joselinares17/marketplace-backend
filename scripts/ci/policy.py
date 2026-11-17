"""Validate immutable event provenance and integration candidate."""
import json
import os
from pathlib import Path
import re
import subprocess
import sys

REPOSITORY = "Joselinares17/marketplace-backend"
SHA = re.compile(r"[0-9a-f]{40}")


def validate(event_name, event, repository, ref, candidate):
    if repository.lower() != REPOSITORY.lower() or not SHA.fullmatch(candidate):
        raise ValueError("Unexpected repository or candidate.")
    if event_name == "push":
        if ref != "refs/heads/dev" or event["repository"]["full_name"].lower() != repository.lower():
            raise ValueError("Push must belong to the expected development branch.")
        if event["after"] != candidate:
            raise ValueError("Push candidate does not match the event.")
        before = event["before"]
        if not SHA.fullmatch(before):
            raise ValueError("Invalid previous revision.")
        return before if before != "0" * 40 else None
    if event_name != "pull_request":
        raise ValueError("Unsupported event.")
    pr = event["pull_request"]
    if any(pr[side]["repo"]["full_name"].lower() != repository.lower() for side in ("head", "base")):
        raise ValueError("Promotions must originate in the expected repository.")
    if (pr["head"]["ref"], pr["base"]["ref"]) not in {("dev", "test"), ("test", "main")}:
        raise ValueError("Invalid promotion direction.")
    if not SHA.fullmatch(pr["head"]["sha"]) or not SHA.fullmatch(pr["base"]["sha"]):
        raise ValueError("Invalid promotion revisions.")
    return pr["base"]["sha"]


def main():
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
    candidate = os.environ["GITHUB_SHA"]
    start = validate(os.environ["GITHUB_EVENT_NAME"], event, os.environ["GITHUB_REPOSITORY"],
                     os.environ["GITHUB_REF"], candidate)
    actual = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    if actual != candidate:
        raise ValueError("Checkout is not the event candidate.")
    if os.environ["GITHUB_EVENT_NAME"] == "pull_request":
        parents = subprocess.check_output(["git", "show", "-s", "--format=%P", candidate], text=True).split()
        if parents != [event["pull_request"]["base"]["sha"], event["pull_request"]["head"]["sha"]]:
            raise ValueError("Candidate is not the expected pull request merge.")
    evidence = Path("quality-evidence")
    evidence.mkdir(exist_ok=True)
    (evidence / "candidate.json").write_text(json.dumps({
        "sha": candidate, "event": os.environ["GITHUB_EVENT_NAME"],
        "run_id": os.environ["GITHUB_RUN_ID"], "attempt": os.environ["GITHUB_RUN_ATTEMPT"],
        "scan_range": (start + ".." if start else "") + candidate
    }, indent=2) + "\n")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError):
        print("Candidate rejected by promotion policy.", file=sys.stderr)
        sys.exit(1)
