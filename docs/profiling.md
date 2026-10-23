# Player Journey Profiling

## Baseline profile generation

`:baselineprofile` uses Jetpack `BaselineProfileRule` against the real configured
server with one journey: cold start, connection and initial sync, Channels, tune
a channel and Back. It produces both baseline and startup rules and covers SDK and
HTSP code on that path. No credentials are supplied to or inspected by the test.
Normal builds only package the committed profiles; generation is manual and is
not part of `tools/verify` or assemble. Add a journey only when a measured screen
is slow on first use, not hand-written rules.

Use an authorized **Android 13 (API 33)+ or rooted** device/emulator; the LXC 119
Google TV emulator is the usual target. The unrooted G10 (API 31) **cannot
generate** profiles; it only consumes them. Before generation, store a reachable
server profile using a debug-key build with application ID
`at.bernhardberger.tvhplayer`, disable autoplay, and confirm Channels has playable
channels. The generator's Play non-minified release target retains that ID and
debug signing key, is non-debuggable and shell-profileable. Do not uninstall or
clear its app data; do not use a production-signed installation as the target.
The target version must be installable as an update over the existing version.

After the usual exact-target identity checks, run from the repository with JDK 21
inside `android-emulator with-adb-tunnel`, which sets `ANDROID_SERIAL` and
`ANDROID_ADB_SERVER_PORT` (for a directly connected device set `ANDROID_SERIAL`
yourself):

```bash
: "${ANDROID_SERIAL:?Set the explicit authorized device serial first}"
umask 077
log=$(mktemp /tmp/opencode/baseline-profile.XXXXXX.log)
timeout --kill-after=5s 20m ./gradlew :app:generateBaselineProfile \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  -Ptvhplayer.testForks=1 --console=plain --no-scan >"$log" 2>&1
```

AGP's `leaveApksInstalledAfterRun` option preserves the target and its stored
server profile after the run. Only Play generation is enabled. The plugin writes
`app/src/main/generated/baselineProfiles/baseline-prof.txt` and `startup-prof.txt`;
both shipping flavors consume them, and R8 maps them into the minified releases
(`assets/dexopt/baseline.prof` and `baseline.profm`). Commit the regenerated text.

Play applies the profile on install. A sideloaded install only gets it at the
next background dexopt, so before timing a sideloaded build run the
`ProfileInstallReceiver` broadcast and `cmd package compile -m speed-profile -f`
on physical TVs only (forced compilation hung an emulator's system server).
Check only the package's dexopt line:
`adb shell dumpsys package at.bernhardberger.tvhplayer | grep -m1 'status='`.

## Current capture workflow

Use `tools/profiling/capture` and the standard Perfetto Trace Processor through
`tools/profiling/analyze.py`. `tools/profiling/trace_processor.py` fetches the
pinned official `trace_processor_shell` (v58.2) into the user cache on first use
and checks its SHA-256; `analyze.py` uses it unless `--trace-processor` names
another binary. Run it without arguments for the binary's path, or with
arguments to query a trace directly. Moving to a newer release means updating
its version and checksums from that release's prebuilt manifest. Keep traces,
CSVs, screenshots and source snapshots private. Device authorization and exact-target checks in `device-targets.md` and
`android-tooling.md` apply. Explicitly authorized unattended profiling does not
require a new attended confirmation; human motion acceptance remains separate.

### Two build boundaries

- `profile`: isolated `.profile` UID, plain Application, no INTERNET or production
  activity. The original static `RailProfileActivity` remains available.
  `JourneyProfileActivity` feeds published SDK domain observations through the
  production `ChannelsViewModel`, Channels screen and Guide indexing/navigation.
  Its bounded extras are `dataset=normal|stress`, `journey=channels|guide`, and
  `datasetEpochSeconds` (the beginning of the synthetic schedule). Normal has
  60 channels × 48 half-hour programmes; stress has 300 × 144. Both have two tags,
  complete cached coverage and no picons. This exercises application mapping,
  not protocol decoding, network fetches or image downloads. Playback callbacks
  in this browsing fixture are intentionally inactive.
- `profileServer`: production UID, Application, settings and configured-server
  behavior; nondebuggable and shell-profileable, signed with the existing local
  test certificate. It has no fixture activity or SDK-testing dependency.
  `verifyExternalSdkConsumption` checks its public dependency graph as well as
  debug/release. Install only over a matching signer and non-newer version,
  using official CLI `-r,-t`; never uninstall, downgrade or clear configuration.

Both retain the release native dependencies. R8 is independently selectable for
both profiling variants with `-Ptvhplayer.profileMinify=true` (default `false`);
ordinary release minification and resource shrinking remain disabled. The
method-scoped R8 9.3.16 workaround in `app/proguard-rules.pro` excludes only the
Guide entry point from optimization. Label optimized measurements accordingly.
Retain the generated mapping, configuration, seeds, usage and config-analyzer
artifacts alongside each APK; a dirty HEAD or the current build directory does
not identify a previously installed binary.

For physical G10 usability judgments, use optimized `profileServer` with
`-Ptvhplayer.profileMinify=true` as the baseline. The operator found the same-code
optimized 0.2.50 **much better and usable**, after ordinary debug 0.2.49 was severely
laggy. Keep unoptimized builds for explicitly identified diagnostic comparisons
and non-minified emulator fixtures for correctness; neither substitutes for this
physical baseline. This comparison changed R8, debuggability and instrumentation
together, so it does not establish an R8-only speedup. See the
[recorded operator result](responsive-ui-implementation-record.md#optimized-g10-comparison--usable-with-remaining-roughness).

Build the physical performance baseline explicitly:

```bash
timeout --kill-after=5s 20m ./gradlew :app:assemblePlayProfileServer :app:lintPlayProfileServer :app:verifyExternalSdkConsumption -Ptvhplayer.profileMinify=true --offline --console=plain --no-scan
```

The output is `app/build/outputs/apk/play/profileServer/app-play-profileServer.apk`.
Archive its exact bytes and R8 outputs before another build; verify its hash
against the installed package. This command prepares an APK, not permission to
install or an alternative to the repository's final verification gate.

Only these two variants enable fixed `P44:*` timing sections and `P1:compose:*`
body attribution. `P48:key:*` records key event uptime and dispatch age. Ordinary
debug/release paths bypass these helpers. Composition-body measurements include
nested work; they are neither exclusive costs nor counts of every descendant's
recomposition.

`P2:focusedRowDraw:*` is a profiling-only headline draw witness in `ChannelRow`.
It is retained as a supported optional attribution probe, not a product focus
implementation or a routine acceptance test. Use it only for the question below.
It reads the existing callback focus state during drawing and compares the
headline's composition-supplied `LocalContentColor` with the applicable native
ListItem focused/selected foreground. `nativeFocusColor` means a match,
`otherColor` a mismatch, and `ambiguousColor` means the theme aliases focused and
unfocused colours. Restrict this diagnostic to isolated directional inputs.
It calls `drawContent()` once and does not replace native colours or interactions.
The additional composition-local and draw-state observations can perturb
scheduling; compare with uninstrumented evidence rather than assuming zero cost.

Attribute a witness through its enclosing Choreographer frame and matching
RenderThread/FrameTimeline token, not the nearest timestamp. It establishes an
observed headline draw with that foreground state, not execution of the ancestor
background, GPU completion, submitted-buffer contents or physical presentation.
Cached drawing also makes absence of a witness insufficient to infer unreadiness.

```bash
timeout --kill-after=5s 20m ./gradlew :app:assemblePlayProfile :app:assemblePlayProfileServer :app:lintPlayProfile :app:lintPlayProfileServer --offline --console=plain --no-scan
```

The command above builds the **unoptimized diagnostic arm**. For a controlled
comparison, add `-Ptvhplayer.profileMinify=true` for the optimized arm. Preserve the first
arm's APK and outputs before building the second, use increasing installed
version codes, compare native ZIP-member hashes, and prepare an ordinary restore
APK before a device window. The API 36 fixture is not a substitute for testing
the exact `profileServer` APK on supported targets: see the
[Phase 1 verifier failure and correction](responsive-ui-phase1-results.md).

The isolated lifecycle regression uses the standard AGP test build type, enabled
only by `-Ptvhplayer.profileTests=true`. Build with
`:app:assemblePlayProfile :app:assemblePlayProfileAndroidTest`, install both matching APKs
on the authorized emulator, and run `ProfileRecreationTest` through
`at.bernhardberger.tvhplayer.profile.test/androidx.test.runner.AndroidJUnitRunner`.
Require the named test's success status, `OK (1 test)` and final code `-1`, not
ADB exit status alone. The test recreates the activity, checks catalog/session
identity and dataset size, and verifies exactly one shutdown for each owner.
The fixture deliberately restarts its activity-owned runtime on recreation and
clears the corresponding catalog; ordinary debug instrumentation is unchanged.

The current optimized fixture's AndroidJUnitRunner fails before running tests
because it references a shared `androidx.tracing.Trace` class removed from the
target by R8. Record this as an unpassed gate. Do not add broad keep rules or
claim the nonoptimized suite proves the optimized binary. External navigation,
process-restoration and exact-APK ART verification provide separate, bounded
evidence; full optimized instrumentation qualification remains open.

Record source HEAD plus any profiling diff, exact APK hash/version/signer,
installed-byte equality, SDK provenance, dataset epoch, default or explicitly
selected compilation state, display/awake state and warm-up separately. A source
HEAD alone does not identify a dirty build. Keep the latest delivered chrome
baseline distinct from earlier repair or static-rail evidence.

### Capture and validate an actual journey

Prepare the known screen and visible focus outside recording. Verify Player's
foreground before each setup key; use the app's browse rail to enter Guide,
**not hardware GUIDE**. Do not blindly repeat Back. If the known tag-navigation
trap occurs, retain its trigger, allow at most one straightforward Back, then
force-stop/relaunch without clearing data. A repeat ends that scenario for the run.

Use a separate warm-up rehearsal and a short, reversible sequence that returns to
its initial focus. The helper accepts only D-pad directions and channel-page keys,
checks scoped foreground before every injected key, and checks PID continuity.
These checks add WindowManager work: document that overhead and do not mix guarded
and unguarded baselines. It records one to three runs, eight seconds by default,
and **force-stops only the target package on exit**. This can reveal the launcher or another app
underneath; no further keys are sent there. A new batch needs explicit launch and
foreground verification again.

For an already prepared Channels first-row focus, for example:

```bash
bash tools/profiling/capture causal "${TVHPLAYER_ADB_SERIAL:?select the authorized device}" at.bernhardberger.tvhplayer "$PRIVATE_OUTPUT" 3 20 20 20 20 19 19 19 19
python3 tools/profiling/analyze.py --focus-kind channel --focus-count 8 "$PRIVATE_OUTPUT"
```

For sustained journeys, `TVHPLAYER_PROFILE_DURATION_SECONDS=30|60` permits up to
80 keys; more than 12 keys requires a longer recording. The helper streams and
flushes long Perfetto captures every second without increasing the buffer. Only
30-second/40-key causal capture has been qualified on G10 so far. Longer atrace
still uses its existing bounded buffer and requires its own health evidence.
Use a rehearsed reversible sequence with enough rows, for example ten Down, ten
Up, repeated twice. Supply all 40 keycodes and `--focus-count 40`; do not infer
coverage from process success. That sequence alone does not classify which inputs
move the viewport or establish scroll/page performance. The operator's reported
Channels lag is tag switching, viewport-moving Down navigation and CH+/CH− paging;
capture each as its own journey. The evidence each area needs is listed in
[the acceptance matrix](responsive-ui-implementation-record.md#acceptance-matrix).
`TVHPLAYER_PROFILE_KEY_DELAY_SECONDS=0|0.1|0.4`
specifies a sleep between guarded calls, not an actual fixed or physical-remote
cadence. Inspect recorded input intervals. A device-side deadline prevents late
injection; a too-long sequence fails instead of becoming a partial success.

`TVHPLAYER_PROFILE_VIDEO=true` adds a separate 1280×720 timestamped recording to
causal capture. Use it in a dedicated diagnostic run, not the timing-only
baseline: it adds encoder/virtual-display work. The helper checks the video
child before every key and waits for both recorders, stopping its owned video
child on failure. A failed run is not accepted even if a partial file exists.
Remote failure codes are 42 (foreground/PID guard), 43 (recorder/deadline),
44 (trace recorder), and 45 (video recorder).

For input-to-recorded-highlight evidence, decode original video frames, verify
their presentation order, inspect the *actual white target* before and after
each change, and read the numeric monotonic overlay from those same frames.
Compare it to `P48:key:*:time:*` uptime, allowing one-millisecond input timestamp
granularity. Validate the vendor's clock domain and sampled frame/drop counters;
do not derive timing from a nominal video frame rate or a root draw marker.
Report last-old/first-new timestamp brackets, sample count and recording overhead.
This measures mirrored output, not the TV panel or physical remote transmission.
The first G10 qualification is a four-key visible-row run; it does not establish
a sustained p95 or quantify video overhead. First/second-row highlight timing is
not evidence for tag changes, scrolling or page completion. Those recordings must
show the affected tag/list transition or viewport movement and its completion,
rather than only a changing white rectangle on stationary rows.

On LXC119, run capture inside the existing `with-adb-tunnel offline-player` lane
with explicit `emulator-5556` and package `at.bernhardberger.tvhplayer.profile`.
Use `atrace` mode where the vendor Perfetto recorder fails as described below.
Other measured sequences are Guide `22 22 21 21 20 19 167 166` (eight Guide focus
callbacks), Channels filter/return `19 22 21 20` (one channel callback; tag focus
is not instrumented), and controls/open-rail `22 21 19 22 21 20 22 21` (eight
callbacks across `--focus-kind control --focus-kind rail`). Each requires its
documented starting focus and rehearsal, not just the right activity name.

Trace health and delivered input counts alone do not establish a successful
journey. Supply the expected focus kinds/count to analysis; without them its
`focus_contract` is explicitly `not_checked`. Zero or ambiguous focus callbacks
must not be converted into zero latency. Reject incomplete frames, foreign PIDs,
ring overwrite, kernel loss, parser errors and stale files. Preserve failures
separately rather than silently dropping a run from a batch.

### Qualified sources and limits

- G10 Perfetto 15: scheduling, application sections, input dispatch and FrameTimeline
  work with `causal.pbtxt`. Actual loss required 8 MiB per-CPU buffers, 50 ms drain
  and a 64 MiB service buffer. Inspect each trace; these values are not a guarantee.
- LXC119 Perfetto 49 still reports `FTRACE_STATUS_PARTIAL_PAGE_READ`. Standard
  **uncompressed atrace** reads the text trace endpoint and provides usable
  scheduling/application sections. Check its raw entries-written accounting as
  well as Trace Processor health. Do not use an ignore-parser-errors override.
- `frames` records FrameTimeline separately where ftrace recording fails. It
  cannot be joined to frames from a different run or prove the intended focus
  journey on its own. Frame lifetime is not CPU duration or key-to-photon latency.
- `cpu` uses standard 99 Hz scoped Perfetto call-stack sampling. Report unwinding
  errors and unresolved frames. `native-allocations` uses scoped heapprofd with
  16 KiB sampling: estimates of native allocation activity, not Java object
  contents or exact managed allocation rate. These are separate, costlier
  diagnostic captures, not the normal frame-timing baseline. No heap dump is used.
- `work.sql`, `states.sql` and `phases.sql` intersect slices with scheduled CPU;
  do not sum nested sections. Runnable wait differs from blocked wait. `gc.sql`
  reports elapsed GC sections; concurrent GC duration is not a main-thread pause.
  `latency.sql` measures app-dispatch-to-focus-callback, not physical remote or
  photon latency. Recommendations need trace-backed attribution and explicit
  confidence, not an assumed bottleneck from source complexity alone.

Use small repeated baselines, expand only when observed variance warrants it,
and keep builds and other captures out of measurement windows. Run affected
tests and the final repository gate for the final relevant state; reuse unchanged
evidence. The historical qualification linked below is not current journey acceptance.

The earlier static-rail fixture (`JourneyProfileActivity` replaced it), its tool
qualification and the first measurements are kept in
[profiling-p38-static-rail-2026-09-09.md](profiling-p38-static-rail-2026-09-09.md).
They are not current journey acceptance.

## Physical boundary

A physical journey needs an attended safe foreground/time window before launching
UI or sending keys on the household TV. The operator being asleep is not that
confirmation. At that boundary, recheck exact G10 test identity, artifact/signer
and installed version/provenance, and query actual Android 12 sources before a
bounded trace. An offline or emulator trace does not establish G10 event
support, SurfaceView timing, readability, motion, remote-repeat feel or a
perceptual pass.
