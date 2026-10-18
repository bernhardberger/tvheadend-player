# Android CLI qualification, 2026-09-09

Status: Dated qualification and evidence record, moved out of `android-tooling.md`
on 2026-10-18. It describes the CLI and tooling as qualified on 2026-09-09
(including emulator-only evidence at the app version named below); re-verify
before relying on a version, hash or exit code. The lasting conclusions are in
the "Android CLI limits" section of `android-tooling.md`.

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
  explicit-serial `get-state` returned **1** for that nonexistent target. P38's
  resulting ADB-default decision is superseded: CLI is now the ordinary
  install/capture workflow, with fresh usable output observed as described above.
- A deliberately missing instrumentation class reported `initializationError`,
  status **-2**, `FAILURES!!!`, yet ADB exited **0** and instrumentation ended **-1**.
  It was rejected as a failed test, not accepted as transport success.
- A nonexistent Gradle task exited **1** with its task diagnostic. Regression
  fixtures cover inherited live-test filtering/explicit opt-in, ordinary failure
  exit preservation, timeout reporting and signal-resistant descendant cleanup
  after both timeout expiry and an external cancellation signal. The final
  verifier ran 127 tool tests successfully and retained the same app APK hash.

Caller inspection retains custom pieces for secret provisioning, live acceptance
result/count/skip checks, signed-bundle verification, synthetic-backdrop setup and
cleanup, bounded diagnostics/keys, shared build serialization and the existing
remote SSH tunnel. Legacy `tools/device install-debug` and ordinary `screenshot`
remain available but are not the prescribed workflow; no new code delegates CLI
through that wrapper. These retained pieces are not universal preflights. Removed:
mandatory Gradle ledger wrapper/tests and screenshot Git-state lookups. Prior
logs, captures, tags and P37 results are not deleted or reinterpreted.

This slice does not change installed Player bytes, product behavior or dependencies.
It therefore requires no G10 progress install; emulator qualification is not a
public release or physical acceptance result.
