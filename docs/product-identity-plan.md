# Product identity specification

Status: approved and implemented beginning 2026-07-24

## Decision record

| Decision | Result |
|---|---|
| Product boundary | Independent public Android TV live-TV client for Tvheadend, with appliance behavior as an optional profile/integration layer |
| Public name | **Tvheadend Player** |
| Launcher label | **Tvheadend Player** (Play build); **Tvheadend Player Appliance** (Appliance build, so both can be told apart when installed side by side) |
| Descriptor | **Live TV client for Tvheadend servers** |
| Repository | `bernhardberger/tvheadend-player` |
| Distribution | GitHub-first, while keeping a later Google Play path possible |
| Backends | Tvheadend/HTSP only; another backend requires an explicit identity review |
| Application continuity | Clean break before stable signing; credentials are provisioned or entered again without a migration bridge |
| Mobile support | Out of scope; a future touch client should share core code but use a separate UI |

The name describes compatibility and does not imply endorsement by or affiliation
with the Tvheadend project. Public copy should identify this as an independent
client where that distinction matters.

## Stable identifiers

| Surface | Value |
|---|---|
| Application ID (Play build) | `at.bernhardberger.tvhplayer` |
| Application ID (Appliance build) | `at.bernhardberger.tvhplayer.appliance` |
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

Two builds come from one code path through the `distribution` flavor dimension
(owner, 2026-10-11). `play` is the Google Play build: no HOME launcher filter and
no GUIDE accessibility service. `appliance` is the sideloaded household build: it
adds both, the Appliance settings section, and its own application ID, so a Play
update can never replace an Appliance install. Both builds can be installed side
by side and are configured separately; existing Appliance TVs are set up once
more.

No compatibility code preserves the former application ID, DataStore files, or
Keystore alias. The previous package may remain installed as a rollback client
during validation, but it is a separate Android application.

## Visual identity

The settled family wordmark uses Outfit 550: **Tvheadend** in off-white
`#E3E3E8` and **Player** in orange `#FA7F00` on charcoal `#0F1014`.
The paired launcher banner stacks the two words beside the original symbol;
the horizontal family uses the accepted single-line marquee proportions.
Avatars and app icons contain only the symbol. A separate contextual
**for Android TV** lockup is available; family artwork has no platform suffix.
The native splash is symbol-only; in-app startup pairs the symbol with the
single-line family wordmark and silent hybrid-synced motion. Startup layout,
loading, recovery and playback-handoff behavior are defined in
[`DESIGN.md`](DESIGN.md#startup). Asset construction and provenance live in
[`artwork/README.md`](../artwork/README.md#startup-artwork).

Public copy distinguishes **Tvheadend Player** (this app) from **Tvheadend**
(upstream) and **your Tvheadend server** (the backend). German uses
**Ihr Tvheadend-Server**. A connection failure is not proof the server is down;
request-specific failures retain their own meaning.

The mark is a cyan diamond aperture on a dark neutral field, layered outward
from the play symbol: orange play, transparent aperture, cyan diamond. The
rotated square is a deliberate nod to the diamond at the center of the Tvheadend
logo; the four chevrons around that diamond are not reproduced, and no upstream
path geometry is reused. The colors invert the upstream roles, so orange marks
playback rather than the source.

Cyan forms the complete outer silhouette without a dark keyline. The dark field
matches the app and starting splash, while the transparent opening separates orange
from cyan and reveals the underlying surface. The mark remains legible at launcher
scale and reads directly as a live-TV player without implying affiliation or
endorsement.

- Source and exports live in `artwork/`.
- `tools/RenderArtwork.java` is the canonical generator for the Android launcher
  layers, density fallbacks, monochrome adaptive layer, 512x512 Play listing icon,
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

## Google Play distribution

Accepted by the owner on 2026-10-29 (roadmap S9). Not legal advice.

The app's code is GPLv3 and partly the predecessor's copyright, so every build,
including the one on Google Play, is distributed under GPLv3 and never
relicensed. Play distribution complies on these conditions:

1. **Corresponding source for every Play release.** The AAB uploaded to Play
   comes from the same signed bundle as the GitHub release `v<version>`, which
   carries the application source archives, the exact SDK source classifiers and
   the Media3/FFmpeg corresponding source (`docs/release-process.md`). The APKs
   Play builds from the AAB are object code from that source. No Play upload
   without the matching public GitHub release.
2. **Directions next to the object code.** Both store descriptions name the
   GPLv3 and link the repository, and Settings → About shows the license and
   notices. That is the "clear directions" GPLv3 section 6(d) asks for when the
   source sits on a different server.
3. **Kept available.** A release's source stays on GitHub for as long as that
   version is offered on Play, as section 6(d) requires; published GitHub
   releases are not deleted.
4. **No added restrictions.** The listing, the in-app text and the Play
   Console settings add no terms beyond GPLv3. Users may build, modify and
   sideload their own builds; a self-built APK just carries a different
   signature and installs beside or instead of the Play one.
5. **Installation information** (GPLv3 section 6, "User Products") does not
   apply: the app is not conveyed with a device. The Appliance build on a
   household TV is installed by the owner, not sold with the TV.

Play App Signing uses this project's own release key (uploaded with PEPK), so
the Play build and the GitHub APK are the same signer and update each other.

## Release boundary

No signed binary may be published until all of these gates pass:

1. `./tools/verify` passes from a clean checkout.
2. `./tools/check-native-libs --release` matches the byte-pinned public SDK AAR,
   released source classifiers, required legal/source entries, shipped ABI
   matrix, applicable 16 KiB ELF alignment, and APK native bytes. The published
   Media3/FFmpeg corresponding-source classifier must accompany every APK.
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
  and native integrity checks pass.
- Launcher icon/banner readability and remote focus are validated on the physical
  TV; automated screenshots do not substitute for human motion-quality review.
