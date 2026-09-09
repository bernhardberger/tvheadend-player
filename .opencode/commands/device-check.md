---
description: Run an authorized bounded Android TV check with CLI-first install/capture.
agent: build
---

Use `$ARGUMENTS` as the requested bounded device task. If no task or exact target
was supplied, ask for it rather than operating a default device. Follow
`android-tv-device-testing` and `docs/android-tooling.md`: official CLI with
explicit `--device` for ordinary install/capture, ADB for missing CLI capabilities,
and retained specialized credential/release/acceptance gates. Respect the
configured production/test role without bypassing it, avoid
broad device dumps, and report which checks still require a physical remote
action or human-visible judgment.
