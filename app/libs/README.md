# Bundled Media3 decoder extensions

These AARs came from the upstream TVHStream repository. They are retained for
the accepted playback baseline, but they are not yet approved for a new signed
distribution from this fork.

`native-dependencies.json` records the immutable hashes and archive layout that
were observed during the 2026-07-23 audit. Run:

```bash
./tools/check-native-libs
```

That command verifies each AAR hash, its expected native libraries and ABIs,
and 16 KB ELF load-segment alignment. It does not turn an unidentified binary
into a reproducible or license-cleared artifact.

The stricter release check intentionally fails:

```bash
./tools/check-native-libs --release
```

It must not pass until every AAR has been rebuilt from recorded source commits,
with the exact Media3 revision, native dependency revision, NDK/toolchain,
commands, patches, checksums, and complete redistribution notices committed.

## Current inventory

| AAR | Native implementation | Status |
|---|---|---|
| `lib-decoder-av1-release.aar` | Media3 AV1 wrapper plus dav1d | Source revisions and build environment unknown |
| `lib-decoder-ffmpeg-release.aar` | Media3 FFmpeg wrapper | Source revisions, configure flags, and effective FFmpeg license mode unknown |
| `lib-decoder-iamf-release.aar` | Media3 IAMF wrapper plus iamf-tools | Source revisions and build environment unknown |
| `lib-decoder-mpegh-release.aar` | Media3 MPEG-H wrapper plus Fraunhofer mpeghdec | Source revision unknown; redistribution, source-availability, GPL compatibility, and patent obligations require review |

All four archives currently contain `armeabi-v7a`, `arm64-v8a`, `x86`, and
`x86_64`. Their AAR metadata requires compile SDK 35 or newer and their
manifests declare minimum SDK 23.

## Known upstream build references

These links describe current upstream build procedures. They do not prove which
revision or procedure produced the checked-in binaries:

- Media3 AV1: https://github.com/androidx/media/tree/release/libraries/decoder_av1
- Media3 FFmpeg: https://github.com/androidx/media/tree/release/libraries/decoder_ffmpeg
- Media3 IAMF: https://github.com/androidx/media/tree/release/libraries/decoder_iamf
- Media3 MPEG-H: https://github.com/androidx/media/tree/release/libraries/decoder_mpegh

The FFmpeg archive exposes decoders for Vorbis, Opus, FLAC, ALAC, G.711,
MP1/MP2/MP3, AMR-NB/WB, AAC, AC3, EAC3, DTS, TrueHD, H.264, and HEVC. The
checked-in binary does not disclose the complete configure invocation. The
accepted household playback path relies on the FFmpeg extension specifically as
the MP1/MP2 fallback for the TCL's failing platform decoder.

## License observations

- Media3 extension wrapper source is Apache-2.0.
- dav1d uses a BSD 2-clause license; Media3's AV1 build also uses Google's
  Apache-2.0 `cpu_features` library.
- FFmpeg licensing depends on the exact configure flags and linked components.
  Those inputs are not recoverable from the AAR inventory.
- iamf-tools uses a BSD 3-clause license plus the Alliance for Open Media patent
  license and requires its notices with binary distribution.
- Fraunhofer mpeghdec uses its project-specific FDK MPEG-H license. Binary
  redistribution requires the complete license and corresponding source; it
  grants no patent license and may introduce restrictions that need review
  against this application's GPLv3 distribution.

Do not infer that the current binaries match the latest instructions or the
versions named there. Replace them with reproducible builds or remove unneeded
extensions after format and device regression testing.
