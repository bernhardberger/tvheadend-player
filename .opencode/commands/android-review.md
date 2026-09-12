---
description: Run a scoped audit or closure with the read-only Android runtime and TV interaction reviewer
agent: android-reviewer
subtask: true
---

Use `$ARGUMENTS` as the complete review contract. It must name `mode=audit` or
`mode=closure`, the exact slice and acceptance criteria, included paths,
exclusions, caller-inlined hard requirements, verification evidence and, for
closure, the prior finding IDs and delta since the audit.
Never default an empty or ambiguous contract to the complete dirty worktree;
report what the primary must supply instead.

Review the frozen packet under the loaded `android-reviewer` scope, restrictions
and verdict contract. Report missing evidence under that contract; the command
does not turn a runtime review into screenshot-based design acceptance.
