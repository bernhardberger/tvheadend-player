---
description: Run an Astra final TV visual review or focused closure on a curated current evidence manifest.
agent: build
---

Dispatch the configured Astra `tv-ux-reviewer` directly with its default effort.
This is Astra coverage and does not satisfy an explicit non-substitutable Opus
gate; report that gap to its authority owner (central for admitted work) rather
than waiving it. Actual Opus dispatches
still require a fresh `./review-provider-route.sh select eligible` result of
`opus`. Pass the screenshot-first contract below to the reviewer rather than
performing its review in the implementing primary.

Use `$ARGUMENTS` as the complete visual-evidence contract. It must name
`mode=review` or `mode=closure`; the exact surface, states, visual
goal, and acceptance criteria; the canvas or device; and every exact current and
historical evidence path from the curator manifest. Closure must include prior `UX-` finding IDs and
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
