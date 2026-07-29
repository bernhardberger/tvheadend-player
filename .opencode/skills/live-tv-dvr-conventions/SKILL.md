---
name: live-tv-dvr-conventions
description: Use for TVHeadend channels, channel scopes, EPG timelines, programme details, DVR recordings, schedules, archive folders, recording actions, or recording playback behavior.
---

# Live TV And DVR Conventions

Use `docs/README.md` to select the current plan for the requested channel, EPG,
or DVR work. Read the relevant behavior in `docs/appliance-mode-spec.md` and the
matching sections of `docs/appliance-mode-plan.md` only when the change affects
appliance behavior or a contract documented there. This skill supplies a safe
method, not a second copy of product requirements.

## Preserve domain boundaries

- Distinguish browse focus, preview state, confirmed selection, and playback.
  Persist only a channel actually sent to the player.
- Preserve TVHeadend channel order, numbers, tags, identifiers, EPG metadata,
  recording state, and server-relative hierarchy unless a tested product rule
  explicitly transforms them.
- Treat TVHeadend access control as the visibility boundary. Client grouping and
  filtering are presentation, not authorization.
- Keep EPG navigation on one coherent channel/time model. Programme selection
  opens details before playback or recording actions.
- Keep playable archive, active/future schedule, and failed/cancelled problems
  semantically separate. Do not infer playability from a label or folder alone.
- Require the specified safe confirmation flow for recording, stop, and delete
  operations. Restore the invoking row's focus after a details/action surface
  closes.
- Preserve mode, hierarchy, scroll, and focused-item context across recording
  playback when the specification requires a warm return.

Do not change TVHeadend accounts, permissions, tuners, recording storage, stream
profiles, or recordings on a real server from client implementation work without
explicit user approval.

## Change behavior safely

1. State the user-visible invariant and classify the change as generic,
   product-specific, appliance-specific, or mixed.
2. Locate the existing pure policy and repository boundary before editing UI.
3. Write the failing JVM policy/repository test first. Add Compose coverage only
   for focus, semantics, geometry, or interaction that requires it.
4. Make the minimum implementation change; avoid a new abstraction for a
   one-screen rule.
5. If the change touches Media3, HTSP streaming, stream readers, or playback
   lifecycle, also apply `media3-htsp-playback-safety`.
6. Run the focused tests and `./tools/verify`; list any remaining physical-TV or
   live-server validation separately.

Use `android-tv-compose-ux` for the presentation and focus layer. Do not replace
the project's details-first EPG/DVR interaction with a touch-first mobile pattern
or make focus itself perform destructive or tuning actions.

This domain skill does not replace Kotlin or Compose implementation guidance.
Load `kotlin-flow-state-event-modeling` and
`kotlin-coroutines-structured-concurrency` when changing repository streams or
command lifecycles. Load `compose-state-holder-ui-split`,
`compose-state-hoisting`, and `compose-side-effects` when changing screen state
or event collection. Preserve explicit lifecycle owners, treat Channel delivery
as non-durable, and avoid broad architecture churn.
