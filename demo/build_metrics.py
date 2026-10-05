"""Publish only the synthetic report produced by this checkout's successful tests."""
import argparse
import json
import re
from datetime import datetime, timezone
from pathlib import Path


def build(report, commit, run_id):
    if not re.fullmatch(r"[0-9a-f]{40}", commit) or not re.fullmatch(r"[0-9]+", run_id):
        raise ValueError("A full commit SHA and numeric GitHub run ID are required")
    if report.get("liveModel") is not False or report.get("dataset") != "synthetic-eval-v1":
        raise ValueError("Only the versioned deterministic corpus is supported")
    if type(report.get("cases")) is not int or report["cases"] != 6:
        raise ValueError("The current corpus must contain six cases")
    keys = ("statusAccuracy", "sourcePresenceAccuracy", "supportedTermAccuracy")
    for key in keys:
        value = report.get(key)
        if type(value) not in (int, float) or not 0 <= value <= 1:
            raise ValueError("Invalid metric: " + key)
    return {**{key: report[key] for key in ("dataset", "cases", "liveModel", *keys)},
            "limitations": "Deterministic fake models; semantic grounding and real-model latency are not measured",
            "provenance": {"commit": commit, "runUrl": "https://github.com/ErikaMendes89/customer-support-resolution-agent-/actions/runs/" + run_id,
                           "generatedAt": datetime.now(timezone.utc).isoformat()}}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--run-id", required=True)
    args = parser.parse_args()
    result = build(json.loads(args.report.read_text()), args.commit, args.run_id)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
