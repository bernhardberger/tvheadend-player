# Android Tooling

## Standard workflow

Use Gradle/AGP for builds, AndroidJUnitRunner for instrumentation, and explicit
ADB serials for the authorized existing LXC119 emulator. No Player device profile,
Gradle workflow ledger, emulator reset, uninstall, data clear, new AVD or
credential provisioning is part of an offline run. Keep one operation owner.
Physical TV operations still follow `device-targets.md` and `tools/device`.

Build on the engineering host, with disk-backed `$HOME/.gradle`:

```bash
timeout --kill-after=5s 20m ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --offline --no-daemon --console=plain --no-scan
timeout --kill-after=5s 30m ./tools/verify
```

`--offline` requires already cached dependencies; a cache miss is a failed build,
not permission to change dependencies. `tools/verify` includes the published SDK
and native provenance gates. It compiles Android tests but does not run them.
Use the `gradle-run` skill for bounded logs, cancellation and live-test isolation.

Use the existing remote lane only to obtain the ADB tunnel. This example runs a
single bounded ADB command; for a sequence, use one owner shell/script as the
command after `--`, with the same explicit serial on every ADB invocation:

```bash
/root/homelab/tools/android-emulator with-adb-tunnel offline-player -- /opt/android-sdk/platform-tools/adb -s emulator-5556 get-state
```

Inside that tunnel, use these standard operations in order. Do not run them
outside it: `emulator-5556` is scoped to that remote ADB server, not globally unique.

```bash
adb -s emulator-5556 shell 'for p in ro.product.manufacturer ro.product.model ro.product.device ro.product.name; do getprop "$p"; done'
adb -s emulator-5556 install -r -t app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5556 install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5556 shell pm list instrumentation
adb -s emulator-5556 shell pm path at.bernhardberger.tvhplayer
adb -s emulator-5556 shell pm path at.bernhardberger.tvhplayer.test
adb -s emulator-5556 shell am instrument -w -r -e class at.bernhardberger.tvhplayer.ui.player.PlayerScreenshotTest -e playerScenarios live at.bernhardberger.tvhplayer.test/androidx.test.runner.AndroidJUnitRunner
```

Before installation, confirm the intended emulator's four live properties against
the qualified identity below. Stop on mismatch. Confirm the installed runner
targets the app. Hash the exact base APK paths returned by `pm path` using
`adb -s emulator-5556 shell sha256sum <base-apk-path>` and compare both to local
`sha256sum` results before attributing tests to source. Preserve those hashes,
local source revision and verification result. An install acknowledgment is not
installed-byte verification. No implicit uninstall retry or downgrade is allowed.

The named capture run must report both `captureProductionChrome[live-dark=false]`
and `captureProductionChrome[live-dark=true]` completed with status 0, `OK (2 tests)`
and `INSTRUMENTATION_CODE: -1`. Reject failed, skipped, missing or zero tests.
**ADB exit 0 and final instrumentation code -1 do not prove test success.** Read
the runner result before accepting or pulling captures. For unattended test gates,
retain the existing acceptance runner's result/count/skip validation rather than
replacing it with an exit-code-only shell invocation.

After that successful run, pull the production-composable PNGs into a new private
evidence directory (`umask 077`, directory mode 0700):

```bash
adb -s emulator-5556 pull /sdcard/Android/data/at.bernhardberger.tvhplayer/files/p37-player-captures/live-bright.png "$evidence/live-bright.png"
adb -s emulator-5556 pull /sdcard/Android/data/at.bernhardberger.tvhplayer/files/p37-player-captures/live-dark.png "$evidence/live-dark.png"
adb -s emulator-5556 shell am force-stop at.bernhardberger.tvhplayer
adb -s emulator-5556 shell am force-stop at.bernhardberger.tvhplayer.test
```

Before reusing an on-device capture filename, preserve any existing capture in a
separate evidence directory and record the new run's time. Reject stale images
after a failed run. The historical directory name `p37-player-captures` is not
proof of a P37 result; it is simply this unchanged test's output path. Keep app
and test hashes, runner/scenario, image hash, canvas, locale, font scale and focus
together. The example scenario uses US English, font scale 1.0 and Info focus;
canvas depends on the emulator. A checkout-based filename is not installed provenance.

For a safe current-screen capture, standard
`adb -s emulator-5556 exec-out screencap -p` writes PNG bytes to a private file.
Validate the PNG and inspect its content. A post-test launcher image is not a
Player screenshot. Never capture connection/password/account-entry screens or
dump layouts/logcat/app data. Offline fake-state captures do not prove live video,
TVHeadend interaction, motion quality or physical-TV acceptance.

Always force-stop only the app and test package after a run, including failure or
cancellation; wait and verify neither process remains. Do not clear data or
uninstall to perform cleanup. Exiting the tunnel closes its SSH forward; leave
the existing emulator service/boot state alone. The external helper owns tunnel
lifecycle, not Player install or test policy.

## Android CLI qualification

Executed 2026-09-09 under P38-A1, starting from Player
`4ffc7b178e80e17953a881ae7afa481a3249ffc5`. Product source and dependencies were
unchanged. This is a bounded qualification, not approval of every CLI command.

The official installer at
`https://dl.google.com/android/cli/latest/linux_x86_64/install.sh` was read first.
Its binary download was performed directly to `$HOME/.local/bin/android` to avoid
shell-profile changes, then the executable was invoked to extract its payload.
No SDK update, `init`, new emulator, root or boot configuration was requested.
Use `--no-metrics` on subsequent invocations. The initial bootstrap displayed
Google's terms and metrics notice; it was not a no-metrics trial.

| Artifact | Exact qualification |
|---|---|
| CLI version | `1.0.16261425` |
| Launcher SHA-256 | `5f4d1c3db15d664554cd14bf4f5496771fcb25617dd99ac1719f8de5e59ac22c` |
| Extracted `main.jar` SHA-256 | `ad03b3e1c63aa566ce4b87df677716b8f910cda9f5b3434737feec4aac5c8c4d` |
| Bundle directory | `$HOME/.android/cli/bundles/a96b60d749065fbbca9aa68ef10502b6ae4f77f8/` |
| R8 delivered `SKILL.md` SHA-256 | `d6440dc430734b917d92a040ba8c5c305d10b8530c601a019e63e41d3a772514` |

Actual `help`, `skills add --help` and `screen capture --help` were executed.
The command is `android screen capture`, not the skill prose's `android screenshot`.
Actual `run` help defaults delta installation to true, unlike the pinned skill's
embedded help. No `run`, layout, journey, Studio, profiler or SDK-management
qualification is claimed.

`android --no-metrics skills add r8-analyzer --agent=claude-code --project=<private-temp>`
installed the actual distributed skill. `--agent=claude` was rejected. All delivered
guidance/references match the repository's pinned
`android/skills@bac232fd02b0855df9275281a2a7a47643768719` tree. The CLI package omits
`LICENSE.txt`, which the local pinned import retains. Like the pinned upstream
revision, it ships neither required script: `scripts/convert_pb_to_json.py` nor
`scripts/analyze.py`. Therefore its documented
quantitative R8 workflow is **not executable as delivered**. No analyzer task,
conversion or score is claimed. Keep the pinned licensed import; do not generate
a substitute analyzer or change AGP/R8 to hide this payload defect.

## Executed evidence

The qualified LXC119 reports manufacturer `Google`, model/product
`sdk_google_atv64_amati_x86_64_16k`, device `emu64xa16k`. The existing tunnel selected
its remote ADB server and `emulator-5556`; no G10 selection trial was performed.

- Offline assembly succeeded. `tools/verify` passed tool tests, static checks,
  JVM/lint/Android-test compilation, debug assembly and SDK 0.12.0 native/source gates.
- CLI explicit-device installs succeeded with `--use-delta-install=false` and
  `--install-options=-r,-t`, separately for app and test APK. Standard ADB `install
  -r -t` also succeeded. No implicit uninstall, clear-data or app launch occurred.
- App version `0.2.7` / code `13`, APK SHA-256
  `68a4f49bad047b3c29a9211c741863751e969904bde37fc26db87783cce23a58`;
  installed app bytes matched. Debug signer SHA-256
  `9bc9f0df0a2cef21954e6c73b947cfc0b4c86b22f2abe379bb491c537d17ba2b`.
- Test APK and installed bytes matched SHA-256
  `f70d2c67d6589b6a7eae46ef8b10dd282a83848cbf68a99f3216c91f4d4562ca`.
- Two `live` production screenshot tests passed in 2.425 seconds. The inspected
  bright capture is 1920x1080, English, 1.0 font scale, Info focus, synthetic
  backdrop; SHA-256 `13ebde38f98be3cc242be8ec824a33e514f3b9d00498ae7b24613d4afdd2389e`.
- Standard tunneled `adb -s emulator-5556 shell am instrument` also executed
  `PlayerTimelineTruthfulnessTest#programmeFillFollowsPositionWithGrayBufferAndOnlyHistoryStartTick`:
  status 0, `OK (1 test)`, 1.29 seconds. Both packages were then force-stopped,
  neither process remained and the owned local tunnel port was closed.
- CLI reinstall kept firstInstallTime `2026-09-09 02:53:32`, advanced lastUpdateTime
  to `02:55:21`, retained that capture hash and left no Player process. This proves
  observed in-place replacement and capture retention, not every preference value.
- CLI capture succeeded at 1920x1080 after instrumentation; inspection showed
  Google TV's unconfigured setup landing screen, not Player. It is transport
  evidence only, separate from the composable PNG.
- A nonexistent CLI `--device` printed `Error: Device with serial or AVD name
  'p38-nonexistent' not found.` but exited **0** and produced no PNG. Standard ADB
  explicit-serial `get-state` returned **1** for that nonexistent target. CLI is
  optional, not the default unattended failure-reporting gate.
- A deliberately missing instrumentation class reported `initializationError`,
  status **-2**, `FAILURES!!!`, yet ADB exited **0** and instrumentation ended **-1**.
  It was rejected as a failed test, not accepted as transport success.
- A nonexistent Gradle task exited **1** with its task diagnostic. Regression
  fixtures cover inherited live-test filtering/explicit opt-in, ordinary failure
  exit preservation, timeout reporting and signal-resistant descendant cleanup
  after both timeout expiry and an external cancellation signal. The final
  verifier ran 127 tool tests successfully and retained the same app APK hash.

Retained custom pieces cover specific gaps: physical-TV role/identity and secret
provisioning, live acceptance result/count/skip checks, private validated physical
captures, shared build serialization and the existing remote SSH tunnel. They
are not prerequisites for this offline install/instrument/pull sequence. Removed:
mandatory Gradle ledger wrapper/tests and screenshot Git-state lookups. Prior
logs, captures, tags and P37 results are not deleted or reinterpreted.

This slice does not change installed Player bytes, product behavior or dependencies.
It therefore requires no G10 progress install; emulator qualification is not a
public release or physical acceptance result.
