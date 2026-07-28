# TV design specification

Status: normative from 2026-07-28. Supersedes ad-hoc values in `ui/`.
Evidence and rationale: `docs/tv-design-audit-2026-07-27.md`.
Implementation sequence: `docs/tv-design-consolidation-handoff.md`.

This document says what the app's surfaces **must** do. It is deliberately short
so it can stay current, as AGENTS.md requires. It does not restate the audit's
findings, and it does not contain a work plan.

Baseline: Google TV is the target experience, Android TV OS is the platform.
Current official TV design guidance, Compose for TV guidance and Material for TV
component behaviour take precedence over anything below. Where this document adds
a rule, it is because the guidance leaves a choice open and the product needs one
answer.

---

## 1. Colour

### 1.1 Scheme

Dark is the product default. `primary` is the product's own cyan.

| Role | Value | Notes |
|---|---|---|
| `primary` | `#00BCFA` | Product cyan. L\* 71.5, 8.7:1 on background |
| `onPrimary` | `#00344B` | **Must be dark.** White on cyan is 2.19:1 and fails |
| `primaryContainer` | `#003E55` | Cyan hue at the existing container tone/chroma relationship |
| `onPrimaryContainer` | `#C3E8FF` | Cyan hue at the existing on-container tone/chroma relationship |
| `secondary` | `#C4E8FE` | Lower-chroma cyan at the existing secondary tone |
| `onSecondary` | `#0D3446` | Lower-chroma cyan at the existing on-secondary tone |
| `secondaryContainer` | `#274B5D` | Lower-chroma cyan at the existing secondary-container tone |
| `onSecondaryContainer` | `#C4E8FE` | Lower-chroma cyan at the existing on-container tone |
| `background` | `#0F1014` | unchanged |
| `surface` | `#17181D` | unchanged |
| `onSurface` | `#E3E3E8` | unchanged |
| `error` | `#F2B8B5` | unchanged; see 1.3 |

`primaryContainer`, `secondary` and their `on*` pairs are re-derived from cyan in
the same tonal relationship the blue scheme used. The parallel mobile scheme in
`Theme.kt` mirrors the TV scheme and must be kept aligned; it exists only so the
permitted mobile primitives inherit the right colours.

### 1.2 One colour, one job

Every colour below has exactly one meaning. A colour that means two things means
neither at ten feet.

| Colour | Means | Appears on |
|---|---|---|
| `primary` cyan | structure and focus | focus indication, selection, active navigation, ambient progress |
| **orange `#FA7F00`** | **playback position** | **the player seekbar, and nothing else** |
| recording red | a recording exists or is running | REC badges and indicators |
| `error` | a failure the user must act on | error text, failed-recording state |

**Orange is reserved.** It is the most saturated colour in the palette and it
appears in exactly one component. This mirrors how the product mark is built —
cyan is the frame, orange is the play symbol — and it is why the seekbar reads as
the most important thing on screen when the overlay is up.

Consequences that follow, and are not negotiable once orange is reserved:

- Ambient progress strips on cards and rows are **not** orange. They are not
  seekbars (see 6.2). Making them orange would put orange beside a red REC mark
  in the same tile, 29° apart in hue, which is the hardest discrimination at
  viewing distance.
- Orange never carries "live", "new", or any other state. State differences are
  carried by shape and label, with colour as reinforcement only — AGENTS.md:
  "subtle color-only state are not acceptable TV interactions".

### 1.3 One recording red

There are currently three warm reds: `error` `#F2B8B5`, a hardcoded
`RecordingRed = 0xFFE53935` in `RecordingStatusIndicator.kt:18`, and `error`
reused as a REC badge in `ProgrammeCard`. Collapse to two roles:

- **Recording** — one token, used by every REC badge, dot and "recording now"
  label. Currently `primary` at `RecordingsScreen.kt:1074`; that must move.
- **Error** — `error`, for failures only.

No component may declare its own red.

### 1.4 Picon accents

`ChannelAccentPolicy` samples per-channel accents from picon artwork. These are
data-derived and outside the palette. Rules:

- A sampled accent may tint a card's **media area** only.
- It may never colour text, focus indication, progress, or a badge.
- It must be desaturated or scrimmed enough that a focused card still reads as
  focused. A broadcaster's own red must not compete with the recording red.

### 1.5 Opacity tiers

Nineteen distinct alpha values are in use. These are the only permitted ones.

| Token | Value | Use |
|---|---|---|
| `TvTextPrimaryAlpha` | 1.00 | primary text |
| `TvTextSecondaryAlpha` | 0.88 | supporting text |
| `TvTextTertiaryAlpha` | 0.72 | metadata, timeline labels |
| `TvTextDisabledAlpha` | 0.38 | disabled |
| `TvPanelBrowseAlpha` | 0.84 | browse panels over video |
| `TvPanelDenseAlpha` | 0.92 | guide and settings — denser content, more opaque |
| `TvScrimNavigationAlpha` | 0.50 | rail scrim over video |
| `TvScrimModalAlpha` | 0.76 | dialogs and confirmations |
| `TvTrackAlpha` | **0.20** | progress and seekbar track |
| `TvGhostFillAlpha` | 0.40 | ghost/rewindable regions |

`TvTrackAlpha` drops from 0.24 to 0.20. Orange separates from the old track at
only 3.46:1; cyan at 4.09:1. Both need the track darker.

---

## 2. Spacing

One 4dp-based scale. Fifty-nine distinct `dp` literals are in use; these nine are
the permitted steps.

`4 · 8 · 12 · 16 · 24 · 32 · 48 · 56 · 80`

Component-internal dimensions that are not spacing — picon boxes, bar heights,
thumb sizes, card widths — are named tokens and are exempt. A raw `N.dp` in a
padding, gap or arrangement is a defect.

---

## 3. Safe area — one owner

The shell owns the safe area. Screens do not add their own.

- The shell computes the total content inset, including whatever the navigation
  drawer occupies, and passes it down as `PaddingValues`.
- A screen applies that inset to its content, never to a scrolling container's
  `Modifier.padding`.
- **Scrolling containers pass the inset to `contentPadding`.** Padding the
  container insets the viewport and prevents rows from reaching the screen edge.
- Rows scroll edge to edge. `contentPadding` aligns only the first and last item
  to the safe area; everything between scrolls past it.

`TvScreenPadding`, `TvFullScreenPadding` and `TvPlaybackPadding` are replaced by
the shell-provided inset. They may survive as the shell's own inputs, not as
things screens import.

---

## 4. Focus

### 4.1 Indication

The guidance offers four indications — scale, border, glow, and colour — that
can be mixed by context. It does not require multiple simultaneous indications.
Use the smallest treatment that remains unmistakable at ten feet:

| Container | Scale | Border | Glow | Colour | Room the container must reserve |
|---|---|---|---|---|---|
| Card in a lazy row or grid | 1.05 | — | yes | — | 8dp `contentPadding` on the cross axis |
| List row in a lazy column | — | — | — | strong focused container | none |
| Drawer / rail item | — | — | — | yes + active indicator | none |
| Tab | — | — | — | pill | none |
| Player icon button | 1.10 | — | — | yes | 4dp inset from the safe edge |

**A scale value is only valid together with the room its overflow needs.** A
1.05 scale on a 176dp card overflows 4.4dp per side; the 8dp reservation absorbs
it. Never raise a scale without checking the container reserves for it — the
prior review already reported focused cards clipping at a container edge.

List rows deliberately stay unscaled to avoid clipping. Their high-contrast
focused container is sufficient and matches the official JetStream Profile
pattern (`focusedScale = 1f` plus `inverseSurface`); do not add a redundant
outline merely to combine indication types. A subtle colour-only change would
still be unacceptable.

### 4.2 Entry

**Every focusable container declares where focus lands when entered.** Use
`Modifier.focusRestorer()`, as `HomeScreen.kt:197` already does.

- On first entry, focus goes to the container's active or selected item.
- On re-entry, focus returns to the item that last held it.
- A `LaunchedEffect` keyed on route is not an entry contract. It handles route
  changes and does nothing for a lateral D-pad move.
- While the standard drawer owns focus, a newly composed destination initializes
  its content but defers its automatic initial-focus request until focus leaves
  the drawer. Page focus must not close the drawer during focus-based navigation.

### 4.3 Commit model

Set by the component, not by preference. Both are correct; the app must not
invent a third.

| Component | Commits on | Source |
|---|---|---|
| Tabs | **focus** | "when moving from one tab to the next the content below also slides" |
| Standard navigation drawer | **focus** | page content updates as focus moves between destinations |
| Lists and grids | selection | a list is not a picker |

Focus-driven commit is safe **only** because of 4.2. Without a declared entry
target, a move into a focus-driven component lands on a geometric nearest and
commits something the user did not choose.

Focus may change what is previewed in a detail pane at any time. That is not a
commit.

### 4.4 Back through navigation layers

Back unwinds focus layers before changing top-level destination history. From
browse content it activates the global drawer on the current destination. From
a non-Home drawer destination, the next Back focuses Home; Back from Home then
delegates to the existing warm-player or activity-exit policy. Settings adds one
local layer: content returns to the current category before category focus
returns to the global drawer on Settings. Focus-previewed drawer destinations do
not form a Back stack, but their saved screen and focus state is restored when
the viewer returns. Remote key dispatch consumes the complete Back key cycle at
the nearest focused layer; dispatcher-backed handling remains available for
accessibility and system Back actions without a focused key target.

---

## 5. Typography roles

No new type scale. Roles are fixed so slots stop being re-chosen per screen.

| Role | Style | Alpha |
|---|---|---|
| Screen title | `headlineMedium` | primary |
| Section heading | `titleLarge` | primary |
| Item title | `bodyLarge` / `headlineMedium` in the player | primary |
| Eyebrow / channel identity | `titleMedium` | secondary |
| Metadata / supporting | `labelLarge` | tertiary |

Long localized text must not change a layout's anchors. See 6.1.

---

## 6. Components

### 6.1 List rows

One shared row composable. Five independent `ListItem` configurations are not
five components.

- Leading content, headline and trailing content share **one anchor**.
- The headline is one line and ellipsizes. Per TV Material's `ListItem` template,
  additional lines belong to supporting or overline content, not the headline.
- Row pitch is a function of the row's declared height, not of its content.

Acceptance: render short and ellipsized long headlines; leading and trailing tops
are unchanged.

### 6.2 Progress: two components, not one

| | Seekbar | Ambient strip |
|---|---|---|
| Where | player overlays | cards, channel rows, hero |
| Interactive | yes — focusable, scrubs | no |
| Fill | **orange** | `primary` cyan |
| Extras | thumb, labels, ghost fill, live edge | none |
| Component | `PlayerTimeline.kt` | `ProgressStrip` in `ui/components/` |

These are separate components and must stay separate. An ambient strip must not
be built from the player timeline, and must not use mobile Material's
`LinearProgressIndicator`, whose default stop indicator draws a mark at 100%
regardless of actual progress.

### 6.3 Cards

Use the TV Material card containers. `StandardCardContainer`, `WideCardContainer`,
`ClassicCard` and `CompactCard` are at zero usages while two card types are
hand-built.

- Widths come from the guidance grid at 20dp peek spacing:
  `844 / 412 / 268 / 196 / 124`.
- Row spacing is 20dp.
- 16:9 for programme and channel content.
- The content block never exceeds the media width.

### 6.4 Category pickers

Use a component; do not assemble one from `ListItem` in a `Column`.

- Persistent left-hand category rail → navigation drawer.
- Horizontal mode switch → `TabRow`.

Settings categories and `ChannelTagBar` are currently hand-assembled and must
adopt one of the two, which also gives them 4.2 for free.

### 6.5 Navigation drawer

Anatomy, per the guidance: **top section** (product mark) → navigation rail, 3–7
destinations → bottom section, 1–3 actions. The app has no top section.

The rail and the panel beside it must be visually distinct. They currently
resolve to the same luminance — `surface` at 0.90 next to `surface` at 0.96 —
with no gutter between them.

Use the **standard push drawer**. Expanding the drawer changes the browse
viewport position while preserving its closed width; the trailing edge clips
rather than remeasuring each destination narrower. The shell passes the safe
content inset and keeps navigation and content as adjacent surfaces. Settings
remains in this global shell, so entering its content collapses the drawer to the
icon rail instead of removing it; its temporary category rail is replaced in the
later component slice.

---

## 7. Derived tokens

The player overlay's `TvOverlay*` block predates this document and is the worked
example it generalises. Those tokens stay, restated as derived:

- `TvOverlaySidePadding` = spacing `56`
- `TvOverlayTopPadding`, `TvOverlayBottomPadding` = spacing `32`
- `TvOverlayActionGap` = spacing `8`, `TvOverlayActionGroupGap` = spacing `24`
- `TvOverlayTextPrimaryAlpha` / `Secondary` / `Tertiary` = the 1.1.5 text tiers
- `TvOverlayTrackAlpha` → `TvTrackAlpha` (0.20)
- `TvOverlayGhostFillAlpha` → `TvGhostFillAlpha`

Gradient run-outs, bar heights, thumb and picon sizes are component dimensions,
not spacing, and stay as they are.

---

## 8. Verification

Every rule above is either testable or explicitly a device check.

| Rule | How it is checked |
|---|---|
| 6.1 anchor | instrumentation: one-line vs two-line headline, tops unchanged |
| 4.2 entry | instrumentation: enter a container laterally, assert the active item holds focus and no mode changed |
| 6.2 no stop indicator | instrumentation or measured capture: no lit pixels beyond the fill |
| 2 spacing | review; a raw `dp` in padding is a defect |
| 1.1 contrast | computed, recorded in the audit |
| 4.1 clipping | **device only** — measure each container on the G10 |
| 1.4 picon clash | **device only** |
| Colour over video | **device only** — ADB captures cannot show it |

ADB screenshots cannot prove SurfaceView visibility, focus feel, or motion
quality. Device checks are not optional and are not satisfied by a passing test.
