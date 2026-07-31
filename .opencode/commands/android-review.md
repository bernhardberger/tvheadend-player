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

Inspect Git status, the relevant scoped implementation and tests, and apply only
the local domain skills matching that scope. Do not edit, commit, push, or use a
device. Review Android runtime, cross-layer wiring, concurrency, playback,
resource ownership, security, native and release correctness, plus Compose for
TV focus, keys, Back, accessibility semantics, safe bounds, and UI-test
truthfulness. Do not perform screenshot-based visual design review. Follow the
reviewer's disposition and finding-ID contract. In closure mode, verify prior
findings and fix regressions without re-auditing unchanged or adjacent code. If
no blocking issue exists, return `PASS` or `ADVISORY` and list only the remaining
verification or physical-TV evidence gates.
