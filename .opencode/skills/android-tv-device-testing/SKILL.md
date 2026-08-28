---
name: android-tv-device-testing
description: Use for TVHeadend Player Android TV or TCL ADB testing, screenshots, test-device credential provisioning, APK installation, playback checks, remote keys, HOME, GUIDE, standby/wake, reboot, and device diagnostics.
---

# Android TV Device Testing

Use the repository's bounded `./tools/device` wrapper instead of ad-hoc broad ADB
dumps whenever it supports the required operation.

## Before touching the device

1. Read `AGENTS.md` and `docs/device-targets.md`. Read runtime criteria from
   `docs/appliance-mode-spec.md` only for an appliance behavior check.
2. Confirm the source tree is clean or identify the exact uncommitted slice.
3. Run the relevant JVM test, then `./tools/verify` before installing.
4. Configure the ADB serial through ignored `.tvhplayer-device.json`,
   `TVHPLAYER_ADB_SERIAL`, or `--serial`. Never commit a household device
   address as a required default.
5. Confirm the package under test. The appliance default is
   `at.bernhardberger.tvhplayer`; rollback clients use different package IDs.
6. Run `./tools/device doctor` and confirm the local role. Only a designated
   development target may be `test`,
   and mutations require matching manufacturer, model, device, and product.

## Safe sequence

The following restricted sequence is available only for a configured test device:

```bash
./tools/device doctor
./tools/device install-debug
./tools/device provision-test-credentials
./tools/device force-stop
./tools/device launch
./tools/device current
./tools/device package-info
./tools/device screenshot --confirm-safe-screen
```

Use named key commands rather than numeric key codes:

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
./tools/device screenshot --confirm-safe-screen --name channels-trailing-clipping
```

The default owner-only output is written beneath the ignored workspace path
`captures/device/<12-char-HEAD>[-dirty]/` with a UTC timestamp and the sanitized
`--name` slug. If `--name` is omitted, it falls back to `current-screen`. This
keeps screenshots previewable in OpenCode/OpenChamber while recording their base
revision without pretending a dirty working tree exactly matches that commit.
Pass `--output` only when an exact path is required; repository paths are allowed
only beneath `captures/device/`. The wrapper requires exact test-device identity,
validates the PNG, and replaces the output atomically. Use the file-reading tool
to inspect the printed result path. Screenshots can validate static layout,
focus appearance, clipping, and text, but cannot establish video visibility or
motion quality.

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
- If a secret appears in output, stop, rotate it, verify the old value is
  rejected, and remove the exposure path before continuing.
