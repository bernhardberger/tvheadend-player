# Notices and attribution

Tvheadend Player is an independently developed GPLv3 application. It is
derived from [Preclikos/tvhstream](https://github.com/Preclikos/tvhstream) and
retains that project's Git history, copyright, and GNU GPL v3 licensing. The
predecessor project in turn acknowledges ideas and code from
[TVHClient](https://github.com/rsiebert/TVHClient).

The original application mark is a cyan diamond aperture on a dark field: an
orange play triangle inside a transparent aperture, inside a rotated cyan
square. The rotated square is a nod to the diamond at the center of the
[Tvheadend](https://github.com/tvheadend/tvheadend) logo, but the four chevrons
around that diamond are not reproduced, no upstream path geometry is reused, and
the color roles are inverted. Tvheadend Player is not affiliated with,
endorsed by, or sponsored by the Tvheadend project. Artwork generation is
documented in `artwork/README.md`.

Brand artwork and the in-app wordmark use Outfit (Copyright 2021 The Outfit
Project Authors), licensed under SIL OFL 1.1. The font source is pinned to
Google Fonts revision `8e44913e4ff26fc997e6856c1ec40ff4791c98c5`; original and
weight-550 derivation provenance are recorded in `artwork/README.md`. The full
license is retained at `artwork/fonts/OFL.txt` and bundled in the APK at
`assets/licenses/Outfit-OFL.txt`.

The in-app vector icons under `app/src/main/res/drawable/ic_*.xml` (excluding
the product's `ic_launcher_monochrome.xml`) are derived
from Google's Material Symbols set (Apache License 2.0), sourced from
`google/material-design-icons` revision
`40a7a292a79d9394157e1ea24f83d52d5e17c556`. The fixed export choices
(Rounded, with the Outlined `settings_accessibility` exception; weight 400,
grade 0, optical size 24) and the project modifications
(removal of the vector-level tint, selective `autoMirrored`, resource renaming)
are recorded in `artwork/material-symbols-LICENSE.txt`, which retains the
upstream Apache License text.

The application consumes the Tvheadend SDK (`at.bernhardberger.tvheadend:sdk-*`,
[`bernhardberger/tvheadend-sdk`](https://github.com/bernhardberger/tvheadend-sdk))
and the Tvheadend HTSP client for Kotlin (`at.bernhardberger.tvheadend:htsp`,
[`bernhardberger/tvheadend-htsp-kotlin`](https://github.com/bernhardberger/tvheadend-htsp-kotlin)),
both by the same author, independently maintained, unofficial, and published
under GPL-3.0 on Maven Central. Released SDK source classifiers accompany every
binary release.

The application also uses AndroidX, Jetpack Compose, Compose for TV, Material
Components, Media3, Kotlin coroutines, Coil, and Koin. Their own copyright and
license terms continue to apply; dependency coordinates and exact versions are
recorded in `gradle/libs.versions.toml`.

The project-local Kotlin and Compose engineering skills under `.agents/skills/`
are selected unmodified files from Chris Banes' `chrisbanes/skills` release
`2026.7.21`, licensed under Apache License 2.0. The `grounded-writing` skill
under `.agents/skills/grounded-writing/` is an unmodified copy from release
`2026.10.8` of the same repository, under the same license. Their exact source
paths and content hashes are recorded in `skills-lock.json`; the upstream
release retains the applicable Apache License text.

The `r8-analyzer` engineering skill under `.agents/skills/r8-analyzer/`
contains unmodified guidance and references from Google LLC's `android/skills`,
revision `bac232fd02b0855df9275281a2a7a47643768719`, under Apache License 2.0.
The source pin and hash are recorded in `skills-lock.json`; a copy of the
upstream license is retained in that skill's `LICENSE.txt`.

The app-owned `decoder-ffmpeg` module contains AndroidX Media3 1.11.1's
Apache-2.0 FFmpeg audio extension. FFmpeg 6.0.2 remains under LGPL-2.1-or-later.
The app uses the extension as the AC-3/E-AC-3/MP3 fallback behind platform
decoding. Upstream copyright and license files are retained in
`decoder-ffmpeg/upstream/` and the complete corresponding-source archive.
Exact source revisions, build inputs and native hashes are recorded in
`decoder-ffmpeg/provenance.json`; rebuild instructions are in
`decoder-ffmpeg/README.md`.

Unused inherited AV1, IAMF, and MPEG-H decoder AARs were removed. Every binary
release must ship that byte-pinned native corresponding-source archive beside
the APK and normal application and released SDK source classifiers.
