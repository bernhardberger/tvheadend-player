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
assert_equal astra "$(route_for "$five_hour_guard")" '5h guard is strict'
assert_equal astra "$(route_for "$weekly_guard")" '7d guard is strict'
assert_equal astra "$(route_for 'not-json')" 'malformed telemetry fails closed'
assert_equal astra "$(route_for '{"ok":true,"configured":true,"usage":{"windows":{"5h":{"remainingPercent":101},"7d":{"remainingPercent":6}}}}')" 'out of range telemetry fails closed'
assert_equal astra "$(route_for '{"ok":true,"configured":true,"usage":{"windows":{"5h":{"remainingPercent":NaN},"7d":{"remainingPercent":6}}}}')" 'nonstandard numeric telemetry fails closed'
assert_equal astra "$(route_for '{"ok":true,"configured":true,"usage":{"windows":{"5h":{"remainingPercent":21}}}}')" 'missing window fails closed'
assert_equal astra "$(OPENCHAMBER_ENV_FILE=/nonexistent/p24-quota-fixture "$SELECTOR" select eligible 2>/dev/null)" 'eligible route probes and fails closed without credentials'
assert_equal astra "$("$SELECTOR" select routine)" 'routine route uses Astra'

python3 -m json.tool "$CONFIG" >/dev/null || fail 'OpenCode config is invalid JSON'
TESTS=$((TESTS + 1))

python3 - "$CONFIG" <<'PY' || exit 1
import json
import sys
from pathlib import Path

config_path = Path(sys.argv[1])
config = json.loads(config_path.read_text(encoding="utf-8"))
for name, agent in config.get("agent", {}).items():
    if "model" in agent and not isinstance(agent["model"], str):
        raise SystemExit(f"{name}: model must be a string")
    if "steps" in agent and (not isinstance(agent["steps"], int) or agent["steps"] < 1):
        raise SystemExit(f"{name}: steps must be positive")
PY
TESTS=$((TESTS + 1))

if grep -Eq 'set[[:space:]]+-a|mktemp|curl[[:space:]]|REVIEW_ROUTE_TEST' "$SELECTOR"; then
  fail 'selector contains an unsafe credential or test-fixture path'
fi
TESTS=$((TESTS + 1))

printf 'PASS: %d app review-routing assertions\n' "$TESTS"
