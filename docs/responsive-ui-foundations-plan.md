# Responsive UI foundations

- **Status:** Implementation started; Phase 0 retirement deployed to the G10.
  The optimized baseline and Channels proof are next.
- **Approved:** 2026-09-12.
- **Compatibility policy:** 0.x API and stored-state compatibility is not required;
  manual migration or fresh setup is acceptable. Updated 2026-09-12.
- **Classification:** Product-specific Player work. Any generic SDK capability is
  owned and delivered separately by the SDK.
- **First delivery:** An optimized baseline, a small Channels prototype in the real
  application shell, and a G10 performance and interaction comparison.

## Outcome and scope

Make remote navigation responsive and sustained scrolling fluid before building
out the planned Channels and Guide UI/interaction redesign. Performance is an
acceptance requirement for each slice, alongside correctness and accessibility.

The work covers browsing data preparation, observation boundaries, rendering,
focus/navigation, cached startup and synchronization presentation. Keep the
accepted SDK/HTSP/Media3 playback path, decoder selection, native libraries,
product identity and appliance boundaries intact.

Use [the TV design specification](tv-design-spec.md) for the interaction and
accessibility floor, [code ownership](code-ownership.md) for application
boundaries, and [the profiling workflow](profiling.md) for measurement. Align
changes to accepted production behavior with the specification as they land.

## Baseline and evidence limits

The planning baseline is the uncommitted `fix/browse-interaction-ownership`
worktree over `9fba29e`, including the corrections delivered in version **0.2.33,
version code 39**. Preserve those changes; HEAD alone does not identify the tested
source. Before implementation, establish current ownership and record the exact
source/dependency/build identity used for comparisons.

The operator reports that browsing still feels sluggish, possibly worse than
before. Previous correctness and static visual passes do not establish physical
performance acceptance.

Relevant G10 observations:

- The nondebuggable `profileServer` build still had R8 disabled. Its warm native
  focus callbacks took roughly **2.8–7.5 ms**, while maximum main-thread
  recomposition sections were approximately **37–45 ms in Guide** and
  **45–53 ms in Channels**. Channels measurement/layout sections reached roughly
  **44–45 ms**. Most of those long sections were scheduled CPU work.
- These maxima cover whole diagnostic runs. They have not been attributed to
  the particular frame that displayed a new highlight; clock ticks, metadata
  updates and profiling overhead can contribute.
- One inspected warm Guide run had **zero `P44:guideIndex` calls**. In one
  eight-second Channels run, **14 `P44:channelProgramme` calls totalled 0.486 ms**.
  Those observations do not justify making a cache/index rewrite the first fix
  for warm focus movement. They do not settle startup or window-change costs.
- Four-key runs are too small to establish sustained fluidity. Some earlier
  Channels traces contained incomplete frames and were rejected for frame-time
  comparison. Keep rejected evidence distinct from qualified results.
- Existing automated coverage establishes specific tag, focus, paging, Back and
  transition behavior. The real-AppRoot Back regression was necessary because a
  fixture-only Back handler had masked a production singleton-root exit.

Optional local corroboration is in the ignored
`captures/device/browse-followup/g10-0.2.33-results.md`. It may be absent from a
clone; the findings and limitations above are the planning baseline. There is no
controlled Headent/Kodi performance comparison yet.

## Architectural contract

```text
SDK: current truth, persistence, synchronization, command validation
    ↓
Player: prepared data needed by the visible screen
    ↓
UI: local navigation, focus, scrolling and rendering
```

1. **Keep one authoritative data owner.** SDK 0.13.1 already persists catalogue,
   EPG and artwork. Do not introduce a second app database, SDK-model mirrors or
   a parallel synchronization state machine.
2. **Prepare outside composition.** Catalogue ordering, tag membership,
   programme lookup, filtering and display preparation belong outside the input
   and composition paths. Reuse results until their actual inputs change.
3. **Publish only relevant changes.** Give the viewport, details and status/header
   separate observation/rendering boundaries. Compare the projected data before
   publishing it. Merely extracting functions or moving calculations to a
   ViewModel does not help if the large parent still reads every changing value.
4. **Keep navigation local and bounded.** Moving focus must not rebuild the
   catalogue, scan the whole retained EPG, wait for persistence, or start a
   catalogue refresh. Some focus-driven recomposition is expected; zero
   recompositions is not the target.
5. **Separate display freshness from command authority.** Retained data may be
   browsable while synchronization continues. Profile/session changes,
   permission loss, invalidated selections and results of user actions must be
   handled promptly. Commands continue to validate against current SDK authority.
6. **Filter before batching.** A blanket 30-second publication delay is not the
   foundation. First prevent unchanged or irrelevant data from reaching the
   screen. Introduce bounded batching only for measured bursts of passive
   updates; never debounce all state or user interaction.
7. **Give clocks narrow consumers.** Update programme labels at relevant
   boundaries, accounting for changed metadata and wall-clock corrections. Read
   progress/now-line state during drawing where practical. Avoid rebuilding rows
   just to advance an indicator.

## Implementation-readiness contract

The preparation boundary must handle concurrent updates without recreating the
old scope races. Apply these constraints to the Channels proof; they do not
prescribe a new framework or one particular Flow/DTO design.

### Ownership and profile admission

- SDK observations remain authoritative immutable inputs. Player may retain
  references and produce UI-specific labels, timestamps and readiness values;
  that is different from replicating SDK domain/result models or persistence.
- Keep scope intent synchronous in its existing owner. Focus and native
  acknowledgement remain local to the screen; the shared selection records
  accepted focus. Viewport, details and status have separate read boundaries.
- Distinguish the context that owns displayed metadata from the current
  connection proof. Same-profile reconnect can retain browsing data and position.
  Replacing the metadata-owning profile must invalidate old presentation, pending
  results and restoration before colliding channel/event IDs can be used.
- Establish that fence at the existing ordered profile-binding boundary. Do not
  depend only on observing an intermediate `null` in a StateFlow: conflation can
  hide it. Define the semantic profile/context identity explicitly; neither
  arbitrary `Available` object identity nor connection-generation identity is a
  substitute. Unrelated preferences must not reset browsing.
- SDK 0.13.1's `ConnectionOwner.connect` clears the previous profile's metadata
  before starting a different profile's worker. Use the ordered binding outcome
  and context fence to admit new-profile data. Do not impose an extra first-Ready
  gate on cached display merely to compensate for an unverified isolation concern.
- `ChannelSelectionStore` is currently an app singleton without a clear operation;
  `GuidePositionStore` has one but no production clearing caller was found in the
  inspected paths. Clear or context-key restoration as part of this boundary,
  preserving same-profile reconnect behavior. Review tag/last-played preferences
  for the same ID-collision issue rather than assuming numeric IDs are global.

### Bounded preparation and publication

- Perform actual required queries and preparation on a worker dispatcher. Those
  queries can naturally initialize SDK lazy indexes; do not use dummy IDs or
  otherwise meaningless lookups as a cache-warming API.
- Separate source-data revision from scope/range intent and profile context.
  Scope-independent work need not restart for a tag round trip. Scope-sensitive
  results must still match the intended scope/range before they become visible.
- Bound preparation concurrency and coalesce pending inputs. A completed result
  from a still-valid context may be usable even if a newer metadata revision has
  arrived. Do not require equality with the newest global snapshot as the sole
  commit condition: frequent publications can starve that pipeline indefinitely.
- Publish monotonically useful results, then process the latest pending input.
  Never overwrite newer displayed data with an older completion, admit results
  from a replaced profile/intent, or label a retained revision as fresh. Bound and
  measure preparation work; a single worker alone is not a latency guarantee.
- Equality-gate the visible projection. DVR-only or off-window changes must not
  rebuild unchanged programme presentation. Keep expensive preparation and
  comparisons outside the input/composition path. Do not pass a whole prepared
  `SessionObservation` to the screen and inadvertently restore broad subscriptions.

### Readiness, actions and artwork

- Distinguish pending/uncovered data from a settled empty result. A non-null old
  projection does not establish readiness for a newly selected channel or range.
  Use the SDK's actual coverage semantics; verify what its coverage envelope and
  query settlement prove before displaying a definitive “No EPG” label.
- A row and its details must use the same committed programme revision. Capture
  the displayed target/context for actions, then revalidate it against current
  SDK state. Read current proof and relevant permissions independently of cached
  presentation. Unavailable actions need visible feedback, not silent consumption
  or implicit activation when a later connection becomes ready.
- SDK 0.13.1 issues `CurrentSessionObservation` only when the session is Ready and
  catalogue, EPG and DVR states are Current. Same-generation metadata updates
  reuse that proof; proof identity is not an EPG revision or permission snapshot.
- The released artwork model, cache-key and load APIs all require this proof.
  Cache-only artwork before readiness therefore needs a narrowly defined SDK
  display API. Keep that request in the cached-startup phase. Existing placeholders
  are sufficient for the Channels throughput proof; retaining expired request
  models or reading private SDK cache files is not an interim requirement.

### Required adversarial sequences

| Sequence | Required outcome |
|---|---|
| Preparation is slower than a stream of EPG publications | Same-context usable results still appear, the latest pending input is eventually processed, and concurrency/memory remain bounded. |
| Tag A → B → A while preparation completes out of order | Final scope and accepted focus match A; obsolete scope-specific results cannot replace it; reusable scope-independent work is not needlessly discarded. |
| Profile A → B with colliding channel/event/artwork IDs | No old-profile presentation, action or restoration is admitted, even if the UI never observes the intermediate loading/null state. |
| Same-profile disconnect followed by a new ready generation | Retained data and position remain usable; commands use only the new current proof when available. |
| DVR-only or off-window EPG update | Unchanged visible programme projection is not republished/reprepared unnecessarily; relevant badges or controls still update. |
| A new scope/channel is absent from the previous prepared revision | Show pending/retained readiness accurately, then data or a proven empty result; never infer “No EPG” from mere absence. |
| Metadata replaces or re-IDs the focused programme | Preserve its time/channel anchor where meaningful, rather than jumping to the window start; row and details remain coherent. |
| Permissions change or the displayed target disappears before OK | Current SDK validation wins, the UI gives appropriate feedback, and no stale/deferred command is executed. |
| Guide placeholder loses focus before its data arrives | Arrival does not reclaim focus or activate the newly available programme. |

Use existing SDK fakes correctly: `FakeTvheadendSession.publish` preserves its
generation across Ready metadata publications; `replaceGeneration` explicitly
changes it, and non-Ready publication retires it. Assert against
`fake.observation.value`, not the standalone input observation's proof. A new
app-owned fake authority or production test seam is unnecessary. Use controlled
dispatchers and focused policy tests for ordering, then actual-shell interaction
tests for the integrated behavior. These tests supplement, not replace, the G10
performance gate.

## 0.x compatibility and retirement policy

The operator has explicitly removed backward-compatibility requirements during
the 0.x development track for Player and the SDK/HTSP libraries. API changes and
stored-format breaks are acceptable. Prefer updating current consumers and
documenting manual migration or fresh setup to maintaining old versions in the
runtime. The repository engineering rules record the same policy.

Apply this to obsolete API adapters, previous storage formats, retired routes,
duplicate old/new implementations and demonstrably unused code. Keep one current
path with tests for its behavior; tests whose sole purpose is preserving an
abandoned format do not justify retaining that format.

Supported Android and TVHeadend versions, genuinely optional protocol fields,
fresh-install behavior, same-version process restoration, reconnect handling and
current authority validation are separate concerns. A fallback is not obsolete
just because it is called a fallback. Changes to those product/runtime contracts
need an explicit decision in their own scope.

Dead source code chiefly adds maintenance burden; reachable legacy branches can
also add startup or runtime work. Neither removal alone nor a smaller source tree
proves a performance improvement. Keep cleanup separate from the rendering
experiment so its effect can be understood.

## Prototype interaction defaults

These defaults define the first proof and remain subject to the planned product
redesign. Changing them is a deliberate product decision, not a hidden performance
shortcut.

- Focus feedback is immediate.
- Channels uses continuous scrolling. Fixed-row paging is a controlled
  comparison only if evidence points to item creation/virtualization cost.
- Details follow focus, using cached text immediately. Images and heavier
  preparation operate independently of highlight rendering. Do not impose an
  extra frame of delay on cheap details by default.
- Cached EPG remains browsable during reconnect without treating it as proof of
  fresh server coverage or authority for commands.
- An unfetched Guide range has an explicit, navigable loading state. Specify its
  cursor, repeat, Back and accessibility behavior before implementing that path;
  a placeholder must not pretend to be a programme or queue activation for data
  that arrives later.
- Preserve the accepted scope-entry and Back layers, latest-intent behavior and
  complete key-cycle consumption. Each accepted input must have an accounted-for
  effect; rendering may coalesce to the latest state without losing movement.

## Implementation sequence

### Phase 0 — Retire obsolete paths in the affected boundaries

**Deliverable:** A bounded cleanup of startup/settings, browsing/navigation and
their direct adapters, with a clear current path and any manual setup consequence.

- Preserve the pre-cleanup source/build reference. Record each candidate as
  removed, retained with a current purpose, or outside this slice. Keep that
  inventory with the change rather than creating another general backlog.
- Check direct consumers plus DI, manifest/resource use, serialization,
  reflection/JNI and relevant public API contracts before declaring code dead.
  A text search with no callers is not sufficient proof.
- Remove confirmed obsolete compatibility branches with their redundant tests,
  resources and dependencies where those are proven unused. Keep current-path
  regression coverage, including fresh setup and same-version restoration.
- Document which old setup/state will no longer import and the supported manual
  migration or fresh-setup route. Do not implement an automatic device wipe or
  silently delete the current profile under this policy.
- Keep SDK/HTSP compatibility cleanup in the relevant library-owned slice. A
  repository-wide cleanup of all three projects is not a prerequisite for the
  Channels proof.

Initial source inspection found reachable legacy paths, not proven dead code:

| Candidate | Production entry points relative to `app/src/main/java/at/bernhardberger/tvhplayer/` |
|---|---|
| Pre-SDK profile and encrypted-credential import | `settings/AppProfileOwner.kt`, `settings/LegacyCredentialSource.kt`, `settings/ServerSettings.kt`, wired by `di/AppModule.kt` |
| Old tag/last-played ID storage and signed-ID interpretation | `settings/ChannelTagSettings.kt`, `stores/LastPlayedChannelStore.kt`, DataStore initialization in `settings/ServerSettings.kt` |
| Old stream-profile name import | `settings/PlayerSettings.kt`, consumed by `settings/AppProfileOwner.kt` |
| Retired Simple TV preference cleanup | `settings/SimpleTvRetirement.kt`, DataStore initialization in `settings/ServerSettings.kt` |
| Old Coil directory removal at runtime initialization | `core/MetadataCachePolicy.kt`, called from `di/AppModule.kt` |
| Retired `UnlockKey` restoration/normalization | `ui/AppNavigation.kt`, `ui/AppRoot.kt`, navigation restoration tests |

Verify those exact boundaries before deleting them. For example, retiring old
signed-ID decoding does not remove validation of current unsigned IDs; retiring
an obsolete route does not remove restoration of current routes. Reconcile the
existing pre-SDK migration disposition in
[the architecture remediation plan](product-architecture-remediation-plan.md).

#### Phase 0 implementation record — 2026-09-12

The pre-cleanup reference is the uncommitted 0.2.33 / code 39 app on
`fix/browse-interaction-ownership`, based on `9fba29e`. Its debug APK SHA-256 is
`5f285c30b7714d1efbd7e40f2561b17c39f0478db693c64a32026206a31d39bf`.
The source/build reference was preserved locally before editing
(`/tmp/opencode/phase0-before.tar`, SHA-256
`55887334c2c214464adc9e9159922523a0d3ad51c401b4fa98897b2b2d12f20d`,
and `/tmp/opencode/phase0-before.apk`). Temporary files are local evidence, not
durable release artifacts.

| Candidate | Disposition in this slice |
|---|---|
| Pre-SDK profile and encrypted-credential import | Removed the importer, old credential source, cleanup callbacks and DI/fixture arguments. Startup loads only the SDK profile store; current save, clear, recovery and credential-lease handling remain. |
| Old tag/last-played IDs and signed-ID interpretation | Removed the Int-to-Long DataStore migrations and negative signed-ID conversion. Current Long and decimal unsigned IDs still validate the complete `0..4294967295` domain. |
| Stream-profile name import | Removed. Discovery resolves only the saved UUID against the current discovered profiles, without rewriting preferences. Current-session checks and server-default selection remain. |
| Simple TV preference cleanup | Removed the obsolete migration and its registration. Current autoplay/appliance policy remains independent of these ignored keys. |
| Old Coil directory cleanup | Removed the startup deletion job. SDK metadata/artwork persistence and its configured limits remain the current cache path. |
| Retired unlock route | Removed the serialized key, destination, entry and rail normalization. Current route serialization and restoration remain tested. |
| SDK/HTSP internals and other dependencies | Outside this slice; no library changes or dependency removals. |

The reference check covered direct production and fixture consumers, Koin wiring,
manifest/resources, route serialization and the current SDK store APIs. No
reflective, JNI or public entry point was found for the retired app-only paths.
Current range validation, missing/unavailable profile handling, credential leases,
generation checks, reconnect behavior and playback ownership are retained.

**Manual setup:** Existing SDK-format server profiles and current preferences
continue to load. A pre-SDK-only installation must enter its server in Connection
settings again. Re-select a stream profile stored only by its old name, and any
tag/last-played choice stored only in the old signed/Int format. Old navigation
state containing the retired unlock route is unsupported; dismiss the old task
from Recents and launch again. Retired preferences, encrypted legacy files and
the old Coil directory are ignored, not automatically deleted. Android's manual
app-storage controls remain available if the operator wants a completely fresh
setup; this change does not clear app data or the current SDK profile.

**Verification:** Focused JVM cases passed for profile ownership (9), player
preferences (4), unsigned tag persistence (2), current navigation (7), and startup
restoration (2), alongside the full `./tools/verify` gate. Debug Android-test and
profile APK assembly plus profile lint passed. The authorized offline emulator
passed four current route-restoration/audio-profile round-trip cases and both
real-`AppRoot` Back regression cases. These prove their current paths, not every
setting or real playback on a TV. Logs are local evidence:
`/tmp/opencode/phase0-restoration.q0W0Rf.log` and
`/tmp/opencode/phase0-root.lEJHx1.log`; installed APK hashes matched local builds
and the tested packages were force-stopped afterward.
The final code-state gate passed in
`/tmp/opencode/phase0-final-verify.mKLNSc.log` after removing the unused test
scaffolding. This test-only removal does not invalidate the unchanged Android
runtime results.

**Independent reviews:** Astra found no in-scope defect. Opus identified unused
private test-context scaffolding, which was removed, and noted that an old saved
stack containing `UnlockKey` may fail to decode. The latter is the intentionally
unsupported old-format case documented above; no decode fallback or retired
serializer was added. Current-route restoration passed on the emulator.

**Deployment — 2026-09-12:** Normal debug version 0.2.34 / code 40 was installed
in place on the designated G10 test TV. The installed APK matched the verified
local SHA-256
`4c7ccdc9b388cd31ea1dda28c04dd0a19fcf738e85d03309d8318b9d6f28bd20`;
the existing debug signer and first-install timestamp were retained. No app data
was cleared. The final versioned build passed `./tools/verify` in
`/tmp/opencode/g10-034-verify.p3agWu.log`; unchanged runtime evidence above was
reused. This deployment did not include launch, navigation or fluency testing.
The operator requested merging the branch and continuing development on `main`.
This cleanup makes no claim about Guide frame times or physical-TV fluency.

**Gate:** The selected obsolete paths are removed without adding replacements for
their old compatibility purpose, current behavior passes focused verification,
and manual setup implications are recorded. Stop the cleanup at that boundary
and continue to measurement and the Channels prototype. Repeat this retirement
check when entering Guide or a necessary SDK slice, rather than doing a global
purge first.

### Phase 1 — Establish a useful performance baseline

**Deliverable:** A reproducible journey matrix, qualified measurements and a
specific expensive path to test.

- Mine existing traces before scheduling another device session. Distinguish
  periodic updates from input-driven work. Composition-lifetime markers are not
  recomposition counts, and lazy measurement can include new item composition.
- Prepare an R8-optimized comparison build alongside the attribution work.
  Validate SDK consumer rules and native-library gates; retain mappings and exact
  build/compilation identity. Do not add blanket keep rules or presume R8 alone
  fixes the problem.
- Reuse the existing profiling tools. Keep the timing marker set small; reserve
  detailed per-item geometry instrumentation for diagnostic runs and assess its
  overhead. Extend existing tooling only for a concrete missing measurement.
- Exercise already-visible movement, scrolling into new rows, page boundaries,
  tag reversals and input coinciding with clock/backend updates. Include
  **40–80-key traversals**, realistic data and browsing with live playback running.
- Establish reliable input-to-visible-highlight evidence. A native focus callback,
  root draw marker or the next arbitrary presented frame is insufficient. Use
  targeted visual/video evidence where frame attribution cannot establish which
  state appeared. Record timing resolution and limitations.

**Gate:** Separate root/details work, item composition, image/text layout,
decoration and observation churn sufficiently to choose the next experiment.
Keep this attribution pass bounded; do not build a new profiling platform.

### Phase 2 — Prove the Channels foundation

**Deliverable:** A small production-intended Channels slice in the actual shell,
with realistic catalogue/EPG data and the prototype interaction contract.

- Prepare catalogue order/tag membership on catalogue changes and now/next data
  outside composition. Keep current-session validation separate from the
  prepared display data.
- Isolate viewport, details and status/header reads. Focus changes must not
  regenerate catalogue or programme projections.
- Try a simpler picon rendering path using the existing image loader/cache.
  `PiconBox` currently uses `SubcomposeAsyncImage`; determine whether that
  subcomposition is useful for this repeated surface while preserving explicit
  loading/error presentation.
- Preserve native TV Material controls, accessibility, visible focus and the
  production shell's Back ownership. Do not substitute a fixture-only input path.
- Measure the continuous-list version first. If new item composition dominates,
  compare a bounded fixed-row viewport. If native decoration dominates, make a
  controlled decoration change. Neither is a preselected production replacement.

**Gate:** Pass the G10 performance and interaction criteria below before expanding
the prototype. If it misses, change the evidenced expensive decision. Do not add
another general-purpose controller to conceal a missed budget.

### Phase 3 — Prove Guide navigation on the same boundaries

**Deliverable:** A Guide slice whose loaded navigation and range transitions pass
the same performance standard.

- Provide prepared data for the visible channel/time range plus limited
  look-ahead. Reuse ordering, lookup and presentation preparation while their
  inputs remain unchanged.
- Keep loaded neighbour movement immediate and stable. Window changes must not
  repeatedly process the entire retained corpus on the main thread.
- Separate local navigation through retained programmes from acquiring fresh
  coverage. Implement the agreed loading/empty-range contract with predictable
  focus, reversal, Back and activation behavior.
- Exercise window crossings, vertical movement, known-empty ranges, reconnect
  with retained EPG, metadata replacement and rapid reversals. A stationary
  two-programme test is not enough.
- Use released SDK APIs first. Request a narrowly defined SDK capability only
  where its absence is demonstrated; deliver that change separately with its own
  tests, then adopt it in Player.

**Gate:** Loaded navigation, window transitions and retained-data browsing pass
on G10 with realistic data. Preserve programme/DVR action correctness and native
accessibility before building out the full Guide.

### Phase 4 — Make cached startup and readiness explicit

**Deliverable:** Predictable cached startup and first synchronization, with honest
progress and independent artwork loading.

- Distinguish saved catalogue availability, connection state, channel
  synchronization, visible Guide-range availability and wider background EPG work.
- Measure restore, first lookup and cache-hit/miss costs before changing storage.
  SDK event indexes are lazy; first construction can occur on the caller's thread.
  Do not leave large first-use preparation for a visible row to trigger.
- Reuse retained catalogue/EPG on a normal cached start. First-time synchronization
  can show meaningful preparation stages. Neither every future programme nor
  every picon is a prerequisite for an otherwise usable screen.
- Check the current-session-bound artwork presentation path: cached bytes may
  exist while `PiconBox` shows a placeholder. If cache-only display needs an SDK
  API, preserve profile isolation and keep remote-fetch authority checks intact.
- The seven-day Guide policy currently also bounds the initial async EPG request.
  Measure actual volume/readiness effects before changing it. Wider background
  retention need not dictate what the first screen waits for.
- Keep progress updates local to a small status surface. Show phase names or
  genuinely measurable progress rather than fabricated transfer percentages.

**Gate:** Cached process start, first synchronization, unavailable backend,
reconnect, profile change and retained-picon behavior have explicit outcomes.
Use isolated data for destructive cache tests; do not clear a household profile
merely to manufacture a cold run.

### Phase 5 — Build out and accept the redesigned surfaces

- Expand Channels and Guide incrementally on the proven data/rendering paths.
  Validate final density, localized text, focus restoration and accessibility as
  the actual design lands.
- Exercise shared consumers such as the channel rail and recording actions where
  changed data ownership affects them. Retain playback behavior.
- Apply repository-required focused regression checks, the final verification
  gate, risk-based independent runtime review and screenshot-first UX review.
  Reuse unchanged successful evidence rather than repeating every prior check.
- Obtain physical G10 acceptance for sustained navigation with realistic data and
  live playback. Polish follows a passing foundation; static captures do not
  substitute for this acceptance.

## Acceptance matrix

These are targets for the new work, not claims about version 0.2.33.

| Area | Required evidence |
|---|---|
| Warm navigation | At least 95% of visible focus responses within **50 ms** on G10, with a defined measurement method and resolution. Report tails/stalls as well as p95. |
| Sustained input | 40–80-key traversals and reversals; every accepted input accounted for; no accumulating delay or continued navigation after release. |
| Rendering cost | Work bounded by visible/changed content rather than the entire catalogue or retained EPG. Investigate recurring misses of the display's frame budget; at 60 Hz one frame is about 16.7 ms. |
| Background activity | Clock ticks and synchronization do not cause recurring visible stalls. Report these frames separately, but include them in overall physical acceptance. |
| Cached startup | Retained data genuinely reused; availability distinguished from synchronization; explicit loading/empty/error/recovery behavior. |
| Correctness | No focus escape, stale activation, tag/content mismatch, offscreen paging focus or broken Back. Current authority still governs actions. |
| Physical quality | Operator confirms fluid focus/scrolling on the actual G10 with realistic data and live playback. |

Hold device, dataset, warmup, compilation state, instrumentation and journey
conditions consistent for comparisons. Separate cached process start from an
empty-cache first sync. Record cache/corpus size and coverage rather than using a
small fixture to imply large-library performance. Follow
[device targeting](device-targets.md) and [Android tooling](android-tooling.md) for
each authorized physical-device session.

If a prototype misses the budget, stop its rollout and revise the evidenced
expensive design decision. Correctness tests and a fast focus callback cannot
close a performance failure. No fixed-page redesign, reduced content density or
custom focus engine is implied merely by a failed measurement.

## Source entry points and ownership

Paths below are relative to `app/src/main/java/at/bernhardberger/tvhplayer/` unless
another source set is specified.

| Concern | Entry points |
|---|---|
| Channels data/input/rendering | `ui/screens/ChannelsScreen.kt`, `viewmodels/ChannelsViewModel.kt`, `core/ChannelScopePolicy.kt` |
| Guide data/input/rendering | `ui/screens/EpgGridScreen.kt`, `ui/screens/guide/EpgGridContent.kt`, `core/TimelineEpgPolicy.kt` |
| Shell and observation boundaries | `ui/AppRoot.kt`, `ui/AppRootDestinationContent.kt`, `ui/SidebarGuideScene.kt`, `ui/components/SideRail.kt` |
| Artwork and runtime wiring | `ui/components/PiconBox.kt`, `core/IconResolver.kt`, `images/TvheadendArtworkLoader.kt`, `core/MetadataCachePolicy.kt`, `di/AppModule.kt` |
| Instrumentation/build | `profiling/ProfileLayout.kt`, repository `tools/profiling/`, `app/build.gradle.kts` |
| Production-shell regressions | `app/src/profileTest/java/at/bernhardberger/tvhplayer/profiling/AppRootBackNavigationTest.kt`, `SidebarGuideNavigationTest.kt` in the same directory |
| Screen regressions | `app/src/androidTest/java/at/bernhardberger/tvhplayer/ui/screens/ChannelsScreenTest.kt`, `TimelineEpgPerformanceTest.kt` in the same directory |

The Player primary owns the in-repository slice and its verification. SDK
persistence, query and authority gaps remain SDK-owned. No cache replacement,
decoder work or sibling-repository implementation is incidental to this plan.

## Completion and revalidation

Start with Phase 0's bounded retirement pass, then Phases 1–2: optimized baseline,
Channels proof, G10 comparison. Record the measured outcome and the selected
implementation before proceeding to Guide.

Revalidate the baseline when source, SDK, build configuration, interaction
contract or target device changes materially. Revalidate affected gates when an
implementation changes, rather than replaying unrelated historical checks.

Close this plan only when the built-out surfaces meet the matrix, physical
acceptance is recorded, and durable behavior/ownership is reflected in the
normative documents and tests. Then archive the implementation history and remove
this entry from the active-plan index. Until then, record remaining failures
explicitly; a delivered APK is not a performance pass.
