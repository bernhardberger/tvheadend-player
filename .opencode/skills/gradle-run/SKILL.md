---
name: gradle-run
description: Use when planning to execute Gradle through `gradle`, `./gradlew`, or a custom `gradlew*` wrapper script, or diagnosing a Gradle build, check, test, lint, warning, or failure.
license: Apache-2.0
metadata:
  source: "chrisbanes/skills@ded78abbe5a170c9ca0497b614f63c1a872d9f8e"
  modifications: "Standard bounded Gradle execution without workflow ledger; live-test isolation in gradlew"
---

# Gradle Run

Run the smallest owning Gradle task directly, with `--no-daemon --console=plain
--no-scan`. Run `./tools/verify` for the final application gate; it supplies those
flags. Compilation of Android tests is not execution on a device.

On the shared Linux host, bound the command with GNU `timeout`, without
`--foreground`, so interruption/timeout reaches the process group. For example:

```bash
timeout --kill-after=5s 20m ./gradlew :app:testDebugUnitTest --no-daemon --console=plain --no-scan
timeout --kill-after=5s 30m ./tools/verify
```

Use an owner-only temporary log for complete output (`umask 077` and `mktemp`).
Record the command and its actual exit status. Read only relevant diagnostics or
test reports; do not paste full logs. Output can contain secrets: environment
filtering is not log redaction. Never put credentials in arguments or properties.
Timeout exits 124 (or 137 after forced termination), not a test pass. If cancelling
a tool invocation, signal its timeout process and wait for cleanup before another
build. Do not use detached builds, `tee` without pipefail, or ignore exit codes.

`gradlew` retains the shared build lock and removes
`TVHEADEND_SOAK_CREDENTIALS_FILE` and `TVHEADEND_SOAK_NODVR_CREDENTIALS_FILE` by
default. Only an explicitly authorized live-test command may set
`GRADLE_RUN_ALLOW_LIVE_TESTS=1`. Keep Gradle state in disk-backed `$HOME/.gradle`.

No workflow ID, question, fingerprint ledger, create or finish command is needed.
Reuse successful checks for unchanged relevant state. Investigate a repeated
failure before retrying; use source/test evidence rather than a new question string.
Retain only useful private diagnostics and do not delete earlier delivery evidence.

## Provenance

This guidance supersedes the compact-output workflow imported from
`chrisbanes/skills@ded78abbe5a170c9ca0497b614f63c1a872d9f8e`. The imported wrapper
and its ledger tests were removed in favor of Gradle and GNU coreutils. The
bundled Apache 2.0 license and upstream attribution remain preserved.
