#!/usr/bin/env python3
"""Run standard Trace Processor queries on one capture batch; retain private CSV evidence."""
import argparse
import csv
import io
import json
import os
from pathlib import Path
import re
import subprocess


def validate_atrace_header(text):
    counts = re.search(r"entries-in-buffer/entries-written:\s*(\d+)/(\d+)", text)
    if not counts or counts.group(1) != counts.group(2):
        raise ValueError("Missing atrace accounting or overwritten events")
    if re.search(r"LOST \d+ EVENTS|CPU.*LOST", text):
        raise ValueError("atrace reports lost events")


def validate_owned_rows(rows, pid):
    if not rows or {row["pid"] for row in rows} != {str(pid)}:
        raise ValueError(f"Missing samples/frames or unexpected process attribution: {rows}")


def validate_frames(rows, pid):
    validate_owned_rows(rows, pid)
    if any(int(row["frames"]) <= 0 or int(row["incomplete"]) != 0 for row in rows):
        raise ValueError(f"Empty or incomplete app frames: {rows}")


def validate_focus(rows, kinds, expected):
    eligible = [row for row in rows if row["callbacks"] == "1"
                and row["destination_kind"] in {f"P44:focus:{kind}" for kind in kinds}
                and row["callback_ms"] not in ("[NULL]", "", None)]
    if len(eligible) != expected:
        raise ValueError(f"Expected {expected} unambiguous {kinds} focus callbacks, found {len(eligible)}")


def parse_metadata(text):
    required = {"mode", "package", "pid", "runs", "keycodes"}
    metadata = {}
    for line in text.splitlines():
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        if key in required and key in metadata:
            raise ValueError(f"Duplicate capture identity field: {key}")
        metadata[key] = value
    if missing := required - metadata.keys():
        raise ValueError(f"Missing capture identity fields: {sorted(missing)}")
    return metadata


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--trace-processor", required=True, type=Path)
    parser.add_argument("--focus-kind", action="append", choices=("guide", "channel", "control", "rail"))
    parser.add_argument("--focus-count", type=int)
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    directory = args.directory
    (directory / "analysis.json").unlink(missing_ok=True)
    metadata = parse_metadata((directory / "capture-metadata.txt").read_text())
    pid = int(metadata["pid"])
    mode = metadata["mode"]
    if mode not in ("atrace", "causal", "frames", "cpu", "native-allocations"):
        raise ValueError(f"Unknown capture mode: {mode}")
    expected = len(metadata["keycodes"].split())
    if bool(args.focus_kind) != (args.focus_count is not None):
        parser.error("--focus-kind and --focus-count must be supplied together")
    if args.focus_kind and (mode not in ("atrace", "causal") or not 1 <= args.focus_count <= expected):
        parser.error("Focus validation requires causal/atrace and a positive count no greater than input count")
    extension = "atrace" if mode == "atrace" else "perfetto-trace"
    queries = Path(__file__).parent
    results = []
    for run in range(1, int(metadata["runs"]) + 1):
        trace = directory / f"run-{run}.{extension}"
        if mode == "atrace":
            validate_atrace_header(trace.read_text())

        def query(name, sql):
            command = [str(args.trace_processor), str(trace), "-Q", sql]
            result = subprocess.run(command, capture_output=True, text=True, timeout=90)
            (directory / f"run-{run}-{name}.stderr").write_text(result.stderr)
            if result.returncode:
                raise RuntimeError(f"Trace Processor failed for {trace.name}: see {name}.stderr")
            (directory / f"run-{run}-{name}.csv").write_text(result.stdout)
            return list(csv.DictReader(io.StringIO(result.stdout)))

        health = query("health", """
            SELECT name,severity,value FROM stats WHERE value!=0 AND
            (severity!='info' OR name IN ('traced_buf_bytes_overwritten','traced_buf_chunks_discarded'))
        """)
        if health:
            raise ValueError(f"Unhealthy trace {trace.name}: {health}")
        if mode in ("atrace", "causal"):
            inputs = query("inputs", """
                SELECT p.pid,count(*) AS events FROM slice s
                JOIN thread_track tt ON tt.id=s.track_id JOIN thread t USING(utid)
                JOIN process p USING(upid) WHERE s.name='P44:input:down' GROUP BY p.pid
            """)
            if inputs != [{"pid": str(pid), "events": str(expected)}]:
                raise ValueError(f"Unexpected input coverage/PID in {trace.name}: {inputs}")
            for name in ("latency", "work", "states", "phases", "gc"):
                rows = query(name, (queries / f"{name}.sql").read_text())
                if name == "latency" and args.focus_kind:
                    validate_focus(rows, args.focus_kind, args.focus_count)
        if mode in ("causal", "frames"):
            validate_frames(query("frames", (queries / "frames.sql").read_text()), pid)
        if mode == "cpu":
            samples = query("sample-coverage", """
                SELECT p.pid,s.unwind_error,count(*) AS samples FROM perf_sample s
                JOIN thread t USING(utid) JOIN process p USING(upid)
                GROUP BY p.pid,s.unwind_error
            """)
            validate_owned_rows(samples, pid)
            query("samples", (queries / "samples.sql").read_text())
        if mode == "native-allocations":
            validate_owned_rows(
                query("native-allocations", (queries / "native-allocations.sql").read_text()), pid,
            )
        results.append({"trace": trace.name, "health": "passed", "pid": pid, "mode": mode,
                        "focus_contract": {"kinds": args.focus_kind, "count": args.focus_count}
                        if args.focus_kind else "not_checked"})
    (directory / "analysis.json").write_text(json.dumps(results, indent=2) + "\n")
    print(json.dumps(results, indent=2))


if __name__ == "__main__":
    main()
