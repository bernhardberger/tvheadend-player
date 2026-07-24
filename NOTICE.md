# Notices and attribution

TVHeadend Player for TV is an independently developed GPLv3 application. It is
derived from [Preclikos/tvhstream](https://github.com/Preclikos/tvhstream) and
retains that project's Git history, copyright, and GNU GPL v3 licensing. The
predecessor project in turn acknowledges ideas and code from
[TVHClient](https://github.com/rsiebert/TVHClient).

The application uses AndroidX, Jetpack Compose, Compose for TV, Material
Components, Media3, Kotlin coroutines, Coil, Koin, and Timber. Their own copyright
and license terms continue to apply; dependency coordinates and exact versions
are recorded in `gradle/libs.versions.toml`.

Bundled decoder AARs contain native code associated with Media3 decoder modules,
VideoLAN dav1d, Google cpu_features, FFmpeg, AOMediaCodec iamf-tools, and
Fraunhofer-IIS mpeghdec. Their exact source revisions, build toolchains,
corresponding source, complete notices, effective license configuration, and
possible patent obligations are not yet established. The known artifact hashes
and blockers are recorded in `app/libs/native-dependencies.json`.

This file does not cure those missing obligations. Signed binary distribution is
blocked until `./tools/check-native-libs --release` passes with reviewed evidence
for every bundled native artifact.
