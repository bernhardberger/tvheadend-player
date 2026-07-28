---
name: android-tv-compose-ux
description: Use ONLY as the TVHeadend Player product overlay for Compose UI, Material for TV, D-pad focus, key dispatch, Back, safe areas, ten-foot readability, accessibility, or video-backed surfaces. Load matching focused Kotlin and Compose skills for implementation mechanics.
---

# Android TV Compose UX

This is a product overlay, not a general Compose implementation guide. Treat
`docs/tv-design-spec.md` as the normative visual and interaction specification.
Read its current contents, the TV UX requirements in `AGENTS.md`, and
`docs/ai-skills-audit-2026-07-28.md` before making a UI decision; do not copy
token values or mutable screen rules into this skill.

## Load focused implementation guidance

Before editing, load every matching reviewed skill:

- Focus movement, requesters, restoration, or key handling:
  `compose-focus-navigation` and `compose-side-effects`; add
  `compose-ui-testing-patterns` for changed behavior.
- State ownership or a screen/content boundary: `compose-state-hoisting`,
  `compose-state-holder-ui-split`, and `compose-side-effects`.
- Flow collection or coroutine ownership: `kotlin-flow-state-event-modeling`,
  `kotlin-coroutines-structured-concurrency`, and `compose-side-effects`.
- A reusable component API or variable visual region:
  `compose-modifier-and-layout-style` and `compose-slot-api-pattern`.
- Animation: `compose-animations`; add performance skills only when measurement
  identifies a recomposition or frame-rate problem.
- Local Compose state, deferred reads, stability, or branching: load the
  corresponding targeted or diagnostic skill from the audit matrix.

Do not mechanically add a `Modifier` parameter to a private one-use composable,
treat an ordinary calculation variable as persistent state, call a buffered
channel durable, or remember a changing callback under incomplete keys. Those
are audited limitations, not project conventions.

## Establish the interaction contract

Before editing, write down the affected surface's:

1. First-entry focus target and re-entry restoration target.
2. D-pad exits from every focusable region, including list and drawer edges.
3. OK, Back, hardware-key, and repeat behavior in each visible state.
4. Key events that reveal or replace UI and therefore must be consumed.
5. Loading, empty, unavailable, reconnecting, error, and destructive states.

Focus may preview content without committing it. Use the commit model specified
for the component in `docs/tv-design-spec.md`; do not persist or trigger a domain
action from incidental focus unless the specification explicitly says focus is
the commit.

## Implement the smallest conforming slice

- Check current official Google TV and Android TV design, Compose for TV, focus,
  and Material for TV guidance when choosing an interaction or component.
- Prefer the installed `androidx.tv:tv-material` component and semantics. Keep
  mobile Material only at the unsupported primitive boundary documented by the
  project.
- Let the shell own safe-area insets. Preserve focus overflow space and avoid
  scale on clipped list rows.
- Keep focus, selected, active, and disabled states distinguishable without
  relying on subtle color changes alone.
- Keep video visible through deliberate scrims and stable surfaces; dense guide
  and settings content needs more opacity than browsing chrome.
- Preserve localized long-text anchors, accessible labels, reading order, and
  deterministic focus restoration.

Generic focus guidance is only the implementation foundation. On TV, container
entry also requires the specified semantic identity, restoration behavior,
lateral-entry policy, and consumption of a key that reveals, replaces, or moves
focus to new UI.

## Verify without overstating evidence

Write a failing policy or Compose UI test first for changed behavior. Cover
initial focus, lateral entry, restoration, Back, same-event propagation, and
long-content geometry where relevant. Run the focused test and `./tools/verify`.

Use the `android-tv-device-testing` skill for runtime work. A passing build or
ADB screenshot does not prove SurfaceView visibility, focus feel, overscan,
readability over motion, remote-repeat behavior, or motion quality; identify
those as physical-TV checks.
