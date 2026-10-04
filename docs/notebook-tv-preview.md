# Notebook032 TV preview

> **Status:** Operational. Launcher, laptop keyboard and fullscreen viewer were
> confirmed by the operator on 2026-10-04. Recheck live identity and artifact state
> for each operation; this is not a claim that any particular APK remains installed.

## When to use it

Use **local checks → Notebook032 interactive iteration → G10 physical acceptance**.
Notebook032 is the existing test target for quickly trying integrated layouts,
D-pad focus, launcher entry, Home and Back with the operator. Deterministic
fake-state production captures remain the first choice for static visual review;
the LXC119 automated lane remains in [android-tooling.md](android-tooling.md).

Follow `android-tv-device-testing` and the common install/capture rules in
`android-tooling.md`. One primary owns an operation, including SSH and host-service
setup. Coordinate use of an already occupied preview; a running service is not an
ownership lock. Do not start duplicate emulators or silently switch targets.

## Target and private connection configuration

The engineering host's owner-only `~/.config/tvhplayer/notebook-preview.json`
contains `ssh_destination`. It is routing configuration, not a credential store.
Keep the SSH destination and household addresses out of Git. If it is absent,
obtain the intended destination from the operator rather than guessing a host.
Load it for the commands below:

```bash
export TVHPLAYER_NOTEBOOK_SSH="$(python3 -c 'import json,pathlib; print(json.loads((pathlib.Path.home()/".config/tvhplayer/notebook-preview.json").read_text())["ssh_destination"])')"
```

| Property | Qualified value |
|---|---|
| Role | Existing test emulator on Notebook032 |
| AVD | `tvhplayer-android-tv` |
| Serial on Notebook032's ADB server | `emulator-5558` |
| Image | `system-images;android-31;android-tv;x86`, revision 4 |
| Manufacturer / model | `Google` / `AOSP TV on x86` |
| Device / product | `generic_x86` / `sdk_google_atv_x86` |
| Display | 1920×1080; fullscreen viewer scaling does not change Android density |
| Acceleration | KVM and host graphics |
| SDK on Notebook032 | `$HOME/Android/Sdk` |
| SDK CLI Java runtime | `/opt/android-studio-canary/jbr` |
| Emulator user unit | `tvhplayer-android-tv-preview.service` |
| Viewer user unit | `tvhplayer-fullscreen-preview.service` |

Before mutation, check the host, AVD and all four live identity properties; stop
on mismatch. Run console queries on Notebook032 itself:

```bash
ssh "$TVHPLAYER_NOTEBOOK_SSH" 'bash -s' <<'REMOTE'
set -eu
adb="$HOME/Android/Sdk/platform-tools/adb"
"$adb" -s emulator-5558 emu avd name </dev/null
for property in ro.product.manufacturer ro.product.model ro.product.device ro.product.name; do
  "$adb" -s emulator-5558 shell getprop "$property" </dev/null
done
REMOTE
```

`emulator-5558` is scoped to that host's ADB server, not globally unique. The
LXC119 tunnel does **not** reach this target. If using a local CLI instead of the
remote commands below, use a loopback-only SSH forward to Notebook032's ADB
server, set `ANDROID_ADB_SERVER_PORT` to its free local port, keep explicit device
selection, and close the owned forward afterward. `adb emu avd name` needs the
remote emulator console, so it cannot use an ADB-server-only forward. In SSH
stdin scripts, redirect ADB's stdin from `/dev/null` so it cannot consume the
remaining script.

## Reuse or start the preview

Inspect the two named user units with `systemctl --user is-active` on Notebook032.
Reuse running services. These are transient units, so they may be absent after
logout/reboot; the AVD and its data persist. If the emulator is already running
outside the named unit, resolve ownership before starting another instance.

When the emulator is stopped and an interactive preview is requested, start it
in the logged-in desktop user's session:

```bash
ssh "$TVHPLAYER_NOTEBOOK_SSH" 'systemd-run --user --unit=tvhplayer-android-tv-preview --collect "$HOME/Android/Sdk/emulator/emulator" -avd tvhplayer-android-tv -port 5558 -memory 2048 -partition-size 2048 -no-snapshot'
```

Confirm boot completion and the target identity before app operations. Reuse the
existing AVD; do not recreate it or run SDK installation during an ordinary loop.
The TV profile needs `hw.keyboard = yes` in its `config.ini` (already set).
Change it only while this emulator is stopped. ADB key injection does not prove
the laptop keyboard works.

The fullscreen viewer is the official portable scrcpy 4.1 release at
`~/.local/opt/scrcpy-linux-x86_64-v4.1` on Notebook032. Archive SHA-256:
`ad56ae8bfeedf41e824945c11dbf55fcb092b3e615b9b486f48a50e30d389635`.
When the viewer is stopped, start it against the running emulator:

```bash
ssh "$TVHPLAYER_NOTEBOOK_SSH" 'systemd-run --user --unit=tvhplayer-fullscreen-preview --collect --setenv=ADB="$HOME/Android/Sdk/platform-tools/adb" "$HOME/.local/opt/scrcpy-linux-x86_64-v4.1/scrcpy" --serial=emulator-5558 --fullscreen --no-audio --window-title="TVHeadend Player — TV preview" --shortcut-mod=lalt'
```

- **Arrows / Enter:** D-pad / select. Focus the viewer window first.
- **Left Alt+H / Left Alt+B:** Home / Back with the configured scrcpy modifier
  ([upstream shortcuts](https://github.com/Genymobile/scrcpy/blob/v4.1/doc/shortcuts.md)).
- **F11 or Left Alt+F:** toggle fullscreen.
- Closing the viewer leaves the emulator and app running. `--no-audio` disables
  viewer audio forwarding; it is not an app audio test.
- The normal TV launcher supports Player tile launch, Home and Back. Test the
  app's current state explicitly; fresh-setup Back is not configured-app Back.
- Do not retry KDE maximize, invent a fullscreen emulator flag or use obsolete
  `-scale` options. Manual corner resize works for the standalone emulator;
  scrcpy provides the qualified fullscreen path.

## Update and inspect one iteration

1. Build and verify on the engineering host using the task's required gates.
   Reuse successful evidence for unchanged code. Record the source revision/diff,
   exact APK SHA-256, version code and signer. Check the installed version/signer
   before replacement; never automatically downgrade, uninstall or clear data.
   An earlier one-off uninstall was explicitly approved and is not standing policy.
2. Skip installation if the verified APK is already installed. Otherwise transfer
   that exact APK into a fresh private runtime directory on Notebook032, then use
   its official Android CLI. This avoids depending on a local ADB default:

   ```bash
   # APK is the absolute path to the verified artifact selected for this task.
   : "${APK:?Set the verified APK path}"
   remote_stage=$(ssh "$TVHPLAYER_NOTEBOOK_SSH" 'umask 077; mktemp -d "$XDG_RUNTIME_DIR/tvhplayer-preview.XXXXXX"')
   scp "$APK" "$TVHPLAYER_NOTEBOOK_SSH:$remote_stage/app.apk"
   ssh "$TVHPLAYER_NOTEBOOK_SSH" "bash -s -- '$remote_stage'" <<'REMOTE'
   set -eu
   export JAVA_HOME=/opt/android-studio-canary/jbr
   export ANDROID_HOME="$HOME/Android/Sdk"
   "$ANDROID_HOME/cmdline-tools/latest/bin/android" --no-metrics install \
     --device=emulator-5558 --apks="$1/app.apk" \
     --use-delta-install=false --install-options=-r,-t
   REMOTE
   ```

3. Read the installed base APK path with explicit-serial `pm path`, hash those
   installed bytes and compare with the verified local APK. An install message
   alone is not provenance. Preserve app data. If version metadata prevents an
   update, resolve it deliberately for the preview artifact; do not change tracked
   release versions incidentally or overwrite another owner's preview blindly.
4. Launch from the pinned Player tile for launcher-entry checks. Use bounded ADB
   launch/key operations when the CLI lacks them, always on this exact target.
   For human-visible feedback, ask one focused question and wait. A process,
   successful key command or screenshot does not establish the operator's result.
5. For safe-screen evidence, use Notebook032's official CLI
   `android --no-metrics screen capture --device=emulator-5558 --output=<fresh-path>`
   with the Java/SDK environment above. Copy the PNG into a private ignored local
   `captures/` directory and open it. Record installed hash, source, scenario,
   canvas, locale, font scale and focus. Never capture credential/settings screens.
   Use device captures, not desktop screenshots of the scaled viewer.
6. Remove only this operation's staging files and close owned tunnels. Leave an
   interactive preview open for the operator when requested. Automated tests keep
   their app/test cleanup requirements; do not shut down a human's preview as
   routine test cleanup.

## Boundaries and recovery

- The standard Android TV launcher is the qualified path. The retired Google TV
  AVD had unusable onboarding; do not recreate it or try provisioning-flag tricks
  as part of a UI iteration.
- Emulator creation once failed for insufficient disk: despite `-partition-size
  2048`, it required a minimum 6 GiB data partition and about 7.2 GiB free to create
  it. Ordinary iteration reuses existing data; do not delete another AVD or image
  to recover space without explicit authority.
- This is API 31/x86, not LXC119's API 36/16 KiB Google TV image. Do not redirect
  identity-pinned screenshot tests or interpret emulator timing as G10 performance.
- Connection setup/provisioning retains the existing credential policy. The
  qualified launcher/fullscreen workflow does not establish a configured backend,
  successful playback, physical remote feel, SurfaceView visibility on a TV,
  overscan, interlacing, HDR or motion quality. Those remain G10 observations.
