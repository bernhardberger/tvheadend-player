---
description: Run native-integrity, tool-policy, JVM, lint, Android-test compilation, APK, and identity verification.
agent: build
---

Load `gradle-run` and run `./tools/verify`. If it fails, preserve the exact error,
diagnose the root cause, and fix ordinary failures within the authorized task.
Recheck the affected state; stop only for a genuine authority or evidence blocker.
Summarize native provenance
warnings, tool tests, JVM/lint/Android-test results, APK identity/ABI/16 KB
alignment, and Git status without printing secrets. Do not claim release
readiness while the native release gate is blocked.
