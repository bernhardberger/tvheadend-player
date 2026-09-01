# Product architecture remediation plan

**Status:** Active. B4-P6 completed the first bounded slice.

**Scope:** Simplify the existing application around explicit Standard and
Appliance behavior, remove unearned variants, and move cross-cutting policy out
of oversized Compose roots without replacing accepted TVHeadend behavior.

**Evidence base:** App revision
`538b51f3b548b8cd649a0bd63f543e9a0f0e3396`, current source, current normative
specifications, and the accepted household requirement for a constrained
Appliance profile.

Independent review findings are leads, not authority. Each package must verify
its claims against current source and specifications before editing. Findings
that depend on usage or physical-TV behavior stay evidence gates rather than
automatic implementation instructions.

**Closure condition:** Every package below is either completed with its
normative behavior and regression evidence updated, or explicitly rejected with
current source evidence. Archive this plan after the final accepted package.

## Product decisions

- Keep one application with Standard and Appliance profiles. Do not create a
  second app while both products share playback, guide, recordings, focus,
  connection/recovery, persistent video surface, and release cadence.
- Treat Appliance as a root behavior boundary, not a styling flag scattered
  through screens. It must continue to protect a real user from entering flows
  she cannot reliably exit.
- Keep Channels, Guide, Recordings, live playback, recording playback, warm
  playback, moving-window timeshift, the persistent `PlayerView`, and the
  narrowly scoped appliance accessibility integration.
- Keep the custom EPG and timeline domain behavior. Generic grids, sliders, or
  player shells do not own the app's D-pad focus, signed timeshift range, queued
  seeks, live edge, or TVHeadend state truth.
- Remove variants that do not earn their policy and test cost. Channel browsing
  has one list presentation. Dormant Guide routes do not remain as speculative
  architecture.
- Share implementation only where behavior is actually the same. Live and
  recording playback may share chrome, but retain different timeline and
  lifecycle semantics.
- Defer screenshot-golden expansion until the UX has stabilized. Functional,
  architecture, focus, and policy regressions remain required meanwhile.

## Invariants

Every package must preserve these unless a later package explicitly changes a
normative requirement:

- The released SDK owns connection, session, playback, tracks, and protocol
  behavior. Confirm SDK or HTSP ownership before adding an app workaround.
- Exactly one process-scoped player and one persistent `PlayerView` remain.
- Focus uses stable domain keys and restores after data mutation or route return.
- D-pad, Back, CH+/CH-, media keys, and current-channel no-retune behavior stay
  deterministic.
- Appliance launches, Guide interception, Stop/Back behavior, unlock, and
  settings restrictions remain testable as one coherent profile.
- Connection credentials remain transient in UI and are never logged or exposed
  to diagnostics.
- Empty, loading, partial, stale, and error states remain truthful; refactors do
  not replace typed state with booleans or optimistic UI.
- One package changes one architectural boundary and passes focused tests plus
  `./tools/verify` before the next package starts.

## Package sequence

### 1. Channel-surface convergence (B4-P6, complete)

- Keep the existing list-with-details presentation on the top-level Channels
  screen.
- Keep the existing focus-restoring list in the playback channel drawer for both
  profiles.
- Remove the card grid, persisted layout choice, settings controls, layout
  policy, obsolete strings, tests, and Appliance card requirement.
- Preserve channel-group scope, details, recording/current state, CH paging,
  focus restoration, Right-to-close, and current-channel no-retune behavior.

Stop if the two list surfaces require a shared state owner to remain correct;
that belongs to a later screen/state package, not a premature common component.

### 2. Dormant navigation removal (B4-P7, complete)

- Prove that `FilteredGuideKey` has no reachable production entry.
- Remove the route, its root branch, and route-only programme-category policy.
- Keep the main Guide's current local scope and D-pad behavior unchanged.

Stop if a reachable deep link, appliance entry, or persisted navigation state
still depends on the route. Record that consumer before changing navigation.

### 3. Verification-tooling repair (B4-P8/P8S, B4-H3C, RM-PLAYER-1D, and B4-P9R complete)

- Inventory JVM tests that inspect production source text or exact whitespace.
- Replace behavioral intent with behavioral tests and rule intent with purpose-
  built static analysis. Do not preserve source structure merely to satisfy a
  string assertion.
- Introduce formatting and Compose-aware static analysis incrementally with
  reviewed baselines. Ratchet new findings without turning legacy cleanup into
  an unbounded package.
- Configure Android Lint as a bounded gate rather than accepting an unlimited
  warning stream.
- Keep the existing functional, SDK-consumption, Android-test compilation, APK,
  native-library, manifest, and alignment gates.

The current Paparazzi evidence generator is explicitly deferred while UX is in
flux. Do not expand or replace it in this package.

### 4. Profile boundary

**B4-P10 completed:** `ProductProfile` now owns Standard versus Appliance route,
startup, Back/Stop, player-close, playback-options, and settings policy. The
session publishes that profile, while the root passes concrete access and
recovery decisions to consumers. The transient unlock route remains because its
Playback Options and recovery entry, constant-time PIN check, Back containment,
and session-only exit remain coherent.

- Give the root one explicit source of truth for Standard versus Appliance route
  availability, startup destination, global Back/Stop policy, and settings
  access.
- Pass concrete capabilities or callbacks into screens instead of repeatedly
  reading profile state in unrelated UI branches.
- Keep profile-specific product behavior visible; do not hide it in a generic
  boolean bag or framework.

Stop if the package would also redesign navigation or player UI. Establish the
boundary first, then migrate one consumer at a time.

Evaluate the transient Simple-TV unlock route here. Consolidate it into a
player-owned layer only if both Playback Options and recovery can retain safe
entry, constant-time PIN verification, Back behavior, and session-only unlock.
This is a medium-confidence simplification, not a pre-decided deletion.

### 5. Root orchestration decomposition

**B4-P11 completed:** `AppRootPlaybackOrchestrator` now owns live and recording
route publication, one shared stale-selection generation, current-channel
no-retune, warm-return state, and guarded-route stop-before-redirect ordering.
`AppRoot` retains typed Navigation 3 entries, destination rendering, and the
persistent player surface; route-content extraction remains a later package.

**B4-P12 completed:** one cohesive route-content file now renders Channels,
Guide, Recordings, Settings, unlock, and both player destinations through
explicit functions. `AppRoot` retains typed Navigation 3 entry ownership,
saved-state and ViewModel decorators, transitions, playback orchestration, Back
and focus wiring, and the persistent player surface below destination content.

- Extract duplicate live-play request and guarded-route decisions from
  `AppRoot` into named state/policy owners.
- Keep Navigation 3 entries declarative and move destination rendering into
  small route content functions.
- Retain warm playback and one persistent video surface outside route content.

Stop on any need to change SDK lifecycle or player ownership; attribute that to
the owning repository or a separately authorized app package.

### 6. Runtime seam cleanup

**RM-APP-D1 completed:** the Appliance lifecycle documents now distinguish a
warm foreground browse route from target-aware background handling. Live playback
stops and retunes once on foreground, while a playing recording pauses and resumes
only while the same target remains current; explicit Stop and serialized root exit
remain terminal teardown boundaries.

**B4-P13 completed with retention:** concrete `SdkRuntimeOwner` tests would
require test-only construction hooks for the final SDK coordinator and
Android-bound runtime owners. The narrower internal `SdkShutdownActions` seam is
retained, with explicit coverage for exact shutdown order, primary failure
identity, suppressed-failure order, and completion of every later shutdown
action.

**B4-P14 completed:** the existing timeshift enablement setting now maps through
one named product policy to either the fixed two-hour SDK request or zero. The
duration remains non-configurable, and SDK timeshift semantics and seek ranges
are unchanged.

**B4-P15 completed:** the persistent `PlayerView` is the sole keep-screen-on
owner. Its attached-view lifetime preserves keep-awake across player routes and
warm playback, becomes ineffective with its background window, and clears on
surface release. The redundant route-local window flag owner was removed.

- Remove production interfaces that exist only as test seams, including
  `SdkShutdownActions`, only after tests can exercise the concrete owner without
  weakening ordered shutdown coverage.
- Reassess the single-production-implementation `ConnectionProfileEditor`
  alongside connection-form work; do not keep an interface solely for mocking.
- Name the two-hour timeshift period and decide whether it is fixed product
  policy or a real setting. Do not expose configurability without a user need.
- Choose one owner for keep-screen-on behavior while preserving window and
  surface lifecycle correctness.

The shutdown order is invariant: coordinator shutdown, coordinator cancellation
and join, profile-owner cancellation and join, session shutdown, listener
detachment, player release, then scope cancellation, with suppressed failures
retained.

### 7. Player UI state owner

- B4-P16: live layer visibility, auto-hide ownership, transition timing, and
  opening-key tokens now live in `LivePlayerLayerState`.
- B4-P17/P17S: `PlayerTimelinePresentationState` now owns lifecycle-aware
  timeline polling and queued-seek presentation for both player surfaces. Its
  live owner fences queue, projection, feedback, and dispatch jobs by source
  generation, while the recording owner retains absolute targets rather than
  sharing a generic progress model.
- Keep key dispatch and focus targets at the UI boundary where they are visible
  and testable.
- Timeline rendering, programme-relative axes, recording admission truth, and
  source-specific SDK command ownership remain at their existing boundaries.

Stop if state extraction changes commands sent to the SDK. First reproduce and
classify the behavioral difference.

### 8. Shared DVR actions (B4-P18, complete)

- Extract recording create/cancel/delete orchestration and typed result handling
  used by Guide and Recordings.
- Keep screen-specific confirmation copy, selection, focus restoration, and
  list mutation behavior local.
- Preserve Archive, Schedule, and Problems until usage evidence supports a
  product change.

### 9. Connection and profile-owner simplification

**B4-P19 completed:** one transient `ConnectionFormState` now owns endpoint and
credential validation, editable-profile loading, submission, password removal,
and typed feedback for onboarding and Settings. Its saver retains only host and
port; credentials and feedback never enter saved state. Both screens retain
their separate navigation, category, focus, secure-window, and post-save
credential-lifetime behavior, while the SDK-backed profile owner remains the
only persistence boundary.

**B4-P20 completed with retention:** `AppProfileOwner` no longer exposes the
unused lease-free password-save overload, so every production password write
carries its credential lease through the process-owned command path. The narrow
`ConnectionProfileEditor` capability remains because the shared form and Compose
tests can exercise concrete connection behavior without constructing Android
Keystore and the released SDK runtime; replacing it with callbacks or test-only
hooks would only move the seam. The command owner remains because it preserves
initialization-before-write, serialized non-cancellable commits, redacted
sensitive values, lease release, stale-observation rejection, and shutdown join.

- Share endpoint validation, credential field state, submission, and typed
  feedback between onboarding and Settings.
- Keep onboarding navigation and Settings category/focus shells separate.
- Do not add a second probe path or duplicate SDK connection behavior.
- Simplify `AppProfileOwner` coordination only after preserving serialized
  writes, verified readback, credential zeroing, redacted values, lease release,
  Keystore behavior, and stale-observation protection with behavioral tests.
- Remove one-use abstractions only when tests can use concrete public behavior;
  do not replace them with test-only production hooks.

### 10. Settings consolidation (B4-P21, complete)

- Merge thin General and Options presentation into one coherent user-facing
  section while retaining internal player and UI setting ownership.
- Place Appliance configuration under a single product-facing category without
  weakening unlock or exit policy.
- Keep channel tags, connection, playback language, and refresh-rate controls.

Stop if a move makes settings reachable from Appliance without its existing
guard.

### 11. Guide and Recordings decomposition

- Split large screen files by state/policy, content, and modal concerns after
  the shared DVR boundary exists.
- Do not create generic base screens or one-file-per-composable fragmentation.
- Keep two-dimensional Guide focus, coverage/frontier loading, and Recordings
  selection/restoration behavior unchanged.

Before adding `focusRestorer()` to Guide or replacing per-row
`BoxWithConstraints`, reproduce focus entry and measure row cost on a target TV.
The independent review identified plausible risks, not proven defects.

### 12. Player shell convergence

- Share only proven-identical player chrome, focus containment, and surface
  placement between live and recording playback.
- Keep source-specific lifecycle, timeline, live-edge, track, and error behavior
  explicit.

### 13. Dependency hygiene

- Remove Timber if it remains a planted tree with no logging calls.
- Re-parent the app theme and remove Material Components only with a visual
  smoke check for splash, system bars, and mobile-Material primitives.
- Remove Coil's OkHttp network artifact only after proving no valid artwork
  request reaches the generic HTTP fetcher and confirming target-TV artwork.
- Replace the frozen extended-icons artifact with committed vectors or the
  smallest supported icon source, then measure the APK change.
- Remove inert dependencies only after both source and resolved-graph proof.

Each item is its own reversible package where physical or APK evidence differs.
Do not bundle dependency cleanup into player or navigation refactors.

### 14. Locale, identity, and release readiness

- Recount default and German resources and complete user-visible German copy.
- Decide the pre-SDK credential migration before the first signed release. Keep
  it only as a documented, dated compatibility requirement; otherwise remove it
  after confirming the operator no longer needs it.
- Prototype release shrinking before publication, then verify Koin,
  serialization restoration, native decoders, licenses, corresponding-source
  obligations, and release-native-library gates.
- Add dependency update and wrapper/dependency validation automation in a
  separately authorized repository-maintenance package. Playback-critical
  pinned coordinates remain manually reviewed.

### 15. Measured quality additions

- Add accessibility checks and measured startup/frame benchmarks as separate
  tooling packages once the architecture settles.
- Use Compose compiler metrics only to answer an identified recomposition or
  stability question; do not treat report generation as product progress.
- Revisit production-composable screenshot goldens only after the UX reaches an
  accepted visual baseline.

## Revalidation triggers

Reconsider a second app module only when at least one of these becomes real:

- Appliance needs a different application ID, brand, distribution, or release
  cadence.
- Managed-device ownership or HOME deployment requires a different manifest and
  operational trust boundary.
- The two profiles no longer share most player, Guide, recordings, connection,
  and focus behavior.
- Independent physical-TV acceptance and rollback are required for each product.

Until then, a second package would duplicate the difficult integration surfaces
without removing their architectural coupling.
