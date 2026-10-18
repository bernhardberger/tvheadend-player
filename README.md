# Tvheadend Player

![Tvheadend Player](artwork/tvheadend-player-logo.png)

Tvheadend Player is an independent, remote-first live-TV client for
Tvheadend servers, built specifically for Android TV and Google TV. It combines
live television, a widescreen programme guide, recordings, and timeshift in one
ten-foot interface. A separate Appliance build adds household-TV entry points.

The app connects directly to your Tvheadend server over HTSP and uses AndroidX
Media3 for playback. Phone and tablet support is not currently planned.

> [!IMPORTANT]
> This project is under active development and does not yet have a stable public
> release. It is not an official Tvheadend application and is not affiliated
> with or endorsed by the Tvheadend project.

## Features

- **Designed for the television:** Compose for TV, Material for TV, predictable
  D-pad navigation, visible focus, TV-safe layouts, and focus restoration
- **Tvheadend features:** live TV, channel tags, programme guide, recording
  management, recording playback, and server stream profiles over HTSP
- **Physical remote support:** channel up/down, direct channel-number entry,
  media controls, Info, and supported channel-list keys
- **Playback:** server-backed timeshift, shared live and recording controls,
  track and display options, automatic recovery, and playback diagnostics
- **Two builds:** a Play build for ordinary TVs and an Appliance build for
  household TVs that open the app from HOME and the GUIDE key

## Live TV and remote control

- Native Tvheadend channel synchronization, picons, channel numbers, and live
  playback over HTSP
- First-run connection setup with explicit connection and synchronization status
- Optional **Start playback when the app opens** setting (off by default) resumes
  the last successfully played channel after a fresh launch
- Per-device browsing scopes using Tvheadend channel tags
- `CH+` and `CH-` switching with wraparound during fullscreen playback
- Direct 1- to 3-digit Tvheadend channel-number entry
- Hardware Info, media play/pause, TV contents, TV number-entry, and supported
  remote channel-list keys
- Recent channels in the player and saved channel logos before the server is ready
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
  the programme state and Tvheadend account permissions
- Bounded, incremental EPG synchronization that preserves the last complete
  guide while reconnecting

## Recordings

- Separate **Archive**, **Schedule**, and **Problems** views
- Tvheadend recording folders preserved as a navigable hierarchy
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
- Tvheadend tuner signal, SNR, reception errors, queue delay, and server-side
  drops in diagnostics when the active adapter supplies them

Timeshift availability depends on the Tvheadend server and selected stream
profile. Recording visibility and write operations follow the permissions of the
configured Tvheadend account.

## Builds

Each release provides two builds from one commit. They share the same screens
and playback, and update independently because their application IDs differ.

| Build | Launcher name | Application ID | What it adds |
|---|---|---|---|
| **Play** | Tvheadend Player | `at.bernhardberger.tvhplayer` | The store build for ordinary TVs. It has no HOME entry point and no accessibility service. |
| **Appliance** | Tvheadend Player Appliance | `at.bernhardberger.tvhplayer.appliance` | A HOME launcher entry, an optional accessibility service and an **Appliance** section in Settings, for a TV that should start straight into the app. |

Only the Appliance build can open the app after TV startup or display wake and
act on the Android GUIDE key and one captured TCL TV key. That is done by an
accessibility service the user must enable in Android settings. The service
subscribes to no accessibility events, cannot inspect screen content, and passes
every other key through unchanged. Appliance entry and special remote-key
behavior are device-dependent; direct HOME replacement is not claimed on TCL
firmware that gives its system launcher priority. The Appliance build is not
Android lock-task or device-owner kiosk mode.

## Privacy and network security

- No Firebase, analytics, advertising, or external product account
- Tvheadend credentials remain in app-private storage
- The saved username and password are encrypted with an Android Keystore key
  (AES-GCM)
- Android backup and device transfer are disabled for application data
- Password input is kept out of saved-instance state and protected from screen
  capture while present

Direct HTSP traffic is not encrypted and does not authenticate the server. Use
it only on a trusted local network or through a protected tunnel such as a VPN.

## Requirements

- Android TV 9 / API 28 or newer
- Tvheadend server reachable over HTSP
- Remote control with D-pad navigation
- Appropriate Tvheadend permissions for EPG or recording operations

Local builds require Java 21 and Android SDK platform 37 (`compileSdk` in
[`app/build.gradle.kts`](app/build.gradle.kts)).

## Build and verify

```bash
./tools/verify
```

The verifier checks native-library integrity and 16 KB alignment, tool policy,
JVM tests, lint, Android-test compilation, debug assembly, and APK identity and
ABI requirements.

For actual offline instrumentation and captures on the existing emulator lane,
see [Android tooling](docs/android-tooling.md). Use standard Gradle and official
Android CLI with explicit `--device` for ordinary install/capture; use ADB for
missing CLI capabilities such as instrumentation. No Gradle workflow ledger is
required. Android-test compilation alone is not execution.

Physical-TV operations follow [device targets](docs/device-targets.md) and the
same CLI-first workflow. Confirm the intended role and all four live identity
fields before mutation. Existing ignored named profiles remain useful for the
specialized credential, acceptance and release tools, but are not a prerequisite
for ordinary CLI install/capture and do not select its `--device`.

Never put Tvheadend credentials, signing keys, or private device addresses in
Git. The debug-only designated-test-device provisioning flow is documented in
[`docs/test-device-credential-provisioning.md`](docs/test-device-credential-provisioning.md).

## Release integrity

Signed GitHub releases carry the Play and Appliance APKs together with the
corresponding source; a path to Google Play distribution is retained. Release
preparation and signing are deliberately separated: reproducible unsigned bundles
are prepared on an engineering host, while the stable private signing key
remains on an isolated, owner-controlled host.

The app consumes the Tvheadend SDK and supplies its own Media3 FFmpeg audio
extension in [`decoder-ffmpeg/`](decoder-ffmpeg/README.md). Playback is
platform-first, with AC-3/E-AC-3/MP3 software fallback. Native provenance pins
the upstream sources, build inputs and binary hashes; verification checks all
four ABIs, applicable 16 KiB ELF alignment, and byte identity in the APK.
Every binary release must include application source, released SDK source
classifiers, the app-owned Media3/FFmpeg corresponding-source archive, and
their checksums.

The app consumes a public SDK release from Maven Central; the version is pinned
in [`gradle/libs.versions.toml`](gradle/libs.versions.toml). `./tools/verify`
checks the resolved dependency graph and byte-pinned SDK evidence. Opt-in local SDK
substitution remains available for development; `./tools/verify --staged-sdk`
cannot establish public SDK provenance or authorize a release.

See [`docs/release-process.md`](docs/release-process.md) for the complete release,
signature, source, and rollback gates.

## Engineering harness

The repository carries the AI engineering harness used to develop it:
`.opencode/` (agents, commands, skills), `review-provider-route.sh` and
`skills-lock.json`. [`docs/ai-engineering-harness.md`](docs/ai-engineering-harness.md)
describes it; building or using the app does not depend on it.

## Relationship to TVHStream

This GPLv3 project descends from
[`Preclikos/tvhstream`](https://github.com/Preclikos/tvhstream) and preserves its
Git history, copyright, and license. Tvheadend Player is now maintained as a
standalone product repository rather than as a GitHub fork so that it can have
its own identity, roadmap, and release process.

Generic fixes can still be prepared for the predecessor project without product
or appliance assumptions. Tvheadend Player product UX, release policy, and
optional appliance integration remain specific to this repository. The
predecessor acknowledges ideas and code from
[`TVHClient`](https://github.com/rsiebert/TVHClient).

See [`NOTICE.md`](NOTICE.md) for complete predecessor, artwork, dependency, and
native-library attribution.

## License

The combined work is licensed under the GNU General Public License v3.0. See
[`LICENSE`](LICENSE). Distributed binaries must be accompanied by corresponding
source and all applicable third-party license and notice material.
