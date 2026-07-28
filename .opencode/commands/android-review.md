---
description: Run the read-only Android correctness and release-safety reviewer on the current change or supplied scope.
agent: android-reviewer
subtask: true
---

Review `$ARGUMENTS` when a scope was supplied; otherwise review the complete
current worktree diff. Inspect Git status, the relevant implementation and tests,
and apply the local domain skills that match the change. Do not edit, commit,
push, or use a device. Lead with concrete findings ordered by severity and include
file and line references. If there are no findings, say so and identify only the
remaining verification or physical-TV evidence gaps.
