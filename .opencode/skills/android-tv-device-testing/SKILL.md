---
name: android-tv-device-testing
description: Use for physical TV or emulator operations including ADB, device screenshots, test-device credential provisioning, APK installation, playback checks, key injection, HOME, GUIDE, standby/wake, reboot, and device diagnostics. Not for source-only UI or key-handler edits.
---

# Android TV Device Testing

Use official Android CLI with explicit `--device` for ordinary install/capture
on physical TVs and the authorized existing LXC119 emulator. Follow
`docs/android-tooling.md`; use explicit-serial ADB only for capabilities the CLI
lacks. Keep specialized credential, signed-release and instrumentation acceptance
gates. No route permits broad ADB dumps or automatic uninstall/data clearing.

## Before touching the device

1. Apply `AGENTS.md` (caller-inlined hard requirements for restricted children).
   Read `docs/device-targets.md` for physical targets; use the emulator procedure
   in `docs/android-tooling.md` for emulator operations. Read runtime criteria from
   `docs/appliance-mode-spec.md` only for an appliance behavior check, the credential
   provisioning document only for provisioning, and `docs/release-process.md`
   for signed installation/release work.
2. Identify the exact source/artifact state and attribute any uncommitted work;
   do not claim a clean checkout or adopt another owner's changes.
3. Confirm relevant checks and the required final gate passed for the artifact
   being installed. Reuse unchanged verified-artifact evidence rather than
   rerunning the build for every install. Existing admitted gates still apply.
4. Select the intended serial from ignored owner configuration and pass it
   explicitly as CLI `--device` (or ADB `-s`). A `tools/device` profile does not
   select the CLI target. Never commit a household address as a required default.
5. Confirm the package under test. The appliance default is
   `at.bernhardberger.tvhplayer`; rollback clients use different package IDs.
6. Confirm the selected role and matching live manufacturer, model, device and
   product before mutation. Only a designated development target may be `test`.
   Bounded ADB `getprop` supplies these fields; `tools/device doctor` is optional,
   not a redundant mandatory preflight.

## Safe sequence

For an authorized test device, install only when the required verified APK is not
already installed. An install does not authorize credential provisioning or launch:

```bash
android --no-metrics install --device="$TVHPLAYER_ADB_SERIAL" --apks=app/build/outputs/apk/debug/app-debug.apk --use-delta-install=false --install-options=-r,-t
```

The CLI lacks standalone remote-key input. Existing bounded named key commands
remain useful for separately authorized navigation:

```bash
./tools/device key up
./tools/device key down
./tools/device key left
./tools/device key right
./tools/device key center
./tools/device key channel-up
./tools/device key channel-down
./tools/device key guide
./tools/device key home
./tools/device key back
./tools/device key power
```

For ordered navigation, send a short screen-agnostic sequence in one invocation
instead of consuming one agent turn per key:

```bash
./tools/device keys down down right center --delay-ms 250
./tools/device key down --repeat 3 --delay-ms 250
./tools/device key center --long-press
```

Both commands validate device readiness and exact identity once per invocation.
They accept at most 100 events and a delay from 0 through 5000 milliseconds; the
default delay is 300 milliseconds. `--long-press` uses Android's key-event
long-press flag rather than holding a key for a configurable duration. During
active UI iteration, prefer short explicit sequences that can change with the UI
over permanent screen-specific scenarios. Keep atomic `key` calls for exploratory
steps where the next direction depends on the resulting screen.

For production and unclassified devices, use only bounded diagnostics such as
`doctor`, `current`, and `package-info`. Do not bypass the role policy with raw
ADB commands.

## Screenshots

After confirming that no connection, settings, password, or other secret-bearing
screen is visible, capture the designated test TV with:

```bash
android --no-metrics screen capture --device="$TVHPLAYER_ADB_SERIAL" --output="$evidence/channels-trailing-clipping.png"
```

Use a fresh filename in a private ignored evidence directory (`umask 077`,
directory mode 0700), and open the newly produced PNG to confirm usable output and
the intended screen. CLI 1.0.16261425 can exit 0 with no PNG for a nonexistent
device; missing/stale output is failure, not a reason for an ADB fallback or a new
checker/preflight framework. Filenames do not establish source provenance.
For attributed evidence, record the installed app/test APK
hashes matched to verified local artifacts, source revision, scenario, canvas,
locale, font scale and focus. Keep host screenshots distinct from composable
captures and never attribute a launcher image to Player.
The existing `tools/device screenshot --synthetic-video-backdrop` flow remains
specialized setup/capture/cleanup for a debug backdrop, not ordinary capture or
proof of live video. Screenshots can validate static layout,
focus appearance, clipping, and text, but cannot establish video visibility or
motion quality.

## Video-plane progress

`screencap` never captures the SurfaceView, so a black capture cannot separate
"controls hidden over live video" from a frozen or empty picture. For a bounded
playback-progress signal, sample the compositor's per-frame timing for the app's
video layer:

```bash
./tools/device video-frames
./tools/device video-frames --window-seconds 0.5
```

It prints `newFrames`, `approxFps`, and `videoPlane=rendering|frozen|no-surface`
for frames presented between two samples of the window (0.2 to 30 seconds). The
query carries only frame timestamps and is allowed for every device role. Repeat
short windows after a channel change to measure time to first frame and stalls.
It proves that frames reach the display, not what they show; picture quality,
deinterlacing, and motion judgement remain human physical-TV gates.

`--plane ui` samples the activity window instead, which shows whether the main
thread keeps producing Compose frames, for example while a channel change is in
flight.

## Thread load

```bash
./tools/device thread-load
./tools/device thread-load --window-seconds 5
```

It reports the app process's per-thread CPU share for one `top -H` window
(`processCpuPercent` and the busiest threads by name). Thread names are the only
content it exposes; it is allowed for every role. Use it before profiling to
tell decoder or renderer work from dispatcher or main-thread churn.

## Test credential provisioning

Provision only a designated test device after installing the debug APK. Put the
credential JSON in the ignored path configured by `credential_file`, set its
mode to `0600`, and run `./tools/device provision-test-credentials`. The wrapper
validates role plus all four live identity properties before reading the secret,
streams the payload over stdin into the debug app's private directory, suppresses
device output for that operation, launches the app to consume it, and reports
only a non-sensitive acknowledgment.

The password is then stored by the existing Android Keystore-backed store. The
plaintext staging file is deleted whether import succeeds or fails. Delete the
local secret after provisioning unless it is intentionally retained for repeated
test setup. See `docs/test-device-credential-provisioning.md` for setup, cleanup,
threat model, and limitations.

## Verification matrix

For an appliance-affecting change, record the applicable checks:

- APK installs on the 32-bit `armeabi-v7a` Android TV target.
- Fresh launch connects and reaches the expected UI or live channel.
- Progressive playback remains smooth.
- Interlaced sports playback passes direct human motion-quality review.
- Physical `CH+` and `CH-` zap correctly and wrap.
- Last played channel survives force-stop and relaunch.
- Back exits playback without an autoplay loop.
- HOME reaches the expected appliance entry path.
- GUIDE/TV is intercepted only when the accessibility service is enabled.
- Standby/wake behavior is correct.
- Cold reboot retains the chosen HOME and accessibility state.
- Google Basic TV and rollback clients still launch directly.

When a check requires a physical remote action or human-visible judgment, ask
one focused question and wait. ADB subscription or decoder counters do not prove
acceptable motion quality.

## Secret and privacy boundary

- Do not dump UI hierarchies on connection/settings/password screens.
- Do not capture screenshots while a connection, settings, password, or other
  secret-bearing screen is visible.
- Do not print SharedPreferences, DataStore, Android Keystore entries, app-private
  files, full `dumpsys`, or unrestricted `logcat` output.
- Do not type credentials through an uncertain focus state.
- Do not add exported debug components to inject credentials.
- Do not pass credential values with `--serial`, `--package`, shell arguments,
  environment variables, or raw ADB commands. Use only the ignored local secret
  file and the bounded provisioning command.
- If a secret appears in output, stop the exposing operation, avoid copying it,
  and report the affected credential and clients without its value. Contain the
  exposure path within existing authority. Never rotate or replace a credential
  without explicit approval; TVHeadend credentials must not be changed.
