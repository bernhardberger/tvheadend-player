# Media3 upgrade assessment

Date: 2026-07-28

## Decision

Keep the production baseline on Media3 `1.9.2`. Use `1.10.1` as the next stable
candidate only in a dedicated playback compatibility slice after the current UI
work is complete. Do not adopt `1.11.0-rc01` for production.

This is a deferral, not a finding that `1.10.1` is incompatible. A temporary
dependency-forced compile passed, but the accepted playback path and native
extension require source-matched rebuilding and physical-TV evidence before the
catalog can change.

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
- Forcing every resolved `androidx.media3` module to `1.10.1` with an external
  Gradle init script allowed `./gradlew compileDebugKotlin --no-daemon` to pass.
  No tracked dependency or source file was changed for this check.
- The repository FFmpeg patch still matches the `1.10.1` tag's
  `build_ffmpeg.sh` and `CMakeLists.txt` context. A real candidate must still run
  the complete native build and prove reproducibility rather than relying on
  source inspection.

The compile check proves only current Kotlin/Java source compatibility. It does
not prove binary compatibility with the existing `1.9.2` FFmpeg AAR, successful
native rebuilding, decoder selection, audio output, video cadence,
deinterlacing, SurfaceView behavior, or runtime stability.

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

## Sources

- Google Maven metadata:
  https://dl.google.com/dl/android/maven2/androidx/media3/media3-exoplayer/maven-metadata.xml
- Media3 `1.10.1` release:
  https://github.com/androidx/media/releases/tag/1.10.1
- Media3 `1.9.3` release:
  https://github.com/androidx/media/releases/tag/1.9.3
- Media3 `1.9.4` release:
  https://github.com/androidx/media/releases/tag/1.9.4
