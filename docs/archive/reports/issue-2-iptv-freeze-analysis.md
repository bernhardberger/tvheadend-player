# Issue #2 — m3u8 / IPTV freezes after the first frame — blind analysis

> **Status: historical unverified hypothesis record.** This is not a confirmed
> root cause or current playback instruction. Reproduce against current source
> before acting on any hypothesis.

**Symptom:** IPTV-network channels (m3u8/HLS, e.g. Acestream via PyAcexy) show one frame
then freeze. TVHeadend "Status → Subscriptions" shows video output (data IS flowing).
SAT/DVB tuner channels play fine. Reporter suspects a bad PTS.

The whole custom playback path: `HtspSubscriptionDataSource` (ring buffer of framed HTSP
messages) → `HtspSubscriptionExtractor` → per-stream `StreamReader` → ExoPlayer SampleQueue.

## Most likely root causes (prioritized)

### 1. No shared PTS normalization across streams (top suspect)
Every reader passes the **raw** HTSP `pts` (microseconds, arbitrary epoch) straight to
`trackOutput.sampleMetadata(pts, …)`:
- `PlainStreamReader.consume`: `val pts = (fields["pts"] as Number).toLong() ?: 0L`
- `H264StreamReader.consume`: same.

There is **no single per-subscription base PTS** subtracted from all streams. Problems this
creates specifically for IPTV/HLS:
- **A/V desync stall:** video first-pts (Tv) and audio first-pts (Ta) can differ a lot when
  TVHeadend doesn't share a PCR (transcoded/restreamed IPTV). ExoPlayer renders the first
  video frame, then waits to sync with audio whose timeline is far away → frozen picture.
  SAT works because audio+video share the same PCR (Tv≈Ta).
- **Discontinuity / PTS reset:** HLS segment boundaries and concatenating restreamers
  (Acestream) routinely **reset or jump PTS backward**. A backward jump makes media3 treat
  later samples as "already late" (dropped) or stalls the position → freeze after the first GOP.
- **33-bit PTS wraparound** in the source can survive into the µs values for passthrough IPTV.

**Fix direction:** capture the first `dts` (fallback `pts`) seen across *all* streams of the
subscription, subtract it from every sample timestamp (clamp negatives to 0), and detect
discontinuities (if `|pts - runningClock|` exceeds a threshold, rebase the offset). This must
be a **shared** normalizer object passed to every StreamReader, not per-reader. This is the
standard fix in HTSP↔ExoPlayer ports.

### 2. Race between subscriptionStart and muxpkt in the data source
`HtspSubscriptionDataSource.startPumpIfNeeded()` launches **two independent collectors** that
write to the **same ring buffer**:
- `controlEvents.collect { … "subscriptionStart" → writeFramedMessage }`
- `muxEvents.collect { … → writeFramedMessage }`

There is no ordering guarantee between them. If a `muxpkt` is written before
`subscriptionStart`, the extractor's `handleMuxpkt` finds no StreamReader yet
(`mStreamReaders.get(idx) ?: return`) and **silently drops** the packet. For IPTV with sparse
or mislabeled keyframes, dropping the initial packets can mean "one frame then nothing until
the next (far/мislabeled) keyframe."

**Fix direction:** serialize both onto a single ordered channel/flow, or buffer muxpkts until
`subscriptionStart` has been emitted.

### 3. Keyframe flagging may be wrong for IPTV (`H264StreamReader` / `PlainStreamReader`)
Keyframe = `frametype == 73 ('I')` or `-1` (unknown). If a TVHeadend transcode/passthrough
profile labels IPTV frames differently (or marks none as 73), then after the first GOP the
renderer can't find a sync sample to recover from any glitch → freeze. (First frame can still
show if the very first packet happened to be flagged.) Worth logging actual `frametype` values
for an IPTV channel vs a SAT channel.

### 4. ProgressiveMediaSource for a live stream
The source is wired through `ProgressiveMediaSource` with `durationUs = C.TIME_UNSET` and a
"seekable" SeekMap returning (0,0). That's a VOD source type used for a live feed. Combined
with non-zero/large start PTS it can confuse buffering/position logic. A `LiveConfiguration` or
treating it as live could help, but (1) is the more probable cause.

## Recommended diagnostic steps (need the IPTV source to reproduce)
1. Add temporary logging in `H264StreamReader.consume` and the AAC/AC3 readers:
   first pts/dts per stream index, then per-packet `pts`, `dts`, `frametype`, delta vs previous.
   Play the IPTV channel and watch logcat for: backward jumps, big gaps, video↔audio offset,
   unexpected `frametype` values.
2. Compare the same log for a SAT channel (known good) to see what differs.
3. Try a different TVHeadend **stream profile** for the IPTV channel (pass vs transcode) — the
   `profile` is already sent in `subscribe` (`HtspSubscriptionDataSource.open`).

## Proposed first concrete attempt
Implement a shared `PtsNormalizer` (per subscription/extractor instance) injected into every
StreamReader; normalize all sample timestamps to a zero base with discontinuity rebasing, and
fix the subscriptionStart/muxpkt ordering. Ship behind logging so it can be validated on the
reporter's Acestream/m3u8 setup before release.
