# Product identity specification

Status: approved and implemented beginning 2026-07-24

## Decision record

| Decision | Result |
|---|---|
| Product boundary | Independent public Android TV live-TV client for TVHeadend, with appliance behavior as an optional profile/integration layer |
| Public name | **TVHeadend Player for TV** |
| Launcher label | **TVHeadend Player** |
| Descriptor | **Live TV client for TVHeadend servers** |
| Repository | `bernhardberger/tvheadend-player` |
| Distribution | GitHub-first, while keeping a later Google Play path possible |
| Backends | TVHeadend/HTSP only; another backend requires an explicit identity review |
| Application continuity | Clean break before stable signing; credentials are provisioned or entered again without a migration bridge |
| Mobile support | Out of scope; a future touch client should share core code but use a separate UI |

The name describes compatibility and does not imply endorsement by or affiliation
with the TVHeadend project. Public copy should identify this as an independent
client where that distinction matters.

## Stable identifiers

| Surface | Value |
|---|---|
| Application ID | `at.bernhardberger.tvhplayer` |
| Kotlin/Gradle namespace | `at.bernhardberger.tvhplayer` |
| Root Gradle project | `TVHeadendPlayer` |
| Compose theme API | `TVHeadendPlayerTheme` |
| Android style | `Theme.TVHeadendPlayer` |
| HTSP client name | `TVHeadend Player / <version>` |
| Appliance action | `at.bernhardberger.tvhplayer.action.APPLIANCE_ENTRY` |
| Main preferences | `tvhplayer_settings` |
| Secure preferences | `tvhplayer_secure` |
| Appliance preferences | `tvhplayer_appliance` |
| Keystore alias | `tvhplayer_secure_aes_key` |
| Local device config | `.tvhplayer-device.json` |
| Local credential file | `.tvhplayer-credentials.json` |
| Device environment prefix | `TVHPLAYER_` |

No compatibility code preserves the former application ID, DataStore files, or
Keystore alias. The previous package may remain installed as a rollback client
during validation, but it is a separate Android application.

## Visual identity

The mark is a diamond aperture on a cyan field, layered outward from the play
symbol: orange play, navy core, cyan band, navy keyline. The rotated square is a
deliberate nod to the diamond at the center of the Tvheadend logo; the four
chevrons around that diamond are not reproduced, and no upstream path geometry is
reused. The colors invert the upstream roles, so the orange marks playback rather
than the source.

Because the outermost layer is navy and the cyan sits inside the mark, the mark
is self-contained: it holds on any ground rather than depending on the cyan field
behind it. It remains legible at launcher scale and reads directly as a live-TV
player without implying affiliation or endorsement.

- Source and exports live in `artwork/`.
- `tools/RenderArtwork.java` is the canonical generator for the Android launcher layers,
  density fallbacks, monochrome adaptive layer, 512x512 Play listing icon,
  320x180 TV banner, SVG/logo, and GitHub social preview.
- The monochrome layer and SVG paths are emitted from the same geometry as the
  rasters, so themed and colored artwork cannot drift apart.
- The palette and export procedure are documented in `artwork/README.md`.
- Public screenshots must not contain private servers, channels, addresses,
  credentials, or household identifiers.

## Repository and lineage

The standalone repository preserves the complete Git history. It is not created
as a GitHub fork because the product now has its own identity and release path.
The history, `LICENSE`, `NOTICE.md`, and README retain the GPLv3 predecessor and
third-party attribution.

An optional read-only `upstream` remote may point to
`Preclikos/tvhstream`. Product and appliance commits must never be pushed there.
Generic fixes can still be extracted into narrow predecessor-compatible commits.

## Release boundary

No signed binary may be published until all of these gates pass:

1. `./tools/verify` passes from a clean checkout.
2. `./tools/check-native-libs --release` has complete corresponding-source,
   revision, toolchain, license, notice, and patent evidence for every native
   AAR. The current pinned MP1/MP2/MP3-only FFmpeg extension passes this gate;
   its generated native source archive must accompany every APK.
3. Stable signing is configured outside Git and reviewed separately.
4. Progressive and interlaced playback, D-pad focus, launcher artwork, HOME,
   GUIDE, wake, reboot, and rollback behavior pass on the designated test TV.
5. Public release notes, source, notices, and privacy text match the artifact.

## Migration acceptance

- Production source, UI, tools, CI, artwork, and public metadata use the approved
  identifiers and names.
- References to TVHStream remain only where they describe predecessor lineage,
  rollback compatibility, historical audit findings, or upstream contributions.
- The clean application ID installs beside the previous package and receives
  credentials only through normal entry or the bounded debug test-device flow.
- Unit tests, lint, Android-test compilation, APK identity/ABI, 16 KB alignment,
  native integrity, and AI harness checks pass.
- Launcher icon/banner readability and remote focus are validated on the physical
  TV; automated screenshots do not substitute for human motion-quality review.
