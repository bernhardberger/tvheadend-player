# Player overlay consolidation implementation handoff

> Written for an implementer with no access to the investigation that produced it. Everything
> needed is in this file; nothing depends on prior conversation. Read it end to end before
> touching code.

## Agent selection

Use the project `android-tv` agent with **gpt-5.6 Sol Medium**, assigning **one numbered slice per
session**. The plan is pre-sliced into eight bounded, independently committable units precisely so
that Medium effort is sufficient; do not attempt multiple slices in one session, and do not attempt
the plan as a single edit or commit.

Slices 1 and 2 are risk-free groundwork. Slice 8 is the only behaviour change and depends on slice 3.
Stopping after any completed slice leaves the app in a shippable state.

Use the `android-tv-device-testing` skill for all ADB, remote-key, playback and G10 validation work.

## Authority and evidence

The findings in the Context section are derived from **pixel measurements of ADB screenshots taken
on the physical G10** at 1920x1080, density 2.0 (so 1dp = 2px), across four player surfaces: live
SimpleTV, live full player, recordings controls, and the recordings seek peek — plus all four
timeshift states.

Do not relitigate or downgrade a finding because the source looks reasonable. Each one has a
measured pixel geometry attached. Source inspection determines *where and how* to implement the
remedy; it does not invalidate the measurement.

The evidence screenshots contain household programme metadata and are **deliberately not committed**.
They remain local review evidence. Do not add them to the repository, and do not paste programme
titles into commit messages, issues or generated reports.

Two claims in this document are explicitly marked as **not verifiable from ADB captures** (scrim
opacity and anything contrast- or motion-related), because ADB renders the video as a black
SurfaceView. Do not act on those without the product owner's eyes on the physical TV.

## Working rules

**Repo:** `/root/projects/tvhstream` — Android TV client for TVHeadend, Kotlin + Compose,
branch `main`. **Read `AGENTS.md` first**, then `docs/device-targets.md` before any build/ADB work.

**Before you start, run:** `git status -sb`, `git log --oneline -10`, `./tools/device doctor`.
Expect unrelated staged or unstaged work in the tree (artwork, launcher icons, `tools/`, other
`docs/` plans). **Leave all of it alone** — none of it is part of this work, and no commit from
this plan may include it. Every commit here touches only the files named in its slice.

### Non-negotiable constraints (from AGENTS.md)

1. **Do not touch the HTSP extractor, Media3 playback path, or decoder behaviour.** Progressive and
   interlaced playback are regression gates. This work is UI-layer only, plus pure policy in `core/`.
2. **Focusable TV UI uses `androidx.tv:tv-material`.** Mobile `androidx.compose.material3` is allowed
   *only* for primitives TV Material 1.1.0 lacks (text fields, progress indicators, dividers,
   dialogs) — see the comment at `ui/Theme.kt:59-61`. Never use a focusable mobile Material control
   where a TV one exists, and never recreate a component TV Material provides.
3. **One small slice per commit**, buildable and independently reviewable. Never force-push, never
   amend, never rewrite history.
4. **For behaviour changes, write the failing test first.** Slices 1 and 8 are behaviour; slices 2–7
   are presentation.
5. **Run `./tools/verify` before every commit.**
6. Keep policy logic out of Compose where practical so it can be covered by fast JVM tests in
   `app/src/test/`.

### Build commands

```bash
# per-slice gate
./gradlew --no-daemon testDebugUnitTest lintDebug compileDebugAndroidTestKotlin assembleDebug
# focused unit test
./gradlew --no-daemon testDebugUnitTest --tests '*PlaybackTimeFormatTest'
# full verification, required before each commit
./tools/verify
```

**Never set `GRADLE_USER_HOME` to `/tmp` or any tmpfs path** — this is a shared LXC host and it will
exhaust container memory. Use the default `$HOME/.gradle`. Keep `--no-daemon`. Do not run concurrent
full Gradle builds. If SSH gets sluggish during a build, stop the build rather than retrying.

### Device work

Target is the dining-room **G10** (`TCL` / `Smart TV Pro` / `G10` / `G10_4K_GB`), local role `test`.
Never use the bedroom G08 — it is handed-over production.

```bash
./tools/device doctor                                     # always first; confirm G10_4K_GB + role=test
./tools/device install-debug
./tools/device key down                                   # reveal player controls
./tools/device screenshot --confirm-safe-screen --name <descriptive-slug>
```

- **Reveal controls with `key down`, never `key center`.** With timeshift available, centre maps to
  `REVEAL_AND_TOGGLE_PAUSE` and will pause the user's live TV.
- **`connectedDebugAndroidTest` requires the physical G10.** If you only compiled the instrumentation
  tests, say **"compiled only, not run on hardware"** in the commit message. Do not claim otherwise.
- **Synthetic `input keyevent` presses do not reliably drive focus in these overlays.** Do not infer
  focus defects from them; that already produced one false alarm during investigation.
- ADB screenshots render video as a **black SurfaceView**. You may measure geometry and typography
  from them. You may **not** draw any conclusion about scrims, contrast, or motion quality.
- Never run `uiautomator dump`, broad `dumpsys`, or unrestricted `logcat`. Use `./tools/device` only.
- Keep IP addresses, ADB serials and credentials out of tracked files, commit messages and output.

### Measuring screenshots

Geometry claims in this plan are in **pixels at density 2.0 on a 1920x1080 panel, so 1dp = 2px**.
To re-measure, scan for runs of lit pixels rather than eyeballing:

```python
from PIL import Image
px = Image.open(path).convert('L').load()
# vertical extents of a column of text between x0..x1
runs, cur = [], None
for y in range(y0, y1):
    on = max(px[x, y] for x in range(x0, x1)) > 60
    if on and cur is None: cur = y
    if not on and cur is not None: runs.append((cur, y - 1)); cur = None
```

### Two traps caught during planning — do not reintroduce them

1. **The header row must be flat.** `paddingFrom(FirstBaseline)` positions a baseline relative to the
   element's *own* top. Two columns share a baseline only if they share a top. Nesting the text
   column inside a `CenterVertically` row reintroduces exactly the bug being fixed. See the
   `PlayerIdentityHeader` section for the required tree and its acceptance check.
2. **Never assert `clock.top` invariance as a header regression test.** The clock is pinned to the
   container top and is *already* invariant on the broken build — such a test passes against the code
   you are fixing. Assert on `eyebrow.top`, `picon.top`, and `eyebrow.top == clock.top` instead.

### Stop and ask the user if

- A slice would require changing playback, HTSP, or decoder code.
- Existing tests fail in a way **not** listed in the slice 7/8 test-impact tables.
- `TvOverlayHeaderFirstBaseline = 44.dp` turns out to clip the 36sp clock on the G10 — report the
  measured ascent rather than guessing a new number.
- You believe a product-visible change beyond the ones listed under "Decisions taken by the user"
  is needed. In particular: **the clock stays at `displaySmall` 36sp — demoting it was explicitly
  declined**, even though it is larger than the programme title.

### Suggested order

Slices are numbered and ordered. 1 → 8. Slices 1 and 2 are risk-free groundwork. Slice 8 is the only
behaviour change and depends on slice 3. If time runs short, stopping after any completed slice
leaves the app in a shippable state.

## Context

Four player overlay surfaces were screenshotted on the G10 (TCL C655, 1920x1080, density 2.0)
and measured pixel-by-pixel. The user's report was "positioning, line-heights, whitespace feel
off but I can't point my finger on it."

The measurements found a single root cause: **there is no shared layout contract**. Each overlay
independently reimplements the same top text block, bottom action row, and progress bar with
different numbers. Three progress bars exist with three heights (3/4/6dp), three fills, three
label placements, three label→bar gaps (3/12/19dp), and two different time formatters that
disagree on zero-padding. The two top blocks use opposite slot orders and two unrelated vertical
alignment systems.

Intended outcome: one timeline component, one identity header, one action row, one duration
formatter, and a token block in `ui/TvLayout.kt` — so the three overlays cannot drift apart again.

All measurements below are reproducible with the pixel-scan snippet in the Working rules section.
The original capture set is local-only review evidence and is not committed (household metadata).

### Empirical proof of the header defect (captured, not inferred)

A German programme title long enough to wrap (~44 characters at `headlineMedium`) occupied two lines
on the G10 and confirmed the predicted failure. Measured glyph tops, same channel, same overlay,
one-line title versus two-line title:

| element | 1-line title | 2-line title | delta |
|---|---|---|---|
| channel identity | 89 | 71 | **−18** |
| title line 1 | 138 | 120 | −18 |
| title line 2 | — | 192–245 | new |
| next-up | 199 | 252 | **+53** |
| picon | 123 | 142 | **+19** |
| clock | 79 | 79 | **0** |
| "Endet um" | 155 | 155 | **0** |

Three consequences, all visible:

1. **The relationship inverts.** With one line the clock sat 10px above the channel line; with two
   lines the channel line sits 8px above the clock. The sign flips depending on content.
2. **The accidental baseline coincidence is destroyed.** Title baseline 180 ≈ "Endet um" baseline 179
   was luck. At two lines the title baselines are 168 and 245, so "Endet um" (179) now falls in the
   24px gap *between* the two title lines and reads as colliding with them.
3. **Three elements move independently** — picon down 19px, text up 18px, clock fixed — because the
   picon centres on a text block that grew while the right column is pinned to the container top.

This is the single strongest justification for the header work, and it is why the fix must be
type-size independent rather than a tuned offset.

### The timeshift layout, now measured

Timeshift was enabled mid-planning, so the layout that had never been seen is now captured in all
four of its states. It is **not** a safe baseline to freeze — it has its own defects:

1. **The action row is broken in every state, not just the empty-centre one.** Measured button
   spans with the fullest possible row (7 controls, timeshift active):
   `channels 112–207 │ 592px void │ back 800–895, pause 912–1007, fwd 1024–1119 │ 336px void │
   info 1456–1551, options 1568–1663, stop 1712–1807`.
   The `Box(weight(1f))` structure produces large voids even when nothing is missing. This is the
   evidence that changes the slice 7 recommendation below.
2. **The pause button moves 56px horizontally.** At the live edge there is no seek-forward button,
   so the two-button cluster spans 856–1068 and pause sits at x=1015.5. Once you seek back, forward
   appears, the cluster spans 800–1119, and pause lands at x=959.5. The primary transport control is
   not anchored — it drifts depending on whether you are at the live edge.
3. **The bar silently changes what it measures.** Unfocused it shows *programme* progress —
   centred "18:56 / 45:44", 4dp bar. Focused it shows the *timeshift buffer* — "-0:30" left, "Live"
   right in primary blue, 6dp bar, 14dp white thumb at x=632–659. Same y, same width, opposite
   semantics, no transition. This is the `showingProgramme` inversion at `PlaybackSeekbar.kt:66-69`,
   and it is a fourth bar variation on top of the three already catalogued.
4. **"Zu Live" is a fifth visual language.** An outlined pill, 159x82px at x1650–1808, y698–779 —
   floating 154px above the bar in otherwise empty space, in an overlay built entirely from circular
   icon buttons. It is attached to nothing.

*(An earlier suspicion that the seekbar was unreachable by D-pad Up turned out to be an artefact of
synthetic `input keyevent` injection — the user confirms Up works normally from the real remote.
Not a defect; not in scope.)*

### The axis rescales — why the timeshift seekbar feels wrong

The user independently reported the timeshift seekbar as "weird and unintuitive". The cause is
measurable. Focused, the bar spans the **timeshift buffer**, not the programme. At `-0:30` the thumb
sat at x=632 of the 112–1807 track — 30.7% along, which solves to a buffer only ~43 seconds long.
That buffer grows toward the requested 7200s (`REQUESTED_TIMESHIFT_PERIOD_SEC`), so:

- the thumb drifts left continuously while you watch, with no input;
- a fixed 30s seek step is a huge visual jump early and a sliver later;
- the EPG boundary ticks sit on that same rescaling axis, so they move too.

Combined with the focus flip in item 3, the control you are about to manipulate is never the one you
were looking at. Slice 8 addresses this.

## Decisions taken by the user

Accepted: full consolidation; reorder the recordings header to match live; three alpha tiers;
drop the centred `pos / dur` label; ghost fill on the seek peek with the delta folded into the
label row.

**Declined: demoting the clock.** It stays `displaySmall` (36sp), larger than the `headlineMedium`
(28sp) programme title. The measured hierarchy inversion therefore remains by choice. The
alignment mechanism below is deliberately **type-size independent** so this can be revisited later
without touching layout code.

Also accepted: a behaviour fix for the timeshift seekbar as **slice 8**, anchoring the axis to the
current programme instead of the growing buffer.

The timeshift capture is now done (see above), so slice 0 is complete. The measurements changed the
slice 7 recommendation: because the action row has 592px and 336px voids even in its *fullest* state,
**one layout rule is applied to both branches** rather than freezing the centred-transport path.

## Architecture

Governing rule, which is what keeps the existing tests green:

> **Shared components never emit their own `testTag`. Every tag is passed in by the caller.**

Tags stay attached to the same call sites that emit them today, so mutual exclusivity,
focus-conditional emission and text content are all structurally unchanged even though the drawing
code moves into shared files.

Four new files:

| File | Contents |
|---|---|
| `ui/player/PlayerTimeline.kt` | `PlayerTimelineTone` enum, `PlayerTimelineBar`, `PlayerTimelineBlock` |
| `ui/player/PlayerIdentityHeader.kt` | `PlayerHeaderTags`, `PlayerIdentityHeader` |
| `ui/player/PlayerActionRow.kt` | `PlayerActionRow` (slot-based) |
| `core/PlaybackTimeFormat.kt` | `formatPlaybackDuration`, `formatPlaybackDelta` |

### `PlayerTimeline.kt`

```kotlin
enum class PlayerTimelineTone { AMBIENT, INTERACTIVE, ACTIVE, PREVIEW }

@Composable
fun PlayerTimelineBar(
    progress: Float,
    tone: PlayerTimelineTone,
    modifier: Modifier = Modifier,
    ghostProgress: Float? = null,
    boundaryFractions: List<Float> = emptyList(),
    thumbTestTag: String? = null,
    progressSemantics: Boolean = true,
)

@Composable
fun PlayerTimelineBlock(
    progress: Float,
    tone: PlayerTimelineTone,
    modifier: Modifier = Modifier,
    leadingLabel: String? = null,
    trailingLabel: String? = null,
    leadingLabelColor: Color? = null,
    trailingLabelColor: Color? = null,
    leadingLabelTestTag: String? = null,
    trailingLabelTestTag: String? = null,
    ghostProgress: Float? = null,
    boundaryFractions: List<Float> = emptyList(),
    thumbTestTag: String? = null,
    progressSemantics: Boolean = true,
)
```

`PlayerTimelineBar` contract:

- Outer `Box(Modifier.fillMaxWidth().height(TvOverlayTimelineRowHeight))`, bar `align(Center)`.
- Bar height = `if (tone == ACTIVE) TvOverlayTimelineBarFocusedHeight else TvOverlayTimelineBarHeight`.
  This is the only height branch — it collapses today's 3dp/4dp/6dp into one number.
- Track `onSurface.copy(alpha = TvOverlayTrackAlpha)`, clipped with `MaterialTheme.shapes.small`.
- Ghost fill drawn first when `ghostProgress != null`: `onSurface.copy(alpha = TvOverlayGhostFillAlpha)`.
- Fill `MaterialTheme.colorScheme.primary` at full alpha in every tone.
- Boundary ticks: reuse the technique at `PlaybackSeekbar.kt:194-209`, alpha `TvOverlayTimelineTickAlpha`.
- Thumb only when `tone == ACTIVE`, `size(TvOverlayTimelineThumbSize)`, `CircleShape`, tagged only
  when `thumbTestTag != null`.
- `progressSemantics = false` must be passed by `PlaybackSeekbar`, which already sets
  `progressBarRangeInfo` on its own focusable node. Two progress nodes would break tag uniqueness.

Deletes `ProgrammeProgressBar` (`PlaybackSeekbar.kt:233-257`) — one call site, fully absorbed.

### `PlayerIdentityHeader.kt`

```kotlin
data class PlayerHeaderTags(
    val picon: String? = null,
    val eyebrow: String? = null,
    val title: String? = null,
    val support: String? = null,
    val clock: String? = null,
    val clockSupport: String? = null,
)

@Composable
fun PlayerIdentityHeader(
    imageLoader: ImageLoader,
    piconPath: String?,
    eyebrow: String?,
    title: String,
    support: String?,
    clock: String,
    clockSupport: String?,
    modifier: Modifier = Modifier,
    tags: PlayerHeaderTags = PlayerHeaderTags(),
)
```

**Alignment mechanism — this is the core fix and it must be implemented exactly.**

The measured defect is that the outer `Row` is `Alignment.Top` while the inner left `Row` is
`CenterVertically`, so the clock (glyph top y=79) and the channel line (y=89) align with nothing.
Because the user is keeping a 36sp clock next to a 16sp channel line, plain top-alignment cannot
make them agree — their line-height leading differs.

Use baseline anchoring instead. Both columns are top-anchored, and the **first `Text` in each
column** carries:

```kotlin
Modifier.paddingFrom(alignmentLine = FirstBaseline, before = TvOverlayHeaderFirstBaseline)
```

(`androidx.compose.ui.layout.FirstBaseline`, `androidx.compose.foundation.layout.paddingFrom`.)

This places both first baselines at exactly the same distance from the container top regardless of
type size. It fixes the alignment for the current 36sp clock **and** stays correct if the clock is
demoted later — no layout change needed either way.

Structure — **the row must be flat.** `paddingFrom` places a baseline relative to the element's own
top, so the two columns only share a baseline if they share a top. Nesting the text column inside an
inner `CenterVertically` row (as the current code does) offsets its top by
`(rowHeight − textColumnHeight) / 2`, which changes with content — that is precisely today's bug, and
it would survive the `paddingFrom` change. Every child is therefore a direct, top-anchored sibling:

```
Row(modifier.fillMaxWidth().heightIn(min = TvOverlayHeaderMinHeight),
    verticalAlignment = Alignment.Top) {          // applies to ALL children
    PiconBox(Modifier.width(TvOverlayHeaderPiconWidth)
                     .height(TvOverlayHeaderPiconHeight))
    Spacer(Modifier.width(TvOverlayHeaderPiconGap))
    Column(Modifier.weight(1f).padding(end = TvOverlayHeaderColumnGap)) {
        eyebrow  // tier 2, maxLines 1, Ellipsis
                 //   Modifier.paddingFrom(FirstBaseline, before = TvOverlayHeaderFirstBaseline)
        title    // tier 1, maxLines 2, Ellipsis
        support  // tier 3, maxLines 1, Ellipsis
    }
    Column(horizontalAlignment = Alignment.End) {
        clock    // tier 2 alpha, displaySmall
                 //   Modifier.paddingFrom(FirstBaseline, before = TvOverlayHeaderFirstBaseline)
        clockSupport // tier 3
    }
}
```

No `CenterVertically` anywhere in this tree. The picon is top-anchored like everything else, which is
what stops it drifting (measured: it moved 19px down when the title wrapped). If the picon needs an
optical nudge relative to the first text baseline, add a `TvOverlayHeaderPiconTopInset` token — do
**not** reintroduce centring.

**Acceptance check for the implementing agent:** render with `support = null` (recordings without a
subtitle, text column ≈ 84dp, shorter than the 90dp picon). The text column's top and the clock
column's top must still be equal. If they are not, the tree is still nested somewhere.

`TvOverlayHeaderFirstBaseline` must be **≥ the clock's first-line ascent** or the clock clips at the
top. 36sp Roboto ascent is roughly 33sp; the starting value below is 44.dp with headroom. Verify on
the G10 before committing — clipping is visible and this is the one number that cannot be derived
from the type scale safely.

No explicit `Spacer` between the three left slots. Rhythm comes from line height alone, which
removes the uneven 26px/19px pairing caused by the ad-hoc `Spacer(4.dp)` at `OverlayControlsTv.kt:213`.

`heightIn(min=)` rather than a fixed height: a fixed height clips at `fontScale > 1.0` and would
violate the AGENTS.md requirement for stable layouts with long localized text.

### `PlayerActionRow.kt`

```kotlin
@Composable
fun PlayerActionRow(
    modifier: Modifier = Modifier,
    navigation: (@Composable RowScope.() -> Unit)? = null,
    transport: (@Composable RowScope.() -> Unit)? = null,
    utilities: (@Composable RowScope.() -> Unit)? = null,
    terminal: (@Composable RowScope.() -> Unit)? = null,
)
```

**One rule for every state** — the measurements showed the old `Box(weight(1f))` structure produces
592px and 336px voids even with all seven controls present, so there is no state worth preserving:

```
Row(modifier.fillMaxWidth().padding(horizontal = TvOverlayFocusInset),
    verticalAlignment = Alignment.CenterVertically) {
    Row(horizontalArrangement = Arrangement.spacedBy(TvOverlayActionGroupGap)) {
        navigation      // channels (absent in the recordings overlay)
        transport       // back / play-pause / forward / go-live — may be empty
        utilities       // info / options
    }
    Spacer(Modifier.weight(1f))
    terminal            // stop
}
```

- Groups are internally spaced by `TvOverlayActionGap`, separated by `TvOverlayActionGroupGap`.
- The terminal action is the only pinned element, at `TvOverlayTerminalGap` minimum from utilities.
- Must tolerate any slot being null or empty without collapsing — including `transport = null`
  (recordings without seek) and an empty transport cluster (live at the live edge).
- **This anchors the play/pause button**, which was measured drifting 56px as seek-forward
  appears and disappears.
- All `FocusRequester`s, `focusProperties`, `onFocusChanged` and the `DirectionUp`
  `onPreviewKeyEvent` stay at the call sites. The row only does layout.

Because this changes the centred-transport path, the four ordering assertions at
`PlayerOverlayCompositionTest.kt:128-130` must be re-verified — they assert
`navigation.right < transport.left < utilities.left < terminal.left`, which the new left-grouped
order still satisfies.

### `core/PlaybackTimeFormat.kt`

```kotlin
/** Under one hour "m:ss", one hour or more "h:mm:ss". Never negative. */
fun formatPlaybackDuration(ms: Long): String

/** Signed delta for seek previews, e.g. "+0:30" / "−1:15". */
fun formatPlaybackDelta(deltaMs: Long): String
```

Android-free (`String.format(Locale.US, …)`) so it lives in `core/` with the other policy code and
gets fast JVM coverage. Reproduces `formatPlaybackClock` semantics exactly.

Delete `formatPlaybackClock` (`PlaybackSeekbar.kt:259`) and update **all 12 call sites**:
`PlaybackSeekbar.kt:78,79,83,84,88,138,139,148,153,167` and `OverlayControlsTv.kt:319,320`.
Note that five of those feed accessibility `contentDescription` strings, not visible labels.

`formatHms` (`ui/common/TimeConverter.kt:16`) stays for `PlaybackStatsOverlay.kt` (developer
overlay, out of scope) but is removed from `RecordingOverlayControls.kt`.

**Out of scope:** `shouldShowProgrammeProgress` (`SeekbarPolicy.kt:90`) and `floorToMinutes`
(`TimeConverter.kt:24`) are dead but deleting the former breaks `SeekbarPolicyTest.kt:73`. Leave both.

## Tokens — append to `ui/TvLayout.kt`

Match the file's existing style: top-level `val`/`const val` with a `Tv` prefix, no object wrapper.

```kotlin
// ---- Player overlay geometry ----
val TvOverlaySidePadding = 56.dp
val TvOverlayTopPadding = 32.dp
val TvOverlayBottomPadding = 32.dp

/** Gradient run-out, not content spacing. Do not "unify" with the paddings. */
val TvOverlayHeaderGradientRunout = 72.dp
val TvOverlayFooterGradientRunout = 80.dp

val TvOverlayHeaderMinHeight = 96.dp
/** First-baseline anchor for both header columns. Must exceed the clock's ascent. */
val TvOverlayHeaderFirstBaseline = 44.dp
val TvOverlayHeaderPiconWidth = 160.dp
val TvOverlayHeaderPiconHeight = 90.dp
val TvOverlayHeaderPiconGap = 24.dp
val TvOverlayHeaderColumnGap = 48.dp

val TvOverlayTimelineBarHeight = 6.dp
val TvOverlayTimelineBarFocusedHeight = 10.dp
val TvOverlayTimelineThumbSize = 20.dp
/** Fixed band the bar is centred in; must be >= the thumb size. */
val TvOverlayTimelineRowHeight = 24.dp
val TvOverlayTimelineLabelGap = 12.dp
val TvOverlayTimelineBlockGap = 24.dp

val TvOverlayActionButtonSize = 48.dp
val TvOverlayActionGap = 8.dp
val TvOverlayActionGroupGap = 24.dp
/** Separation before the terminal (Stop) action. Must read as intentional at 10ft. */
val TvOverlayTerminalGap = 40.dp
/** Keeps a focus-scaled (1.10x) 48dp control inside the safe margin. */
val TvOverlayFocusInset = 4.dp

// ---- Player overlay tone ----
const val TvOverlayTextPrimaryAlpha = 1.00f
const val TvOverlayTextSecondaryAlpha = 0.88f
const val TvOverlayTextTertiaryAlpha = 0.72f
const val TvOverlayTrackAlpha = 0.24f
const val TvOverlayGhostFillAlpha = 0.40f
const val TvOverlayTimelineTickAlpha = 0.70f
```

## Type and alpha assignment

Because the clock demotion was declined, **no type size changes anywhere**. Only alphas, slot order
and alignment change. This makes the diff smaller and safer than a full retype.

| Slot | Live | Recording | Style (unchanged) | Alpha |
|---|---|---|---|---|
| eyebrow | channel number + name | channel name | `titleMedium` | secondary 0.88 |
| title | programme title | recording title | `headlineMedium` | primary 1.00 |
| support | "Up next at …" | episode subtitle | `labelLarge` | tertiary 0.72 |
| clock | wall clock | wall clock | `displaySmall` | secondary 0.88 |
| clockSupport | "Ends at …" | — | `titleMedium` | tertiary 0.72 |
| timeline labels | pos / dur / Live | pos / dur | `labelLarge` | tertiary 0.72 |

Today's six alpha values (1.0/0.88/0.72/1.0/0.88/0.82) collapse to three. The clock moves from
`Color.White` to 0.88 and the timeline label from 0.82 to 0.72 — both small and visible.

## Test-coupling strategy

Only one overlay UI test exists: `app/src/androidTest/.../ui/player/PlayerOverlayCompositionTest.kt`
(3 tests). `RecordingOverlayControls` has zero tags and zero coverage.

| # | Coupling | Outcome |
|---|---|---|
| 1 | `player-timeline` emitted at two mutually exclusive sites (`OverlayControlsTv.kt:266`, `:313`); `onNodeWithTag` throws on multiple matches | **Preserved and hardened.** Merge into one `Column(Modifier.testTag("player-timeline"))` guarded by `if (timeshiftState.available \|\| nowEvent != null)`, branching internally. Exactly one node can ever exist. No test change. |
| 2 | `player-programme-progress` emitted at two sites (`PlaybackSeekbar.kt:142`, `OverlayControlsTv.kt:323`); test `:194` requires 0 when focused | **Preserved by tag injection.** The shared block never emits it; `PlaybackSeekbar` passes `leadingLabelTestTag = "player-programme-progress".takeIf { showingProgramme }`. No test change. |
| 3 | Focus-conditional `player-seekbar-thumb` / label inversion | **Preserved.** `focused` state, key handling and tone computation stay inside `PlaybackSeekbar`. No test change. |
| 4 | Triple-conditioned `showingProgramme` (`:66-69`) plus caller's `shouldShowProgrammeTimeline` pre-filter | **Untouched.** No test change. |
| 5 | Ten geometric assertions `:118-130`, incl. `utilities.right + 8f < terminal.left` | **All survive.** Slot order unchanged for live; test runs with `available = true` i.e. the byte-identical centred path; `TvOverlayTerminalGap` = 40dp = 80px ≫ 8px floor. No test change. |
| 6 | Verbatim `"Up next at …"` at `:132-137` | **Preserved.** `OverlayControlsTv` still builds the string and passes it into `support`; the header formats nothing. No test change. |
| 7 | Verbatim `"29:56 / 1:00:00"` at `:185-188` | **Breaks deliberately** — this is the centred label the user asked to drop. Replace with two `assertEquals(1, onAllNodesWithText("29:56")…)` / `("1:00:00")`. Safe: test 3 has `positionMs = -4_000` so no go-live button renders and `nextEvent` is null, so neither string can collide with header text. |

Net test edit for slices 1–6: two changes in one file.

**Slices 7 and 8 go further and that is intended.** Slice 7 changes the centred-transport path, so
`:128-130` must be re-verified. Slice 8 retires couplings 2, 4 and half of 3 outright, because the
behaviour they pin is the behaviour being fixed — see the slice 8 test-impact table. Do not treat a
failing assertion in those two slices as a regression without checking it against that table first.

## Slices

One commit each. Gate after every slice:

```bash
./gradlew --no-daemon testDebugUnitTest lintDebug compileDebugAndroidTestKotlin assembleDebug
./tools/verify   # required before every commit
```

`connectedDebugAndroidTest` needs the G10 and `./tools/device doctor` first. **Do not claim
`PlayerOverlayCompositionTest` passed unless it actually ran on hardware** — say "compiled only" in
the commit message otherwise.

### Slice 0 — capture the timeshift layout — **DONE**

Captured on the G10 in all four states (unfocused, focused, live edge, timeshift active). Findings
are in the Context section and are already folded into slices 7 and 8. The captures themselves are
local-only evidence and are not committed.

Note for anyone re-capturing: reveal the controls with `./tools/device key down`, **never `center`** —
with timeshift available, `playerKeyAction` maps centre to `REVEAL_AND_TOGGLE_PAUSE`
(`PlaybackKeyPolicy.kt`) and will pause the user's live TV. Also note that synthetic
`input keyevent` presses do not reliably drive focus in this overlay; do not infer focus defects
from them.

### Slice 1 — unified duration formatter

Files: `core/PlaybackTimeFormat.kt` (new), `app/src/test/.../core/PlaybackTimeFormatTest.kt` (new),
`PlaybackSeekbar.kt`, `OverlayControlsTv.kt`.

Write the test first (AGENTS.md: failing test, then minimum code). Then delete `formatPlaybackClock`
and update all 12 call sites. Output is byte-identical, so this slice changes no pixels.

Focused run: `./gradlew --no-daemon testDebugUnitTest --tests '*PlaybackTimeFormatTest'`

### Slice 2 — tokens

Files: `ui/TvLayout.kt` only. Nothing consumes them yet.

### Slice 3 — `PlayerTimeline.kt`, one bar language

Files: `PlayerTimeline.kt` (new), `PlaybackSeekbar.kt`, `OverlayControlsTv.kt`,
`PlayerOverlayCompositionTest.kt`.

1. Create the tone enum, bar and block.
2. Rewrite `PlaybackSeekbar`'s body (`:131-227`) to delegate to `PlayerTimelineBlock`.
   **Signature unchanged.** Pass `progressSemantics = false`, `thumbTestTag = "player-seekbar-thumb"`,
   `leadingLabelTestTag = "player-programme-progress".takeIf { showingProgramme }`.
   *(This conditional tag is deliberately temporary — it preserves coupling 2 through slices 3–7 and
   is deleted in slice 8 when the focus flip is retired. Not an accidental regression.)*
3. Delete `ProgrammeProgressBar`; merge the two `player-timeline` columns per coupling 1. The
   AMBIENT branch gets `leadingLabel = formatPlaybackDuration(programmePositionMs ?: 0L)`,
   `trailingLabel = formatPlaybackDuration(programmeDurationMs ?: 0L)` — this is the centred-label drop.
4. `Spacer(Modifier.height(14.dp))` at `:334` → `TvOverlayTimelineBlockGap`.
5. Apply the coupling-7 test edit.

### Slice 4 — `PlayerIdentityHeader.kt`, live overlay

Files: `PlayerIdentityHeader.kt` (new), `OverlayControlsTv.kt`.

Replace `OverlayControlsTv.kt:180-257` with one `PlayerIdentityHeader` call passing
`PlayerHeaderTags(picon = "player-picon", eyebrow = "player-channel-identity",
title = "player-programme-title", support = "player-next-programme", clock = "player-clock",
clockSupport = "player-programme-end")`. Swap hardcoded 56/32/72 for tokens.

**Verify `TvOverlayHeaderFirstBaseline` on the G10 in this slice** — screenshot and confirm the
clock is not clipped at the top and that the clock and channel-line baselines coincide.

### Slice 5 — recordings header + first recordings coverage

Files: `RecordingOverlayControls.kt`, `app/src/androidTest/.../ui/player/RecordingOverlayCompositionTest.kt` (new).

Replace `RecordingOverlayControls.kt:125-178` with `PlayerIdentityHeader`, passing
`eyebrow = channelName`, `title = title`, `support = subtitle`. **This is the approved slot reorder** —
it ends the measured 42px title jump between live and recordings. Add tags `recording-picon`,
`recording-channel-identity`, `recording-programme-title`, `recording-subtitle`, `recording-clock`.

### Slice 6 — recordings seek peek

Files: `RecordingOverlayControls.kt`, `RecordingPlayerScreen.kt`.

1. Replace `RecordingSeekProgress` (`:333-371`) with:

```kotlin
@Composable
internal fun RecordingSeekPreview(
    targetMs: Long,
    originMs: Long?,
    durationMs: Long,
    modifier: Modifier = Modifier,
)
```

Renders `PlayerTimelineBlock(tone = PREVIEW)` with `progress` = target fraction, `ghostProgress` =
origin fraction, `leadingLabel = formatPlaybackDuration(targetMs)`, `trailingLabel` = signed delta
from `formatPlaybackDelta` when `originMs != null`, else duration or `R.string.recording_duration_unknown`.

This removes the mobile-M3 `LinearProgressIndicator`, which kills the phantom stop-indicator dot
measured at x1800-1805 and the 8px fill/track gap.

2. `RecordingPlayerScreen.kt:455` — pass `originMs = pendingSeekOriginMs`.
3. `RecordingPlayerScreen.kt:462` — change the guard to
   `if (controlsVisible && pendingSeekTargetMs != null && pendingSeekOriginMs != null)`.

   **Why this matters:** the centre `+00:30` Surface currently renders whenever a pending seek
   exists, *including while controls are visible*, whereas the peek renders only when they are
   hidden. Deleting the Surface outright would silently lose the delta readout during
   controls-visible seeking. Gating it to `controlsVisible` gives exactly one delta indicator per
   state and removes the measured 470px orphan pairing.

4. Replace remaining hardcoded 56/96/32 paddings with tokens.

### Slice 7 — `PlayerActionRow.kt`

Files: `PlayerActionRow.kt` (new), `OverlayControlsTv.kt`, `RecordingOverlayControls.kt`,
`PlayerOverlayCompositionTest.kt`.

- `OverlayControlsTv`: `navigation = { channels }`, `transport = { back / playPause / forward }`
  (any subset, gated as today by `canSeekTimeshiftBackward` / `canSeekTimeshiftForward`),
  `utilities = { info / options }`, `terminal = { stop }.takeIf { showStop }`.
- `RecordingOverlayControls`: `navigation = null`, same transport/utilities/terminal. Fixes the
  empty left side and the 336px void.
- `.size(48.dp)` → `TvOverlayActionButtonSize`; `.padding(start = 16.dp)`
  (`OverlayControlsTv.kt:501`, `RecordingOverlayControls.kt:309`) → handled by `TvOverlayTerminalGap`.
- `16.dp → TvOverlayTerminalGap` is **not** value-preserving (24dp effective → 40dp). Call it out in
  the commit message as an intentional strengthening of the Stop separation and confirm
  `utilities.right + 8f < terminal.left` still holds.
- Re-verify `:128-130` ordering assertions on hardware, since this slice now changes the
  centred-transport path too (see `PlayerActionRow` above for why).

**Validate on the G10 in all four timeshift states** — live edge (no forward button), timeshift
active (forward present), recordings, and SimpleTV. The play/pause button must not move between the
first two; that 56px drift is the specific regression this slice fixes.

### Slice 8 — programme-anchored timeshift axis (behaviour)

Files: `core/SeekbarPolicy.kt`, `PlaybackSeekbar.kt`, `PlayerTimeline.kt`, `OverlayControlsTv.kt`,
`app/src/test/.../core/SeekbarPolicyTest.kt`, `PlayerOverlayCompositionTest.kt`.

This is the only slice that changes behaviour rather than appearance. Do it last.

**Policy (JVM-testable, no Compose):** add to `core/SeekbarPolicy.kt`

```kotlin
/** Programme-anchored timeshift axis. Fractions are 0..1 across the programme. */
data class ProgrammeAxis(
    val playbackFraction: Float,
    val liveEdgeFraction: Float,
    val rewindableStartFraction: Float,
)

/**
 * Returns null when there is no usable programme, in which case the caller falls
 * back to [timeshiftSeekbarRange] and today's buffer axis.
 */
fun programmeAnchoredAxis(
    state: TimeshiftState,
    nowEpochSec: Long,
    programmeStartSec: Long?,
    programmeStopSec: Long?,
): ProgrammeAxis?
```

Derivation (all inputs already available at `OverlayControlsTv.kt:139-163`):

- `playbackEpochSec = nowEpochSec + state.positionMs / 1000`
- `span = programmeStopSec - programmeStartSec`, return null if `span <= 0`
- `playbackFraction = (playbackEpochSec - programmeStartSec) / span`
- `liveEdgeFraction = (nowEpochSec - programmeStartSec) / span`
- `rewindableStartFraction = (nowEpochSec + state.bufferStartMs / 1000 - programmeStartSec) / span`
- all three coerced into `0f..1f`

Because the programme's duration is fixed, **the axis stops rescaling**. The thumb no longer drifts,
a 30s step is always the same visual distance, and the EPG ticks become unnecessary on this axis
(the programme *is* the axis) — drop `epgBoundaryFractions` from the timeshift path.

Disposition for the stranded tick code: `timeshiftEpgBoundaryFractions` (`SeekbarPolicy.kt:53`) and
its test `SeekbarPolicyTest.kt:36 timeshiftProgrammeBoundariesMapIntoSeekableRange` lose their only
production caller. Delete both — they encode the buffer axis this slice replaces. Same treatment as
`shouldShowProgrammeTimeline` below.

**Rendering:** extend `PlayerTimelineBar` with `rewindableStartFraction: Float? = null` and
`liveEdgeFraction: Float? = null`. Draw, back to front: track → rewindable region
(`onSurface` at `TvOverlayGhostFillAlpha`, from `rewindableStartFraction` to `liveEdgeFraction`) →
played fill (`primary`, to `playbackFraction`) → live-edge marker → thumb when `tone == ACTIVE`.

**Retire the focus flip.** Delete `showingProgramme` (`PlaybackSeekbar.kt:66-69`) and its
`displayedProgress` branch. The bar shows the programme axis whether focused or not; only the thumb
and bar height remain focus-dependent. Labels become `leadingLabel = elapsed-into-programme`,
`trailingLabel = programme duration`, matching every other surface.

**Fold in the redundant go-live affordance.** With a live-edge marker on the axis, the separate
`"Live"` text label is redundant — drop it. Move the `Zu Live` outlined pill out of the timeline
block and into the transport cluster as a normal icon button, which removes the fifth visual
language and the unattached floating control.

**Test impact — this slice deliberately retires several couplings:**

| Coupling | Effect |
|---|---|
| 2 (`player-programme-progress` double emission) | Label is now unconditional, so `:194`'s `assertCount(0)` after focus is wrong. Delete that assertion. |
| 3 (thumb/label inversion) | Half retired — the thumb stays focus-conditional, the label no longer flips. `:197-201` survives. |
| 4 (`showingProgramme` triple condition) | Gone. `shouldShowProgrammeTimeline` (`TimeshiftPolicy.kt:38`) becomes dead — delete it **and** its pinning test `TimeshiftPolicyTest.kt:156 programmeTimelineIsTheRestingLivePresentation`, which encodes the old design. |
| 5 (`goLive.right <= timeline.right`, `:126-127`) | Gone — `Zu Live` moves into the action row. Delete both assertions. |
| Test 3 (`liveEdgeUsesProgrammeProgressUntilTimelineIsFocused`) | Its entire premise is the flip. Rewrite as `timelineKeepsOneAxisWhetherFocusedOrNot`: assert the leading label text is identical before and after `requestFocus()`. |

**New JVM tests** in `SeekbarPolicyTest.kt`: `axisIsNullWithoutAProgramme`,
`playbackFractionTracksPositionWithinProgramme`, `liveEdgeSitsAheadOfPlaybackWhenBehindLive`,
`rewindableRegionClampsToProgrammeStart`, `axisIsStableAsTheBufferGrows` (same position and
programme, two different `bufferStartMs` values → identical `playbackFraction`; this is the
regression test for the reported weirdness).

**Validate on the G10:** watch the thumb for ~60s without input and confirm it advances smoothly
instead of drifting left; seek back 30s twice and confirm each step moves the same distance.

## New tests

**`app/src/test/.../core/PlaybackTimeFormatTest.kt`** (slice 1, fast JVM):
`subMinutePositionUsesMinuteSecondForm` (0 → `"0:00"`, 4_000 → `"0:04"`),
`subHourPositionOmitsLeadingHour` (1_796_000 → `"29:56"`),
`hourBoundaryAddsHourComponent` (3_600_000 → `"1:00:00"`, 3_599_000 → `"59:59"`),
`negativeInputClampsToZero`, `formatterIsLocaleIndependent` (assert identical output with
`Locale.GERMANY` as default), `deltaCarriesExplicitSign` (+30_000 → `"+0:30"`, -75_000 → `"−1:15"`),
`zeroDeltaIsPositive`.

**`app/src/androidTest/.../ui/player/RecordingOverlayCompositionTest.kt`** (slice 5, first coverage
this file has ever had):
`recordingHeaderUsesSameSlotOrderAsLiveOverlay` (`channelIdentity.bottom <= title.top`,
`title.bottom <= subtitle.top`),
`recordingHeaderKeepsItsAnchorsWhenTheTitleWraps` — see the warning below,
`recordingActionsFormOneClusterWithSeparatedStop` (slice 7).

**Do not assert `clock.top` invariance.** The measured captures show the clock at y=79 with both a
one-line and a two-line title — it is already invariant *on the broken build*, because it is pinned
to the container top. That assertion passes against the code being fixed and guards nothing.

Assert instead on the elements that actually moved, and on the anchor being built:

```kotlin
// one-line vs two-line title, same composable
assertEquals(shortEyebrow.top, longEyebrow.top, 1f)   // measured 89 -> 71 today: FAILS pre-fix
assertEquals(shortPicon.top,   longPicon.top,   1f)   // measured 123 -> 142 today: FAILS pre-fix
// and the anchor itself
assertEquals(eyebrow.top, clock.top, 1f)              // measured 89 vs 79 today: FAILS pre-fix
```

Add the same three to the live-overlay equivalent in `PlayerOverlayCompositionTest.kt` (slice 4).
Without them nothing in the suite guards the header invariant this plan exists to create.

**Edits to `PlayerOverlayCompositionTest.kt`**: the coupling-7 replacement in slice 3, the
`clock.height` addition in slice 4, the `:128-130` re-verification in slice 7, and the retirements
listed in the slice 8 table.

No new UI test may assert absolute pixel positions — assert relative ordering, relative size, and
invariance under long text.

## Verification

Per slice: the gradle gate above, then `./tools/verify`.

End to end on the G10 (`./tools/device doctor` first; role must be `test` and device/product must
read `G10` / `G10_4K_GB`). Reveal controls with `key down`, **never `key center`** — with timeshift
available that toggles pause on live TV:

1. **Live, no timeshift** — confirm the clock and channel-line baselines coincide, the bar is 6dp
   with labels at both ends, and the action row has no void.
2. **Live, timeshift at the live edge** — confirm play/pause sits where it sits in state 3.
3. **Live, timeshift active** (seek back 30s) — confirm play/pause has **not** moved when the
   seek-forward button appeared, and that `Zu Live` is in the transport cluster (slice 8).
4. **Timeshift axis stability** (slice 8) — leave the focused seekbar untouched for ~60s and confirm
   the thumb advances rather than drifting left; seek back twice and confirm equal visual steps.
5. **SimpleTV** — confirm the header and action row match live. Note that
   `SimpleTvCapability.TIMESHIFT` gates timeshift off here (`SimpleTvCapabilityPolicy.kt:49`), so
   the no-timeshift path is what renders.
6. **Recordings controls** — confirm channel name now sits above the title and the title no longer
   jumps versus live.
7. **Recordings peek** — with controls hidden, `key right` then screenshot within ~1.3s (debounce is
   400+600+350ms). Confirm the ghost fill, the delta in the label row, no centre bubble, and no
   phantom dot at the right end of the bar.
8. **Long-title check** — find any channel whose current programme title wraps to two lines (~44+
   characters at `headlineMedium`); this is the known reproducer. Confirm the clock, picon and
   channel line no longer move between the one-line and two-line cases.

Reuse the pixel-scan snippet from the Working rules section to verify geometry numerically rather
than by eye; ADB screenshots render the video as black, so **no scrim, contrast or motion-quality
conclusion may be drawn from them** (AGENTS.md). Synthetic `input keyevent` presses do not reliably
drive focus in these overlays — use the real remote for focus checks.

## Out of scope

Scrim opacity tuning (`topGradient` 0.78 vs `bottomGradient` 0.92) — unmeasurable from ADB captures,
needs the user's eyes on real picture. Clock demotion (declined). `PlaybackStatsOverlay`.
The six non-player progress bars in cards, rows and the hero carousel.
