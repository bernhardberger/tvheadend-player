# Performance measurements

The suite records numbers; it has no timing limits, budgets, or speed verdicts.
The owner judges the interaction on the TV. Screen/focus readiness checks may
fail a Macrobenchmark run, but a slow result does not.

## Optimized instrumentation lane

`:macrobenchmark` targets `playProfileServer` with
`-Ptvhplayer.profileMinify=true`. This reuses the real-server build and its `P44:`
trace markers, rather than timing a non-minified debug build. The benchmark is
self-instrumenting: its runner stays in a separate process/classloader from the
optimized app. `app/benchmark-rules.pro` also preserves the three tracing bridge
classes for instrumentation of the profile targets; ordinary release is unchanged.
Assembly alone does not prove the runner works on a device.

Use an authorized Android TV emulator/test target with a stored server profile
in the same app ID (`at.bernhardberger.tvhplayer`) and debug signing key. Turn
autoplay off. Do not uninstall the target or clear its data. Provision credentials
through the existing approved workflow, never through benchmark arguments.
Channels and Guide must be populated; Recordings needs permission and a populated
Schedule. Use at least 13 channels (and enough grid rows/schedule entries for the
scroll journeys) and two adjacent playable channels for zapping.

Build with JDK 21; bound each Gradle invocation and redirect its output:

```sh
umask 077
ANDROID_USER_HOME=/root/.android timeout --kill-after=5s 20m ./gradlew -q \
  :macrobenchmark:assembleProfileServer :app:assemblePlayProfileServer \
  -Ptvhplayer.profileMinify=true --offline --console=plain --no-scan \
  > /tmp/opencode/perf-build.log 2>&1
```

After device authorization and identity checks, run one method. Select the exact
device with `ANDROID_SERIAL`; do not put its address or credentials in evidence.
The install/connected command is a device mutation, not a source-only check.

```sh
ANDROID_USER_HOME=/root/.android timeout --kill-after=5s 20m ./gradlew -q \
  :macrobenchmark:connectedProfileServerAndroidTest \
  -Ptvhplayer.profileMinify=true \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  -Pandroid.testInstrumentationRunnerArguments.class=at.bernhardberger.tvhplayer.macrobenchmark.PlayerBenchmarks#drawerFirstVisit \
  -Pandroid.testInstrumentationRunnerArguments.iterations=3 \
  --offline --console=plain --no-scan > /tmp/opencode/perf-connected.log 2>&1
```

Default compilation is `CompilationMode.Partial()` using the packaged baseline
profile; default iterations is 3. Every test records frame timing; startup tests
also record startup timing. Existing `P44:` input, focus, layout and index sections
are recorded where relevant. These are aggregate section times, not independent
end-to-end latency measurements. Cold means a new app process, not empty disk or
server caches. Warm startup is AndroidX's warm activity-start mode.

| Methods | Preparation / measured work |
|---|---|
| `coldStart`, `warmStart` | Launch to populated Channels |
| `drawerFirstVisit`, `drawerWarmVisit` | Down through Guide, Recordings, Settings; Up to Channels; Right into page. Warm makes an unmeasured first tour in the same process |
| `channelsImmersive`, `channelsGrid`, `channelsList` | Select that layout in Channels → View options before running the matching method; 12 forward/back keys. Do not run all three with one layout |
| `guideFirstOpen`, `guideNavigation` | First open separately; then Down/Up, Right/Left, Channel Down/Up page jumps |
| `sideRail`, `zapRailScroll` | Settled playback; Up, Down to rail, optional Right/Left ×12, Back ×2 |
| `recordingsTabs`, `recordingsScroll` | Archive/Schedule/Problems tabs; populated Schedule list scroll |
| `settingsGeneral`, `settingsConnection`, `settingsAbout` | Root → child → Back; Connection overview only, no editing |
| `playerTune`, `playerControls`, `playerZap` | Tune to settled video; Up with 2.5-second wait/Back ×5; Channel Up/Down zap ×5 (D-pad Up/Down navigate chrome, not channels) |

All navigation uses D-pad keys and stable resource IDs/test tags, never server
channel names. The only new app UI tags are `settings-level-<id>` on headings
(to distinguish active from preview columns using `depth-active`) and
`settings-row-<id>` on rows (to find General, Connection and About without locale
text or a row index that changes with permissions). They do not alter focus,
layout, Back, or spoken labels.

AndroidX emits `*benchmarkData.json` and Perfetto traces under the connected test's
additional output directory in `macrobenchmark/build/outputs/`. Keep the raw
output private. Split its metrics into the shared result layout:

```sh
tools/perf/collect-macrobenchmark path/to/benchmarkData.json --device-label tv-emulator
```

This writes one JSON per method and `summary.md` under
`build/perf/runs/<UTC timestamp>-<device-label>/` (ignored). Samples are preserved;
unavailable metric columns remain blank. Device context/serials are not copied.
Keep AndroidX's associated traces alongside the run when needed; traces can
contain private programme and device metadata and must not be published.

## G10 key-and-histogram lane

The G10 uses no instrumentation for this suite. The primary/operator performs
the repository's device authorization/identity checks, prepares the documented
focus state, and runs one journey at a time:

```sh
tools/perf/journeys --help
tools/perf/journeys --serial "$ANDROID_SERIAL" --device-label g10 controls
tools/perf/journeys --serial "$ANDROID_SERIAL" --device-label g10 --trace rail
```

Help lists every journey, its exact keys/waits and starting state. This lane
cannot verify focus: confirm visually that the intended journey happened before
interpreting the numbers. In particular, `drawer-first` starts on the Channels
group tab after a fresh launch; `drawer-warm` starts there after a complete tour
in the same process. Settings journeys start on the named root row. Each Channels
layout is selected manually through View options; scrolling starts on its first
card/row. Player journeys start with the passive banner and controls hidden.

The script snapshots only `dumpsys gfxinfo <package>` and subtracts cumulative
CPU/GPU histogram bins. It never resets HWUI counters, dumps the UI, captures
screenshots, or reads credentials. It writes per-journey JSON and a Markdown
summary in the same timestamped result layout, including frame counts and
p50/p90/p95/p99 in milliseconds. Percentiles are histogram bucket bounds; zero
frames gives null percentiles. Missing bins/counter decreases invalidate a run
rather than masquerading as a fast result. Cold start explicitly uses a fresh
process and zero baseline. G10 warm start backgrounds/reopens the existing app;
it is not identical to AndroidX's activity-recreation warm start. These histograms
are not startup latency metrics and exclude SurfaceView video motion quality.

`--trace JOURNEY` sends `build/perf/evidence/pf.cfg` on stdin to
`perfetto --txt -c - -o …` (file input is unsuitable on this TV). That local config
must exist, or use `--trace-config PATH` with an equivalent bounded config. The
script extends only `duration_ms` in memory when a journey needs more time,
pulls the trace beside the JSON, and removes its own remote trace file. No config
or result contains a device serial. Keep traces private as above.

Offline parser checks:

```sh
python3 -m unittest discover -s tools/perf -p 'test_*.py'
python3 -m py_compile tools/perf/journeys tools/perf/collect-macrobenchmark tools/perf/test_journeys.py
```
