# Responsive UI Phase 1 — first measured baseline

Date: 2026-09-12. Scope: Player-specific profiling on `main`, based on `e3e683d`
plus the Phase 1 changes. This is a baseline and attribution result, **not a
fluency fix or approval to enable R8 for releases**.

## Outcome

- Added an explicit optimized profiling arm, small Channels/details/row/picon
  timing scopes, longer guarded captures and timestamped video support.
- Obtained one qualified 30-second, 40-key G10 run per arm, with exactly one
  native channel-focus callback per dispatched key and no trace-health or
  incomplete-frame errors.
- Located and bounded an R8 compiler-output defect that initially prevented the
  production-UID optimized APK from starting.
- Qualified a four-key video method that shows why quick native callbacks must
  not be reported as quick visible feedback.
- Selected a small Phase 2 experiment around focus-driven details/root work and
  details/icon measurement. Cached programme lookup is not the dominant measured
  cost in these runs.

The complete journey matrix is **not accepted**: the combined tag/paging
follow-up ended in the player before the paging assertion. Its intermediate
focus states were not captured, so this is neither a passing scenario nor proof
of an R8-specific defect. Full optimized instrumentation, sustained visible-focus
p95, controlled recording-overhead and human live-playback/motion acceptance also
remain open. Keep these separate from the useful measured baseline.

## Build and device identity

Both measured APKs use AGP 9.3.1, R8 9.3.16, min API 28, SDK 0.13.1 and the same
native dependencies. Resource shrinking is off. Both are nondebuggable,
shell-profileable `profileServer` builds signed with the existing local test key.
The optimized arm excludes **one Guide entry method** from optimization; Channels
remains eligible for optimization. No compiler or SDK upgrade was made.

| Artifact | Version | SHA-256 |
|---|---|---|
| A, unoptimized | 0.2.35 / 41 | `03d557ac231151fe75c800561c03ad5f52e685dafe195107c7bc70f6c959719d` |
| B, rejected optimized attempt | 0.2.36 / 42 | `a5cee4d5df4694b36030725dea5efc9f7a09d38cf6a1cf3258080b204db930a7` |
| B2, corrected optimized | 0.2.38 / 44 | `fcfc79df226b8f5697ee33e2bb82b039444aa131c0660cdbdcbde9002b714cdd` |
| Ordinary restore, installed at handback | 0.2.39 / 45 | `2825c84a0e2372a0c89dd97ded13703bca07bb2287749294830e55cae7906e44` |

Installed bytes and signer were checked on the exact designated G10 test TV:
TCL / Smart TV Pro / G10 / G10_4K_GB, Android 12 / API 31, armeabi-v7a process.
No app data, credentials or server configuration was cleared. The last window
ended at 15:20:07 UTC with ordinary 0.2.39 installed and force-stopped; automation
did not continue. The original installation date remained unchanged.

Artifacts, R8 outputs, source snapshots, raw failures and captures are ignored
under `captures/phase1/`. All 12 native ZIP members are byte-identical between the
measured arms. These are local test artifacts, not signed public releases.

## Method and comparison limits

Use [the profiling workflow](profiling.md) to reproduce capture and qualification.
Both runs start in All channels at the first row, after the same separate
40-key rehearsal. The measured sequence is ten Down, ten Up, repeated twice.
It includes already-visible movement, new rows and direction reversals. The
helper uses `cmd input`, a requested 100 ms sleep and identical foreground/PID
guards; actual cadence includes those guards and is not a 100 ms remote-repeat
benchmark. Default compilation state was retained, without forced compilation
or cache clearing. Neither arm ran alongside Gradle or another capture.

This is **one run per arm**, not a randomized repeated crossover. A and B2 were
captured in separate windows because of the compiler failure. The server/profile,
channel ordering and journey were retained, but live EPG content and time changed;
they were not frozen. Playback state was indicated by the UI, but black captures
and the playing indicator do not prove live video/audio or equal playback load.
Clock/backend work can occur within these runs; individual publications are not
causally labelled. Treat differences as directional evidence, not a clean
percentage attributable only to R8.

## Timed work

Standard Trace Processor `phases.sql` output, milliseconds per call over each
whole 30-second recording. Scopes are **inclusive and nested**: never add root,
details, picon or layout values into a total. A body marker is not a measurement
of all native Material/Compose descendants.

| Scope | A p95 | B2 p95 | A max | B2 max |
|---|---:|---:|---:|---:|
| Recomposer | 30.971 | 19.436 | 83.771 | 83.055 |
| Android owner measure/layout | 19.293 | 11.300 | 56.465 | 25.332 |
| Channels composition body | 15.476 | 16.649 | 43.189 | 33.781 |
| Details composition body | 15.208 | 9.620 | 20.105 | 12.976 |
| Row composition body | 13.402 | 10.470 | 16.684 | 16.826 |
| Picon composition body | 4.507 | 4.301 | 6.460 | 4.797 |
| Details measure | 19.816 | 10.821 | 34.789 | 18.404 |
| Row measure | 9.211 | 4.641 | 24.356 | 13.001 |
| Picon measure | 8.990 | 5.987 | 11.785 | 9.900 |
| Cached programme lookup | 0.088 | 0.054 | 1.576 | 0.634 |

The lookup was called 306/240 times. Its mean was 0.052/0.027 ms. Details had
45/49 body executions and 85/75 measurements. Channels had 85/75 body executions;
rows 71/69. These counts include non-input work and lazy item creation. Lower
per-call distributions do not mean fewer recompositions or improved p95 for every
scope: the Channels body p95 did not improve.

Recomposer maximum scheduled CPU was 69.923/39.145 ms; details-measure maximum
scheduled CPU was 29.761/10.607 ms. This establishes substantial execution work,
not merely a slow focus callback or an assumed network wait. It does not assign
all remaining Recomposer work to one widget.

App-dispatch-to-native-focus-notification medians were 4.492/2.324 ms, with
maxima 13.056/13.567 ms. These are the paired intervals in each accepted run's
`run-1-latency.csv`, computed by `tools/profiling/latency.sql`: from the start of
`P44:input:down` to the start of the sole `P44:focus:channel` before the next
input. They are not callback execution durations and exclude waiting before
app dispatch. Main-layer frame **lifetimes** averaged 68.452/62.266 ms, with maxima
277.316/252.661 ms and zero incomplete frames. Neither quantity is visible-focus
latency or main-thread CPU duration.

## Actual recorded highlight evidence

The separate B2 eight-second recording contains four already-visible row
reversals: 1→2→1→2→1. Its trace passed health and exact four-callback coverage.
The video is 1280×720, 228 decoded frames; original presentation timestamps
strictly increase. No frame-rate-derived timing was used. The eight boundary
images were inspected: consecutive frame pairs 18/19, 34/35, 52/53 and 67/68
show the old and new white row respectively, with zero reported drops at those
boundaries. Numeric monotonic overlays agree with the traced key-event clock.

| Key | Event uptime (ms) | Last old highlight minus event | First new highlight minus event |
|---|---:|---:|---:|
| Down | 1736923061 | 74.9 ms | 109.7 ms |
| Up | 1736923444 | 92.3 ms | 108.3 ms |
| Down | 1736923955 | 67.0 ms | 99.3 ms |
| Up | 1736924341 | 81.0 ms | 121.6 ms |

These are brackets for **mirrored output under recording**, with 1 ms input
timestamp granularity and 16–41 ms between the sampled video boundaries. They
are not TV-panel photons, physical remote latency or a sustained p95. Recording
adds encoder/virtual-display work, and its overhead was not controlled against
an identical video-off sequence. No matching A video was accepted.

Nevertheless, this directly disproves treating the short callback as the time
the new white highlight appeared in this recording. The first key is followed
by a 26.7 ms Recomposer slice, including 9.1 ms of Channels body and 7.1 ms of
details body, then 10.5 ms of owner measurement including 8.3 ms of details
measurement. There is additional native/framework work and later recomposition;
the details body alone does not explain the whole interval. The video evidence
directory retains `first-key-work.sql` and its CSV output for this nested trace
inspection.

## R8 correctness finding

Version 42's production APK failed verification on G10 before startup. R8 merged
the original `EpgGridScreenKt.EpgGridScreen` into `kj.c`, alongside unrelated
startup code. Direct disassembly, not just the mapping, shows:

```text
move-object/from16 v3, p16   # GapComposer reference
# no integer assignment to v3 before the following operation
or-int v4, v3, v4           # offset 0x1f: verifier requires an integer
```

The supported, method-scoped rule in `app/proguard-rules.pro` disables only that
method's optimization, permitting shrinking and obfuscation. Version 44 keeps
the integer flags in separate registers. Offline ART verification of the exact
APKs changed application NotReady holders from three to zero; the same thirteen
platform stubs and three access-check statuses remain explicit. G10 startup,
Channels traversal and Guide entry then ran successfully on the exact version 44
APK. This does not identify the faulty R8 pass or prove all application behavior.
Revalidate/remove the exclusion after a proven compiler fix. Guide/startup
comparisons must be labelled optimized-except-one-method.

The API 36 isolated fixture APK had previously started despite the production
APK defect: it is not the same artifact. Its AndroidJUnitRunner also fails before
tests because its shared tracing dependency was removed from the target. No
optimized instrumentation pass is claimed and no runner-wide keep was added.

## Rejected evidence and checks

- Initial long A captures overwrote 32 KiB and reported
  `config_write_into_file_no_flush`. Added periodic flush as well as streamed
  writes; only the fresh qualified A/B2 recordings above are used.
- LXC119 ftrace parsing errors remain rejected; no ignore-errors override was used.
- The first G10 video attempt failed and was not accepted. The shorter B2
  four-key recording succeeded; the first failure's cause was not established.
- The combined tag/paging smoke path ended in the player. No paging result or
  regression diagnosis is inferred from that combined sequence.
- `./tools/verify` passed for the final application state in
  `/tmp/opencode/phase1-corrected-final.ziXKX1.log`; optimized assembly/lint and
  external-SDK consumption checks passed. The nonoptimized 28-case real-root and
  browsing suite passed. Tool tests cover input limits and actual generated-shell
  video-failure cleanup. Later provenance/error-message-only tool changes use
  the focused tool tests; unchanged application checks are reused.
- Independent Astra and Opus reviews supported the bounded profiling changes and
  the scoped compiler correction. Trace/callback/video evidence limitations are
  retained rather than promoted into physical acceptance.

## First Phase 2 experiment

Isolate focus-driven details and status reads from the large Channels root,
including the shared-selection reads that would otherwise keep invalidating it.
Keep the existing native list, SDK cache and current-action guards. Within that
slice, make details/icon measurement predictable; compare the existing
`SubcomposeAsyncImage` path only as a separately measured change.

Re-run the visible-neighbour and new-row controls on the same optimized baseline
with identical instrumentation. Inspect both body and measurement work, native
descendants and the actual highlight; do not claim an off-main programme
projection alone addresses the measured cost. If isolating reads does not change
the expensive path, retain that result and test the remaining measured layout or
decoration cost before expanding the architecture.
