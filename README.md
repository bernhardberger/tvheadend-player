# TVHeadend Player for TV

![TVHeadend Player](artwork/tvheadend-player-logo.png)

TVHeadend Player for TV is an independent, remote-first live-TV client for
TVHeadend servers, built specifically for Android TV and Google TV. It combines
live television, a widescreen programme guide, recordings, timeshift, and an
optional simplified TV experience in one ten-foot interface.

The app connects directly to TVHeadend over HTSP and uses AndroidX Media3 for
playback. Phone and tablet support is not currently planned.

> [!IMPORTANT]
> This project is under active development and does not yet have a stable public
> release. It is not an official TVHeadend application and is not affiliated
> with or endorsed by the TVHeadend project.

## Why TVHeadend Player?

- **Designed for the television:** Compose for TV, Material for TV, predictable
  D-pad navigation, visible focus, TV-safe layouts, and focus restoration
- **Complete TVHeadend experience:** live TV, channel tags, programme guide,
  recording management, recording playback, and server stream profiles over HTSP
- **Physical remote support:** channel up/down, direct channel-number entry,
  media controls, Info, and supported channel-list keys
- **Modern playback:** server-backed timeshift, cinematic controls, track and
  display options, automatic recovery, and detailed playback diagnostics
- **Optional Simple TV mode:** start on the last channel and keep the current
  session in a deliberately restricted, player-only interface

## Live TV and remote control

- Native TVHeadend channel synchronization, picons, channel numbers, and live
  playback over HTSP
- First-run connection setup with explicit connection and synchronization status
- Normal launches open channel browsing in a detailed list or large-card layout;
  autoplay and Simple TV resume the last successfully played channel
- Per-device browsing scopes using TVHeadend channel tags
- `CH+` and `CH-` switching with wraparound during fullscreen playback
- Direct 1- to 3-digit TVHeadend channel-number entry
- Hardware Info, media play/pause, TV contents, TV number-entry, and supported
  remote channel-list keys
- Last-played-channel restoration after a fresh launch
- Warm foreground playback while browsing, with a one-shot return to fullscreen
  without unnecessarily retuning the channel
- Bounded automatic connection and playback recovery with visible,
  Back-cancellable status
- System, German, and English application language selection

## Programme guide

- Widescreen timeline EPG with one shared time axis and fixed channel rows
- D-pad navigation by channel and time, plus channel-key paging without tuning
- Programme details with channel identity, description, episode metadata,
  recording state, and available actions
- Watch, watch from start, record, and cancel-recording actions when supported by
  the programme state and TVHeadend account permissions
- Bounded, incremental EPG synchronization that preserves the last complete
  guide while reconnecting

## Recordings

- Separate **Archive**, **Schedule**, and **Problems** views
- TVHeadend recording folders preserved as a navigable hierarchy
- Recording artwork, channel identity, episode metadata, status, storage size,
  and failure information where available
- Permission-aware recording, cancellation, and deletion actions with safe
  confirmation defaults
- Seekable recording playback with an auto-hiding TV overlay
- 30-second and 10-minute remote seeking shortcuts when controls are hidden
- Restoration of the previous recording view, folder, scroll position, and focus
  after playback

## Playback experience

- Shared cinematic control language for live TV and recordings
- Server-backed live pause and timeshift seeking, including a stable live
  timeline and **Go live** action
- Audio-track, subtitle, display/aspect-ratio, and stream-profile controls
- Programme information and channel selection without leaving playback
- Delayed, non-blocking tuning feedback that keeps the video surface and remote
  input active during ordinary channel changes
- Optional **Stats for nerds** overlay with formats, decoders, rendered and
  dropped frames, audio underruns, HTSP read rate, display mode, thermal state,
  and app memory
- TVHeadend tuner signal, SNR, reception errors, queue delay, and server-side
  drops in diagnostics when the active adapter supplies them

Timeshift availability depends on the TVHeadend server and selected stream
profile. Recording visibility and write operations follow the permissions of the
configured TVHeadend account.

## Simple TV and appliance integration

Simple TV is an optional player-only session intended for straightforward family
or single-purpose television use. It is not Android lock-task or device-owner
kiosk mode and does not take control of the whole device.

- Start a fresh app session directly on the last valid channel
- Keep channels, Guide, Recordings, Settings, Stop, and normal app-exit navigation
  out of the restricted session
- Continue to use channel keys, direct number entry, the player channel drawer,
  and optionally timeshift
- Prevent Back from accidentally leaving fullscreen playback
- Exit through an explicit owner flow with an optional PIN and a separate safe
  confirmation

An optional, user-approved accessibility service can open the app after TV
startup or display wake and handle only the Android GUIDE key plus a captured TCL
TV key. It subscribes to no accessibility events, cannot inspect screen content,
and passes every unrelated key through unchanged. Appliance entry and special
remote-key behavior are device-dependent; direct HOME replacement is not claimed
on TCL firmware that gives its system launcher priority.

## Privacy and network security

- No Firebase, analytics, advertising, or external product account
- TVHeadend credentials remain in app-private storage
- Password protection uses Android Keystore and AES-GCM
- Android backup and device transfer are disabled for application data
- Password input is kept out of saved-instance state and protected from screen
  capture while present

Direct HTSP traffic is not encrypted and does not authenticate the server. Use
it only on a trusted local network or through a protected tunnel such as a VPN.

## Requirements

- Android TV 9 / API 28 or newer
- TVHeadend server reachable over HTSP
- Remote control with D-pad navigation
- Appropriate TVHeadend permissions for EPG or recording operations

Local builds require Java 21 and Android SDK 36.

## Build and verify

```bash
./tools/verify
```

The verifier checks native-library integrity and 16 KB alignment, tool policy,
JVM tests, lint, Android-test compilation, debug assembly, and APK identity and
ABI requirements.

Device operations use an ignored local configuration and the bounded wrapper:

```bash
cp .tvhplayer-device.example.json .tvhplayer-device.json
./tools/device doctor
./tools/device --target nvidia-shield doctor
```

The ignored config can retain named G10, Shield, and production profiles. Its
`active_target` is used by default; `--target` or
`TVHPLAYER_DEVICE_TARGET` selects another profile for one command. Every
restricted operation still verifies that profile's role and all four expected
identity fields against the live device.

Never put TVHeadend credentials, signing keys, or private device addresses in
Git. The debug-only designated-test-device provisioning flow is documented in
[`docs/test-device-credential-provisioning.md`](docs/test-device-credential-provisioning.md).

## Release integrity

The intended distribution path is signed GitHub releases first while retaining
a path to later Google Play distribution. Release preparation and signing are
deliberately separated: reproducible unsigned bundles are prepared on the
engineering host, while the stable private signing key remains on an isolated
owner-controlled host.

The bundled decoder is a pinned Media3 1.10.1 FFmpeg extension containing only
the MP1, MP2, and MP3 decoders from FFmpeg n6.0.1. Its exact revisions,
toolchains, configuration, hashes, licenses, build procedure, and corresponding
source are recorded in [`app/libs/README.md`](app/libs/README.md). Every binary
release must include both application and native corresponding-source archives
and their checksums.

See [`docs/release-process.md`](docs/release-process.md) for the complete release,
signature, source, and rollback gates.

## Relationship to TVHStream

This GPLv3 project descends from
[`Preclikos/tvhstream`](https://github.com/Preclikos/tvhstream) and preserves its
Git history, copyright, and license. TVHeadend Player is now maintained as a
standalone product repository rather than as a GitHub fork so that it can have
its own identity, roadmap, and release process.

Generic fixes can still be prepared for the predecessor project without product
or appliance assumptions. TVHeadend Player product UX, release policy, and
optional appliance integration remain specific to this repository. The
predecessor acknowledges ideas and code from
[`TVHClient`](https://github.com/rsiebert/TVHClient).

See [`NOTICE.md`](NOTICE.md) for complete predecessor, artwork, dependency, and
native-library attribution.

## License

The combined work is licensed under the GNU General Public License v3.0. See
[`LICENSE`](LICENSE). Distributed binaries must be accompanied by corresponding
source and all applicable third-party license and notice material.
