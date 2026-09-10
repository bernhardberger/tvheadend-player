# Player journey measurements — 2026-09-10

Status: dated reference recording completed, bounded profiling measurements.

These are bounded app-side observations, not a motion-quality certification or
an optimization change. Reproduction and source qualifications are in
[profiling.md](profiling.md). Raw traces and screenshots remain private.

## Source and method

Work started at `6573ffc7eebed1adb7aa01639b3d165a4412259d`. Earlier repair and
harness work belongs to its preceding owners. The measured latest chrome was
separately delivered at `8ac7b3ab3ed21fb1978a3fe4dda017b43093b108` (CI
`34468274444` succeeded). This profiling change adds build support, fixture data,
trace sections and analysis; it does not claim the preceding chrome as its work.

- G10: Android TV 12, ARMv7, nondebuggable shell-profileable production UID,
  existing configuration and matching test signer retained. Version 0.2.13/19;
  measured APK SHA-256
  `54905c0a60aff321681732c437e139d77d1deeb03d93e84756911ec0546d3d52`.
  First-install time was unchanged by the in-place update. No credential export,
  uninstall, downgrade, data clear or server mutation was used.
- LXC119: Android TV x86_64 16 KiB-page emulator; isolated, nondebuggable `.profile`
  fixture APK SHA-256
  `4169f57f066df6902d94d2940f330ece30eed62db7148d160279a4644acac501`.
  Normal: 60 channels/2,880 events; stress: 300 channels/43,200 events, two tags,
  half-hour programmes, epoch `1789034400`, complete cached coverage, no picons.
  SDK domain observations drive production processing; protocol/network reduction
  is not represented by this fixture.
- Both consume public SDK 0.13.0, HTSP 0.10.0 and Media3 1.11.0 with the unchanged
  native/R8 baseline. Neither is a debuggable performance baseline. The earlier
  debug G10 qualification is excluded from the comparisons below.
- Each accepted batch has three eight-second recordings with separate launch
  settling and one unrecorded journey rehearsal. Nominal key spacing is 400 ms;
  guarded batches also perform a foreground check before every input. Host load,
  timestamps, PID and input sequence are retained with each batch. No concurrent
  builds or captures were run. Default ART/JIT was retained; no compilation reset,
  forced AOT or Baseline Profile optimization was applied.
- UI captures were 1920×1080. G10 was observed awake during qualification and
  Player foreground was checked during guarded runs. Panel state/refresh rate
  were not independently sampled on every run. Live-server data was not frozen
  or exported; these are that configured-server workload at the recorded times,
  not a fixed-catalog cross-device benchmark. Emulator normal Channels had extra
  inspection idle before rehearsal. Do not derive platform speed ratios.

The measurements used the profiling diff on the chrome revision above. A later
fixture-only correction clears the activity-owned catalog on recreation and
checks anonymous fixture-store initialization. An isolated recreation regression
passed; this does not change the uninterrupted measured journeys. The enclosing
delivery commit and immutable result identify the final source revision.

## Ranked observed delays

Latency here is **app dispatch to an unambiguous focused-item callback**. It is
not shell-command duration, physical remote latency, frame lifetime or photon
latency. Ranges below are the three observed runs, not population percentiles.

| Rank | G10 journey | Observed focus delay | Attribution and next experiment | Confidence |
|---|---|---|---|---|
| 1 | Guide channel-page return | 286–353 ms on page return; per-run median across all eight moves 44–52 ms | In run 1, input slice `43084` spans 352.776 ms: main thread Running 236.526 ms, off-CPU 116.250 ms. `EpgGridScreen` awaits row `animateScrollToItem`, a frame, then programme focus. Compare page-scroll/focus sequencing in a separately authorized experiment; measure CPU and waiting separately. | High for this delay; medium for how much each intervening operation contributes. |
| 2 | Reopen channel rail from retained controls | 209–265 ms; ordinary adjacent focus moves are much shorter | Run 1 input `38435`: 265.484 ms elapsed, 242.363 ms main Running. Layout reaches 131 ms CPU in that run. Start with rail opening/layout/composition, rather than assuming transport or decoder delay. Test a bounded rail-layout experiment with the same warm journey. | High for CPU-dominated opening; medium for a particular implementation culprit. |
| 3 | Channels tag cycle → return to first row | 199.596 / 200.733 / 205.687 ms | Run 1 input `22591`: 205.687 ms elapsed, 199.225 ms main Running. Pure `channelScope` maximum CPU is only 1.689–4.350 ms across runs; layout maximum CPU is 178–217 ms. Investigate list/layout work and focus restoration after a filter cycle, not an assumed expensive tag predicate. | High for main-thread work dominating; medium for precise layout ownership. |

Sources: `EpgGridScreen.kt` page navigation and deferred focus; `ChannelDrawer.kt`
and retained-control focus callbacks; `ChannelsScreen.kt` focus/layout and
`ChannelsViewModel.kt` scope calculation. Intersect scheduling with the input-to-
callback interval to obtain the Running values above. Off-CPU includes runnable
and blocked states; it is not automatically I/O or an animation delay. Do not
sum nested composition/layout sections.

The rail batch shows warming: all-move medians fall 14.181 → 7.408 → 3.721 ms.
The opening remains the expensive move, but these observations do not establish
a stationary tail distribution. No optimization was implemented from this sample.

## Other completed journeys and scaling observations

| Journey | Three-run result |
|---|---|
| G10 Channels scroll down four / reverse up four | Eight focus callbacks per run; medians 5.753 / 5.205 / 4.357 ms; maxima 15.059 / 8.591 / 6.283 ms. |
| G10 Guide horizontal/reversal, vertical, page down/up | Eight Guide callbacks per run. Page return dominates; within-window movement did not rebuild the window index in the inspected first run. |
| Emulator normal Guide, eight-move journey | Eight callbacks per run; medians 27.580 / 27.831 / 25.766 ms; maxima 72.020 / 56.797 / 54.165 ms. |
| Emulator stress Guide, valid within-window journey | All eight Guide callbacks in all three runs. This is distinct from the rejected wide-window stress trial; no stress window-index scaling claim is made. |
| Emulator normal wide Guide, six right / six left | Twelve callbacks and two window-index calls per run; maximum index CPU 1.297 / 1.517 / 1.523 ms. |
| Emulator Channels scroll, normal vs stress | Normal medians 1.138 / 1.071 / 1.042 ms; stress 1.098 / 1.107 / 0.994 ms. No material warm-scroll increase is established by this sample. |
| Emulator tag cycle/return, normal vs stress | One instrumented row-return callback per four-key cycle. Normal 8.689 / 13.824 / 11.940 ms; stress 13.145 / 18.122 / 11.754 ms. The overlapping values do not support a strong latency scaling claim. |

Tag callbacks themselves are not instrumented. A one-callback four-input filter
run is an explicit contract, not three missing callbacks treated as zero delay.

G10 actual UI FrameTimeline frames were complete and process-attributed. Mean
lifetimes were approximately 65–74 ms for Guide/Channels scroll and 54–58 ms for
controls/rail. These lifetimes include scheduling/presentation effects and are
not CPU time, a video frame-rate result, or a physical responsiveness verdict.
Separate emulator FrameTimeline recordings qualify the source but are not joined
to atrace recordings or accepted as a matched normal/stress Guide comparison.

## Diagnostics, exclusions and limitations

- G10 scheduling/app/input/FrameTimeline capture is qualified. Kernel overrun and
  service-ring overwrite trials were rejected. Channels scroll used a 32 MiB
  service buffer with enlarged per-CPU buffers; later Guide/rail/filter used
  64 MiB. Keep those conditions distinct. The final reusable config uses 64 MiB.
- Emulator binary Perfetto still fails with `FTRACE_STATUS_PARTIAL_PAGE_READ`.
  Standard uncompressed atrace works; raw entry accounting and Trace Processor
  health are both checked. No parser-error override or custom recorder was used.
- A separate G10 CPU capture yielded 536 samples: 525 without unwind error,
  11 with explicit unwind errors. Some leaf frames are SDK EPG conversion and
  indexing, but this differently warmed diagnostic is not causal evidence for
  the filter/rail delays. Missing thread names in that initial capture are
  recorded; the reusable configuration now requests them.
- Separate native heapprofd capture yielded 1,207 allocation rows without recorded
  data loss. These are sampled native allocation estimates, not Java object
  allocations. Managed allocation rate was not established and no heap contents
  were collected. Dalvik concurrent GC sections are observable; the 1.31-second
  concurrent GC section in filter run 3 is **not** a 1.31-second main-thread pause.
- One twelve-input emulator stress capture never obtained Guide programme focus.
  It is excluded despite healthy trace parsing. A fresh tag-entry diagnostic also
  recorded no programme callback after Down; the scenario was stopped without a
  navigation repair. The valid earlier eight-input stress Guide batch remains
  separately attributable. The known trap recovery is at most one Back followed
  by force-stop/relaunch; recovery is not timed as a journey.
- Early manual hardware GUIDE setup exposed TCL TV, and a later two-Back setup
  preceded ChannelPlus. No between-key observation proves every event recipient
  or the system launch mechanism. Those exploratory intervals are excluded.
  Automated batches now reject GUIDE, Back and activation keys and guard foreground.
  Announced Player-only force-stop cleanup may expose another app underneath.

## Retained evidence identifiers

Each accepted batch has `capture-metadata.txt`, three raw traces, per-run SQL CSVs
and health/focus results. These identifiers refer to private retained evidence,
not downloadable raw data:

- `g10-channels-scroll-guarded-v2`
- `g10-channels-filter-guarded`
- `g10-controls-rail-guarded`
- `g10-guide-guarded-64m`
- `emulator-guide-normal`, `emulator-guide-window-stress`, `emulator-guide-wide-normal`
- `emulator-channels-scroll-normal`, `emulator-channels-scroll-stress`
- `emulator-channels-filter-normal`, `emulator-channels-filter-stress`

The standard queries, explicit focus contract, narrow capture commands and the
fixture recreation test are delivered with this report. Broader trace archaeology,
navigation repairs and performance optimizations require their own authorization.
