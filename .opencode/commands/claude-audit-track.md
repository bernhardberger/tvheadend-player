---
description: Run one isolated Claude audit track and return a sealed report
agent: claude-audit-lead
subtask: true
---

Use `$ARGUMENTS` as the complete neutral audit contract. It must include the
exact app revision, bounded goal, constraints, raw operator facts, included
paths, exclusions, evidence limits, stop condition, separate task packets for
local analysis and external research, and these exact runtime attestations:

- lead: `observed_route=anthropic/claude-opus-5`,
  `observed_provider=anthropic`, `observed_model=claude-opus-5`,
  `observed_variant=max`, `observed_mode=subagent`, `observed_steps=60`
- local: `observed_route=anthropic/claude-sonnet-5`,
  `observed_provider=anthropic`, `observed_model=claude-sonnet-5`,
  `observed_variant=xhigh`, `observed_mode=subagent`, `observed_steps=45`
- research: `observed_route=anthropic/claude-sonnet-5`,
  `observed_provider=anthropic`, `observed_model=claude-sonnet-5`,
  `observed_variant=high`, `observed_mode=subagent`, `observed_steps=35`
- redacted quota evidence with `quota_checked_at` in RFC 3339 UTC,
  `quota_max_age_minutes=30`, the applicable remaining percentages, and
  `quota_eligible=true`

Match every runtime field exactly. Reject a future, malformed, or
more-than-30-minute-old check. Reject missing, stale, ambiguous, or ineligible
runtime/quota evidence and stop without substitution. Do not silently select a
4.x Claude model, another provider, or a different configured variant.

Do not accept a Sol-track map, report, candidate list, or conclusion, or any
Grok/other-track generated material. Reject every Sol-, Grok-, or
other-track-generated input regardless of its label. Require provenance for
every supplied fact and evidence item; reject missing provenance and any
provenance that identifies another audit track. Construct separate task packets
for `claude-local-analysis` and `claude-external-research` from neutral inputs
only. Do not pass one child's output to another child. Any Luna retrieval must
use a third independent, track-local packet and must not be exported to another
audit track.

Require the local child to end with `SEALED_LOCAL_ANALYSIS` and the research
child to end with `SEALED_EXTERNAL_RESEARCH`. Reconcile only complete sealed
child results. Return direct evidence, inferences, alternatives, uncertainties,
and precise gaps, then end a complete Claude report with
`SEALED_CLAUDE_AUDIT`. Do not emit that final seal after a quota stop, model
ambiguity, partial child result, or exhausted evidence budget. Cross-review is a
separate optional phase only after both the Sol and Claude final reports have
independently sealed.
