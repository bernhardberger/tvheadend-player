# Responsive UI foundations

- **Status, 2026-09-13:** Browse correctness, Channels read isolation, picon
  rendering, shared page motion and off-main Guide/Recordings preparation are
  implemented. Optimized **0.2.53 / code 59** is installed on G10; its physical
  assessment is pending. The operator described optimized **0.2.52** as “MUCH
  better” with some animation stutter. This is qualitative improvement, not
  completion of the performance matrix.
- **Approved:** 2026-09-12; product-specific Player work. SDK capabilities remain
  owned and delivered separately by the SDK.
- **Current priority:** Consolidate the delivered foundation and assess the new
  build before selecting another bounded correction. Main navigation takes
  priority over further channel-tag performance work. Scrolling is sufficiently
  improved according to operator feedback; further scrolling optimization is
  deferred. Paging defects are separate correctness work.

## Scope and authorities

Make remote navigation responsive and sustained browsing fluid before expanding
the planned Channels/Guide redesign. Preserve the accepted SDK/HTSP/Media3
playback path, decoder selection, native libraries, product identity and appliance
boundaries. Follow the [TV specification](DESIGN.md),
[code ownership](code-ownership.md) and [profiling workflow](profiling.md).

Completed experiments, source/build hashes, rejected measurements and deployment
history are in the [implementation record](responsive-ui-implementation-record.md).
The [Phase 1 report](responsive-ui-phase1-results.md) contains the initial R8
comparison and compiler workaround. Neither document is a standing instruction
to repeat its tests or resume an old device window.

## Delivered foundation

| Slice | Current outcome |
|---|---|
| Phase 0 retirement | One current startup/settings/navigation path; obsolete imports and migrations removed. Current restoration and authority guards remain. |
| Phase 1 baseline | Qualified optimized/unoptimized traces, bounded highlight recording, narrow Guide R8 workaround and explicit measurement limits. |
| Channels boundaries | Focus/clock reads moved to consuming leaves; native navigation retained. Picon loading avoids the former layout subcomposition. |
| Tag viewport | Actual tag changes show the playing channel in the destination, otherwise the top; Down agrees. Returning from content to the same tag preserves browsing position. |
| Shared motion | Main destinations and tab content share `BrowseMotionPolicy`; both pages translate and fade. Exiting content cannot act, claim focus or mutate the current viewport. Settings category fades remain separate. |
| Preparation | Guide indexing and Recordings projections run on a bounded worker lane. Controllers and live session/action guards remain mounted while static pending UI hands focus to ready content. |
| Reuse follow-up | Shared preparation owns authority reset versus request retention. Main-page measurement reads a visibility threshold rather than every animation tick. Implemented in 0.2.53; stutter improvement unmeasured. |

## Architectural contract

1. **One data authority.** The SDK owns persistence, synchronization, canonical
   models and command validation. Player prepares presentation from immutable
   observations; it does not add a second database, protocol-model mirrors or
   parallel synchronization state machine.
2. **Prepare actual work off Main.** Ordering, filtering, required queries and
   display projections use a bounded worker. Do not issue dummy queries to warm
   indexes. Reuse results until relevant inputs change; separate source revision,
   scope/range intent and profile context.
3. **Keep consumers narrow.** Viewport, details and headers/status observe only
   their needs. Extracting functions or moving work to a ViewModel is insufficient
   if the large parent still reads every changing value. Progress/now-line reads
   belong in drawing where practical, with correct metadata/time corrections.
4. **Keep input local.** Scope intent is synchronous in its existing owner; native
   focus acknowledgement updates shared selection. Navigation cannot wait for
   persistence, refresh a catalogue or scan the retained corpus on Main.
5. **Separate display from authority.** Retained data may remain browsable, but
   commands use current proof and permissions. Profile replacement, permission
   loss, invalid targets and action results are handled promptly. No deferred
   activation when readiness arrives.
6. **Make progress under churn.** Coalesce queued metadata, publish complete useful
   same-context results, then catch up. Requiring every completion to equal the
   newest global revision can starve display. Never let an older completion
   overwrite newer output or cross a replaced context/intent.
7. **Filter before batching.** Equality-gate visible projections outside composition.
   DVR-only/off-window changes should not unnecessarily rebuild programmes.
   Bounded passive batching needs measured justification; a blanket 30-second
   delay or input debounce is not the foundation.

### Profile, request and readiness boundaries

- Metadata ownership is distinct from connection-generation proof. Same-profile
  reconnect can retain data and position; a different profile must invalidate
  old presentation, pending work and restoration before colliding IDs are used.
  Use the ordered binding boundary, not an intermediate null that Flow may
  conflate. Unrelated preferences must not reset browsing.
- SDK 0.13.1 clears old-profile metadata before starting a different profile's
  worker. Do not add an unverified first-Ready requirement to cached display.
  Review selection, Guide position, tag and last-played restoration for explicit
  context ownership; numeric IDs are not globally unique.
- Shared preparation resets output on authority replacement. Request replacement
  retains the last complete same-authority display while cancelling obsolete
  work. An explicit request key stays stable within a request and has fresh
  identity for each replacement, including A→B→A. Retained request provenance
  cannot satisfy current coverage or navigation-resolution requirements.
- Distinguish pending/uncovered, retained and settled empty data. Absence from an
  old projection does not establish “No EPG.” Use actual SDK coverage semantics.
  Rows and details use the same committed programme revision; actions capture
  their displayed target/context and revalidate against current SDK authority.
- SDK 0.13.1 issues `CurrentSessionObservation` only for Ready plus Current
  catalogue/EPG/DVR; same-generation publications reuse that proof. It is not an
  EPG revision or permission snapshot. Artwork models currently require it.
  Cache-only artwork before readiness needs a narrow SDK display API, not expired
  request models or direct access to private cache files.

### Interaction and compatibility

Keep continuous Channels scrolling, immediate focus feedback and cheap cached
details following focus. Heavier preparation/images proceed independently. Fixed
pages, reduced density and custom focus engines are not implied by a missed
performance budget. Preserve scope-entry/Back layers, latest intent and complete
key-cycle consumption; account for accepted inputs even when rendering coalesces.

Pending Guide ranges remain navigable with defined repeat, Back and accessibility
behavior; placeholders cannot masquerade as programmes or queue activation.
Future main destinations and tab sections must use the shared motion hosts and
extend their real integration tests. Shared conventions are not automatic
integration or permission to duplicate screen controllers in departing pages.

During 0.x, obsolete API/storage compatibility is not required. Prefer one current
path and document manual setup where needed. Supported Android/TVHeadend targets,
current protocol optionality, fresh setup, same-version restoration, reconnect
and permission guards remain requirements. No automatic data clearing or device
reset is authorized. Cleanup alone is not evidence of a runtime speedup.

## Remaining work

### Phase 2 closure — main navigation and interaction acceptance

- Obtain the operator's assessment of 0.2.53. Diagnose any remaining animation
  stutter from relevant evidence before choosing another change; avoid another
  open-ended row-focus investigation.
- Keep pending UI static/simple until the layout direction is settled.
- Track paging correctness separately. The existing Recordings archive return
  defect can restore an offscreen selection without positioning its native focus
  target. Delivery or unrelated passing tests do not close it.
- Preserve the optimized baseline. Ordinary debug and non-minified emulator
  fixtures are correctness tools, not equivalent physical performance builds.

### Phase 3 — Prove Guide navigation on the same boundaries

Prepare visible channel/time ranges plus limited look-ahead, reuse unchanged
ordering/indexes and keep loaded neighbour movement immediate. Window changes
must not repeatedly process the retained corpus on Main. Separate local movement
from acquiring fresh coverage. Exercise window crossings, vertical movement,
known-empty ranges, reconnect, metadata replacement and rapid reversals. Preserve
programme/DVR actions and native accessibility. A stationary two-cell test is
not Guide acceptance. Request SDK capabilities only for demonstrated gaps.

### Phase 4 — Make cached startup and readiness explicit

Measure retained-data restore, first queries, cache hits/misses and actual corpus
coverage before changing storage. Separate catalogue availability, connection,
visible-range readiness and wider synchronization. Neither every future programme
nor every picon is a prerequisite for first use. Investigate cache-only artwork
through SDK-owned APIs. The seven-day policy also bounds initial EPG acquisition;
measure actual transfer/readiness before changing it. Show real phase progress,
not fabricated percentages. Test cold process start, first synchronization,
unavailable backend, reconnect and profile replacement with isolated test data.

### Phase 5 — Expand the redesigned surfaces

Build Channels/Guide incrementally on proven boundaries, checking density,
localization, restoration, accessibility and shared consumers such as the channel
rail. Use focused correctness tests, final verification, risk-based runtime
review and screenshot-first UX review. Physical G10 acceptance with realistic
data and live playback remains necessary.

## Acceptance and measurement

| Journey | Required observation |
|---|---|
| Main navigation | Latest route progresses during preparation; measure motion onset, stalls and ready-content handoff separately. |
| Tags | Start at confirmed tag focus, switch materially different scopes and reverse. Observe both tag feedback and the correct usable list. |
| Scrolling | Confirm row focus and actual viewport movement/new rows. Ten Down/Up movements exercise the reported viewport; further optimization is deferred. |
| CH+/CH− paging | Confirm content focus, page down/up/reverse, and observe final visible focus. Separate first response from settling and legitimate supersession. |

Targets remain p95 visible focus response **≤50 ms**, bounded frame work (about
16.7 ms at 60 Hz), 40–80 accepted inputs/reversals without accumulated delay or
catch-up after release, and no recurring clock/synchronization stalls. Report
tails as well as p95. Preserve focus, Back, tag/content agreement, current action
authority and explicit loading/empty/error/recovery behavior.

Four-key first/second-row recordings are not tag, scrolling or paging acceptance.
Whole-run distributions do not attribute viewport-moving inputs. A fast callback
does not establish visible response, and missing callbacks are not zero latency.
Record actual cadence; shell sleep is not physical remote repeat. Keep device,
data, warmup, compilation, instrumentation and journey comparable. Separate cached
process start from empty-cache sync and acknowledge recording overhead and timing
resolution. Operator judgment of fluidity on G10 remains a separate gate.

### Adversarial correctness coverage

| Sequence | Required outcome |
|---|---|
| Preparation slower than continuing publications | Useful complete results appear; latest pending input catches up with bounded work/memory. |
| Tag/request A→B→A, out-of-order completions | Latest scope/focus wins; obsolete results cannot revive. |
| Profiles with colliding IDs | No old presentation, action or restoration leaks, even without observing intermediate null. |
| Same-profile reconnect | Retained browsing survives; actions use only the new current proof. |
| DVR-only/off-window update | Unchanged programme projections are not unnecessarily rebuilt; relevant badges still update. |
| Scope absent from old revision | Pending/retained state precedes data or proven empty, never false “No EPG.” |
| Focused programme replaced/re-IDed | Preserve meaningful time/channel anchor and row/details coherence. |
| Permission/target changes before OK | Live validation wins, with feedback and no deferred command. |
| Focus leaves a pending Guide | Data arrival neither steals focus nor activates content. |

Use released SDK fakes: `publish` preserves Ready generation, `replaceGeneration`
rotates it and non-Ready retires it. Assert against `fake.observation.value`, not
an input observation's old proof. Reuse unchanged successful evidence; test
affected ordering and actual-shell behavior rather than adding protocol mirrors.

## Completion and revalidation

Revalidate affected gates when source, SDK, build configuration, interaction or
device changes materially. Keep [device targeting](device-targets.md) and
[Android tooling](android-tooling.md) authority for every physical operation.
Close the plan only after the built-out surfaces pass the matrix, physical
acceptance is recorded and durable contracts live in specifications/tests. Until
then, distinguish implemented, deployed and physically accepted outcomes.
