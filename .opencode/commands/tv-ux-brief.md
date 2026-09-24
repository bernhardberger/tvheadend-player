---
description: Produce one Opus pre-implementation TV design brief from supplied requirements and baseline evidence.
agent: build
---

Before dispatching `tv-ux-brief`, run
`./review-provider-route.sh select eligible`. Only successful stdout `opus`
permits its configured Opus model. Otherwise dispatch the same `tv-ux-brief`
role with an Astra model override (`openai/gpt-6-astra#xhigh`) as documented in
`docs/ai-engineering-harness.md`; preserve any explicit non-substitutable gate.
Pass the design contract below to that read-only role.

Use `$ARGUMENTS` as the complete design-brief contract. It must name the exact
surface, user goal, required states, navigation constraints, canvas or device,
applicable product rules, and every exact baseline evidence path. Do not inspect
an implementation diff or produce several equivalent concepts. Return one
preferred direction and the exact evidence matrix required for final review.
