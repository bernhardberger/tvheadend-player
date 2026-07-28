# TV design consolidation handoff

> Implementation sequence for `docs/tv-design-spec.md`.
> Evidence for every claim: `docs/tv-design-audit-2026-07-27.md`.
> Read all three before starting. Nothing here depends on prior conversation.

## How to work this plan

**Repo:** `/root/projects/tvhstream`, branch `main`. **Read `AGENTS.md` first**,
then `docs/device-targets.md` before any build or ADB work.

Before starting: `git status -sb`, `git log --oneline -10`, `./tools/device doctor`.
There are pre-existing uncommitted changes in `docs/appliance-mode-plan.md`,
`tools/device`, `tools/tests/test_device.py` — **leave them alone**.

### Non-negotiable constraints

1. **Do not touch the HTSP extractor, Media3 playback path, or decoder
   behaviour.** Progressive and interlaced playback are regression gates. This is
   UI-layer work plus pure policy in `core/`.
2. **Do not restructure the player overlay.** It was consolidated in `8612653`,
   it is tested, and `shouldUseWarmVideoSurface` encodes a route/surface
   relationship that cost a real bug to find. Slices 1 and 5 touch its colours
   only.
3. **Focusable TV UI uses `androidx.tv:tv-material`.** Mobile Material 3 only for
   primitives TV Material 1.1.0 lacks — text fields, progress indicators,
   dividers, dialogs (`ui/Theme.kt:59-61`).
4. **One small slice per commit**, buildable and independently reviewable. Never
   force-push, never amend.
5. **For behaviour changes, write the failing test first.**
6. **Run `./tools/verify` before every commit.**
7. Keep policy out of Compose where practical, for fast JVM tests in
   `app/src/test/`.

### Build

```bash
./gradlew --no-daemon testDebugUnitTest lintDebug compileDebugAndroidTestKotlin assembleDebug
./tools/verify   # required before every commit
```

**Never set `GRADLE_USER_HOME` to `/tmp` or any tmpfs path** — shared LXC host,
it will exhaust container memory. Keep `--no-daemon`. No concurrent full builds.

### Device

Target is the dining-room **G10** (`TCL` / `Smart TV Pro` / `G10` / `G10_4K_GB`),
local role `test`. **Never the bedroom G08** — handed-over production. Run
`./tools/device doctor` first, every time. Note that a raw Gradle
`connectedAndroidTest` bypasses the role check in `./tools/device`; pin the
serial so an unrestricted run cannot reach the G08.

- ADB screenshots render video as a black `SurfaceView`. Geometry and typography
  are measurable; **scrims, contrast, colour-over-video and motion are not**.
- Synthetic `input keyevent` presses do not reliably drive focus in these
  surfaces. Do not infer a focus defect from them — that already produced one
  false alarm.
- Never `uiautomator dump`, broad `dumpsys`, or unrestricted `logcat`.
- Never screenshot Settings → Connection; credential fields.
- Keep addresses, serials and credentials out of tracked files and commit
  messages. Captures contain household metadata and are not committed.

### Three traps recorded from the audit

1. **Downscaled screenshots lie.** Two "obvious" overlaps in the audit turned out
   to be adjacent elements with a real gap. Measure runs of lit pixels; do not
   judge composition from a scaled image.
2. **Summaries of the design guidance are not the guidance.** Two claims in
   audit drafts came from paraphrases and were both wrong. Quote the page.
3. **Old captures describe old code.** The `artifacts/` set is at head `25400e7`;
   41 UI files have changed since. Diff the composable before trusting any
   measurement taken from it.

### Stop and ask if

- A slice would require changing playback, HTSP or decoder code.
- A focus scale clips on the G10 at the reserved room the spec gives — report the
  measured overflow rather than guessing a new number.
- You believe a product-visible change beyond the spec is needed.
- Anything in slices 9–11 seems to need the drawer decision resolved. It does.

---

## Slice order

Slices 1–8 are independent of the drawer decision and can all proceed. The
standard push drawer was selected on 2026-07-28, so slices 9–11 are unblocked.
Stopping after any completed slice leaves the app shippable.

### Slice 1 — Colour scheme

Files: `ui/Theme.kt`.

Apply spec 1.1. `primary = #00BCFA`, `onPrimary = #00344B`, re-derive
`primaryContainer` / `secondary` and their `on*` pairs in the same tonal
relationship the blue scheme used. Mirror into the mobile scheme at
`Theme.kt:62-98` — it exists so permitted mobile primitives inherit the right
colours, and it must not drift.

No layout change. No call site changes: all 30 `primary` consumers are tints or
fills on dark grounds, and no hand-written site uses `onPrimary`.

**Device check before commit:** cyan over live video behind the player scrim, and
whether picon-sampled card accents now clash (spec 1.4). Both are stated in the
audit as unverifiable without the TV.

### Slice 2 — Tokens

Files: `ui/TvLayout.kt`.

Add the spacing scale (spec 2) and the opacity tiers (spec 1.5). Restate the
`TvOverlay*` block as derived (spec 7). `TvTrackAlpha` is **0.20**, down from
0.24 — required by both cyan and orange.

Nothing consumes the new tokens yet. Zero behaviour change.

### Slice 3 — Shared list row

Files: new `ui/components/TvListRow.kt`; `RecordingsScreen.kt`; new
instrumentation test.

Spec 6.1. **Write the failing test first** — short versus long headline,
asserting leading and trailing tops are unchanged. It fails on the current tree;
that is the point. TV Material defines the headline as one line; long titles
ellipsize and the persistent metadata pane carries the complete title.

Replace the five independent `ListItem` configurations. Measured today: a wrapped
title drops the trailing block 24px and the picon 39px, and row pitch runs
140 / 130 / 170px.

This is the same defect and the same fix already executed in
`PlayerIdentityHeader.kt` — a flat row with one declared anchor. Reuse that
shape.

### Slice 4 — `ProgressStrip`

Files: new `ui/components/ProgressStrip.kt`; `ProgrammeCard.kt`; `ChannelRow.kt`;
`ChannelCardGrid.kt`; `ChannelsScreen.kt`; `HomeHeroCarousel.kt`.

Spec 6.2, ambient column. Track plus fill, height and track alpha from tokens, no
thumb, no labels, `primary` fill.

**This is not a `PlayerTimeline` variant and must not import from `ui/player/`.**

Removes the three mobile `LinearProgressIndicator` sites, whose default stop
indicator draws a 6px mark at 100% regardless of progress — measured at x 754–759
on channel rows where real progress was 11%. Also settles 3dp-vs-4dp and
0.25-vs-0.22.

### Slice 5 — Orange seekbar

Files: `ui/player/PlayerTimeline.kt`.

Spec 1.2. The seekbar fill becomes orange `#FA7F00`. Depends on slice 1 and 2.

Orange is **reserved to this one component**. Do not use it anywhere else. The
live-edge marker stays `onSurface` white; it is not `primary` and does not move.

Contrast against the lowered track is 3.46:1 at `TvTrackAlpha` 0.24 — this is
why slice 2 lowers it to 0.20. **Device check:** orange over video behind the
bottom scrim.

### Slice 6 — Focus entry contract

Files: `RecordingsScreen.kt`; `SideRail.kt`; `SettingsSubRail.kt`;
`ChannelTagBar.kt`; instrumentation.

Spec 4.2. Behaviour change — **failing test first**.

Every focusable container declares its entry target via `focusRestorer()`, as
`HomeScreen.kt:197` already does. Keep `Tab(onFocus = …)`; it is on-pattern (spec
4.3) and is safe once entry is deterministic.

**Acceptance, the reported bug:** Up from the folder-preview pane lands on the
selected tab and **the mode does not change**. Today it lands on the geometric
nearest and silently switches the screen out of Archive.

### Slice 7 — Focus indication

Files: review the 14 `focusedScale = 1f` sites; change code only where focus is
not unmistakable on the G10.

Spec 4.1. Apply the per-container table. List rows keep `focusedScale = 1f` and
use the strong focused-container colour supplied by TV Material. Do not add a
border merely because scale is disabled: official JetStream Profile list items
use the same no-scale, inverse-surface pattern, and direct G10 review found the
additional cyan outline excessive.

**Do not raise a scale anywhere the container does not already reserve room for
the overflow.** The prior review reported focused cards clipping at a container
edge. Where the room is not there, leave the site alone and list it as blocked on
the device measurement below.

**Device check, required, per container:** confirm that focus remains obvious at
viewing distance and that any scaled container keeps its overflow visible. This
slice cannot be completed from a build alone.

### Slice 8 — One recording red

Files: `RecordingStatusIndicator.kt`; `ProgrammeCard.kt`; `RecordingsScreen.kt`;
`ui/Theme.kt`.

Spec 1.3. One recording token replaces the hardcoded
`RecordingRed = 0xFFE53935` (`RecordingStatusIndicator.kt:18`), the `error` reuse
in `ProgrammeCard`, and `primary` at `RecordingsScreen.kt:1074`. `error` is left
for failures only.

Do this after slice 5 so orange and the recording red can be judged together on
the panel.

---

## Drawer-dependent slices

The standard push drawer was selected on 2026-07-28 (spec 6.5). Every screen's
viewport is therefore dynamic. Implement the shell-owned safe-area contract in
slice 9 before changing drawer anatomy or viewport-dependent components.

### Slice 9 — Safe area ownership

Files: `AppRoot.kt`; `SideRail.kt`; `HomeScreen.kt`; `ChannelsScreen.kt`;
`RecordingsScreen.kt`; `EpgGridScreen.kt`; `ui/TvLayout.kt`.

Spec 3. The shell computes the total inset and passes it down; screens stop
importing `TvScreenPadding`. Today the leading inset is 80dp from the shell plus
24dp from each screen, with no token for the total.

Fixes the reported Home defect: `HomeScreen.kt:126` pads the container rather
than the content, so the viewport is inset and card rows cannot reach the screen
edge. It is the only lazy container in the app doing this.

### Slice 10 — Drawer anatomy

Files: `SideRail.kt`.

Spec 6.5. Add the top section carrying the product mark — the app currently ships
no top section, and the mark in `artwork/` appears nowhere in the running app.
Give the rail and the adjacent panel distinct surfaces; they resolve to the same
luminance today.

### Slice 11 — Components

Files: `ProgrammeCard.kt`; `ChannelCardGrid.kt`; `SettingsSubRail.kt`;
`ChannelTagBar.kt`.

Spec 6.3 and 6.4. Adopt the TV Material card containers and the guidance width
grid; move the two hand-assembled category pickers onto a real component, which
also gives them slice 6's entry contract for free.

Last because card widths depend on the settled viewport.

---

## Explicitly out of scope

- **Home information architecture.** The owner scoped it in, but the evidence for
  redesigning it was withdrawn: Home was rebuilt in seven commits after the only
  captures were taken, and at HEAD it is a hero carousel plus `LazyRow`s of cards
  with `focusRestorer()` and a focus latch. The fresh capture update below does
  not justify broader Home work. Slice 9 fixes its padding defect; that is all.
- **EPG grid internals.**
- The player overlay's structure, timeline behaviour, and surface ownership.
- Scrim opacity tuning over video — unmeasurable from ADB captures.

## G10 capture update — 2026-07-28

A fresh 1920x1080 capture set was taken from the exact-identity G10 after
`./tools/verify` passed and the current `0.1.3` debug APK was installed through
`./tools/device`. The captures contain household programme metadata, remain only
under `/tmp`, and must not be committed.

What the captures establish:

- Home is now a coherent hero-plus-card dashboard, not the stale full-width row
  layout withdrawn in W2. The focused card in the captured Recently played row
  had reserved overflow and did not clip. There is no current evidence for a
  broader Home redesign beyond slice 9's code-confirmed safe-area correction.
- The collapsed rail and browse content have a visible boundary in the capture.
  The expanded drawer is unmistakably modal: it overlays a fixed viewport and
  scrims the content. It still has no top-section product mark.
- The captured Channels and Recordings rows use a strong focused-container colour
  with no row scale. A later direct G10 check rejected the proposed inner border
  as redundant and excessive; the captures do not justify adding one.
- The Recordings capture reproduces the wrapped-title anchor drift: leading
  artwork, headline and trailing date do not retain one shared top anchor.
- Ambient progress strips still show the mobile indicator's terminal stop mark,
  independently confirming slice 4's target.

What the captures do **not** establish:

- Rail/panel separation at normal viewing distance. That still needs a person in
  front of the TV; a screenshot is not the viewing-distance check named by D6.
- Cyan or orange over video, or picon clashes. The captured build still uses the
  pre-slice palette, and ADB renders the `SurfaceView` black in any case. Run the
  physical colour checks after slices 1 and 5 are implemented.
- A blanket clipping pass for every container in slice 7. Only the Home card row
  had scale plus reserved overflow in the captured states; rows intentionally had
  scale disabled. Measure each newly scaled container on-device as it is changed.

The production G08 remained connected on two ADB transports during this run but
was not targeted or modified. No instrumentation command was run.

## Slice 1 validation — 2026-07-28

The verified debug APK with the cyan colour scheme was installed on the
exact-identity G10 test target. Direct owner review on the physical panel passed
both required checks: cyan remained clearly readable over live video, and
picon/card accents did not compete with the focused state. The production G08
was not targeted or modified.

## Slice 3 validation — 2026-07-28

The final shared recording row uses the TV Material dense two-line metric: a
56dp container with one-line ellipsized `titleMedium`, one-line `bodySmall`
support, `labelLarge` trailing metadata, and independently vertically centred
leading, text and trailing content. Folder icons are 32dp. Direct owner review
on the physical G10 passed row density, hierarchy, focus contrast and vertical
alignment. The production G08 was not targeted or modified.

## Slice 4 validation — 2026-07-28

All five ambient progress sites now use one `ProgressStrip`. Direct G10 review
confirmed clean endpoints without the former terminal marker and passed the 4dp
weight. Focused channel rows retain cyan fill and use a stronger gray track so
both portions remain legible on the pale focused container. Whether to restore
Material 3's standard gap and stop indicator remains a reversible product choice
inside this single component. The production G08 was not targeted or modified.

## Slice 5 validation — 2026-07-28

The player timeline now reserves product orange `#FA7F00` for playback position.
Direct G10 review confirmed that the orange seekbar remains clearly readable over
live video behind the bottom scrim and stays distinct from the white live-edge
marker and focused thumb. The production G08 was not targeted or modified.

## Slice 7 validation — 2026-07-28

An initial 2dp cyan border implementation passed local verification and did not
clip on Channels rows, but direct G10 review found it visually redundant and
excessive beside TV Material's strong focused-container colour. The implementation
was removed. Official JetStream Profile list items independently use
`focusedScale = 1f` with `inverseSurface` focus colour and no border. The accepted
row contract therefore retains the existing high-contrast colour transition with
no scale or outline; scaled cards remain governed by their reserved overflow.

## Slice 9 validation — 2026-07-28 — complete

The verified debug APK was installed on the exact-identity G10. Direct owner
review confirmed that the standard drawer updates destinations as focus moves,
keeps drawer focus stable while newly composed pages initialize, and restores the
current destination directly on drawer entry without flashing Home or traversing
intermediate items. Home, Channels, Guide, Recordings, and Settings retain their
closed browse width and translate right rather than reflowing narrower. Entering
Settings content collapses the global drawer to its icon rail rather than removing
the shell. Settings categories update their adjacent pane as focus moves, and
both Right and OK enter that category's first real control without returning to
the global rail. Home card rows and the Guide timeline use edge-to-edge viewports
with safe content alignment. Direct owner review accepted the final Guide
geometry: its leading edge follows the shell safe inset while the continuing
timeline reaches the trailing viewport edge like the Home card lanes. The
production G08 was not targeted or modified.

The layered Back correction is implemented, locally verified, and installed on
the exact-identity G10. Bounded synthetic remote keys plus safe-screen captures
confirmed Channels content → Channels in the global drawer → Home, Settings
content → Language category → Settings in the global drawer → Home, and live
player controls → fullscreen player → Channels. Top-level destinations are
saved/restored siblings rather than Back history; nested recording surfaces and
text-field editing consume Back first; dispatcher fallbacks remain alongside
focused remote-key handling. Direct owner validation with the physical G10
remote confirmed that three successive presses move exactly one layer each from
Settings content → Language category → global Settings → Home; no press skipped
a layer or fell through to Home content.

Deferred follow-up: the owner reported that the Home feature carousel can trap
D-pad focus and plans to rework Home separately. Treat that as a Home redesign
concern rather than reopening completed slice 9; give it its own focus graph and
regression coverage.

**Check `adb devices -l` before every hardware run.** The production G08 has been
observed connected on two transports at once, left over from a deployment. That
is harmless for `./tools/device`, which pins the serial and verifies role plus all
four identity properties — but a bare `./gradlew connectedAndroidTest` installs on
**every** connected device and would target the G08. On a previous occasion only a
signing-key mismatch prevented that. Disconnect it, or pin the serial, before any
instrumentation run.

Do not use Gradle's `connectedDebugAndroidTest` against a configured household
test installation. Its cleanup uninstalls the application package and wipes
app-private credentials even when `ANDROID_SERIAL` pins the G10. Compile the
instrumentation APK in normal verification; any future physical UI-test runner
must preserve the installed application package and its data.
