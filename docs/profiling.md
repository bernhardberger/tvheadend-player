# Player Journey Profiling

## Current capture workflow

Use `tools/profiling/capture` and the standard Perfetto Trace Processor through
`tools/profiling/analyze.py`. Keep traces, CSVs, screenshots and source snapshots
private. Device authorization and exact-target checks in `device-targets.md` and
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

Both retain the existing release R8/shrinking settings and native dependencies.
Only these two variants enable the fixed, content-free `P44:*` trace sections.
Ordinary debug/release paths do not call Android tracing through this helper.

```bash
timeout --kill-after=5s 20m ./gradlew :app:assembleProfile :app:assembleProfileServer :app:lintProfile :app:lintProfileServer --offline --no-daemon --console=plain --no-scan
```

The isolated lifecycle regression uses the standard AGP test build type, enabled
only by `-Ptvhplayer.profileTests=true`. Build with
`:app:assembleProfile :app:assembleProfileAndroidTest`, install both matching APKs
on the authorized emulator, and run `ProfileRecreationTest` through
`at.bernhardberger.tvhplayer.profile.test/androidx.test.runner.AndroidJUnitRunner`.
Require the named test's success status, `OK (1 test)` and final code `-1`, not
ADB exit status alone. The test recreates the activity, checks catalog/session
identity and dataset size, and verifies exactly one shutdown for each owner.
The fixture deliberately restarts its activity-owned runtime on recreation and
clears the corresponding catalog; ordinary debug instrumentation is unchanged.

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
and unguarded baselines. It records one to three eight-second runs and **force-stops
only the target package on exit**. This can reveal the launcher or another app
underneath; no further keys are sent there. A new batch needs explicit launch and
foreground verification again.

For an already prepared Channels first-row focus, for example:

```bash
bash tools/profiling/capture causal "${TVHPLAYER_ADB_SERIAL:?select the authorized device}" at.bernhardberger.tvhplayer "$PRIVATE_OUTPUT" 3 20 20 20 20 19 19 19 19
python3 tools/profiling/analyze.py --trace-processor "$TRACE_PROCESSOR" --focus-kind channel --focus-count 8 "$PRIVATE_OUTPUT"
```

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
evidence. The historical qualification below is not current journey acceptance.

## Historical P38 static-rail qualification

The remainder describes the earlier static-only fixture and its dated evidence.
Its single-activity/no-testing-library inventory predates `JourneyProfileActivity`.

### Earlier build boundary

`profile` is a nondebuggable, shell-profileable variant initialized from `release`.
It uses the same main Kotlin code, release-only no-op debug backdrop, compiler,
resources, native libraries and dependencies. Release currently disables R8
minification and resource shrinking. Profile preserves those settings; it is
release-like relative to this unoptimized release baseline, not an optimized
production benchmark. No R8, dependency, codec or product behavior change is included.

The deliberate fixture differences are an application ID suffix `.profile`, the
existing local debug signing certificate, a plain Application instead of SDK
startup, removal of INTERNET and the production activity/accessibility service,
and one synthetic activity calling the unchanged production `SideRail`. The
four ordinary destinations are Channels, Guide, Recordings and Settings. The
content pane is a static label, not a simulated Guide or live catalog. This build
cannot load saved Player profiles or contact TVHeadend and is not a deployment
replacement for Player. It adds no libraries or custom profiler.

```bash
timeout --kill-after=5s 20m ./gradlew :app:assembleRelease :app:assembleProfile :app:lintProfile --offline --no-daemon --console=plain --no-scan
timeout --kill-after=5s 30m ./tools/verify
```

Inspect the packaged manifest with `aapt2 dump xmltree <apk> --file
AndroidManifest.xml`: require absent/false `debuggable`, `profileable shell=true`,
the suffixed ID and no INTERNET permission. A debug **certificate** does not make
an APK debuggable. Verify the signer using `apksigner verify --print-certs`.
The only exported activity must be `RailProfileActivity`; its export permits
explicit shell `am start` on the nondebuggable fixture and accepts no payload.
Require no production activity or accessibility service. The dependency-provided
ProfileInstaller receiver retains its `android.permission.DUMP` protection.
Compare release/profile resolved compile/runtime artifacts and their hashes,
build-type minification/shrinking/ProGuard settings, and each uncompressed `lib/`
ZIP member. Do not compare compressed APK sizes as an optimization measurement.

## Standard tools

Use the existing LXC119 tunnel and exact identity procedure in
[Android tooling](android-tooling.md). No new AVD, service, root, boot property,
server, credential provisioning or data reset is needed. Install only the
profile APK, only if needed, with official CLI explicit `--device`,
`--use-delta-install=false` and `--install-options=-r` as in `android-tooling.md`,
then compare the installed base APK's SHA-256 with the local APK. Preserve prior artifacts and
stop rather than retrying with uninstall, downgrade or another signer.

Recording uses Android's installed `perfetto` CLI, not a custom recorder.
`perfetto --query` establishes advertised sources, not their actual health.
Use `rail-frametimeline.pbtxt` for the qualified path. Both checked-in configs
use Android 12-era sources; UI FrameTimeline does **not** imply SurfaceView
FrameTimeline. `rail.pbtxt` retains the bounded ftrace diagnostic configuration,
which failed qualification on this emulator. Never ignore its parser errors.

Analysis uses the official native Trace Processor v53.0, selected from the
reviewed [v54.0 helper manifest](https://github.com/google/perfetto/blob/v54.0/tools/trace_processor).
The helper itself was not installed or invoked. The exact Linux x86-64 binary
was downloaded from
`https://commondatastorage.googleapis.com/perfetto-luci-artifacts/v53.0/linux-amd64/trace_processor_shell`
and its SHA-256 checked before execution:
`ad41bda12a862bc6ad560d3bc99d9381c34a28bd5fec8e9a94f6fb1d1380ad60`.
It reports `Perfetto v53.0-c1bbc1652`, RPC API 14. No daemon or HTTP endpoint is
required. Keep traces private: these sources include compositor and process
metadata even though the fixture itself contains only synthetic state.

## Repeatable journey

In one existing tunnel, after identity/install verification, use the following
standard commands. Set `out` to a new owner-only evidence directory and `run` to
a unique run identifier; never overwrite a prior trace. All `adb` calls below
are scoped to the tunnel's `emulator-5556`, not a physical TV. Arrange an EXIT
trap to force-stop only the profile package on failure as well as success.

```bash
adb -s emulator-5556 push tools/profiling/rail-frametimeline.pbtxt /data/misc/perfetto-configs/rail-ft.pbtxt
adb -s emulator-5556 shell am force-stop at.bernhardberger.tvhplayer.profile
adb -s emulator-5556 shell am start -W -n at.bernhardberger.tvhplayer.profile/at.bernhardberger.tvhplayer.profiling.RailProfileActivity
sleep 3
adb -s emulator-5556 shell perfetto --txt -c /data/misc/perfetto-configs/rail-ft.pbtxt -o /data/misc/perfetto-traces/rail-$run > "$out/record-$run.log" 2>&1 &
recording=$!
sleep 1
adb -s emulator-5556 shell 'for cycle in 1 2 3; do for key in KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN KEYCODE_DPAD_UP KEYCODE_DPAD_UP KEYCODE_DPAD_UP; do input keyevent "$key"; sleep 0.3; done; done'
wait "$recording"
adb -s emulator-5556 pull /data/misc/perfetto-traces/rail-$run "$out/rail-$run.perfetto-trace"
test -s "$out/rail-$run.perfetto-trace"
adb -s emulator-5556 shell am force-stop at.bernhardberger.tvhplayer.profile
```

Wait for the duration-bounded recording process before pulling; do not accept
an empty file or transport exit alone. On interruption let the 20-second device
recording expire before closing the tunnel. Verify `pidof` reports no fixture
process afterwards. Run three times without concurrent builds or captures.
Capture rehearsal endpoints separately, outside the timed recording, to check
Channels initial focus, Settings after three Downs, and Channels after three Ups.
This is a **collapsed-rail focus sweep**. The drawer did not expand or commit
destination changes in this fixture; do not label it full browse navigation.

Run each SQL file separately (Trace Processor permits one result set per query):

```bash
trace_processor_shell "$out/rail-$run.perfetto-trace" -q tools/profiling/health.sql
trace_processor_shell "$out/rail-$run.perfetto-trace" -q tools/profiling/rail.sql
trace_processor_shell "$out/rail-$run.perfetto-trace" -q tools/profiling/jank.sql
```

Require successful import without parser overrides, no nonzero warning/error/loss
stats, the exact app process/activity layer, nonzero complete frames, and repeated
scenario evidence. Empty health output alone is not a pass for an empty trace.

## First measurement

Measured 2026-09-09 under P38-A2, based on
`1177c28d31e6bcf8995c67497423fe1ddb25d4a9` plus this profiling-only slice.
The local evidence directory is ignored `artifacts/p38-profile/`; its APK hash,
not a checkout-based filename, binds the following measurements to built bytes.

| Identity | Observed value |
|---|---|
| Profile APK and installed bytes | `c9c37b7c3f1d0efe160056dc8bce11d9d958c2fb4832078a1b3e894561833c8c` |
| Package/version | `at.bernhardberger.tvhplayer.profile`, `0.2.7` / code `13` |
| Local signer SHA-256 | `9bc9f0df0a2cef21954e6c73b947cfc0b4c86b22f2abe379bb491c537d17ba2b` |
| Release unsigned APK | `ba6a9fadf96a695b81c41f8d99ef7d3e69a4d5a76e6a5e5b96f2d009124bc314` |
| Unchanged debug APK | `68a4f49bad047b3c29a9211c741863751e969904bde37fc26db87783cce23a58` |
| LXC119 identity | Google / `sdk_google_atv64_amati_x86_64_16k` / `emu64xa16k` / `sdk_google_atv64_amati_x86_64_16k` |
| Runtime | API 36, built-in Perfetto v49.0; 1920x1080, density 320, font scale 1.0 |
| Locale | Rail labels use English; `persist.sys.locale` was empty, not proof of a forced locale |
| Workload | Three cold fixture starts; 3-second settle, then 18 key presses per run, 300ms sleeps plus input-command overhead |

Release/profile resolved compile/runtime artifact identities **and bytes** matched
in a successful Gradle assertion run (`baseline.gradle`, `baseline-3.log` in local
evidence; run with `--no-configuration-cache`). Nondebuggable, unminified,
resource-shrinking and ProGuard equivalence passed. All native ZIP member names
and uncompressed bytes matched across release/profile. SDK remains public 0.12.0,
Media3 1.11.0, AGP 9.3.1, Kotlin 2.4.10; the final verifier passed native/source
provenance, 127 tool tests, static checks, JVM tests, debug lint/assembly and
Android-test compilation. Profile assembly and profile lint passed separately.
The regular SDK-consumption gate enumerates debug/release, not profile; profile
equivalence rests on the separate artifact-byte comparison above, which must be
repeated when build settings or dependencies change. The final merged manifest
also confirmed shell profileability, the suffixed ID, no INTERNET permission,
and only the fixture activity exported; `apkanalyzer` returned `false` for
debuggability. The existing release no-op source is tracked despite the broad
release-directory ignore pattern. Release preparation selects the exact unsigned
release APK path, not a wildcard including the profile output.
Android instrumentation tests were not executed for this fixture.

Each final recording requested 20 seconds and completed normally. Only the
active UI events span about 5.6 seconds; idle time need not generate frames.
Every final trace imported without overrides, returned no nonzero non-info
health stats, and contained one attributed app layer with 54 complete frames.
All three runs had zero incomplete app frames. `health.sql` rejects any incomplete
app frame, and `rail.sql` excludes negative-duration sentinels from aggregates.

| Run | App PID / layer suffix | Event bounds (s) | Frames | Mean (ms) | p50 (ms) | p95 (ms) | Max (ms) |
|---|---|---:|---:|---:|---:|---:|---:|
| 16 | 13070 / 6094 | 5.632 | 54 | 17.889 | 16.585 | 33.257 | 33.392 |
| 17 | 13238 / 6103 | 5.616 | 54 | 17.815 | 16.611 | 33.248 | 33.512 |
| 18 | 13391 / 6114 | 5.616 | 54 | 18.730 | 16.606 | 33.152 | 33.340 |

These are `actual_frame_timeline_slice.dur` elapsed frame lifetimes. They are not
CPU work durations, key-to-focus latency, key-to-photon latency, or a continuous
FPS measure. Mean lifetime varied by 0.915ms across runs; frame count and p50
were stable. This establishes a repeatable basic UI measurement path, not an
optimization verdict. No explicit ART compilation or baseline-profile operation
was performed; default installed/JIT state and shared virtual-host load remain
confounders. The final three runs had no owner-initiated concurrent builds.

Frame classifications are a further limit: respectively 33/32/31 of 54 frames
were `Unknown Jank`, and many remaining frames included `Prediction Error`.
The classifier tagged 6/5/8 frames with `App Deadline Missed`, but these are not
a trustworthy physical-TV jank rate. Do not turn these labels into a product
performance defect without better device evidence.

Trace SHA-256 values:

- Run 16: `53ec575bc1b12f4b5ca078d666538f5fc58d735fd2ffd56f7ec31464e702dd6e`
- Run 17: `73d073055051a83e677d3ce0ae9f68afd3fcc8af22999e99831c9d3f47a6362b`
- Run 18: `0fd70150ea0c5e4ceb09de56a7ca34328c4fc2ae8e6797e80cd2217f42cbccbd`

Rejected trials remain separate: background-mode pulls were zero bytes; foreground
ftrace runs failed import with `FTRACE_STATUS_PARTIAL_PAGE_READ`. Moving the config
from `/data/local/tmp` to `/data/misc/perfetto-configs` resolved a permission denial
without root or property changes. Preliminary APK/traces and a run concurrent with
lint are not the final measurement. Ftrace scheduling, app slices, CPU attribution
and input latency remain unqualified; no parser bypass was used.

## Physical boundary

No G10 launch, keys, capture, trace or install occurred. The normal debug APK is
byte-identical to P38-A1, so this setup-only slice does not qualify for a
higher-version G10 progress update. The isolated profile fixture is not that
standing policy's data-preserving Player debug replacement. Existing installed
G10 identity and prior artifacts have not been changed or re-attributed.

A physical journey needs an attended safe foreground/time window before launching
UI or sending keys on the household TV. The operator being asleep is not that
confirmation. At that boundary, recheck exact G10 test identity, artifact/signer
and installed version/provenance, and query actual Android 12 sources before a
bounded trace. No G10 event support, SurfaceView timing, readability, motion,
remote-repeat feel or perceptual pass is claimed from this offline result.
