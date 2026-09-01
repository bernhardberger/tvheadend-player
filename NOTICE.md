# Notices and attribution

TVHeadend Player for TV is an independently developed GPLv3 application. It is
derived from [Preclikos/tvhstream](https://github.com/Preclikos/tvhstream) and
retains that project's Git history, copyright, and GNU GPL v3 licensing. The
predecessor project in turn acknowledges ideas and code from
[TVHClient](https://github.com/rsiebert/TVHClient).

The original application mark uses a four-part cyan widescreen and an orange
play symbol isolated by circular dark negative space. Its palette and broken visual
rhythm recall compatibility with
[Tvheadend](https://github.com/tvheadend/tvheadend), but it does not reuse the
Tvheadend logo or its geometry. TVHeadend Player for TV is not affiliated with,
endorsed by, or sponsored by the Tvheadend project. Artwork generation is
documented in `artwork/README.md`.

The application uses AndroidX, Jetpack Compose, Compose for TV, Material
Components, Media3, Kotlin coroutines, Coil, and Koin. Their own copyright and
license terms continue to apply; dependency coordinates and exact versions are
recorded in `gradle/libs.versions.toml`.

The project-local Kotlin and Compose engineering skills under `.agents/skills/`
are selected unmodified files from Chris Banes' `chrisbanes/skills` release
`2026.7.21`, licensed under Apache License 2.0. Their exact source paths and
content hashes are recorded in `skills-lock.json`; the upstream release retains
the applicable Apache License text.

The released `at.bernhardberger.tvheadend:sdk-media3:0.3.0` AAR contains
AndroidX Media3 1.11.0's Apache-2.0 FFmpeg extension. FFmpeg remains under
LGPL-2.1-or-later. The app uses the extension as the AC-3/E-AC-3/MP3 fallback
behind platform decoding. Exact release hashes and complete Media3/FFmpeg source
are provided by the public SDK publication and its `ffmpeg-sources` classifier.

Unused inherited AV1, IAMF, and MPEG-H decoder AARs were removed. Every binary
release must ship that byte-pinned native corresponding-source classifier beside
the APK and normal application and released SDK source classifiers.
