# Media3 upgrade assessment

Date: 2026-07-28
Status: dated decision record for the named upgrade and its remaining device
gate; not general playback authority.

## Decision

Upgrade the source and packaged native extension from Media3 `1.9.2` to stable
`1.10.1`. Do not adopt `1.11.0-rc01` for production. Automated compatibility,
native integrity, and reproducibility gates pass; production deployment remains
blocked on the designated physical-TV playback matrix.

The upgrade preserves the accepted custom HTSP extractor, data sources,
renderer policy, and FFmpeg configuration. It changes only the Media3 release
surface and the explicit opt-ins newly required by `1.10.1`.

## Current evidence

- Google Maven metadata checked on 2026-07-28 lists `1.10.1` as the newest stable
  release and `1.11.0-rc01` as the newest pre-release.
- The official `1.10.1` tag resolves to commit
  `5fb306449733dd71595700c1227ad6087578c559`.
- `1.9.3` fixes compressed-offload gapless audio and `1.9.4` fixes an effects GL
  buffer transition. Neither provides a reason to rebuild this app's native
  playback stack for an intermediate patch.
- `1.10.1` includes potentially relevant fixes for decoder-error recovery and
  video codec reuse at frame-rate changes. Those changes are also reasons to run
  the full decoder and motion matrix rather than assuming a low-risk update.
- The tracked dependency graph now resolves every `androidx.media3` module to
  `1.10.1`; no `1.9.2` Java artifact remains.
- The repository FFmpeg patch applies to the `1.10.1` tag's `build_ffmpeg.sh`
  and `CMakeLists.txt`. Two clean four-ABI builds produced byte-identical AARs
  and corresponding-source archives.

`./tools/check-native-libs --release` and `./tools/verify` pass with the rebuilt
artifact. These automated checks do not prove decoder selection, audio output,
video cadence, deinterlacing, SurfaceView behavior, or runtime stability.

## Coupled change surface

An upgrade must update and review these together:

- `gradle/libs.versions.toml` for all Media3 Java artifacts.
- `tools/build-media3-ffmpeg` for the exact tag commit and source archive name.
- `app/libs/lib-decoder-ffmpeg-release.aar`, rebuilt from that same Media3
  revision with the existing audited FFmpeg inputs unless a separate FFmpeg
  change is justified.
- `app/libs/native-dependencies.json` for revision, hash, ABI, toolchain,
  license, and release evidence.
- `app/libs/README.md` for pinned-source and audited-output documentation.
- `tools/prepare-release` for the corresponding-source archive name.

The source compatibility surface includes `PlayerSession`, `LegacyRenderer`,
the HTSP `DataSource` implementations, `TvheadendExtractorsFactory`, the custom
extractor, and every stream reader using Media3 extractor or `Format` APIs. Much
of this surface is annotated or treated as unstable API, so a successful version
resolution alone is insufficient.

## Candidate execution gate

1. Start from an otherwise stable worktree and change only Media3/native
   compatibility files.
2. Write or identify regressions for command ordering, extractor timestamps and
   formats, live tuning, recording seeks, recovery, and teardown before adapting
   source APIs.
3. Rebuild the four-ABI FFmpeg AAR and corresponding-source archive from the
   exact `1.10.1` commit. Update all audited hashes and run
   `./tools/check-native-libs --release`.
4. Run `./tools/verify` and inspect dependency resolution to confirm that no
   `1.9.2` Media3 artifact remains.
5. On the designated test TV, validate progressive and interlaced live TV,
   MP1/MP2/MP3 and representative platform-decoded audio, subtitles, channel
   changes, timeshift, recording playback and accumulated seeks, recovery,
   Stop/Back/warm return, standby/wake, and repeated cold starts.
6. Require direct human comparison for interlaced motion and deinterlacing.
   Decoder names, rendered-frame counters, and screenshots are diagnostic only.

The native rebuild, dependency-resolution, and repository gates in steps 3-4
passed on 2026-07-28. Steps 5-6 remain mandatory before production deployment
or a claim that playback quality is unchanged.

## Sources

- Google Maven metadata:
  https://dl.google.com/dl/android/maven2/androidx/media3/media3-exoplayer/maven-metadata.xml
- Media3 `1.10.1` release:
  https://github.com/androidx/media/releases/tag/1.10.1
- Media3 `1.9.3` release:
  https://github.com/androidx/media/releases/tag/1.9.3
- Media3 `1.9.4` release:
  https://github.com/androidx/media/releases/tag/1.9.4
