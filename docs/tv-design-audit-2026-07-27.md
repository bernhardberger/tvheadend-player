# TV design system audit

Date: 2026-07-27
Audited head: `6fb8d4d` (`Prepare 0.1.3 release`)
Worktree at audit: `docs/appliance-mode-plan.md`, `tools/device`,
`tools/tests/test_device.py` modified and unrelated to this work.

## Scope and how to read this

This audit answers a product-owner report that the app "doesn't yet feel 100%
native to Material on TV" and that cards, colour, focus navigation, and list
spacing are inconsistent. It is a **design-system** audit, not a repeat of the
UX review programme recorded in `docs/claude-opus-5-ux-review-implementation-handoff.md`
and `docs/ui-ux-overhaul-implementation-review-handoff.md`. Those catalogue
missing behaviour on individual screens. This one asks why the same defects keep
being reintroduced.

Every finding is classified:

- **Code-confirmed** — established by reading source at the audited head.
- **Screenshot-measured** — measured in pixels from device captures at
  1920x1080, density 2.0, so 1dp = 2px, **and** cross-checked against source at
  the audited head (see the evidence-validity section below).
- **Needs the TV** — cannot be settled from ADB captures. Listed, not claimed.

## Evidence validity — read before trusting any measurement

The only device evidence available is the post-overhaul set captured on the G10
on 2026-07-26 at 15:48–16:21, at head `25400e7`, stored in
`artifacts/ux-screen-inventory/review/2026-07-26_16-21-post-overhaul/`.

**That set is largely stale.** Between `25400e7` and the audited head, 41 UI and
policy files changed (+3485/−1285), including seven commits that rebuilt Home
between 23:23 on 07-26 and 00:32 on 07-27 — after the captures were taken.

Consequently every screenshot claim in this audit was checked by diffing the
relevant composable between `25400e7` and the audited head, and is quoted only
where the code is unchanged. Two findings drafted from those captures were
withdrawn on that check and are recorded as W1 and W2 rather than deleted,
because the temptation to re-derive them will recur.

Those artifacts contain household channel and programme metadata. This document
quotes geometry only and names no content.

A second caution, recorded because it happened twice: **downscaled views of these
captures are misleading.** Two "obvious" element overlaps turned out to be
adjacent elements separated by a real gap once measured in pixels. No composition
claim below rests on looking at an image.

A third, also twice: **summaries of the design guidance are not the guidance.**
Two claims in earlier drafts — that Android prescribes a "focus-based content
update model", and that it names 1:1 as the ratio for channel cards — came from
paraphrases of the official pages rather than their text. Both were wrong, and
both were caught by the product owner rather than by this audit. Every guidance
claim that survives below is quoted from the page itself.

## The diagnosis

There is no design contract, so every screen re-derives one.

Quantified at the audited head, across `ui/` only:

| Axis | Distinct values in use | Contract |
|---|---|---|
| Spacing (`N.dp` literals) | **59** — including 1, 2, 3, 5, 10, 14, 18, 22, 26, 30, 38, 42, 54, 76, 88, 92, 99, 110, 190 | none |
| Surface and scrim opacity | **19** across 49 call sites | 4 named constants in `TvLayout.kt`, covering some of them |
| Focus scale | **3** — `1f` (14 sites), `1.05f`, `1.06f` | none |
| Card construction | 2 uses of TV `Card`; 14 files hand-rolling `Surface` | none |

`ui/TvLayout.kt` is the closest thing to a token file. It holds four screen
paddings, four panel alphas, three Home card dimensions, and — since commit
`8612653` — 28 `TvOverlay*` tokens covering the player overlay only. Nothing
governs typography roles, focus indication, list rhythm, or card anatomy, and
nothing outside the player consumes the overlay tokens.

The player-overlay consolidation proved the pattern works: one shared header, one
timeline, one action row, one formatter, tokens for the geometry, and a
regression test that fails on the old tree. **D2 and D5 below are the same
defects that overlay had, one level up in the tree.** The remedy is to hoist that
approach to app scope, not to invent a second system beside it.

Two places already solve pieces of this correctly and should be the templates
rather than being rewritten:

- `HomeScreen.kt:197` already uses `Modifier.focusRestorer()`. The API exists in
  this Compose version and is in production use in exactly one container.
- `HomeScreen.kt:193-194` already pairs a focus scale with the room it needs:
  *"8 dp absorbs 1.06 focused scale (~5.3 dp overflow) without clipping."*
  That is the reasoning D3 needs generalised, not invented.

---

## D1 — No container restores focus to its active item on entry

**Severity:** High · **Code-confirmed** · Reported by the product owner

**This finding was re-framed after reading the official component guidance.** An
earlier draft treated focus-driven tab selection as the defect. It is not.

The TV tabs guidance describes exactly that behaviour as the pattern: *"When
moving from one tab to the next the content below also slides left or right based
on the tab movement."* The navigation-drawer guidance separately says the page
*"automatically updates to the new destination upon selection."* So tabs
committing on focus and a drawer committing on selection is **not** an
inconsistency to resolve — it is the documented difference between two
components. The app is roughly right in kind.

What is missing everywhere is focus restoration. `RecordingsScreen.kt:449-490`:

```kotlin
TabRow(selectedTabIndex = selected.ordinal, ...) {   // no entry target declared
    Tab(
        selected = selected == mode,
        onFocus = { onFocused(mode) },   // on-pattern, given restoration
        onClick = { onClick(mode) },
    )
}
```

Pressing Up from the right-hand pane runs a geometric focus search, which lands on
whichever tab is nearest in the beam — the middle one — and `onFocus` then
switches the screen out of Archive. **With restoration the same keypress lands on
Archive, which is already selected, and nothing changes.** Focus-to-select is
safe precisely and only when entry is deterministic.

The same gap exists in `SideRail.kt:148-153` and `SettingsSubRail.kt:53-55`,
which re-request focus from a `LaunchedEffect` keyed on the current route. That
works when the route is what changed and does nothing for a lateral D-pad entry.

**Remedy:** every focusable container declares its entry target, using the
`focusRestorer()` already proven at `HomeScreen.kt:197`. Keep focus-to-select on
tabs; keep selection-to-commit on the drawer.
**Acceptance:** Up from the folder-preview pane lands on Archive and the mode does
not change.

## D2 — List rows use two alignment systems, so they shift when text wraps

**Severity:** High · **Screenshot-measured**

`FolderRecentRecordingRow` (`RecordingsScreen.kt:717-750`) and `RecordingListRow`
(`:1046-1108`) are **byte-identical** between the capture head and the audited
head, so the following measurements describe current behaviour. Glyph tops in px
from the folder-preview list:

| Row | Title top | Trailing date top | Picon top | Pitch to next |
|---|---|---|---|---|
| 1-line title | 363 | 362 | 360 | 140 |
| 1-line title | 503 | 502 | 500 | 130 |
| **2-line title** | 633 | **657** | **672** | 170 |
| **2-line title** | 803 | **827** | **842** | — |

`ListItem`'s `headlineContent` is top-anchored while `leadingContent` and
`trailingContent` are centre-anchored. As soon as a title wraps, the date drops
24px and the picon drops 39px, and both align with nothing. Row pitch runs
140 / 130 / 170px, so the list has no vertical grid.

Titles wrap often: `maxLines = 2` is set at `:1062` and `:729`, and long German
programme titles reach it routinely.

This is precisely the defect fixed in the player header by commit `8612653` — an
outer `Alignment.Top` row containing a `CenterVertically` row, so content growth
moves siblings by a content-dependent amount. The fix there was a flat,
baseline-anchored row. The same fix applies.

**Remedy:** one shared list-row composable with a declared anchor, replacing the
five independent `ListItem` configurations.
**Acceptance:** render one-line and two-line titles and assert the trailing block
and leading picon tops are unchanged. This fails on the current tree, which is
what makes it worth writing first.

## D3 — Focus scale is switched off almost everywhere, and cannot simply be switched back on

**Severity:** High · **Code-confirmed**

Android's TV focus-system guidance documents scale as one of four focus
indications, with 1.025, 1.05 and 1.1x as the default values. The app:

| Value | Sites |
|---|---|
| `1f` (scale disabled) | 14 — `SettingsSubRail:74`, `SettingsSwitchRow:39`, `ChannelRow:110`, `ChannelTagBar:83,247`, `PlaybackOptionsSheet:452`, `RecordingsScreen:629,747,1101`, `SettingsPlayer:73`, `EpgGridScreen:942`, `SettingsLanguage:58`, `SettingsOptions:95` |
| `1.05f` | `ChannelCardGrid:112` |
| `1.06f` | `ProgrammeCard:53` |

**Scale is not the finding, and an earlier draft over-weighted it.** The guidance
lists four indications — scale, border, glow, colour — and says to "mix and match
these properties to achieve different effects for different contexts". Disabling
scale is a legitimate choice. The defect is what replaces it:

| Indication | Sites in `ui/` |
|---|---|
| Scale | 2 (`ChannelCardGrid`, `ProgrammeCard`) |
| **Border** | **0** |
| **Glow** | **0** |
| Colour | everywhere |

There is no `Border` or `Glow` anywhere in the UI — the single `.border` call in
`RecordingStatusIndicator.kt:41` is an unrelated recording dot. So at all 14
sites, focus is carried by **container colour alone**, which is exactly what
AGENTS.md forbids: "subtle color-only state are not acceptable TV interactions."

Restated: the app uses one of the four available focus indications, uniformly,
and the guidance expects a mix chosen per context.

**This finding must not become a slice that sets `focusedScale = 1.05f` in 14
places.** Several `1f` settings are load-bearing: scale inside a `LazyColumn` or
`LazyRow` clips at the container edge unless the container reserves room, and the
prior review reported exactly that defect ("the focused first card overlapped the
rail area"). Re-enabling scale without the matching inset reintroduces a known,
already-reported bug.

`HomeScreen.kt:193-194` shows the correct shape of the rule: a scale value is
only meaningful together with the room its overflow needs, computed from the
element's own size.

**Remedy:** a focus-indication contract that names, per container class, which of
scale / border / glow / colour applies **and** the inset the container must
reserve. Apply it only where the container already reserves room; everything else
is blocked on device measurement.
**Needs the TV:** the actual clipping behaviour per list.

## D4 — TV Material's card containers are unused

**Severity:** Medium · **Code-confirmed**

| Component | Usages |
|---|---|
| `StandardCardContainer` | 0 |
| `WideCardContainer` | 0 |
| `ClassicCard` | 0 |
| `CompactCard` | 0 |
| `Card` | 2 |
| `Surface` (hand-rolled panels) | 14 files |

`ProgrammeCard.kt:51-155` hand-builds media-over-text: a `Card`, a gradient media
box, a picon or initials fallback, a badge, a progress strip, and a three-line
caption. That is `StandardCardContainer`'s exact anatomy. `ChannelCardGrid` builds
a second, differently-proportioned version of the same thing.

AGENTS.md is explicit: "Do not recreate a component that the installed TV
Material version provides." This is the largest standing violation, and it is why
cards differ between Home and Channels — they are two unrelated implementations,
not two configurations of one.

The official cards guidance also supplies the dimensions this work needs, and the
app is off them:

| Spec | Guidance | App |
|---|---|---|
| Card width grid, at 20dp peek spacing | 844 / 412 / 268 / 196 / 124 dp | `HomeCardWidth = 176.dp` — off-grid, between the 4- and 5-card values |
| Row spacing | 20dp | 12dp (`HomeScreen.kt:195`), 16dp (`ChannelCardGrid.kt:74-75`) |
| Aspect ratio | 16:9 "most common… well-suited for displaying images and videos" | 176×99 — correct |
| Card variants | Standard, Classic, Compact, Wide Standard, Wide Classic | none used |

**Withdrawn from this finding:** an earlier draft claimed the guidance names 1:1
as the ratio for channel cards. It does not. The verbatim text offers 1:1 for
"cards that need to be visually balanced, such as cast and crew, channel logos,
or team logos" — icon-like content — and the page **gives no ratio for channel or
programme cards at all**. A channel card here carries a picon *and* the current
programme, so it is not a logo tile, and 16:9 is defensible. The claim came from
a summary of the page rather than its text; see the note on that failure mode
below.

## D5 — Ambient progress strips are built three ways, and three of them draw a stray marker

**Severity:** Medium · **Screenshot-measured**

An earlier draft of this finding said these strips should adopt
`PlayerTimeline.kt`. **That was wrong and is corrected here.** An ambient
progress strip is not a seekbar: `PlayerTimelineBar` hardcodes a 24dp row band
and a 6dp bar sized for a ten-foot player overlay, carries `ghostProgress`,
`liveEdgeFraction`, `boundaryFractions` and `thumbTestTag`, and lives in
`ui/player/` under a `Player*` name. Pushing a 3dp strip at the bottom of a 176dp
card through it would be over-abstraction, not consolidation.

The real finding is narrower and more concrete. Five ambient strips, three
implementations:

| Site | Built with | Height | Track |
|---|---|---|---|
| `ProgrammeCard.kt:103` | hand-rolled `Box` | 3dp | `onSurface` @ 0.25 |
| `HomeHeroCarousel.kt:260` | hand-rolled `Box` | 4dp | `onSurface` @ 0.22 |
| `ChannelRow.kt:64` | mobile M3 `LinearProgressIndicator` | 3dp | M3 default |
| `ChannelCardGrid.kt:207` | mobile M3 `LinearProgressIndicator` | 3dp | M3 default |
| `ChannelsScreen.kt:726` | mobile M3 `LinearProgressIndicator` | — | M3 default |

The three `LinearProgressIndicator` sites render the M3 **stop indicator**, on by
default since Material3 1.3 and present under the pinned Compose BOM
`2026.02.00`. Measured on channel-list rows — and `ChannelRow`'s progress block is
byte-identical between the capture head and the audited head, so this is current
behaviour:

| Row | Fill ends | Track gap | Track resumes | Stray mark |
|---|---|---|---|---|
| A | 673 | 674–681 | 682 | **754–759** |
| B | 620 | 621–627 | 628 | **754–759** |
| C | 335 | 337–343 | 344 | **754–759** |

The 6px mark at 754–759 is `#A8C7FA` — the same colour as the fill — parked at
100% on every row regardless of actual progress. On row C real progress is 11%,
and there is a primary-coloured mark sitting at the end of the track. At ten feet
it reads as a second playhead. The 7–8px gap before the track resumes is the
other M3 default.

**This is the identical artifact removed from the player's recordings peek
overlay during the overlay consolidation.** It is still live on every channel row
and channel card.

AGENTS.md permits mobile Material 3 for progress indicators as a primitive TV
Material lacks, so the import is not a violation. The defect is that the M3
defaults draw a marker this product does not want.

**Remedy:** one small `ProgressStrip` in `ui/components/` — track plus fill,
height and track alpha as tokens, no thumb, no labels, no player coupling. The
minimum fix is passing an empty `drawStopIndicator` to the M3 indicator; the
shared composable is preferable because it also settles the 3dp/4dp and 0.25/0.22
disagreements in the same change.

## D6 — Rail and content separation is unspecified

**Severity:** Medium · **Code-confirmed, geometry needs re-measurement**

`SideRail.kt` changed after the captures, so the earlier pixel measurement of the
rail/content boundary described the pre-fix layout and has been withdrawn (W1).
What holds at the audited head, from source:

- The drawer surface reaches the screen edge and its content is inset 24dp
  (`SideRail.kt:154-162`).
- Screen content is offset by `padding(start = 80.dp)` (`:190-198`).
- The drawer is `surface` at `TvSettingsPanelAlpha` 0.90; the browse panels
  beside it are `surface` at 0.96 (`RecordingsScreen.kt:501`) and 0.98
  (`EpgGridScreen.kt:662`). **Those three alphas are visually indistinguishable
  over the same background** — the rail and the panel next to it are the same
  colour by arithmetic, not by design.
- `TvScreenPadding` then adds `start = 24.dp` inside content that has already
  been offset 80dp, so the effective leading inset is 104dp with no token saying
  so.

**Correction: this was closed too firmly and is re-opened.** An earlier draft
recorded the drawer type as settled. The comment at `SideRail.kt:136-137` is a
past implementation decision, not a product constraint, and the guidance presents
both variants as legitimate: the **standard** drawer "expands to create additional
space, **pushing the page content aside**"; the **modal** drawer "appears as an
overlay on top of the app's content with a scrim" and suits immersive
experiences. The app's modal choice is on-spec — but so is the alternative, and
the owner has twice raised wanting content pushed rather than overlaid.

The choice is load-bearing for everything else in this section: a standard drawer
makes each screen's viewport **dynamic**, while the modal drawer gives every
screen a fixed inset. That is the difference between adjusting the shell and
rewriting its layout contract.

One structural gap against the guidance: the documented drawer anatomy is a **top
section** (app logo, doubling as profile or search), then the navigation rail of
3–7 destinations, then a bottom section of 1–3 actions. The app has the rail
(4 destinations) and the bottom section (Settings, and Unlock in Simple TV), but
**no top section** — so the collapsed rail begins with a bare Home icon at the
screen's top-left. This product has an approved mark (`artwork/`) that is exactly
what that slot is for, and it currently appears nowhere in the running app.

**Needs the TV:** the resulting gutter and whether rail and panel read as
separate surfaces at viewing distance.

## D7 — Colour has five independent sources and no relationship to the product identity

**Severity:** Medium · **Code-confirmed** · Product decision required

`Theme.kt:14-31` is a hand-authored dark scheme in the Google-blue family. It is
**not** Android's default — M3's baseline dark primary is purple `#D0BCFF`.

`docs/product-identity-plan.md` (status: approved and implemented) and
`artwork/README.md` define the product palette as cyan `#00BCFA` and orange
`#FA7F00`, chosen explicitly to recall TVHeadend compatibility. Neither appears
anywhere in the app. The launcher icon and the app interior are unrelated.

Colour currently originates from five places: the TV scheme, the parallel mobile
scheme (`Theme.kt:62-98`), 19 ad-hoc alpha literals, hardcoded `Color.Black`
scrims, and — since `da23038` — per-channel accents sampled from picon artwork
(`ChannelAccentPolicy.kt`, `ChannelAccent.kt`). The last is a legitimate feature
but means card tint is data-derived while everything around it is theme-derived,
with no rule for how they coexist.

Two constraints bound any palette change:

- `primary` is load-bearing for focus indication **and** every progress fill —
  six surfaces recolour together.
- `error` is already the REC badge red, so any "live" or "new" hue must be
  distinguishable from it at viewing distance.

**Needs the TV:** saturated cyan behaves differently on a large emissive panel
than on a monitor, and colour over moving video behind a scrim cannot be judged
from captures at all.

## D8 — Two category pickers are hand-rolled rather than using a TV component

**Severity:** Low · **Code-confirmed**

**Downgraded after reading the component guidance.** An earlier draft called the
drawer/tabs difference an inconsistency. It is not — see D1; the two components
are documented to behave differently, and the app matches that.

| Surface | Mechanism | Commits on | On-pattern? |
|---|---|---|---|
| Global rail | `ModalNavigationDrawer` + `NavigationDrawerItem` | selection | yes |
| Recordings modes | `TabRow` + `Tab` | focus | yes |
| Archive folders | `ListItem` in a `LazyColumn` | selection | yes — it is a list |
| **Settings categories** | `ListItem` in a plain `Column` | selection | **no component** |
| **Channel tags** | `ListItem` + `Button` in `ChannelTagBar` | selection | **no component** |

The last two are lateral category pickers hand-assembled from `ListItem` in a
`Column`, with `focusedScale = 1f` and a manual `LaunchedEffect` focus request
standing in for what a drawer or tab row provides. Settings in particular is a
persistent left-hand category rail — structurally a navigation drawer — built by
hand.

This is low severity because it works; it is listed because those two are where
the focus-restoration bug in D1 has to be fixed by hand rather than by adopting a
component that already handles it.

## D9 — The leading inset has two owners, and Home is boxed as a result

**Severity:** High · **Code-confirmed** · Reported by the product owner

Nobody owns a screen's total safe area. The shell offsets content by
`padding(start = 80.dp)` (`SideRail.kt:190-198`), and then each screen
independently applies its own:

| Screen | Applies |
|---|---|
| Home, Channels, Recordings, EPG | `TvScreenPadding` — 24 start / 32 top / 48 end / 32 bottom |
| Settings, Onboarding, Unlock | `TvFullScreenPadding` — 48 / 32 / 48 / 32 |
| Player channel drawer | `TvPlaybackPadding` — 48 / 32 / 24 / 32 |

So the leading inset is 80dp + 24dp with no token expressing the total, and the
end inset is 48dp regardless of the fact that nothing occupies the right edge.
The result is asymmetric by accident rather than by design.

The visible consequence is at `HomeScreen.kt:122-128`, which is the **only** lazy
container in the app applying `Modifier.padding` to the container rather than
`contentPadding`:

```kotlin
LazyColumn(
    modifier = Modifier.fillMaxSize().padding(TvScreenPadding),   // insets the viewport
    contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
)
```

Padding the container insets the **viewport**, so the card rows inside cannot
reach the screen edge — they terminate 48dp short of it and are clipped there.
Every other lazy container in the app (`ChannelCardGrid`, `ChannelDrawer`,
`ChannelsScreen`, four in `RecordingsScreen`, `EpgGridScreen`, the Home
`LazyRow`s) uses `contentPadding` correctly.

Edge-to-edge rows that scroll past the safe area, with `contentPadding` aligning
only the first item, is the single most recognisable structural difference
between a native TV browse surface and a ported one. Home currently reads as a
boxed page.

**Remedy:** one owner for the safe area. The shell declares the total inset;
screens receive it and pass it to `contentPadding`, never to `Modifier.padding`
on a scrolling container.

---

## Withdrawn findings

Recorded so they are not re-derived from the same stale evidence.

### W1 — "The rail and content are visually fused, with a zero-pixel gutter"

Measured from the captures: rail surface x 24–183, icons x 86–121, first content
pixel x 184. **Withdrawn.** At capture time `SideRail` inset its surface by 12dp
and applied *no* offset to content; the current head reaches the edge and offsets
content by 80dp. The measurement is real but describes code that no longer
exists. The surface-alpha half of the observation survives as D6.

### W2 — "Home reads as a full-width vertical list of near-duplicate rows"

Drafted from `post-overhaul-11` and `-23`, which show stacked full-width text
rows with no hero. **Withdrawn.** Home was rebuilt in seven commits between 23:23
on 07-26 and 00:32 on 07-27, all after the captures. At the audited head
`HomeScreen.kt:122-227` is a `LazyColumn` containing `HomeHeroCarousel` plus
`LazyRow`s of `ProgrammeCard`, with `focusRestorer()` on each row and a bounded
focus latch at `:102-111` — i.e. the structure the withdrawn finding asked for,
and the closest thing in the codebase to a correct TV dashboard.

Whether Home *succeeds* is unknown and unmeasured. Assessing it needs a fresh
capture. Two related defects recorded elsewhere may or may not still hold and
were not re-verified here: missing deterministic initial focus (E4) and duplicate
active recordings across sections (E7); `HomeContentPolicy.kt` changed by 586
lines since those were written.

## Already decided — record, do not re-litigate

- Standard push-drawer behaviour, selected by the product owner on 2026-07-28;
  the modal implementation at `SideRail.kt:136-138` is now migration evidence.
- Mobile Material 3 only for primitives TV Material 1.1.0 lacks — text fields,
  progress indicators, dividers, dialogs (`Theme.kt:59-61`).
- The player overlay's tokens, shared header, timeline and action row are the
  worked example this work extends, not something to redesign.
- The player header clock stays `displaySmall`; demotion was declined.
- Dark theme is the product default.

## Open product decisions

1. ~~**Colour direction**~~ — **decided 2026-07-28: cyan as primary.** `primary`
   becomes `#00BCFA`, and orange `#FA7F00` is reserved to the player seekbar
   alone. Recorded normatively in `docs/tv-design-spec.md` §1. Two knock-ons are
   folded into that spec: `TvTrackAlpha` drops to 0.20 because orange separates
   from the old track at only 3.46:1, and the app's three warm reds collapse to
   one recording token so orange is not confusable with a REC mark.
2. ~~**Focus commit model**~~ — **resolved by the component guidance; no decision
   needed.** Tabs commit on focus, the drawer commits on selection, and both need
   deterministic entry. See D1.
3. ~~**Drawer behaviour**~~ — **decided 2026-07-28: standard push drawer.** The
   shell owns its dynamic content inset; expanding navigation must reflow browse
   content rather than overlaying and scrimming a fixed viewport.
4. **Home** — in scope by owner decision, but W2 means there is currently no
   evidence to redesign against.
5. **Drawer top section** — whether the product mark occupies the documented top
   slot, and whether it is decorative or actionable; the guidance pairs that slot
   with profile or search, neither of which this product has. See D6.
6. **Channel card ratio** — the guidance names 1:1 for channel logos. Adopting it
   changes the channel grid's proportions and column count. See D4.

## Reference

Guidance this audit is measured against:

- `.../tv/guides/styles/focus-system` — focus indication types; default scales
  1.025 / 1.05 / 1.1x (D3)
- `.../tv/guides/foundations/navigation-on-tv` — Select selects the focused item;
  Back returns focus to the active menu item
- `.../tv/guides/components/navigation-drawer` — standard vs modal variants,
  drawer anatomy, page updates on selection (D1, D6)
- `.../tv/guides/components/tabs` — content follows tab movement (D1, D8)
- `.../tv/guides/components/cards` — five variants, width grid, aspect ratios,
  20dp peek spacing (D4)

All under `https://developer.android.com/design/ui/`.

## Not verifiable without the TV

- Whether any chosen focus scale clips inside each list container (D3).
- Rail/content separation at viewing distance (D6).
- Colour on the panel, and all colour-over-video judgements (D7).
- Whether Home works (W2).
- Focus feel, remote repeat, and animation quality.

The G10 was not reachable over ADB during this audit
(`./tools/device doctor` → `device not found`), so no fresh captures were taken
and no device was modified. **A fresh capture set is the highest-value next
input**: it would settle D3, D6 and W2, which together are most of what remains
open.
