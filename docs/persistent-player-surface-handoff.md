# Persistent player surface implementation handoff

> Written for a new implementation session with no access to the investigation
> that produced it. Read this file end to end before editing. Implement one
> numbered slice at a time and do not combine this work with unrelated player,
> shell, or decoder changes.

## Outcome

Make live and recording playback transitions between fullscreen player UI and
the operator shell visually immediate while preserving the accepted Media3/HTSP
session and decoder behavior.

There must be exactly one Media3 `PlayerView` for the foreground playback
session. `AppRoot` owns that view below navigation content. Fullscreen player
destinations and shell destinations render only UI above the same video plane.
The video view must not detach, move, fade, or be recreated during a warm
player-to-shell-to-player cycle.

Disable Navigation Compose's full-destination transition only when crossing
between a player destination and a non-player destination. Preserve the current
destination transition for shell-to-shell and player-to-player navigation.

This is a **mixed change**:

- Explicit `PlayerView` attach/release hygiene is generic and should remain
  separable for possible upstream contribution.
- The persistent warm surface, shell layering, one-shot warm return, and
  player/shell transition policy are product-specific.
- No appliance-specific behavior changes. HOME, GUIDE, startup, Simple TV, and
  accessibility-service policies are regression constraints only.

## Why the current implementation repaints

`PlayerSession` keeps one `ExoPlayer` and makes a same-service play request
idempotent, but the pixels do not stay attached to one Android surface:

- `AppRoot.kt` mounts `PlayerVideoSurface` only when playback is active and the
  current route is not a player route.
- `VideoPlayerScreen.kt` mounts another `PlayerVideoSurface` for live TV.
- `RecordingPlayerScreen.kt` mounts another one for recordings.
- `PlayerVideoSurface.kt` creates a new `PlayerView`, whose default video child
  is a `SurfaceView`.

Navigation therefore removes one `SurfaceView`, creates another, and points the
same player at the new surface. The player and audio can remain warm while video
briefly has nowhere ready to render. The plain `NavHost` also inherits
Navigation Compose 2.9.7's 700 ms fade-in/fade-out, making the handoff look even
slower and allowing outgoing and incoming focus trees to overlap.

Commit `1faa222` introduced the route-owned fullscreen surface as a diagnostic
for an intermittent decoder/motion issue. The diagnostic did not improve that
issue. The later architecture decision in `docs/appliance-mode-plan.md` already
requires the Media3 `PlayerView` to be mounted at the app root.

## Required reading and skills

Before implementation, read current versions of:

1. `AGENTS.md`
2. `docs/appliance-mode-spec.md`
3. `docs/appliance-mode-plan.md`
4. `docs/tv-design-spec.md`
5. `docs/codebase-audit-2026-07-23.md`
6. `docs/ai-skills-audit-2026-07-28.md`
7. `docs/device-targets.md` before any build or device operation
8. This handoff

Load these skills before editing the matching concerns:

- `android-tv-compose-ux`
- `media3-htsp-playback-safety`
- `compose-focus-navigation`
- `compose-side-effects`
- `compose-ui-testing-patterns`
- `compose-animations`
- `compose-modifier-and-layout-style`
- `compose-state-hoisting`
- `compose-state-holder-ui-split`

Load `kotlin-flow-state-event-modeling` and
`kotlin-coroutines-structured-concurrency` only if the implementation starts to
change Flow collection, `PlayerSession` commands, or coroutine ownership. The
planned implementation should not need those changes.

At implementation time, recheck the installed Navigation Compose 2.9.7
transition API and current official Media3 `PlayerView`, Android TV playback,
Compose focus, and TV app-quality guidance. Do not upgrade dependencies in this
work.

## Non-negotiable boundaries

- Do not alter the HTSP extractor, data sources, stream readers, timestamps,
  codecs, renderers, decoder selection, native AARs, Media3 version, timeshift
  behavior, or refresh-rate strategy.
- Keep `ExoPlayer` owned by the process-scoped `PlayerSession`. A route or
  `PlayerView` disposal must never call `PlayerSession.release()` or
  `ExoPlayer.release()`.
- Keep the Android `PlayerView` owned by the current Activity composition. Never
  store an Activity, `PlayerView`, or `SurfaceView` in `PlayerSession`, Koin, an
  object singleton, or a ViewModel.
- Keep exactly one `PlayerView` attached to the player. Do not temporarily ship
  both root and route-owned surfaces.
- Keep `SurfaceView`. Do not switch to `TextureView`, add a Compose surface-sync
  workaround, or use `PlayerView.switchTargetView()` in this architecture.
- Preserve explicit Stop as serialized teardown before route close.
- Preserve ordinary Back as navigation only: playback remains warm while the
  Activity remains foreground.
- Preserve `MainActivity.onStop()` as the hard boundary that stops playback on
  HOME/background transitions.
- Do not add background playback, a media foreground service, Picture-in-Picture,
  blur, a second full-screen scrim, or a new navigation framework.
- Do not redesign player controls, shell panels, navigation rail, channel
  drawer, playback options, recovery UI, or focus styling in this work.
- Do not infer SurfaceView continuity, HDR behavior, or motion quality from ADB
  screenshots or counters.

## Target composition and ownership

```text
MainActivity
└── AppRoot
    └── root Box
        ├── persistent PlayerVideoSurface       (zero or one)
        │   └── AndroidView(PlayerView/SurfaceView)
        ├── existing shell video treatment      (shell routes only)
        └── NavHost / active destination UI
            ├── live player overlay             (no PlayerView)
            ├── recording player overlay        (no PlayerView)
            └── shell content + navigation
```

The `PlayerVideoSurface` call must remain at one stable composition position.
Changing route state may change siblings above it, but must not key, move, or
replace the video call.

### Surface mounting truth table

Use one pure policy equivalent to:

```kotlin
hasActivePlayback || isPlayerRoute
```

| Destination | Session state | Root video host |
|---|---|---|
| Shell | `Idle` | Absent |
| Shell | `Starting`, `Playing`, `Recovering`, `Failed`, or `Finished` before teardown | Present |
| Live player | Any state, including `Idle` before the play effect runs | Present |
| Recording player | Any state, including `Idle` before preparation runs | Present |

Mounting on a player route even before session state changes prevents a new-play
entry from starting without a target surface. Keeping the condition true across
player to warm shell navigation preserves the same Android view instance.

On explicit Stop, the player route remains enough to keep the host mounted until
serialized teardown finishes and navigation closes the route. Once the shell is
visible and session state is `Idle`, the host may leave composition and detach.

### Native-view release contract

When `PlayerVideoSurface` actually leaves composition:

```kotlin
view.player = null
view.keepScreenOn = false
```

Wire this through `AndroidView.onRelease`. This removes `PlayerView` listeners
and clears only that view's video surface. It must not release the shared player.

The root video view is a background plane:

- `useController = false`
- not focusable and not focusable in touch mode
- absent from the accessibility interaction tree
- no click or D-pad behavior

Player overlays remain the only playback interaction and accessibility surface.

### Aspect-ratio ownership

The root surface continues to render from `AppRoot`'s lifecycle-aware
`PlayerSettingsStore` collection. Live and recording player UI may retain their
small local optimistic `aspectRatio` value for the options selection and Stats
label while the DataStore write completes. Do not hoist or redesign settings
state in the first implementation unless a failing test or visible G10 delay
shows that it is necessary.

Acceptance requires that selecting Fit, Fill 16:9, or Fill 4:3 updates the root
surface without a detach, black frame, or visible stale-selection interval. If
that fails, stop and make aspect ratio one explicitly hoisted effective value in
a separate follow-up slice rather than adding a second surface.

## Focus and key contract

The video plane never receives focus. Route UI must have exactly one focus,
semantics, and key owner immediately after a transition; the outgoing route must
not remain an invisible interaction owner during a crossfade.

### Shell

```text
First entry       -> active/selected content item
Re-entry          -> last focused item by stable semantic identity
Content Left      -> current global navigation destination
Drawer Right/OK   -> restored content item
Drawer Up/Down    -> adjacent destination, committed on focus
```

Settings retains its extra category layer. Existing `focusRestorer`, drawer
entry, safe-area, and push-drawer behavior remain unchanged.

### Player

- Player entry and warm re-entry keep the existing visible-controls initial
  target: primary transport when available, otherwise the existing fallback.
- A hidden-controls reveal consumes the complete revealing key cycle so the same
  OK/Down press cannot activate the newly focused control.
- Live channel drawer entry still focuses the current channel by stable ID.
- Recording hidden-seek and reveal behavior remains unchanged.
- Simple TV never reaches shell UI through Back.

### Back

Preserve the existing precedence inside each player: close details/options/stats,
cancel numeric input or drawer where applicable, hide visible controls, and only
then leave the player route warm. At shell root, preserve one consumed warm return
and the existing anti-loop policy. Stop clears the warm return. HOME/background
stops playback.

## Navigation transition policy

Create one pure route-edge classifier and unit-test it before wiring Compose.
Classify destinations by their exact top-level route segment, not broad text
matching:

- player family: `Routes.PLAYER`, `Routes.RECORDING_PLAYER`
- non-player family: Channels, EPG including category routes, Recordings,
  Settings, and Unlock

The policy is symmetric:

| Initial | Target | Transition |
|---|---|---|
| Player | Non-player | None |
| Non-player | Player | None |
| Non-player | Non-player | Existing default |
| Player | Player | Existing default |

Apply the result to all four `NavHost` paths:

- `enterTransition`
- `exitTransition`
- `popEnterTransition`
- `popExitTransition`

For player/non-player edges, both incoming and outgoing transitions are
`EnterTransition.None` / `ExitTransition.None`. For every other edge, explicitly
preserve the installed Navigation Compose 2.9.7 default 700 ms fade. Do not add
`AnimatedContent`, `Crossfade`, or a second animation around `NavHost`.

If the immediate UI swap feels harsh after physical review, a later change may
animate only a non-focusable shell scrim for roughly 150-180 ms. Do not delay the
new route's focus surface, semantics, or key ownership and do not include that
optional polish in the initial implementation.

## Implementation slices

Each slice starts with a failing focused test, ends with its focused tests, and
must leave the tree buildable. Do not commit unless the user explicitly asks.

### Slice 1 — Generic `PlayerView` lifecycle hygiene

Files:

- `app/src/main/java/at/bernhardberger/tvhplayer/ui/player/PlayerVideoSurface.kt`
- `app/src/androidTest/java/at/bernhardberger/tvhplayer/ui/player/PlayerVideoSurfaceLifecycleTest.kt` (new)

Write an instrumentation test that mounts `PlayerVideoSurface` with a local
test-owned ExoPlayer, locates and retains the native `PlayerView` from the root
Android view tree, then removes the composable. The test must fail initially and
then prove:

- the mounted view has the supplied player;
- it has no controller and cannot take focus;
- it is not an accessibility interaction target;
- after removal, `player == null` and `keepScreenOn == false`;
- the ExoPlayer itself has not been released by view cleanup.

Use `AndroidView.onRelease` for cleanup. Release the test-owned ExoPlayer in test
cleanup. Do not add a public factory or callback solely to let the test observe
the view; traverse the test Activity/root view tree instead.

This slice does not move the surface and should be independently upstreamable in
principle.

Focused gate:

```bash
./gradlew compileDebugAndroidTestKotlin --no-daemon
```

Run the instrumentation test only on the exact configured G10 test device. If it
is only compiled, report that accurately.

### Slice 2 — One persistent root surface

Files:

- `app/src/main/java/at/bernhardberger/tvhplayer/core/PlaybackSurfacePolicy.kt`
- `app/src/test/java/at/bernhardberger/tvhplayer/core/PlaybackSurfacePolicyTest.kt`
- `app/src/main/java/at/bernhardberger/tvhplayer/ui/AppRoot.kt`
- `app/src/main/java/at/bernhardberger/tvhplayer/ui/player/VideoPlayerScreen.kt`
- `app/src/main/java/at/bernhardberger/tvhplayer/ui/player/RecordingPlayerScreen.kt`
- the Slice 1 lifecycle instrumentation test if it can cover identity without a
  production-only test seam

First replace the current browse-only policy assertions with the complete truth
table above. Rename the policy to describe mounting a persistent surface rather
than using a warm browse surface.

Then make the migration atomically:

1. Use the new policy at the existing stable root surface position in `AppRoot`.
2. Remove `PlayerVideoSurface` from `VideoPlayerScreen`.
3. Remove `PlayerVideoSurface` from `RecordingPlayerScreen`.
4. Remove now-unused surface imports and the route-level
   `debugVideoBackdropVisible` parameters/call arguments. The debug backdrop is
   rendered by the single root surface in both player and shell states.
5. Keep route-owned player references used by controls, seeking, options, and
   diagnostics. Do not mistake removal of the view for removal of player access.
6. Keep current root shell treatment and z-order unchanged; do not add another
   scrim.

Extend the instrumentation test by driving the mounting policy through these
state changes in one composition:

```text
player route + idle
-> shell + active playback
-> player route + active playback
```

Capture the native `PlayerView` after the first state and assert reference
identity after each warm transition. Then move to shell + idle and assert that
the captured view is detached through the Slice 1 release contract. This test
may reproduce the production conditional around `PlayerVideoSurface`; do not
extract a one-use production wrapper only for testing.

Focused gate:

```bash
./gradlew testDebugUnitTest --tests '*PlaybackSurfacePolicyTest' --no-daemon
./gradlew compileDebugAndroidTestKotlin --no-daemon
```

### Slice 3 — Remove player/shell destination crossfades

Files:

- a small pure policy file under
  `app/src/main/java/at/bernhardberger/tvhplayer/core/`
- its JVM test under `app/src/test/java/at/bernhardberger/tvhplayer/core/`
- `app/src/main/java/at/bernhardberger/tvhplayer/ui/AppRoot.kt`
- `app/src/androidTest/java/at/bernhardberger/tvhplayer/ui/PlayerShellTransitionTest.kt` (new, or the nearest existing navigation test)

Test the complete route-edge matrix first, including route templates and EPG
category routes. Then wire the classifier into all four `NavHost` transition
callbacks.

Add a minimal Navigation Compose instrumentation harness that uses the production
classifier and focusable tagged player/shell content. Drive both forward and pop
navigation with the test clock paused and prove that on a player/non-player edge:

- the outgoing destination is not retained as an interactive semantic node;
- the target owns the only focused node after the navigation frame settles;
- no 700 ms destination fade must complete before the target can own focus;
- default shell-to-shell behavior is not reclassified as an instant player edge.

Do not build a second app navigation architecture in the test and do not use
click-only assertions for TV focus behavior.

Focused gate:

```bash
./gradlew testDebugUnitTest --tests '*PlayerShell*Transition*Test' --no-daemon
./gradlew compileDebugAndroidTestKotlin --no-daemon
```

### Slice 4 — Regression, documentation, and physical-TV gate

Inspect the final diff for one and only one `PlayerVideoSurface` production call
site. Search for every `PlayerView` assignment and ensure every discarded view
has explicit detach behavior.

Update `docs/appliance-mode-plan.md` only if its current concurrent diff can be
safely preserved. Record that the no-benefit route-owned diagnostic was reverted
and the planned root-owned surface is now implemented. The normative spec already
requires the warm foreground surface; do not duplicate implementation detail
there unless behavior changed.

Run:

```bash
./tools/verify
```

Do not weaken native checks. Native provenance remains a separate signed-release
blocker and is not fixed by this UI architecture.

## Acceptance criteria

### Automated

- Surface policy covers idle/active shell, live player, and recording player.
- There is exactly one production `PlayerVideoSurface` call site.
- Live and recording route composables contain no `PlayerView`/surface host.
- A warm player-to-shell-to-player policy cycle retains one native view instance.
- Actual host removal clears `PlayerView.player` and `keepScreenOn` without
  releasing `PlayerSession`/ExoPlayer.
- The root video host has no focus or accessibility interaction behavior.
- Player/non-player transitions are instant in enter, exit, pop-enter, and
  pop-exit directions.
- Shell/shell and player/player edges retain the existing default transition.
- Existing Back, warm-return, same-service idempotence, Stop ordering, reveal-key,
  channel drawer, playback options, recording seek, and focus tests still pass.
- `./tools/verify` passes.

### Physical G10 — required before claiming completion

Read `docs/device-targets.md`, load `android-tv-device-testing`, run
`./tools/device doctor`, and confirm role `test` plus the exact G10/G10_4K_GB
identity before any device mutation. Never substitute the production G08.

With a human observer and the physical remote:

1. Play a progressive live service and repeat player -> Channels -> warm player
   at least 20 times. Re-arm each later cycle only through the existing deliberate
   browse-navigation or confirmed-current-channel policy; confirm that doing so
   does not retune the current service.
2. Repeat from EPG, Recordings, and Settings so shell restoration is exercised.
3. Confirm continuous audio and video with no black, green, stale, resized, or
   replayed frame and no tuning/recovery indicator.
4. Confirm player controls and shell focus are visible immediately after every
   direction, including rapid complete Back presses.
5. Confirm the player -> shell -> root Back opportunity remains one-shot and
   never loops.
6. Confirm explicit Stop tears down playback, removes the warm return, and leaves
   no video or audio behind the shell.
7. Confirm HOME backgrounds the Activity and stops the session; returning follows
   the existing lifecycle policy.
8. Test live timeshift both at live edge and behind live without changing pause or
   seek state during navigation.
9. Test recording playback while playing and paused, including warm return to the
   exact prior recordings context.
10. Switch Fit, Fill 16:9, and Fill 4:3 and confirm immediate geometry changes
    without detaching or flashing.
11. Repeat on a representative 1080i service and perform the required human
    motion/deinterlacing comparison. A passing progressive service is not enough.
12. Check shell readability over bright moving video, focus clipping, overscan,
    subtitle continuity, and the absence of a focus/accessibility target on the
    video background.
13. Recheck Simple TV: Back dismisses overlays but never exposes the shell.
14. Recheck GUIDE while playback is visible: it must not restart or replace the
    active session.

ADB screenshots with the synthetic backdrop may document UI geometry and scrim
placement, but they cannot prove live SurfaceView continuity, HDR, focus feel, or
motion quality.

## Explicitly out of scope

- Decoder or renderer investigation, Media3 upgrades, native AAR replacement,
  HTSP changes, stream profiles, refresh-rate strategy, or server changes.
- `TextureView`, Compose-native video rendering, `switchTargetView`, Picture-in-
  Picture, background playback, or a media foreground service.
- New player controls, shell redesign, scrim-opacity changes, navigation-rail
  changes, focus styling, or motion polish beyond removing the route crossfade.
- Accessibility-service, GUIDE, boot/wake, HOME-role, credential, signing, or Play
  Store policy work.
- Native provenance remediation or release publishing.

## Stop and ask the user if

- More than one attached `PlayerView` appears necessary at any point.
- The change seems to require `TextureView`, decoder, renderer, extractor,
  timeshift, refresh-rate, or Media3 dependency changes.
- Root-surface playback differs in motion quality from the accepted baseline.
- Aspect-ratio updates visibly lag and require ownership changes beyond the
  bounded follow-up described above.
- Focus/Back behavior must change rather than merely be preserved.
- Existing unrelated tests fail or the current dirty files overlap a planned
  edit in a way that cannot be preserved safely.
- Device validation would require the production G08.

## New-session bootstrap and dirty-worktree warning

The worktree was already changing concurrently when this handoff was written.
At the last observation it contained unrelated changes in `.gitignore`,
`.opencode/opencode.json`, `ChannelDrawer.kt`, AI-harness documentation/tooling,
and `docs/codebase-audit-2026-07-23.md`, plus untracked AI command/tool files and
research/UX documents. That list is not authoritative for a future session.

Before editing:

```bash
git status -sb
git diff --stat
git diff
git log --oneline -10
git fetch --all --prune
```

Do not discard, overwrite, stage, or commit unrelated work. Re-read any dirty
target file before editing it. If another session is writing Kotlin/Compose or
running Gradle, wait; do not use parallel writers or concurrent builds.

No commit, push, device operation, signing, publishing, or release action is part
of this handoff unless the user explicitly requests it.
