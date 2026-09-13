# Responsive UI implementation record — through 0.2.53

**Dated reference, consolidated 2026-09-13.** This preserves the original planning
context, experiments, rejected evidence and deployments through 0.2.53. Historical
instructions and proposed next steps below are not current assignments. Use the
[active plan](responsive-ui-foundations-plan.md) for current priorities and gates,
and the TV specification and code-ownership map for accepted behavior.

The planning baseline below predates the delivered work. Later results supersede
earlier hypotheses; a successful installation is not physical acceptance. The
[Phase 1 results](responsive-ui-phase1-results.md) retain the detailed initial R8
comparison. No evidence has been discarded by this consolidation.

- **Status:** Phase 0 retirement and the first Phase 1 attribution slice are
  delivered. Qualified unoptimized/optimized Channels baselines and a bounded
  recorded-highlight method are available; full optimized runtime and sustained
  physical performance acceptance remain open. Channels read isolation and the
  picon rendering change are implemented and measured. Picon/details work is
  cheaper, but the end-to-end visible-feedback gate remains open. The tag-switch
  viewport correction is delivered in 0.2.47 with a bounded G10 correctness check.
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

### Operator clarification: the lagging journeys — 2026-09-12

The operator identifies **channel-tag switching, Down presses that move the list
viewport, and CH+/CH− paging** as the relevant interactions. Moving focus between
the first two already-visible rows is not the reported problem.

Subsequent physical G10 feedback says the changes helped somewhat and scrolling
now feels reasonably good. Ten Down/Up movements are sufficient to exercise that
viewport: it starts moving after about three Down presses. **Defer further
scrolling optimization.** Paging has separate, not-yet-specified correctness
defects. For tag switching, the unfocused viewport's overlapping-key anchor was
corrected before further performance measurement: show the playing channel in the destination
scope, otherwise the top, and make Down agree. Rapid tag changes must settle on
the latest destination without waiting for intermediate tags to finish loading.

The existing four-key videos measure only the stationary first/second-row
transition. The 40-key traces traverse the first eleven rows and include scrolling,
but their published whole-run distributions do not isolate viewport-moving inputs.
There is no accepted tag-switch or Channels page-completion performance comparison
from those runs. The recorded component savings remain local evidence, not proof
that the reported lag has improved.

Future comparisons must identify these separately. With the tag viewport corrected,
the next performance comparison should be bounded to tag switching:

| Journey | Required observation |
|---|---|
| Tag switching | Start with confirmed tag-row focus; switch between materially different channel scopes and reverse direction. Measure the tag's first visual response and when the corresponding list becomes visible and usable separately. Tag focus alone does not prove that the list changed. |
| Down-driven scrolling | Begin from confirmed channel-row focus, continue through actual viewport movement and new-row appearance, and reverse. Classify each input from observed scroll position/offset and visible range rather than guessing from its sequence number. Measure motion onset, frame stalls and completion with the expected focused row visible. |
| CH+/CH− paging | Begin from confirmed Channels content focus; exercise page down, page up and reversals. Verify page movement and the final visible/focused target. Separate first response from animation/settling time and preserve the accepted paging behavior. |

Record actual input cadence and stop/reversal behavior. A nominal shell sleep is
not a physical remote-repeat rate. Correctness tests and trace health are required,
but do not establish responsiveness. In particular, the isolated-row contract of
one channel-focus callback before the next input is not a completion contract for
tag changes or overlapping page requests. Account for the expected final scope,
viewport and focus, including legitimate supersession, without treating absent
callbacks as zero latency.

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

**First attribution result, 2026-09-12:** See
[Phase 1 results](responsive-ui-phase1-results.md) for exact APKs, two qualified
40-key G10 runs, the R8 verifier correction, four recorded white-highlight
transitions and rejected evidence. The optimized distributions are encouraging
but are not a controlled percentage speedup or a fluency pass. Cached programme
lookup is small in the measured runs; focus-driven root/details work and
details/icon measurement justify the next isolated experiment. A combined
tag/paging follow-up, full optimized instrumentation and sustained visible-focus
acceptance remain unqualified. G10 was restored to ordinary 0.2.39/code45.

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

#### First Channels experiment — 2026-09-12

**Outcome:** The focus-driven root execution was removed. The visible-feedback
gate is still open; this is not completion of Phase 2.

`ChannelsScreen.kt` keeps lifecycle-collected selection as a state handle and
passes a lazy reader. A small `FocusedChannelDetails` composition owns the reads
of local focus, selected channel and clock used for details. Lazy item scopes
retain their clock reads. Input/restoration callbacks read the latest selection
without making it a screen-root composition dependency. Native controls,
restoration/effect keys, scope/generation fences and playback action proofs are
unchanged. No new data cache, preparation worker or focus controller was added.

The controlled regression mounts the production Channels route and observes
actual Compose execution. Before the change, four settled native row moves
executed the two root functions **eight times**. After the change they execute
**zero times**, while the expected row focus and detail text still update. A
real drawer round-trip positively verifies both root hooks before the counter
is cleared, preventing an unobserved hook from creating a vacuous pass.

Verification and review:

- `./tools/verify` passed on the final application state in
  `/tmp/opencode/phase2-final-verify.sBd9GC.log`.
- **29/29** profile integration cases passed: the new isolation regression,
  26 browsing journeys and two actual-`AppRoot` Back cases. **16/16** Channels
  screen cases passed, including external selection updating details without
  stealing tag focus and entering the latest selected channel.
- After strengthening the tracer's positive control, its **1/1** focused rerun
  passed. Unchanged successful cases were reused.
- Independent Astra and Opus reviews found no blocking runtime issue. Their
  shared low-severity positive-control finding was corrected and verified.
- R8 profiling retains the same one-method Guide workaround documented in
  [Phase 1](responsive-ui-phase1-results.md). The 12 native-library payloads are
  byte-identical to that baseline. R8 remains opt-in for profiling.

Artifact identities:

| Purpose | Version/code | APK SHA-256 |
|---|---|---|
| Measured optimized candidate | 0.2.40 / 46 | `c42f21f409da1b2e6952bea21c30bebbf313c8f0b697866e2d63beaf3135cebe` |
| Ordinary build restored on G10 | 0.2.41 / 47 | `7502cbd14458ff9309116e685017df274104fca18e2d6b2a6fad805eaf4ea517` |

The authorized G10 window ran **16:41:38–16:46:42 UTC**. Both installed hashes
matched the archived APKs. The ordinary build was restored in place and the app
was force-stopped at handback. Product changes remain separate from the disjoint
harness-only commit `dc7c6b3`; the application baseline is `e3e683d` plus the
recorded Phase 1 and read-isolation diffs.

One qualified 30-second traversal used the same 40-key sequence and warmup as
Phase 1: ten Down, ten Up, repeated. Trace health passed, all 40 inputs had one
native channel-focus notification, and there were no incomplete main-window
frames. The requested 100-ms sleep is not the actual input interval: the
per-key foreground/PID guards also take time.

| Observed trace quantity | Phase 1 optimized | Read isolation |
|---|---:|---:|
| Channels root-body executions | 75 | 12 |
| Channels root-body p95 elapsed | 16.649 ms | 5.215 ms |
| Recomposer p95 elapsed | 19.436 ms | 11.913 ms |
| Android owner measure/layout p95 elapsed | 11.300 ms | 9.873 ms |
| Details body p95 elapsed | 9.620 ms | 7.431 ms |

These are inclusive scopes; do not sum them. The runs used different live EPG
content and playing-channel state, rather than a frozen crossover dataset.
They support reduced observed work, not a controlled speedup percentage. The
outer details-measure marker is particularly unsuitable for a before/after
claim: child-local remeasurement can now happen below that wrapper without
executing it. Its smaller duration/count does not represent the entire details
layout cost. The parent Android measure/layout trace still includes that work.

The separate four-key recording also passed trace health and exact input/focus
accounting. Consecutive captured frames, manually checked against their burned-in
monotonic timestamps, bracket the old-to-new white highlight as follows:

| Move | Last old highlight after event time | First new highlight after event time |
|---|---:|---:|
| Down | 128.461 ms | 146.200 ms |
| Up | 179.031 ms | 195.222 ms |
| Down | 125.425 ms | 140.403 ms |
| Up | 90.648 ms | 120.393 ms |

All four boundary pairs have consecutive frame numbers and zero reported drops.
These are mirrored-output observations under recording load, with one-ms input
timestamp granularity and roughly 15–30-ms frame brackets. They are not physical
panel latency or a sustained p95. The earlier recording's brackets were roughly
67–122 ms: **this sample did not demonstrate improved visible feedback**. The
different live content/playback state and unmeasured recording overhead prevent
assigning that difference solely to this code change.

For the first recorded move there is no Channels-root body in the inspected
150-ms post-dispatch interval. A Recomposer slice starts at +15.761 ms and lasts
18.718 ms, including a 7.293-ms details body. Android measure/layout then lasts
18.841 ms; later recompositions follow. Root isolation is therefore a verified
work reduction, but it has not explained the full focus-to-visible interval.

Private evidence is under `captures/phase2/`: the source diffs,
R8 mapping/configuration, exact APKs, `g10-traversal/` trace and SQL results, and
`g10-video/` trace, original recording, frame observations and transition montage.
The first-key query is the same archived query used for the Phase 1 recording.

**Next:** Attribute the remaining native-focus, layout and presentation interval
before another structural change. Do not expand to the Guide or claim physical
acceptance from the zero-root regression, shorter trace scopes or fast native
focus callbacks. A picon implementation change remains a separate experiment
requiring its own evidence.

#### Focus-to-frame diagnostic — 2026-09-12

The next bounded diagnostic distinguishes focus-colour delivery from subsequent
frame work. A profiling-only headline draw witness observes the existing native
focus callback state and the applicable TV Material foreground. It adds no
interaction source, colour override or layout change. Its observation and cached
drawing limits are specified in [profiling](profiling.md).

Two short G10 runs used the same optimized APK, warmup, four alternating Down/Up
inputs, programme pair and playing-channel state; one was trace-only and one also
recorded video. Both passed trace health and exact four-input/native-focus
accounting. This small ordered comparison is diagnostic, not a sustained p95 or
a controlled estimate of recording overhead.

**All eight moves produced a matching native-focus foreground witness in the
first main Choreographer frame beginning after input dispatch.** No mismatched or
ambiguous-colour witness occurred. The witness's enclosing frame token—not a
nearest-timestamp guess—links it to RenderThread and the app's FrameTimeline.
The joins resolve the witness's owning process, require that process's
RenderThread, and check one unique association per input. A bounded review caught
the initial token-only RenderThread join; the corrected process-bound queries
reproduced every value.
This supports focusing on frame work rather than assuming an extra Material
focus-colour delivery frame. It does not independently prove background pixels,
submitted-buffer contents or physical panel presentation.

| Event-time-relative observation, four samples each | Trace only | With recording |
|---|---:|---:|
| Matching foreground draw witness | 29.872–65.465 ms | 45.561–70.997 ms |
| Linked app FrameTimeline end | 52.905–96.433 ms | 83.248–102.784 ms |
| RenderThread `DrawFrames` elapsed duration | 6.041–14.327 ms | 7.546–28.723 ms |

All eight linked frames were classified `Late Present` / `App Deadline Missed`.
Two recorded frames also had SurfaceFlinger deadline-miss classifications. These
labels and elapsed durations do not by themselves distinguish active CPU work,
GPU execution, runnable delay or blocked buffer waits.

The recorded white-highlight brackets were **99.983–116.800**,
**90.997–108.015**, **108.755–122.233**, and **76.093–93.145 ms** after key event
time. All four manually checked boundary pairs had consecutive frame numbers and
zero reported drops; original presentation timestamps were strictly increasing.
The frame-end and captured-highlight observations are deliberately separate.
Recording adds another consumer and can affect the work being measured; even the
trace-only linked frames exceeded 50 ms in this sample. Neither result closes the
physical visible-feedback gate.

The useful next attribution is the first responding frame's composition/layout,
layer rendering and buffer/presentation waits. The evidence does not justify a
custom focus engine, changing decoder behavior or attributing the whole delay to
a cache lookup. A correction must target the demonstrated expensive work and
then pass the sustained-input and physical gates.

Local verification passed (`./tools/verify`, profile build/lint and three focused
runtime cases: zero-root execution, Channels entry/Back and paging under metadata
replacement). The probe contract received an independent bounded challenge;
foreground observation is not described as pixel or presentation proof.

The authorized window ran **17:45:30–17:49:18 UTC**. The optimized probe was
**0.2.42 / code 48**, SHA-256
`b8f1b9f0dc44ac241681e6b75b1d85aa0be8c6fb81e99ecddc47484fce6e52e5`.
G10 was restored in place to ordinary **0.2.43 / code 49**, SHA-256
`7994d97e8b29f0c1354d8aa096288bfec717f5a4766bcbda59a89f97d5ce43c8`,
and the app was force-stopped at handback. Installed hashes matched; all 12 native
payloads remained identical. Private APKs, mappings, traces, frame-token query,
CSV results and checked recording evidence are under `captures/phase2-focus/`.

#### Picon rendering experiment — 2026-09-12

`PiconBox` now uses Coil's `AsyncImage` and painter slots instead of
`SubcomposeAsyncImage`. A small private painter preserves the native placeholder's
centred square, theme tint, 35% loading opacity and opaque failure state. Missing
artwork still uses the half-size placeholder. Current-session-bound model
resolution, request cancellation and decorative semantics remain intact.
This removes the image layout's subcomposition; `rememberVectorPainter` still
retains its own vector composition. The measured result, rather than the API name,
determines whether the change is useful.

Both optimized arms were built from the same source apart from this change and
version metadata. They used the same R8 setting, Guide method exception, native
payloads and profiling witnesses. In one authorized G10 session, each arm received
the same warmup and 40-key traversal (ten Down, ten Up, repeated), followed by a
separate four-key recording. All four captures passed trace health and exact
one-native-focus-notification-per-input accounting. The first two programmes,
rich details, row order and playing-channel indicator matched in the checked
screenshots. The live catalogue was not frozen, and the ordered A-then-B procedure
does not control every compiler warmup or operating-system effect.

The 30-second traversal results show a **local rendering-work reduction**, not
an end-to-end response improvement:

| Per-call p95 elapsed duration | Subcompose A | AsyncImage B |
|---|---:|---:|
| Picon composition body | 4.302 ms | 1.630 ms |
| Picon measure | 7.367 ms | 1.687 ms |
| Details composition body | 8.085 ms | 4.388 ms |
| Channel-row composition body | 11.940 ms | 16.500 ms |
| Android measure/layout | 10.960 ms | 11.503 ms |
| Recomposer | 11.341 ms | 11.025 ms |

These are inclusive, nested scopes across the whole trace, not additive costs or
isolated CPU times. Picon body calls were 76/75, with means of 1.590/0.750 ms;
picon measure means were 1.807/0.600 ms. Details body calls were 46/45. Row cost and
overall layout did not improve in this run. Shorter picon scopes must not stand
in for a faster whole frame.

Each of the 80 traversal inputs had its native-focus foreground witness in the
first main frame beginning after dispatch. Process-bound frame-token joins
showed median event-to-foreground-draw times of **44.817/44.004 ms**, and median
event-to-linked-app-frame-end times of **74.161/75.535 ms**. The latter p95s were
**94.151/103.835 ms** (nearest rank over 40 inputs). These observations do not
demonstrate faster end-to-end response. Native dispatch-to-notification medians
were 2.126/2.083 ms; those are a different interval from drawing or presentation.

The four manually checked recorded-white-highlight brackets also show no
consistent improvement:

| Input | Subcompose A: last old → first new | AsyncImage B: last old → first new |
|---|---:|---:|
| Down | 89.395–104.153 ms | 100.432–122.739 ms |
| Up | 89.950–109.845 ms | 113.263–146.784 ms |
| Down | 105.744–119.516 ms | 64.267–93.085 ms |
| Up | 93.475–111.670 ms | 99.743–117.997 ms |

Times are relative to the key event's millisecond uptime and use the recording's
numeric monotonic timestamps. Original presentation timestamps increased strictly
in both recordings. Every selected before/after pair had consecutive buffer
frame numbers; B's final pair had a cumulative drop count of two on both sides,
so the recording as a whole was not drop-free. One split-digit OCR result in A
was corrected against the opened source frame and retained as a separate
verification record. Recording startup alignment differed (A's first transition
was frames 34/35; B's was 3/4). Four samples under this extra recording load are
neither a physical-panel measurement nor a controlled p95 or regression verdict.
The requested 100-ms key delay also excludes the helper's per-key guard overhead;
these runs do not certify physical remote-repeat behavior.

**Disposition:** Keep the narrower image path and its measured picon/details
savings. The fluent-UI requirement is still unmet. Further attribution must
separate exclusive row/layout work from rendering and buffer waits in the
responding frame; inclusive row timings alone do not justify another wrapper or
state-holder change. Do not expand to the Guide on the strength of this result.

Verification passed: `./tools/verify`, both optimized builds/lint/SDK consumption
checks, seven focused picon/channel-row runtime cases, and the production Channels
read-isolation case. Independent Astra and Opus source reviews found no production
blocker. A low-severity late-result test barrier was tightened to observe Coil's
terminal cancellation through its public event listener; both picon cases passed
again. The pixel assertions cover the specified fixed geometry and theme, not a
general visual acceptance matrix.

The authorized window ran **18:48:50–18:57:03 UTC**. A was **0.2.44 / code 50**,
SHA-256 `0b877de6cef94d484cac85f70ba0ca9a6f553f7a4283b8832fdad5b7fbca58b7`;
B was **0.2.45 / code 51**,
`bc9a8654958e6f494630cfed9f80e0d4fb3b700fccb1ecc6fc8a34ac84ec0ff8`.
G10 was restored in place to ordinary **0.2.46 / code 52** containing the change,
SHA-256 `eb76d315724bf5629be5f9fa371cc57fb498530f22bd460470dbd0a52f4d46e4`,
and force-stopped at handback. Installed hashes matched; all 12 native payloads
were byte-identical across the three APKs. Source diffs, mappings, exact APKs,
traces, SQL results and verified frame pairs are in `captures/phase2-picon/`.

#### Channels tag viewport correction — 2026-09-12

The operator's All channels → International → All channels case was reproduced:
the shared lazy-list state retained channel 61's stable key at its new All-channels
index, even though focus remained on the tag. Down then chose a different remembered
channel and scrolled again. Three overlapping-scope regressions failed before the fix.

An actual tag switch now explicitly requests the playing channel's position when
it belongs to the new scope, otherwise the first row. The detail preview and
Down/OK use the same entry anchor. Positioning does not publish channel selection
or request native row focus. Back/re-entry without changing tags preserves the
current browse position. The preview offset uses the native row's focus rectangle;
the existing paging calculation is unchanged.

Rapid tag transitions remain immediate and latest-intent-wins. Review found the
additional A → B → A case that coalesces before composition; an input-side reset
generation now preserves that intent even when the final tag equals the last
rendered tag. Tests inject key-down/up without advancing the Compose clock and
assert that invariant: ordinary `pressKey` injection had advanced time and did not
prove the same-frame case. Both round-trip tests failed before that correction.

Final verification passed: `./tools/verify`, **11 focused tag cases**, and **7
production-wrapper cases**, including read isolation, latest-scope entry, Back,
and paging under metadata publication. Assertions cover playing-channel membership,
top fallback, both overlapping scopes, delayed/first-ever populated lists, no
preview selection publication, and entry without a second scroll (1px tolerance).
Independent Astra and Opus reviews completed; the reproduced round-trip blocker
was fixed and its bounded closure reviewed clean. A speculative first-layout
offset concern did not reproduce in the strict initially-empty-list case; that
case does not establish every possible lifecycle/layout ordering.

The G10 correctness check showed International with **61 EURONEWS GERMAN SD**
at the start and tag focus retained. Returning to All channels immediately showed
the currently playing **1 ORF1 HD**; Down focused that row in the same position.
After browsing to channel 5, three tag round trips again restored ORF1 before
Down. These are inspected static before/after states, not a tag-response benchmark
or physical-remote repeat acceptance. The same-frame ordering is covered by the
instrumentation test, not by claiming that shell key injection has zero delay.

G10 was updated in place to ordinary **0.2.47 / code 53**, SHA-256
`050879f29b41f7d0d1fe689dbadf3223153504712c0b8e8e805b8ebecde92ee5`,
and stopped at handback. The check ran **20:30:24–20:33:31 UTC**. Installed/local
hashes matched; all 12 native payloads matched the preceding ordinary build.
Private screenshots and the exact APK are under `captures/tag-viewport/`.
Further scrolling optimization remains deferred per operator feedback; the
separate paging defects and tag-switch performance measurement remain open.

#### Directional content-motion candidate — physical adjustment requested

The operator clarified that content, not just the tab indicator, must move:
horizontal below Channels/Guide/Recordings tabs and vertical between main-menu
destinations. Animation duration does not explain the reported lag. The official
TV tabs guidance explicitly calls for horizontal content movement; the standard
navigation-drawer motion example also shows vertical page movement.

Version **0.2.48 / code 54** implements a 32dp/150ms incoming tab-body translation
with stationary headers/tabs, and vertical slide plus opacity in the existing
main-destination scene. Tab controllers and one current actionable content tree
remain live; selection, rapid reversals and Down/OK do not wait for animation.
The existing Settings-category crossfade remains inside its own state boundary.

`./tools/verify` passed. Executed focused cases cover actual content geometry,
both horizontal directions and RTL, same-frame reversals, unchanged content
identity, metadata refresh, the Channels viewport rule, and production tab-body
movement. Main-scene tests cover all destination edges, interrupted exit retention
and positive-controlled absence of per-frame destination-slot composition.
Independent Astra and Opus reviews completed. The latter identified a frame-rate
alpha read in the composition predicate and split duration ownership. The final
predicate observes a derived visibility boolean, preserving intermediate outgoing
pages during interruption; both scene properties use the existing destination
duration. The three affected scene tests passed after this correction.

G10 received the reviewed ordinary APK in place, SHA-256
`da418b1e3baa20154e5707dc36699c922b3dee0ec4cca2971762e525a9631ce3`.
All 12 native payloads match version 53. Device screenshots were entirely black;
the operator reported the Channels List visible over warm ORF1 playback, so those
captures cannot establish the motion result. The app was left open for manual
observation. The operator's verdict was **“Movement needs adjustment”**. Physical
motion acceptance is therefore open, pending the specific correction; this is
not a responsiveness improvement claim. Private evidence is under
`captures/content-motion/`.

#### Reference-matching handoff — replacement candidate

The operator supplied the exact Google TV tab-page and standard-drawer videos.
They show departing and arriving content moving together with opacity, over
substantially more distance than the previous incoming-only candidate. Version
**0.2.49 / code 55** uses one-third viewport travel and a critically damped spring
with stiffness 400, calibrated against those examples. These are local parameters,
not an assertion that Google publishes that spring configuration.

Tabs retain presentation values for the departing page. Focus, time and coverage
are read at their consuming leaves, retaining the last observed value on departure;
they do not invalidate the whole body on each focus move. Its shared
viewport, requester registrations, selection/scroll publication and delayed
paging or preview-focus work are revoked; only the latest rendered visit may act.
Screen controllers, SDK authority, tabs, headers and dialogs stay outside the
moving bodies. A pending destination cannot rewrite the departing presentation
before its rows arrive. Metadata does not replay entry, and an external fallback
cannot revive an old entering transform. Main destinations use the same travel
and spring on their vertical axis; Settings categories keep their inner crossfade.

Local verification passed: `./tools/verify`, build/lint, actual two-page pixel
checks in LTR/RTL, latest-visit/external/pending-scope cases, overlapping Guide
requester ownership, populated Recordings mode changes during paging, and delayed
folder-preview cancellation. Production-wrapper checks cover all three tab bodies,
stationary headers/native tab focus, immediate Channels/Guide entry and reversals,
paging under metadata, and the existing root read-isolation property. The three
main-scene tests also pass with the longer spring's intermediate-frame sampling.
Independent Astra and Opus reviews are closed. The Opus review found a rejected-tag
latch and focus reads widened to the whole tab body; both were reproduced and
corrected. The strengthened real Channels check went from four body executions to
zero over four arrows, with both root executions also zero and details updating.
Nine helper tests and three affected production Guide/tab checks passed after the
correction, as did the final `./tools/verify`.

**Physical acceptance failed for ordinary debug 55 on G10.** That build was installed
and launched on 2026-09-13, 00:08:14–00:09:04 UTC, after explicit wake approval.
The operator could not judge the motion because navigation remained severely
laggy and screens appeared to blink in and out. Neither the reference-motion
match nor fluency was accepted at that point. Ordinary **0.2.49** was left open for
the operator. Emulator smoothness and composition counts do not contradict that result.
The subsequent bounded comparison used the same application code with the existing
optimized, non-debuggable profiling variant, and the actual tag/main-menu changes
the operator reports. Ordinary debug 55 is unoptimized; the comparison result is
recorded below. Scrolling remains deferred, and paging defects remain separate.

This work also reproduced a **pre-existing** Recordings return defect: interrupting
Archive paging can save viewport `(2,95)` while selection remains `recording:50`;
returning from another mode and entering content then leaves that selected row
offscreen. The same reproduction fails identically on the exact installed version
54 APK. It remains with the operator-deferred paging work, rather than being
attributed to or silently repaired as part of these transitions.

The ordinary candidate APK has SHA-256
`e85dac09d55e9fb0d4e4943a681012b87b464bfb83902e318a4e54c8140d0735`.
All 12 native libraries are byte-identical to version 54. The frozen source delta,
APK, review packet and separate paging reproduction are under
`captures/content-motion-reference/`. No scrolling or tag-latency improvement is
claimed from the motion correction.

#### Optimized G10 comparison — usable, with remaining roughness

At the operator's request, **0.2.50 / code 56** was installed and launched on G10
on 2026-09-13. Its installed SHA-256 matched the verified candidate:
`2ec451298a1ddbacd6284b28fcca6dc88d1e11a80223c5d9c0edbc3ce761b88d`.
The application code is the same as reviewed 55; this build uses the existing
non-debuggable `profileServer` variant with R8 enabled. All 12 native libraries
are byte-identical. The operator tried the actual tag/main-menu interactions and
reported: **“it's a LOT better. not perfect, but usable”**.

This establishes a substantial qualitative improvement from the build configuration
and changes the diagnosis from the ordinary-debug result. It does not isolate R8
from disabling debugging or the profiling variant's instrumentation, establish a
latency percentile, or independently accept every reference-motion detail. The
optimized build remains installed for manual use. Its APK, source snapshot and R8
outputs are under `captures/navigation-optimization/`.

The operator subsequently clarified that optimized navigation still pauses or
blocks slightly. Their comparison is YouTube: navigation feedback stays immediate
while unavailable content shows a skeleton. **Usable is not acceptance of that
navigation contract.** Selection feedback must not depend on content readiness,
and preparing or rendering a destination must not monopolize the UI thread.
Source inspection confirms that channel-tag intent currently resolves its scope
synchronously before publication; the drawer already has a requested-route state
but shares the UI/render budget with destination creation. These are concrete
coupling points, not a measured attribution of the remaining delay to either one
or evidence of a network wait. A loader alone would not remove main-thread work.

Use optimized, non-debuggable builds as the baseline for further G10 usability
judgments. Keep debug/emulator runs for correctness and deliberately labelled
diagnostics. Further broad performance investigation is paused; a clean-sheet UI
can still be a product choice, but the unoptimized build's lag alone no longer
supports discarding the existing presentation implementation.

#### Bounded task — non-blocking main navigation

The operator initially requested a budget-conscious stop, then explicitly
authorized this implementation. Optimized 0.2.52 is now installed on G10 and is
the new operator-tested baseline; current work is preserved uncommitted.

The operator explicitly prioritized **main navigation over channel tags**. The
authorized slice is drawer navigation between Channels, Guide, Recordings and
Settings:

- Keep drawer focus and destination feedback responsive while the destination's
  content is unavailable or being prepared. The drawer already has requested-route
  state; do not add another selection owner or assume a state split alone fixes
  synchronous destination construction, composition or layout.
- Remove demonstrated blocking work from destination activation. Prepare expensive
  UI projections off the main thread using existing SDK observations, and keep
  rendering work bounded. Ready cached content appears directly; a lightweight
  pending body represents genuinely unready content. Merely delaying the same
  blocking work until after a loader's first frame does not meet this contract.
- Keep pending UI simple and temporary: static loading text or basic blocks using
  existing components are sufficient. Defer polished skeleton layouts and loading
  animations until the content layout settles. Navigation responsiveness and
  correctness are the acceptance criteria for this slice, not placeholder design.
- Rapid drawer movement selects the latest destination without waiting for
  intermediate pages. Obsolete completions cannot replace it, steal focus or
  authorize an action. Preserve Back, content-entry ownership, session/action
  guards, persistent playback and the accepted directional motion design.
- Change destination preparation only where necessary for this main-navigation
  outcome; do not expand into redesigning each screen or its internal tabs.
- Verify the ownership/readiness contracts with focused tests, then judge actual
  main-menu changes on optimized G10 in a fresh authorized window. Emulator
  correctness alone cannot accept responsiveness.

Stop after that slice and its result. Channel-tag optimization, internal Guide or
Recordings tabs, scrolling, paging, broad profiling, SDK changes and a clean-sheet
redesign remain outside this task. A negative G10 result is a stopping point for
an operator decision, not permission for an unattended series of alternatives.

**Implementation checkpoint — optimized 0.2.52, installed on G10:** Guide timeline
index construction and Recordings library/archive/group preparation now execute
off Main under the existing screen owners. Complete prepared displays survive
metadata refresh; authority changes discard old publication. Metadata bursts
conflate without cancelling every calculation and starving the display. Guide
checks cancellation during its event pass; one worker lane bounds background CPU
competition. This does not preempt every bulk operation or remove later Compose
construction/layout cost.

The pending UI is static loading text with a native focus anchor. Review exposed
three readiness defects, all corrected: surviving selections regain native focus
after reconnect, Right into a pending Guide still enters its selected scope, and
Guide session invalidation/navigation deadlines remain alive above the display
boundary. Coverage-dependent navigation uses coherent prepared data; current
session/action authority remains live. Existing motion and paging policy remain.

Verification: final `./tools/verify`, optimized build/lint and SDK-consumption
checks passed. Focused runtime evidence includes preparation conflation/obsolete
authority rejection, surviving Recording focus, actual held Guide preparation
with Right/scope/Down entry, and latest main-destination progress while preparation
is held. An initial review packet was inadvertently mutable; a separate frozen
closure snapshot was subsequently reviewed and all three blockers were closed.
Guide reconnect with a surviving programme ID was inspected but not separately
executed; do not claim that runtime case or physical responsiveness passed.

Candidate: `captures/navigation-preparation/optimized-58.apk`, SHA-256
`deddf557504c62b7e5401dc7d018e2302fac83ba6e7d233eabacae4c4494652a`.
All 12 native library payloads match installed 0.2.50. Offline ART verification
found 6,695 verified classes and the same 13 platform stubs/three access-check
classes as the baseline; this is not an API-31 runtime or physical-TV pass.
Frozen source, R8 output and class inventory are retained alongside the APK.

**Operator G10 result:** After the explicitly requested in-place installation,
version 58/0.2.52 and its installed APK hash were verified against the candidate
above. The operator reported: “animation stutters a bit but this is MUCH better
now.” Record this as substantial qualitative improvement in main navigation,
with residual animation stutter still unresolved. It is not a measured latency
result or acceptance of perfectly smooth animation.

This bounded slice stops here. Keep optimized 0.2.52 as the new baseline and do
not automatically start another animation, scrolling or profiling investigation.

#### Shared preparation and motion follow-up — 0.2.53

The subsequent bounded reuse/responsiveness request is implemented:

- `BrowseMotionPolicy` now owns the existing one-third travel and critically
  damped stiffness-400 spring used by both main-page and tab-content hosts.
  Settings category fades remain separate. The TV specification identifies the
  shared APIs and required integration coverage for future screens; integration
  is explicit, not automatic.
- Main-page measurement reads the derived visibility threshold instead of every
  animated alpha value. Drawing still reads the animation in the graphics layer.
  This removes an unnecessary measurement dependency without changing motion.
- Shared preparation distinguishes authority replacement (discard old output)
  from request replacement (retain the last complete same-authority display).
  Guide uses this contract instead of a separate retained-result mechanism.
  Explicit request keys have fresh identity per replacement, including A→B→A;
  retained output cannot count as the new request's completion.

Verification: 12 distinct debug cases passed across the initial and focused
closure runs, plus six profile integration cases. The new cancellation test
needed a delivery barrier before releasing obsolete work; production behavior
did not change to satisfy it. Final `./tools/verify`, optimized build/lint and
SDK-consumption checks passed. Independent Astra and Opus reviews found no
blocking defect; request-key documentation and snapshot attribution were closed.

Candidate: `captures/browse-reuse/optimized-59.apk`, SHA-256
`4522371b8fa97a46814b5b2e3833d2dd9d844c72a19a48b00f4f7350248685f2`.
All 12 native payloads match 0.2.52. Offline ART reported 6,696 verified classes,
with the same 13 platform stubs and three access-check classes as that baseline.
The source snapshot also includes the independently committed branding change
`0c356c6`; it is not part of this six-file runtime/test review.

**Deployment:** At the operator's request, optimized 0.2.53/code 59 was installed
in place on G10 on 2026-09-13. The installed APK hash matches the candidate above;
the original first-install timestamp remains unchanged. No launch or navigation
test was performed during this deployment. The operator-tested comparison
baseline remains 0.2.52; elimination of animation stutter is not yet established.

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
