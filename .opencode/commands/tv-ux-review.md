---
description: Run a screenshot-first TV design brief, visual review, or visual closure on supplied current evidence.
agent: tv-ux-reviewer
subtask: true
---

Use `$ARGUMENTS` as the complete visual-evidence contract. It must name
`mode=brief`, `mode=review`, or `mode=closure`; the exact surface, states, visual
goal, and acceptance criteria; the canvas or device; and every exact current and
historical evidence path. Closure must include prior `UX-` finding IDs and
matched updated captures.

Treat an image, inventory, or handoff as current only when its exact path is
supplied and `$ARGUMENTS` identifies it as current. Never glob, list, or search
the repository for additional visual evidence. Without current images, return
`EVIDENCE_REQUIRED`; do not perform a source-only UI code review.

Inspect rendered evidence before any exact supplied implementation path. Judge
visual hierarchy, alignment, spacing, typography, density, focus appearance,
color, contrast, scrims, action hierarchy, video primacy, consistency, ten-foot
usability, and Material for TV alignment. Give one preferred correction for each
blocking finding and an image-based closure condition. Do not audit focus/key/
Back implementation, runtime correctness, tests, playback internals, or release
safety. Separate static screenshot conclusions from physical-TV observations.
