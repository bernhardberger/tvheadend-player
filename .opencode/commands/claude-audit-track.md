---
description: Run one isolated Claude architecture or dependency audit track
agent: claude-audit-lead
subtask: true
---

Use `$ARGUMENTS` as a neutral audit contract. It needs a bounded question,
allowed paths or sources, exclusions, required output, and a stop condition.

Use the configured Claude routes. Do not silently substitute another model. Do
not ask any task to repeat route, model, variant, step, revision, quota, or
provenance attestations. Do not query or gate on quota; OpenCode handles provider
quota exhaustion and continuation.

Delegate only sizeable independent work that improves the answer. There is no
required child count. Give each child a simple self-contained packet using the
same five fields above. Never retry a child automatically after refusal, failure,
or a partial answer. Use supported evidence already returned and state the gap.

Do not pass another track's analysis or conclusions into this track before both
independent reports are complete. Neutral product constraints and raw operator
facts are allowed. Do not pass one child's conclusions to another child. Luna
may be used for optional mechanical path lookup.

Return direct evidence, inferences, alternatives, uncertainties, and precise
gaps. Clearly identify partial results. Cross-review is optional only after both
independent reports are complete; no literal seal markers are required.
