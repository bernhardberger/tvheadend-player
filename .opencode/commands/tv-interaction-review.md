---
description: Run a scoped audit or closure with the read-only TV interaction code reviewer.
agent: tv-interaction-reviewer
subtask: true
---

Use `$ARGUMENTS` as the complete review contract. It must name `mode=audit` or
`mode=closure`, the exact TV interaction slice and acceptance criteria, included
source and test paths, exclusions, and, for closure, prior finding IDs and the
delta since audit. Never default an empty or ambiguous contract to the complete
dirty worktree.

Review focus graphs, D-pad and key cycles, Back, layer ownership, semantics,
accessibility implementation, safe bounds, and UI-test truthfulness. Do not
duplicate Android runtime, playback-transport, security, native, release, or
visual-design review. Do not edit, run a device, or infer visual quality from
source. Return the disposition and stable finding IDs first.
