---
description: Run a scoped audit or closure with the read-only Android runtime and TV interaction reviewer
agent: android-reviewer
subtask: true
---

Use `$ARGUMENTS` as the complete review contract. It must name `mode=audit` or
`mode=closure`, the exact slice and acceptance criteria, included paths,
exclusions, and, for closure, the prior finding IDs and delta since the audit.
Never default an empty or ambiguous contract to the complete dirty worktree;
report what the primary must supply instead.

Review only the supplied frozen packet, relevant scoped implementation and tests,
and verification evidence. Do not inspect Git status, edit, commit, push, or use
a device. Review Android runtime, cross-layer wiring, concurrency, playback,
resource ownership, security, native and release correctness, plus Compose for
TV focus, keys, Back, accessibility semantics, safe bounds, and UI-test
truthfulness. Do not perform screenshot-based visual design review. Follow the
reviewer's verdict contract. If no blocking issue exists, return `CLEAN` or
`NON_BLOCKING` and list only the remaining verification or physical-TV evidence
gates.
