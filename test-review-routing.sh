#!/usr/bin/env bash

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SELECTOR="$ROOT/review-provider-route.sh"
CONFIG="$ROOT/.opencode/opencode.json"
TESTS=0

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

assert_equal() {
  local expected="$1" actual="$2" label="$3"
  [[ "$actual" == "$expected" ]] || fail "$label: expected '$expected', got '$actual'"
  TESTS=$((TESTS + 1))
}

route_for() {
  printf '%s' "$1" | "$SELECTOR" evaluate-fixture 2>/dev/null
}

healthy='{"ok":true,"configured":true,"usage":{"windows":{"5h":{"remainingPercent":21},"7d":{"remainingPercent":6}}}}'
five_hour_guard='{"ok":true,"configured":true,"usage":{"windows":{"5h":{"remainingPercent":20},"7d":{"remainingPercent":100}}}}'
weekly_guard='{"ok":true,"configured":true,"usage":{"windows":{"5h":{"remainingPercent":100},"7d":{"remainingPercent":5}}}}'

assert_equal opus "$(route_for "$healthy")" 'quota above both UX guards'
assert_equal sol "$(route_for "$five_hour_guard")" '5h guard is strict'
assert_equal sol "$(route_for "$weekly_guard")" '7d guard is strict'
assert_equal sol "$(route_for 'not-json')" 'malformed telemetry fails closed'
assert_equal sol "$("$SELECTOR" select routine)" 'routine work is Sol-only'

python3 -m json.tool "$CONFIG" >/dev/null || fail 'OpenCode config is invalid JSON'
TESTS=$((TESTS + 1))

python3 - "$CONFIG" <<'PY' || exit 1
import json
import sys
from pathlib import Path

config_path = Path(sys.argv[1])
config = json.loads(config_path.read_text(encoding="utf-8"))
expected = {
    "android-reviewer": ("openai/gpt-6-astra", "medium", 45),
    "app-review-muse": ("openrouter/meta/muse-spark-1.3-contributor", "xhigh", 45),
    "tv-evidence-curator": ("openai/gpt-5.6-terra", "medium", 25),
    "tv-ux-brief": ("anthropic/claude-opus-5", "high", 35),
    "tv-ux-reviewer": ("anthropic/claude-opus-5", "medium", 40),
    "claude-audit-lead": ("anthropic/claude-opus-5", "high", 45),
}
for name, values in expected.items():
    agent = config["agent"][name]
    actual = (agent["model"], agent["variant"], agent["steps"])
    if actual != values:
        raise SystemExit(f"{name}: expected {values!r}, got {actual!r}")
    if config["permission"]["task"].get(name) != "allow":
        raise SystemExit(f"{name}: root task route is not allowed")

for name in ("tv-ux-brief", "tv-ux-reviewer", "claude-audit-lead"):
    text = (config_path.parent / "agents" / f"{name}.md").read_text(encoding="utf-8")
    metadata = text.split("---", 2)[1]
    for unsupported in ("temperature:", "top_p:", "top_k:"):
        if unsupported in metadata:
            raise SystemExit(f"{name}: unsupported Opus sampling option {unsupported}")
    if "<tone_preference>" not in text:
        raise SystemExit(f"{name}: missing response-length calibration")

muse = (config_path.parent / "agents" / "app-review-muse.md").read_text(encoding="utf-8")
for verdict in ("BLOCKING", "NON_BLOCKING", "CLEAN", "INSUFFICIENT_EVIDENCE"):
    if f"`{verdict}`" not in muse:
        raise SystemExit(f"app-review-muse: missing verdict {verdict}")

policy = (config_path.parents[1] / "AGENTS.md").read_text(encoding="utf-8")
if "The Muse reviewer field test is complete; do not invoke `app-review-muse`." not in policy:
    raise SystemExit("AGENTS.md: missing retired Muse field-test route")
if "routine, documentation, test-only, and\nconfiguration-only changes are Astra-only." not in policy:
    raise SystemExit("AGENTS.md: missing routine Astra-only route")
PY
TESTS=$((TESTS + 1))

if grep -Eq 'set[[:space:]]+-a|mktemp|curl[[:space:]]|REVIEW_ROUTE_TEST' "$SELECTOR"; then
  fail 'selector contains an unsafe credential or test-fixture path'
fi
TESTS=$((TESTS + 1))

printf 'PASS: %d app review-routing assertions\n' "$TESTS"
