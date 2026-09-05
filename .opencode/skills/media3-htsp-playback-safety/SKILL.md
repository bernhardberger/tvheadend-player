---
name: media3-htsp-playback-safety
description: Use for Media3, ExoPlayer, PlayerSession, PlayerView, HTSP data sources or extractors, stream readers, codecs, decoder selection, native decoder AARs, timeshift, or playback dependency upgrades.
---

# Media3 And HTSP Playback Safety

The accepted custom HTSP-to-Media3 path is a regression boundary. Read the
relevant current playback decisions selected through `docs/README.md`. Read the
external SDK's `sdk/decoder-ffmpeg-binary/native-dependencies.json` only when the
active task or package lists that repository as an allowed source. Read a dated
audit or upgrade assessment only when the task names that specific finding or
upgrade; revalidate it against current source.

## Scope the change before editing

Identify which layers are affected:

- HTSP subscription, file reads, framing, timestamps, or backpressure.
- Custom `DataSource`, extractor, stream reader, or emitted `Format` data.
- Media source, player command serialization, surface ownership, recovery, or
  teardown.
- Renderer choice, codec reuse, decoder behavior, or native extension loading.
- Media3 coordinates, native AAR revision, corresponding source, licenses, or
  release packaging.

Keep a generic transport or lifecycle fix separate from product/appliance UI.
Do not combine a dependency bump with speculative extractor, renderer, decoder,
surface, or tuning changes.

Consult `kotlin-coroutines-structured-concurrency` or
`kotlin-flow-state-event-modeling` when an ownership, ordering, or delivery
question needs that guidance; do not load both mechanically. Media3 `DataSource`
methods are synchronous framework boundaries, while app-scoped HTSP/player owners have explicit
close/restart lifecycles. Keep any blocking bridge narrow and review those owners
against the audited exceptions rather than mechanically rewriting them.

## Preserve the baseline

- Do not replace the custom HTSP extractor or accepted Media3 playback path
  without an explicit architectural decision and comparative device evidence.
- Serialize player commands and teardown. An older tune, retry, stop, or release
  must not resume after a newer command.
- Treat stream metadata as evidence. Do not guess scan type, deinterlacing,
  format, or tuner state from dimensions, decoder names, or UI labels.
- Preserve the live/recording distinction, active data-source ownership,
  timeshift semantics, and warm-surface behavior described by the specification.
- Keep diagnostics bounded and free of server addresses, paths, credentials,
  subscription identifiers, raw errors, and logs.

## Dependency and native coupling

A Media3 upgrade is one compatibility project, not a one-line version edit.
Before proposing it, inventory every Media3 coordinate, unstable API use, custom
extractor/reader compile surface, bundled extension, build revision, manifest
entry, source archive name, and release script. Keep all Media3 Java artifacts
and native decoder extensions on the same release source revision.

Rebuild the native AAR and corresponding source through the repository build
workflow. Update audited hashes, revision/toolchain/license evidence, and release
packaging together. Never weaken `./tools/check-native-libs` to accept an
unmatched or unexplained artifact.

## Verification gate

Use focused regressions for changed transport, policy, command ordering, or
parsing behavior. Run affected tests and the repository's required final gate;
run `./tools/check-native-libs` for dependency/native artifact changes or when
required by that gate. Do not repeat unchanged checks or add tests mirroring
prose/config edits. Existing admitted verification requirements remain binding.

Select device checks according to affected behavior. Broad Media3/decoder/native
changes warrant the designated test TV matrix; a narrow internal change does not
automatically require every unrelated physical check. Relevant cases include:
progressive and interlaced live TV, representative audio/subtitle formats,
channel changes, recording playback and seeks, timeshift where enabled,
recovery, Back/warm return, Stop teardown, standby/wake, and repeated cold-start
decoder initialization. Human observation is required for motion quality and
deinterlacing. Counters, screenshots, or successful compilation cannot establish
that pass.
