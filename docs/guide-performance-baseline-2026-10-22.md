# Guide performance baseline, 2026-10-22

Status: Dated measurement record (E7 baseline) of the Guide on `main` d3277266,
before the U5 rework. Re-measure on the reworked Guide rather than reusing these
numbers as current.

These are E7 baseline numbers for the current Guide, taken before the U5
rework. They are measurements only, with no budgets or pass marks.

## Setup

- Build: main `d3277266`, optimized `profileServer` APK
  (`-Ptvhplayer.profileMinify=true`), sha256 prefix `496f5b35bbbf`,
  baseline profile installed, `speed-profile` compiled.
- Device: G10 (TCL / Smart TV Pro / G10_4K_GB), Android 12, no live TV
  playing, owner's real server (about 140 channels).
- Each journey starts 45 s after a cold launch, so that the SDK's EPG warm-up
  (about 35 s of redraws after launch) has finished.
- Route into the Guide: Left opens the drawer, Down selects Guide (the Guide
  appears behind the open drawer), Right enters the tag row, and Down enters
  the grid on the current programme of row 1. A screenshot checked the start
  position before every journey. A first batch that started in the drawer
  instead of the grid was discarded.
- Frame numbers come from `dumpsys gfxinfo` histogram deltas taken before and
  after each run (no `gfxinfo reset`, which aborted HWUI on this TV). These
  times run from vsync to completion, so they include buffer queueing, not
  only app work.
- Attribution comes from one Perfetto `causal` trace per journey, recorded
  with `tools/profiling/capture` and queried with trace_processor v58. The
  traces are in `/root/projects/.worktrees/guide-perf/build/guide-traces/`.

## Frame times (gfxinfo, ms, three runs each)

| Journey | Keys | Frames per run | p50 | p90 | p99 | GPU p50 |
|---|---|---|---|---|---|---|
| Grid up/down | 8 Down, 8 Up, 0.4 s apart | 443–459 | 16 / 24 / 24 | 34 / 30 / 28 | 93 / 85 / 42 | 8 |
| Grid left/right | 6 Right, 6 Left, 0.4 s apart | 335–352 | 22 / 24 / 15 | 27 / 29 / 25 | 65 / 53 / 53 | 8 |
| Drawer open/close from the tag row | Left, Right ×4, 0.8 s apart | 247–260 | 38 / 40 / 40 | 57 / 61 / 57 | 81 / 89 / 73 | 9 |
| First Guide open from the drawer | Left, Down, Center | 34–35 | 57–61 | 93–109 | 300–400 (max) | 22–24 |
| Idle in the grid, 6 s | none | 2 | – | – | – | – |

For drawer open/close, 70–75 % of frames took over 33 ms.

## Where the time goes (Perfetto)

**Grid up/down and left/right: the app side is cheap.** Main-thread
`Choreographer#doFrame` median is 2–3 ms (p90 4–12 ms, max 27–30 ms). On the
RenderThread, the Guide's offscreen edge-fade layer
(`drawLayer [graphicsLayer] 1760 x 776`, from `GuideViewport.kt`) costs about
1.3 ms a frame, and `flush layers` about 1.5 ms. During up/down the
RenderThread mostly waits in `dequeueBuffer` (p50 3.2 ms, p90 10.6 ms), so
frame times there are set by buffer availability and composition rather than
by app code.

**Drawer open/close: the whole visible grid recomposes on every animation
frame.** Main-thread frames are p50 21.7 ms and p90 29 ms, and
`Recomposer:recompose` alone is p50 13.8 ms a frame. The programme cells'
recording lookup (`P48:guideRecordingLookup`) ran 6,435 times in 201 frames,
about 31 per frame, which is one call per visible cell. The likely cause:
`EpgGridScreen.kt` passes `visibleRowWidthPx = LocalBrowseVisibleWidthPx.current…`
to every `TimelineChannelRow` during composition. `BrowseViewport`
(`SideRail.kt`) provides a new value on every measure while the drawer
animates, so each frame invalidates every row. GPU stays near 9 ms, so this
journey is CPU-bound. An A/B build without that read has not been measured.

**First Guide open: one long frame of 233–250 ms** (two traces). About
190–200 ms of it is `AndroidOwner:measureAndLayout`, where the lazy grid
composes its first rows:

- 16 subcompositions, about 95 ms;
- 84 `TextAnnotatedStringNode:measure` calls, about 45–49 ms, including
  13–15 ms of `TextLayout:initLayout`;
- `applyChanges`, about 16 ms.

The frame before it (Guide header and tags) is 56–89 ms, mostly
recomposition.

## Side observation

After three runs of 8 Down and 8 Up, grid focus had drifted from the current
programme (19:00) to one starting at 17:56. The opposite moves are not
symmetric. This was not investigated further.

## Follow-up, 2026-10-23: drawer A/B on the G10

Same setup and journeys, three gfxinfo runs per build, on `feat/u5-guide`
builds from main `b2d3dcb2`. The drawer journey is Left and Right four times,
0.8 s apart.

| Build | Drawer p50 | Drawer p90 | Drawer frames >33 ms | Up/Down p50 |
|---|---|---|---|---|
| `d3277266` (this baseline's APK) | 42–46 ms | 61 ms | 78–87% | 42–48 ms |
| A: `b2d3dcb2` | 42–46 ms | 61–65 ms | 83–90% | 48 ms |
| B: no cell culling | 42–44 ms | 57 ms | 62–70% | 48 ms |
| C: B plus a layer on the browse content | 42 ms | 57 ms | 64–65% | 48 ms |
| D: A plus that layer | 40–44 ms | 57–61 ms | 81–88% | 40–46 ms |
| E: culling against a width rounded up to 64 dp | 40–44 ms | 57 ms | 61–73% | 42–48 ms |

The baseline APK measures like A, so the commits between them did not slow the
Guide. This round's method does not reproduce the 16–24 ms grid figures above;
the difference is unexplained.

Perfetto traces of A, B, E and the Settings screen show why no build moves the
p50:

- A spends p50 9.5 ms a frame in `Recomposer:recompose`; B and E bring that
  to under 1 ms. Their main thread then waits in `draw` instead, because
  RenderThread waits p50 12–13 ms in `dequeueBuffer`.
- In every trace, Settings included, the Realtek composer's
  `gdma commit thread` takes p50 20 ms (p90 21 ms) per frame, more than one
  16.7 ms vsync. SurfaceFlinger composes the app layer on the GPU each frame.
  On this TV the display path, not the app, sets the frame rate of any
  animation.

The earlier "GPU near 9 ms, so CPU-bound" reading above is therefore wrong for
the frame rate. E is kept: it removes the per-frame recomposition and the
recording lookups (31 per frame) while keeping the culling. Traces are in
`/root/projects/.worktrees/u5-guide/build/ab/` (ignored build output).

## Follow-up, 2026-10-24: U5 after-numbers on the G10

Same method (optimized `profileServer` with `-Ptvhplayer.profileMinify=true`,
baseline profile and `speed-profile`, gfxinfo histogram deltas, 3 runs unless
noted), all builds measured back to back in one session between 06:45 and
08:05. The new Guide is entered with Right into the grid; its drawer journey
is Back and Right four times, the old one Left and Right from the tag row.
"Held" is 20 Down presses 50 ms apart. Values are frame time p50 / p90 and
the share of frames over 33 ms, with GPU p50.

| Build | Drawer | Up/Down | Held Down | GPU p50 |
|---|---|---|---|---|
| Old Guide (main b2d3dcb2) | 29–32 / 97–150 ms, 30–46% | 24 / 32–34 ms, 8–13% | 26–30 / 69–73 ms, 27–39% | 7–8 ms |
| U5, headline on (default) | 53 / 69 ms, 87–96% | 48–53 / 65–69 ms, 86–92% | 48–53 / 69–77 ms, 85–98% | 19–23 ms |
| U5, headline off | 28–30 / 36–42 ms, 23–34% | 16–24 / 27–34 ms, 4–12% | 27–29 / 48–53 ms, 25–33% | 8–9 ms |
| U5, headline text without its picture | 22–25 / 32–34 ms, 9–10% | 14–16 / 26–28 ms, 4–5% | 17–26 / 38–44 ms, 12–17% | 7–8 ms |
| U5, full-screen tint, no picture (2 runs) | 34 / 44 ms, 55–57% | 28 / 38 ms, 16–18% | – | 11–14 ms |
| U5, background in an offscreen layer (2 runs) | 36 / 48 ms, 65–68% | 34 / 57 ms, 51–53% | – | 14–16 ms |
| Channels list, U5 build (Up/Down only) | – | 26–27 / 36–38 ms, 15–25% | – | 9 ms |

- The old Guide's Up/Down p50 of 24 ms matches the original 16–24 ms
  figures and not the 42–48 ms of 2026-10-23. The same APK measured twice as
  fast today, so the display-path cap seen on 2026-10-23 depends on the TV's
  state. Compare builds only within one session.
- The Guide's headline background is the cost. It stacks a full-screen tint,
  the picture and its cinematic scrim (`ChannelsImmersiveBackground` corner
  mode, `DetailsHero` with `unditheredGradient`) under the grid. On this GPU
  each extra full-screen pass costs about 4–6 ms; the three together add
  about 14 ms of GPU work per frame and halve the frame rate of every Guide
  motion. The Channels list keeps one pass because it drops the tint once its
  opaque picture shows.
- Without the picture, the U5 Guide is faster than the old one, and its drawer
  tail is far shorter (p99 44–48 ms against 250–300 ms), from the 64 dp cull
  step.
- Perfetto (drawer, one run each): main-thread `draw` p50 12.5 ms with the
  picture against 2.7 ms without, so the main thread waits on rendering.
  Holding Down: `Recomposer:recompose` p50 1.5 ms / p90 13 ms,
  `measureAndLayout` p90 16 ms; the settled headline does not redo work on
  every repeat.
- No crash and no `VerifyError` on the minified build (API 31) in any run.

Traces and histograms are in `/root/projects/.worktrees/u5-guide/build/ab/`
(`after-*`, `chan-u5`, `trace-u5*`; ignored build output).

## Follow-up, 2026-10-24 afternoon: baked headline scrim on the G10

Same TV, one session, optimized builds (minify, baseline profile, speed-profile),
gfxinfo histogram deltas. "Today" is the headline picture with the scrim drawn
as a gradient over it; "baked" applies the scrim to the bitmap once when it
loads (commit c1db8535); "baked, no wash" also drops the full-screen tint
behind the picture. Channels runs use Immersive (Right ×8 / Left ×8).

| Build | Guide drawer p50 / p99 | Guide Up/Down p50 / p99 | Guide GPU p50 | Channels p50 / p99 | Channels GPU p50 |
|---|---|---|---|---|---|
| Today | 53 / 81–93 ms | 48 / 89–93 ms | 20–21 ms | 48 / 89–97 ms | 21 ms |
| Baked | 44 / 65–81 ms | 44–48 / 85–93 ms | 17–18 ms | 48 / 73–77 ms | 19 ms |
| Baked, no wash | 44 / 73–85 ms | 46–48 / 85–89 ms | 18–19 ms | 48 / 73–77 ms | 19–20 ms |

- Baking removes about 3 ms of GPU work per frame and shortens the drawer and
  Channels tails. Dropping the wash as well buys nothing more, so the wash stays.
- Frames still sit near 44–48 ms with the picture shown (14–16 ms without it in
  the morning run), so the picture's size on screen is the remaining lever.

Histograms are in `/root/projects/.worktrees/u5-guide/build/ab/wd-*/`.
