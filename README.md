# TVHeadend Player for TV

![TVHeadend Player](artwork/tvheadend-player-logo.png)

TVHeadend Player for TV is an independent, remote-first Android TV and Google TV
live-TV client for TVHeadend servers. It connects over HTSP and uses AndroidX
Media3 for playback.

This project is under active development and is not an official TVHeadend app or
a stable release.

## Features

- TVHeadend channel synchronization and live playback over HTSP
- Channel list and electronic programme guide
- D-pad, channel-up/down, and direct TVHeadend channel-number navigation
- Audio track, subtitle, aspect-ratio, and stream-profile controls
- Automatic connection/playback recovery with visible status
- System, German, and English app language selection
- Encrypted app-private password storage with Android backup disabled
- Last-played-channel restoration
- Optional household appliance entry through a narrowly scoped accessibility
  service that handles only GUIDE/TV entry and cannot inspect screen content

The interface is built specifically for a ten-foot, remote-only TV experience.
Phone and tablet support is not currently planned.

## Requirements

- Android TV 9 (API 28) or newer
- TVHeadend server reachable over HTSP
- Remote control with D-pad navigation
- Java 21 and Android SDK 36 for local builds

Direct HTSP traffic is not encrypted. Use it only on a trusted local network or
through a protected tunnel such as a VPN.

## Build and verify

```bash
./tools/verify
```

The verifier runs native-library integrity and 16 KB alignment checks, tool
policy tests, JVM tests, lint, Android-test compilation, debug assembly, and APK
identity/ABI assertions.

Device operations use an ignored local configuration and the bounded wrapper:

```bash
cp .tvhplayer-device.example.json .tvhplayer-device.json
./tools/device doctor
```

Never put TVHeadend credentials, signing keys, or private device addresses in
Git. The debug-only designated-test-device provisioning flow is documented in
`docs/test-device-credential-provisioning.md`.

## Release status

The intended distribution path is signed GitHub releases first, while retaining
a path to Google Play requirements. Release assembly is local and deliberately
requires an external stable signing key. The bundled FFmpeg extension has pinned
reproducible provenance; every APK release must include both application and
native corresponding-source archives and their checksums.

See `docs/appliance-mode-spec.md`, `docs/appliance-mode-plan.md`,
`docs/product-identity-plan.md`, and `docs/release-process.md` for behavior and
release gates.

## Lineage and contributions

This GPLv3 project descends from
[Preclikos/tvhstream](https://github.com/Preclikos/tvhstream) and preserves its
history and copyright. Generic fixes may be proposed to that predecessor;
TVHeadend Player product UX, repository identity, and household appliance
integration are developed here.

The predecessor acknowledges ideas and code from
[TVHClient](https://github.com/rsiebert/TVHClient). See `NOTICE.md` for third-party
attribution and the unresolved native-distribution boundary.

## License

The combined work is licensed under the GNU General Public License v3.0. See
`LICENSE`. Distributed binaries must be accompanied by corresponding source and
all applicable third-party license and notice material.
