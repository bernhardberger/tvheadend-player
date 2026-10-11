# App-owned FFmpeg audio extension

This module supplies Media3 **1.11.1** audio fallback, not an SDK binary or a
third-party AAR. The SDK's existing `EXTENSION_RENDERER_MODE_ON` discovers
`FfmpegAudioRenderer`; platform audio remains first. No renderer policy changes
are needed. The experimental video renderer is retained only as upstream source
and excluded from compilation. The only enabled FFmpeg decoders are AC3, EAC3
and MP3. All four existing ABIs are retained.

## Provenance and licenses

`provenance.json` records the exact source inputs, toolchain and shipped library
hashes. Media3 tag `1.11.1` resolves to
`8c6678b657ede1e7883fc164ef73ed483c7796c3`. Its official revision archive is
`https://codeload.github.com/androidx/media/tar.gz/8c6678b657ede1e7883fc164ef73ed483c7796c3`.
The verified archive SHA-256 is
`bbe2157458f2eb365f9eebf4a8d4f903d8da7ab1e275aa0ad680cd253811b724`.
`upstream/` preserves exact upstream module production sources and root build/
license inputs. Its Apache-2.0 license and each source copyright are unchanged.

FFmpeg sources are extracted unchanged from the previously released
`sdk-media3-0.23.0-ffmpeg-sources.tar.xz` (SHA-256
`9eeca8490f794574185986c0df7800d65ccca2980f57dc26b630a398581d7929`).
The supplied lineage reports revision `3f92512f` on the 6.0 branch; the actual
source `RELEASE` file says **6.0.2**, not 6.0. No old Media3 1.11.0 source is
reused. FFmpeg's complete source and license files are in
`ffmpeg-media3-1.11.1-sources.tar.xz`, alongside the selected exact Media3 source.
FFmpeg is built without GPL/nonfree options; its LGPL-2.1-or-later terms apply.
The combined Player distribution remains GPLv3 with predecessor attribution.

## Offline rebuild

Requirements: Linux x86-64, Python 3.11+ with xz support, bash, make and Android
NDK **26.1.10909125** (clang 17.0.2). No network access is used by these scripts.

```sh
umask 077
timeout --kill-after=5s 20m python3 decoder-ffmpeg/rebuild.py \
  > /tmp/opencode/ffmpeg-rebuild.log 2>&1
```

The source archive hash is checked before extraction. Upstream `build_ffmpeg.sh`
is used verbatim except that its scratch copy fixes `JOBS=2`. API level 28 and
`ac3 eac3 mp3` are passed to the upstream script; it builds static avcodec,
avutil and swresample, with x86 assembly disabled as upstream specifies.
The unmodified upstream JNI translation unit is compiled directly with the NDK
driver, using the same libraries, C++11 and hidden visibility as upstream CMake.
`rebuild.py` is the complete executable record of flags: notably static C++
runtime, `--no-undefined`, `-z max-page-size=16384`, and arm64 `-Bsymbolic`.
Libraries are stripped with the same NDK's `llvm-strip --strip-unneeded`.

Outputs stay in `decoder-ffmpeg/build/native/jniLibs/`; the script refuses to
overwrite an earlier build and never updates tracked libraries or hashes.
Compare each candidate with `src/main/jniLibs/` before deliberate adoption.
Ordinary Gradle builds use those byte-pinned prebuilt libraries, avoiding an NDK
dependency for app contributors. Java sources are compiled from `upstream/`.

To regenerate the compact corresponding-source archive from the two original
local inputs, run `python3 decoder-ffmpeg/import-sources.py MEDIA3.tar.gz
SDK_FFMPEG_SOURCES.tar.xz`. It verifies both full input hashes, selects only the
new Media3 production module/root inputs plus **all** FFmpeg source, and writes
a sorted archive with normalized metadata. Root upstream build inputs are
retained for attribution/context; the local module and rebuild recipe are the
actual build system and are included in Player's application source bundle.

## Gates and release boundary

`tools/check-native-libs --staged-sdk` still checks app-owned source hashes,
upstream source identity, all four native hashes, ELF alignment and APK byte
identity. Without this flag it additionally requires byte-pinned released SDK
evidence and a native-free SDK AAR. An unpublished SDK does not satisfy that
gate. Release preparation ships this native source archive separately from
the SDK source classifiers; Player's source bundle supplies the module/recipe.

Automated checks do not establish Shield audio fallback or physical-TV playback
acceptance. No signing, publishing, installation or device acceptance is implied.
