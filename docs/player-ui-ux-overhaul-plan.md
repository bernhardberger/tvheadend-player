# Fullscreen player UI/UX overhaul plan

**Status:** Completed through Slice 8 on 2026-07-30.  Reconstructed from the
reviewed 2026-07-29 handoff, revalidated against `51a4f3a`, and supplied with a
replacement G10 screenshot set.  Slice 9 remains optional and was not started.

**Scope:** Fullscreen Live TV, timeshift, and recording playback only.

**Classification:** Mixed.  Generic player key, focus, Back, timeline,
confirmation, and recovery foundations must remain separable from
product-specific presentation and appliance-specific Simple TV restrictions.

**Closure:** Slices 0–8, their automated checks, and their permitted review gates
are complete.  Human-only physical-TV observations remain recorded below rather
than inferred from automation.  Slice 9 requires a separate product decision and
is not part of core completion.

## Authority and evidence

Before implementation read `AGENTS.md`, use `docs/README.md`, then read:

- this plan;
- `docs/current-player-ui-ux-2026-07-29.md`;
- `docs/tv-design-spec.md`;
- only the relevant playback/DVR/appliance sections of
  `docs/appliance-mode-spec.md` and `docs/appliance-mode-plan.md`.

Load `android-tv-compose-ux` for presentation/focus work,
`live-tv-dvr-conventions` for Live TV/DVR behavior, and
`media3-htsp-playback-safety` before Slice 7A or any playback-lifecycle change.
Load every focused Kotlin/Compose skill whose literal trigger matches.

The old 18-file screenshot set was ignored and removed during repository
cleanup.  It is superseded by the exact 20-path G10 manifest in the inventory,
captured against `51a4f3abb2d6-dirty`.  Files outside that manifest, archives,
other screenshot directories, and historical handoffs are not current by
default.

## Product invariant

The viewer can operate Live TV, timeshift, and recordings entirely by D-pad with
predictable key actions, deterministic focus, visible state feedback, safe DVR
actions, honest timelines, and Back that unwinds the visible layer.  The work
must not disturb the accepted Media3/HTSP path, decoder behavior, warm surface,
or serialized teardown.

## Locked interaction contracts

### Center, reveal, and complete key cycles

- Hidden Center/Enter/Numpad Enter toggles Play/Pause and reveals controls for
  recordings and timeshift Live TV.
- Non-timeshift Live TV reveals without toggling because pause is unavailable.
- Up/Down reveals controls without changing playback.
- Media Play/Pause remains an immediate playback command.
- The first non-repeat down acts once.  Repeated downs cannot act again or move
  newly revealed focus.  The matching up is consumed and clears suppression.
  The next independent press works.  Suppression clears if the route disappears
  before key-up.
- The same complete-cycle rule applies when Info, TV Contents Menu, TV Number
  Entry, Bookmark, or hidden non-seekable Live Left opens a new surface.  A held
  opening key cannot continue into the newly created focus tree.  Seekable
  Left/Right is intentionally different: repeats remain seek input and produce
  visible accumulated feedback.
- The custom Compose dispatcher owns keys.  `PlayerView` remains controller-off,
  non-focusable, non-clickable, and accessibility-excluded.

This mapping follows Android TV TV-PC and playback-control guidance.  It is not
an open design question.

### Hidden seeking

- Horizontal hidden seeking remains available for timeshift and recordings.
- Full controls remain hidden.
- Recording retains its compact target/delta preview.
- Timeshift gains a programme-anchored compact preview with target, cumulative
  delta, behind-live offset, true rewindable boundary, and labelled LIVE edge.
- Back before dispatch cancels the pending seek and preview.  Back after dispatch
  dismisses feedback without claiming to undo the seek.

### Foreground layer and Back

Composition and Back derive one foreground layer, in priority order:

1. confirmation;
2. Info;
3. options detail/root;
4. numeric entry or channel drawer;
5. modal recovery/error;
6. hidden seek preview;
7. controls;
8. actually rendered Stats;
9. none.

Do not create mutable duplicate `statsRendered` state.  Stats sampling remains
keyed to the enabled preference; rendering and Back use the same derived
visibility predicate.

The policy returns an explicit action rather than assuming every layer is merely
dismissible.  Confirmation, Info, options, number entry/drawer, seek feedback,
controls, and rendered Stats unwind in order.  Normal Live recovery Back closes
the player route to warm browse without claiming to cancel retry.  Simple TV
recovery Back is consumed and recovery remains visible.  Terminal recording
failure Back performs its defined Close result.  With no layer, normal mode
returns to warm browse and Simple TV consumes Back without changing playback.
Back is not Stop.

Both players use one Back event owner.  Hardware Back down/repeat/up, a focused
child, an absent focus target, `BackHandler`, and accessibility/system Back feed
the same policy.  The nearest visible layer acts once; the complete hardware
cycle is consumed; route disposal cannot apply a second action.

### Simple TV route boundary

Simple TV is Live-TV-only.  Recording browse and recording playback are not
reachable while it is active.  Entering the restricted session from an existing
recording context must close that route through its normal serialized ownership
before opening Live TV.  Tests must prove route gating so recording options and
recording Back never need a contradictory Simple TV variant.  Layerless Simple
TV Back is an explicit appliance exception to ordinary Android TV exit behavior;
HOME and the safely confirmed Exit Simple TV action remain available.
While Simple TV recovery is foreground, expose that existing owner exit flow as
a secondary action beside Retry so recovery cannot trap the viewer away from the
only in-app exit. Retry remains initial focus; Exit retains PIN and separate safe
confirmation requirements.

### Focus ownership

- The screen owns semantic `PlayerActionId` restoration state.
- Modal surfaces remove or disable underlying controls from focus eligibility.
- Every modal edge has explicit links or `FocusRequester.Cancel`; `focusGroup`
  alone is not a trap.
- Closing restores the semantic invoking action, not an index.
- A selected lazy-list choice is scrolled into composition before focus is
  requested.
- Empty Audio focuses header Back.  Empty Subtitles focuses selected Off.
- Passive recovery backing is not focusable.

### Auto-hide

Controls hide after five seconds only when they are already visible, playback is
progressing, and the player is stable.  Paused, seek/scrub pending,
tuning/buffering, modal, recovery, and actionable-error states prevent
auto-hide.  The policy prevents hiding only; it never reveals controls.  Closing
a modal starts the timer only if playback is progressing.  Back may manually
dismiss controls while paused.

Use explicit observable inputs rather than treating `PlaybackSessionState.Playing`
as sufficient: Live combines session Playing with `TimeshiftState.paused` and
the absence of starting/recovery/buffering state; recording uses the player's
observable `isPlaying` transition plus the same foreground/pending/error gates.
The Compose timer must cancel and restart when those inputs change.

### Timeline truthfulness

- One tolerance controls LIVE label, behind-live offset, Forward, Go Live, and
  accessibility.
- Known recordings show elapsed/total.
- Growing unknown-duration recordings show elapsed plus **Still recording**.
- Other unknown duration shows elapsed plus **Duration unavailable**.
- No false total, normalized progress semantics, or focusable seekbar is exposed
  without a real seekable range.

### Header and action labels

Start with a failing geometry test.  The existing weighted metadata column
already yields to the clock; add a stable minimum clock/end-time budget only if
collision or anchor movement is proven.  Titles remain at most two lines.
Focused contextual labels are permitted for non-obvious actions, but there is no
permanent mobile-style label row.

## Implementation sequence

### Slice 0 — attributable baseline

**Classification:** Generic foundation with normative alignment.

1. Confirm a clean, attributable checkout and no concurrent writer.
2. Revalidate every finding against current source.
3. Correct stale appliance prose about vertical recording seeks, complete reveal
   key cycles, rendered Stats, recovery dismissal, and known versus unknown
   recording timeline domains.
4. Run baseline player policy tests and `./tools/verify`.
5. Preserve passive `PlayerView` invariants.

Do not continue from an ambiguous baseline.

### Slice 1 — key-cycle and auto-hide characterization

**Classification:** Generic.

The key mapping is already implemented.  Write missing failing behavior tests
before changing it:

- Center variants, Up, Down, Info, TV Contents Menu, TV Number Entry, Bookmark,
  and hidden non-seekable Live Left down/repeat/up/new-press sequences;
- one playback action per cycle;
- no movement/activation on newly revealed focus;
- route disposal before key-up;
- intentional Left/Right seek repeats remain seek input rather than being
  suppressed as opening keys;
- Live/recording parity.

Extract the smallest pure auto-hide eligibility policy and add controlled-time
Compose tests proving cancellation and restart for playing, paused,
buffering/tuning, pending seek, modal, recovery, and actionable error.  Hidden
seeking must remain hidden.

Add dedicated Live and recording screen/dispatcher Compose tests for complete
key sequences and newly created focus; pure policy tests cannot prove event
ownership.

### Slice 2 — derived player Back policy

**Classification:** Generic with appliance-specific outcomes.

Add `core/PlayerBackPolicy.kt` and exhaustive JVM tests for the complete derived
foreground model, every Stats suppressor, normal/Simple TV recovery, terminal
recording Close, and pending/dispatched seek preview.  Integrate the same derived
values into composition and Back.  Do not expand an options-only policy into a
second incomplete hierarchy.

Add Compose dispatch tests for hardware down/repeat/up, focused-child and
no-focus paths, system/accessibility Back, route disposal, and exactly one
unwind.  Test the Simple TV recording route guard as well as layerless Live Back.

### Slice 3 — Playback options hardening

**Classification:** Generic UI.

- Render unavailable messages as passive content.
- Empty Audio focuses header Back; empty Subtitles focuses Off.
- Scroll a stable selected track into composition before requesting focus.
- Use a deterministic fallback if selection disappears.
- Contain focus, disable obscured controls, and restore detail/root/invoker focus.
- Keep the panel inside top and bottom safe bounds with modal/dense opacity.
- Distinguish tracks still resolving during Starting/Recovering from an
  authoritative empty track set; loading text is passive and does not masquerade
  as an unavailable action.
- Observe Media3 track changes while the sheet remains open; direct snapshots of
  `Player.currentTracks` are insufficient. Test Starting/Recovering → resolved,
  resolved → empty, selected disappearance, and fallback without changing page.

The current G10 set deterministically reproduces focus on the recording seekbar
behind the visible options sheet, with Back failing to dismiss the sheet.  Treat
containment as a confirmed defect.

Write a dedicated `PlaybackOptionsSheetTest.kt`; do not make parallel workers
share `PlayerOverlayCompositionTest.kt`.  Test 960×540, font scales 1.0/1.3,
long German labels, and more tracks than one viewport.  Behavior-critical
instrumentation must be executed, not only compiled, before claiming focus.
The slice also owns modal role, heading, selected/disabled semantics, reading
order, and TalkBack acceptance for the changed options surfaces.

### Slice 4 — safe and complete Live Info

**Classification:** Generic DVR foundation with product copy.

- No EPG opens a channel-identified unavailable state.
- EPG disappearance transitions to unavailable instead of silently closing.
- Record opens a focus-contained confirmation with Cancel initially focused.
- Consume the opening key cycle.
- Cancel/Back before Confirm makes zero repository calls and restores Record.
- Confirm becomes busy after one activation; repeated OK cannot duplicate it.
- Dismissal in flight does not imply cancellation.
- Success remains visible/announced and focuses Close or a real replacement.
- Failure remains localized and restores Record/Retry focus.
- Closing restores the player Info action.
- Confirmation captures an immutable event ID and identity.  Before dispatch,
  programme replacement or disappearance invalidates confirmation, performs no
  call, and moves to a safe unavailable/current state.  After dispatch, the
  result completes against the captured event and must not be relabelled as a
  newly current programme.

Use a named shared or player-local confirmation component, not a private
screen-only composable.  Test policy/repository behavior and Compose focus.  Do
not mutate a real TVHeadend server.  Include dialog role, heading, safe-action
semantics, result announcement, reading order, and programme-boundary tests in
this slice rather than deferring them.

### Slice 5 — Live/timeshift and recording timelines

**Classification:** Generic playback presentation.

Implement the shared LIVE-edge tolerance, readable behind-live state, hidden
timeshift preview and its Back contract.  Preserve repeat acceleration and
coalescing.  Model known, growing unknown, and other unknown recording durations
without fabricated endpoints.  Add pure boundary tests, semantics tests, focus
tests, and 960×540 composition coverage.

This slice changes presentation policy, not the Media3/HTSP transport.

### Slice 6 — header and contextual hierarchy

**Classification:** Product-specific presentation over a generic component.

Begin with failing long English/German geometry tests.  Add a minimum clock
budget only if proven necessary.  Preserve two title lines, stable anchors,
supporting hierarchy, safe edges, and focus overflow.  Add labels only while a
non-obvious action is focused.  Use a dedicated `PlayerIdentityHeaderTest.kt`.

Current source already implements shared anchors and two-line titles; do not
rewrite it without a demonstrated failure.

### Slice 7A — real retry mechanics

**Classification:** Generic playback lifecycle; playback-safety gated.

Load `media3-htsp-playback-safety`, coroutine, and Flow guidance.  Define and
test real state actions before showing Retry:

- Live `Recovering`: `retryLiveNow` cancels the pending delay and retries the
  current active generation through the serialized command gate despite
  same-service idempotence.
- Assign a monotonically increasing request epoch before queueing every new,
  non-equivalent mutually exclusive playback intent: Live tune, Live retry,
  recording start/reopen, Stop/HOME-onStop, and release. Recheck it after
  suspensions and immediately before committing a media source.
- Equivalent same-service or same-recording requests while Starting/Recovering
  coalesce with or join the active issuance and do not advance the epoch. A
  duplicate may assume responsibility only through an explicit handoff that
  guarantees one request still commits; it must never invalidate the sole start
  and then no-op on idempotence.
- Tune versus tune and Live versus recording use the latest non-equivalent
  intent. Retry is subordinate: it cannot supersede a newer tune, recording
  intent, or teardown. Stop, HOME/onStop, and release are teardown barriers that
  invalidate all older work and cannot be superseded while queued or in flight.
  A new play intent may begin only after a non-terminal Stop completes; release
  remains terminal.
- Reject stale generations/epochs, including an older retry overtaken by a new
  tune, recording start, Stop, HOME/onStop, or release.
- HTSP disconnected/error retries through the connection owner's
  `reconnectNow()` boundary.
- Connection cancellation propagates rather than being converted into Error.
  Each reconnect attempt has an identity, and a cancelled/older attempt cannot
  publish state after its replacement starts.
- If connection loss and playback recovery overlap, visible Retry dispatches
  exactly one command: connection-owner reconnect first. Playback retry becomes
  eligible only after connection success and only if its original generation is
  still current. Never retry the player against a disconnected HTSP session.
- Recording read failure after playback began reopens the same playable
  entry/path at the last known local position via explicit Resume intent.
- Missing/unplayable file remains Close-only unless a real refresh path exists.
- Coalesce or reject repeated Retry.

Preserve command order, backoff, progress ownership, data-source ownership, and
teardown. Add command-gate, issuance-epoch, equivalent-request coalescing,
concurrent same-service Starting/Recovering, tune-versus-tune, Live↔recording,
input during queued/in-flight Stop, later-tune/Stop invalidation, mixed-recovery
single-command precedence, reconnect cancellation/stale-write, generation,
duplicate, resume, and ownership tests.
Run focused checks, `./tools/check-native-libs`, and `./tools/verify`.  The full
device playback matrix remains mandatory before completion.

### Slice 7B — tuning, recovery, and recording-failure UI

**Classification:** Mixed generic UI and appliance-specific Back behavior.

Integrate only real actions from 7A.  Ordinary tuning stays delayed,
non-focusable, and non-modal with readable local contrast and a minimum opaque
interval.  Recovery/error surfaces contain focus.  Retry is initial when Retry
and Close exist; Close is initial for Close-only failure.  Passive backing is not
focusable.  Normal Live recovery Back returns to warm browse; Simple TV remains;
terminal recording failure Back closes.  UI dismissal does not imply cancelling
automatic or in-flight retry.

Simple TV recovery shows Retry plus a secondary **Exit Simple TV** action wired
to the existing PIN and separate safe confirmation flow. Retry has initial
focus. A mixed connection/playback failure labels and invokes the one
authoritative action selected by Slice 7A rather than exposing two Retry paths.

This slice owns modal roles, headings, status/action semantics, announcements,
reading order, and TalkBack acceptance for tuning/recovery/failure surfaces.

### Slice 8 — accessibility and safe bounds

**Classification:** Generic accessibility with product presentation.

Audit fullscreen root semantics and any remaining cross-player headings,
selected/disabled/progress semantics, announcements, diagnostics copy policy,
TalkBack order, focus restoration, and maximum Live/recording Stats payload at
960×540.  Stats must not require impossible interaction to reach overflow.
Avoid repeated clock or continuously changing progress announcements.  This is
a final audit, not permission to defer surface-specific accessibility from
Slices 3, 4, or 7B.

**Product decision (2026-07-30):** keep the technical **Stats for nerds**
overlay in canonical English for now.  Do not add locale-specific Stats payload
overrides; localization is explicitly deferred because translating canonical
technical terminology can make diagnostics less clear.  This deferral does not
relax headings, passive semantics, TalkBack order, safe bounds, or the ban on
repeated announcements.

### Slice 9 — optional selection only

After core completion, evaluate one smallest next feature from recording speed,
thumbnail trick play, expanded Now/Next, previous channel, next recording, or
elapsed/remaining preference.  Report value, classification, files, tests,
playback risk, and physical gates, then wait for a product decision.  Do not
guess skip-intro, commercial, or chapter metadata.

## Ownership and safe parallelism

Default to sequential writing.  Parallel writers require explicit approval,
separate clean worktrees/branches, and exclusive ownership.  Never run concurrent
Gradle builds, device operations, or Git mutations.

Bounded foundation ownership, if approved:

1. Key/visibility policy: `PlaybackKeyPolicy`, `RecordingPlaybackPolicy`, a new
   pure auto-hide policy, and their JVM tests only.  The final integrator owns
   the dedicated player-screen key-cycle instrumentation.
2. Back policy: new `PlayerBackPolicy` and its test only.
3. Options component: `PlaybackOptionsSheet.kt` and dedicated test only.
4. Header component: `PlayerIdentityHeader.kt` and dedicated test only.
5. Retry mechanics: `PlayerSession`, connection/player ViewModels, recovery
   policy, command-gate and dedicated retry tests; no player screens/resources.

A single final integrator exclusively owns `VideoPlayerScreen`,
`RecordingPlayerScreen`, both overlay controls, seekbar/timeline, recovery UI,
`AppRoot`, callbacks, DVR/timeshift integration, strings, and normative docs.
That integrator also owns dedicated Live/recording dispatcher tests for key
cycles, Back, pending/dispatched seek feedback, and route disposal.
Retry preparation may begin after Slice 0, but integration waits for Slices 1–2.

## Verification and evidence

For every slice:

1. failing test first;
2. focused JVM tests executed;
3. instrumentation compiled;
4. behavior-critical instrumentation executed on a named emulator/test device;
5. `./tools/verify`;
6. final diff review.

Record each evidence class separately.  Automated checks and screenshots cannot
prove focus feel, `SurfaceView` visibility, overscan, readability over motion,
remote-repeat behavior, HDR, deinterlacing, or motion quality.

Physical G10 gates include complete Center/reveal and layer-opening key cycles,
paused visibility, modal containment/restoration, normal/Simple TV Back, hidden
timeshift and recording seek Back behavior,
held seek, live/timeshift readability, localized title/label/panel clipping,
tuning/failure contrast, retry kinds and duplicate prevention, unknown duration,
Stats bounds, TalkBack, progressive/interlaced playback, `SurfaceView`, HDR,
deinterlacing, and motion.

No slice authorizes installation, server mutation, signing, publication, or a
release.  Native provenance warnings remain release blockers.
