# Notices and attribution

Tvheadend Player is an independently developed GPLv3 application. It is
derived from [Preclikos/tvhstream](https://github.com/Preclikos/tvhstream) and
retains that project's Git history, copyright, and GNU GPL v3 licensing. The
predecessor project in turn acknowledges ideas and code from
[TVHClient](https://github.com/rsiebert/TVHClient).

The original application mark uses a cyan rounded diamond, a dark inner core,
and an orange play triangle. Its palette recalls compatibility with
[Tvheadend](https://github.com/tvheadend/tvheadend), but it does not reuse the
Tvheadend logo or its geometry. Tvheadend Player is not affiliated with,
endorsed by, or sponsored by the Tvheadend project. Artwork generation is
documented in `artwork/README.md`.

Brand artwork and the in-app wordmark use Outfit (Copyright 2021 The Outfit
Project Authors), licensed under SIL OFL 1.1. The font source is pinned to
Google Fonts revision `8e44913e4ff26fc997e6856c1ec40ff4791c98c5`; original and
weight-550 derivation provenance are recorded in `artwork/README.md`. The full
license is retained at `artwork/fonts/OFL.txt` and bundled in the APK at
`assets/licenses/Outfit-OFL.txt`.

The application uses AndroidX, Jetpack Compose, Compose for TV, Material
Components, Media3, Kotlin coroutines, Coil, and Koin. Their own copyright and
license terms continue to apply; dependency coordinates and exact versions are
recorded in `gradle/libs.versions.toml`.

The project-local Kotlin and Compose engineering skills under `.agents/skills/`
are selected unmodified files from Chris Banes' `chrisbanes/skills` release
`2026.7.21`, licensed under Apache License 2.0. Their exact source paths and
content hashes are recorded in `skills-lock.json`; the upstream release retains
the applicable Apache License text.

The `r8-analyzer` engineering skill under `.agents/skills/r8-analyzer/`
contains unmodified guidance and references from Google LLC's `android/skills`,
revision `bac232fd02b0855df9275281a2a7a47643768719`, under Apache License 2.0.
The source pin and hash are recorded in `skills-lock.json`; a copy of the
upstream license is retained in that skill's `LICENSE.txt`.

The released `at.bernhardberger.tvheadend:sdk-media3:0.3.0` AAR contains
AndroidX Media3 1.11.0's Apache-2.0 FFmpeg extension. FFmpeg remains under
LGPL-2.1-or-later. The app uses the extension as the AC-3/E-AC-3/MP3 fallback
behind platform decoding. Exact release hashes and complete Media3/FFmpeg source
are provided by the public SDK publication and its `ffmpeg-sources` classifier.

Unused inherited AV1, IAMF, and MPEG-H decoder AARs were removed. Every binary
release must ship that byte-pinned native corresponding-source classifier beside
the APK and normal application and released SDK source classifiers.
