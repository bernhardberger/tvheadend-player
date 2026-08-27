---
description: Run AI-harness, native-integrity, tool-policy, JVM, lint, Android-test compilation, APK, and identity verification.
agent: android-tv
---

Run `./tools/verify`, which invokes `./tools/check-ai-harness` exactly once. If it
fails, stop, preserve the exact error, diagnose the root cause, and fix only the
current slice. Summarize native provenance warnings, tool tests,
JVM/lint/Android-test results, APK identity/ABI/16 KB alignment, and Git status
without printing secrets. Do not claim release readiness while the native
release gate is blocked.
