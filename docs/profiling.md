# Offline Rail Profiling

## Build boundary

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
profile APK with standard explicit-serial `adb install -r`, then compare the
installed base APK's SHA-256 with the local APK. Preserve prior artifacts and
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
