# Tvheadend Player • TV design contract

Authoritative visual and interaction contract for the Android TV app. It records
**accepted product decisions** only. Implementation mechanics, library internals,
experiment parameters and evidence records belong in code comments, tests or a
dated document, not here.

Precedence: this document → Material for TV guidance and the installed
`androidx.tv:tv-material` behaviour → repository style. Where this document is
silent, the library default is the design.

Historical material: `docs/archive/tv-design-spec-2026-09.md` (superseded, mixed
authority; do not treat as normative).

---

## 1. Authority and sources

- Product decisions come from the operator and are recorded here with a date
  when they change earlier direction.
- **Material for TV** is the component and interaction reference:
  https://developer.android.com/design/ui/tv. The Penpot design kit fork
  (`tvheadend-player-design-kit`, file `8aa8c9a5-d7b1-8076-8008-a23201c2b02c`)
  is the themed component library; the app file
  (`8aa8c9a5-d7b1-8076-8008-a1e6cc530f69`) holds screen compositions.
- The installed TV Material library provides component behaviour. **The app
  does not re-implement a component the library ships** (tabs, drawer items,
  list items, cards, buttons, indicators). Hand-rolled equivalents are defects.

## 2. Theme

Dark-only, one static brand-derived Material scheme (accepted 2026-09-14).
Every role is pinned in `Theme.kt`; no dynamic or wallpaper colour.

| Role | Value | Note |
|---|---|---|
| `primary` / `surfaceTint` | `#79D1FF` | cyan T80 from brand seed `#00BCFA` |
| `onPrimary` / `primaryContainer` / `onPrimaryContainer` | `#003549` / `#004C68` / `#C3E8FF` | |
| `secondary` / `onSecondary` | `#B5C9D7` / `#20333D` | |
| `secondaryContainer` / `onSecondaryContainer` | `#364955` / `#D1E5F4` | |
| `tertiary` / `onTertiary` | `#FF8E32` / `#502400` | orange **T70**, not T80 |
| `tertiaryContainer` / `onTertiaryContainer` | `#723600` / `#FFDCC6` | |
| `background` / `surface` | `#111416` | neutral T6 |
| `onSurface` | `#E1E2E5` | neutral T90 |
| `inverseSurface` / `inverseOnSurface` | `#E1E2E5` / `#2E3133` | focus containers |
| `surfaceVariant` / `onSurfaceVariant` | `#41484D` / `#C0C7CD` | |
| `error` / `onError` | `#FFB4AB` / `#690005` | |
| `errorContainer` / `onErrorContainer` | `#93000A` / `#FFDAD6` | |
| recording red | `#FF5449` | REC mark and label on dark surfaces (5.8:1); distinct from `error` |
| recording red, focused | `#BA1A1A` | REC mark on a focused light row or cell (5.0:1 on `inverseSurface`) |
| recording red fill / on fill | `#D93025` / white | REC badge (4.8:1) |

Surface ladder (roles TV Material lacks): lowest `#0C0F10`, low `#191C1E`,
container `#1D2022`, high `#282A2C`, highest `#323537`, bright `#37393B`.

Meaning of colour:

- **Cyan** leads: selected/active navigation, ambient progress, selective emphasis.
- **Orange** is the contrasting accent: playback position and deliberate
  secondary emphasis. Not restricted to the seekbar, but sparse enough that the
  UI stays cyan-led. Ordinary panels, cards and buttons stay neutral.
- **Progress colour follows what the bar measures** (accepted 2026-10-16):
  orange = where *the viewer* is in something they watch (player seek position,
  resume point, how far a recording has been watched, on cards, details and
  dialogs alike); cyan = time passing on air, independent of the viewer (how far
  the current programme or a running recording is). One bar, one meaning; if a
  surface needs both, show two bars rather than one bar in two colours.
  The playing item carries the same neutral playing mark and orange progress on
  every surface showing it: Channels List, Grid, Immersive, Quick Zap (including
  its now-playing strip), the Guide headline, context menus, programme and recording
  details, and the recording browser. A playing recording's progress follows the
  viewer's current position, not its saved resume point. Live viewer progress uses
  the runtime's correlated time-shift wall-clock estimate, including when paused
  or rewound. Without time-shift, at a confirmed live edge, or before the first sample
  for a new target, viewer progress follows now in orange. Active time-shift with
  unknown viewer timing has no progress bar. Other programmes keep broadcast-time
  progress in cyan.
  Paused or rewound items retain orange and the playing identity (the pause mark
  replaces the animated bars when paused). Guide grid cells stay unchanged;
  focus/selection styling and buttons remain neutral. Only Playing/Buffering
  (including paused intent) identifies a playing item. Starting's tuning spinner
  is loading feedback, not playing identity or orange progress; Recovering, Failed,
  Idle and Finished have neither playing identity nor viewer progress.
- **Recording red** means "a recording exists or runs". **`error`** means "a
  failure the user must act on". No component declares its own red: marks come
  from `RecordingStatusIndicator` (dot) and `RecordingBadge` (badge), and a
  source test keeps the recording reds inside those two and the theme.
  - **Dot** (16dp, a clock when scheduled) only in rows and grid cells, at the
    right end of the title line: channel rows, Recordings rows, Guide cells
    (the title ellipsizes first). The lines above and
    below keep their full width, so progress and metadata never shorten for a
    mark. Watch state ends the same line in the muted content colour: a check
    circle when watched, Material's clock loader filled about as far as the saved
    position when partly watched, nothing when unwatched. A folder row uses the
    same marks: the check when everything in it is watched, the clock loader
    filled by the share watched to the end otherwise, which its line counts as
    "N watched". Tvheadend's play count counts file opens, so the app never shows
    it as a number. The
    trailing slot belongs to where OK leads, such as a folder's chevron.
    A running recording with errors adds the `error` "!" beside the dot. Guide
    cells also mark a finished recording with the dot and a failed or missed one
    with the `error` "!". No dot
    on section headings or titles; the "Recording now" folder carries its dot
    as a badge on the folder icon.
  - **Badge** (one size: 20dp, 4dp corners, `labelMedium`) in eyebrows, info
    headers, menu headers, side panes and image corners: the solid red `● REC`
    after the channel name in the player's top identity, the Channels headline
    and cards, the zap rail cards, the player's programme info and schedule
    tiles, programme details, the context menu header and the Recordings pane.
    Scheduled is a neutral badge with a clock and "Scheduled", failed an `error`
    badge with "Failed" (or "Missed" for a recording that never ran), canceled
    (an invalid entry) a neutral badge with the cancel mark and "Canceled", errors
    while recording a red `● REC` plus an `error` "Errors" badge. "On now"
    and "Available until" are neutral badges. The Guide headline badges what
    its cells mark: also a neutral "Recorded" with a dot.
  - In Recordings overlines a failure, missed recordings included, is an
    `error` "!" symbol; cancelled and stopped early are muted symbols. Problems
    lists missed recordings under "Failed"; only invalid entries are "Canceled"
    (decided 2026-10-24). A running row's overline shows its start and
    "until …", not the word "Recording". The word stands only where the state
    is the topic, never as a red label by itself.
- **Reds are picked by surface.** Recording red and `error` are tones of the
  same Material error palette; each role has a tone for the dark surface, the
  focused light container and a fill. Recording: `#FF5449` / `#BA1A1A` /
  `#D93025` with white. Error: `error` / `errorContainer` / `error` with
  `onError` (Material's badge colours). The pink `error` (1.3:1) and `#FF5449` (2.5:1)
  disappear on a focused row, so anything red inside a focusable item switches
  to its focused tone. Recording and error stay apart by shape (dot versus "!").
- Brand artwork keeps the exact brand cyan `#00BCFA` and orange `#FA7F00`.
- State is never colour alone: shape, label or icon reinforce it.

Typography is the TV Material scale with Roboto; no custom scale. Logos are
drawn artwork and do not follow font scale.

Every logo is inked for the colour behind it (accepted 2026-10-24): only
the parts drawn on the logo's transparent ground that would vanish there flip
their lightness, keeping hue; plates, brand colours and readable parts stay.
`PiconBox` assumes the dark screen background, which also holds under the
app's scrims; a caller on another solid colour names it (a focused row's light
fill, the Channels logo plate). On dark grounds a colour counts as vanishing
below 1.6:1, on light ones below 1.5:1, where 1.6 would turn mid oranges brown.
Logo cards and the programme pane's logo picture ink their logo for their
ambient colour where the logo sits (accepted 2026-10-25), using its settled
colour so a crossfade does not re-ink it. A logo with its own treatment
(zap card halo over a picture; the details hero logo and the player hero's
logo on their own colour) is left alone.
When the ground changes, the logo shown last stays until the new copy is ready.

Passive text steps down in three tones (accepted 2026-10-21), as the kit's
immersive list and dialog do: the title and its subtitle, which names the
episode, in `onSurface`; descriptions in `onSurfaceVariant` at 80%; metadata (channel, times, facts,
age rating, what follows) in `onSurfaceVariant` at 72%, where the kit uses 60%,
which falls below 4.5:1 over our backdrops. Metadata uses the kit's 12sp
`labelMedium`, whose Medium weight keeps it legible in that grey. `TvTextColors` in `ui/Theme.kt`
holds them for the browse headlines, details page, dialogs and Recordings pane.
Focusable rows and cards keep their focus colours, and see-through panels over
video keep `onSurface` at these alphas, because `onSurfaceVariant` at 72% falls
below 4.5:1 over a bright frame there.

### Startup

**Scope (2026-10-02):** app launch, returning to the app and the handoff to
usable content. Destination-level loading is separate, deferred design work.
Channels is the landing destination for a launch without autoplay. A Home
destination is planned (roadmap U3) but its routing is not decided here.

The startup layer (title card, branding and stage message below) is shown while
local state resolves and while an autoplay launch is pending or entering. With no
pending launch the presentation is inactive (`core/MainStartupPresentation.kt:49-62`),
so a launch without autoplay shows no startup layer after local resolution and
Channels reports the connection state itself. Roadmap U9 records the owner's
2026-10-16 direction: no in-app splash when saved data exists.

Startup branding keeps the 80dp symbol, 14dp gap and
28sp/36sp Outfit 550 wordmark (not font-scaled). On the 960×540dp reference
canvas, the passive symbol starts at y=156dp, independent of feedback.
The symbol and wordmark are static: there is no logo build, so a fast startup can
never cut it off (owner decision 2026-10-23). They stay transparent through the
400ms grace, so a start that finishes within it shows no logo, and appear fully
opaque together with the busy ring and stage text.
Under the content, one original creative cyan/orange diagonal light-sweep frame
is graded to light-only values above `#0F1014`, with feathered edges so it cannot
darken the field or reveal a rectangular rim. Once its image is decoded, the light
fades in over 300ms to 20% opacity, then breathes between 20% and 30% opacity over a
four-second cycle for the remaining startup wait. Each two-second rise and fall uses
sinusoidal easing, with softly rounded turns and no pause. The plate stays still and
follows no brand timeline. It fades out with the startup layer at first picture.
Recovery and reduced motion retain the exact flat field. The asset recipe and
provenance are recorded in
[`artwork/README.md`](../artwork/README.md#startup-artwork).
This is never an extra screen or delay. The native splash remains static, with a maximum 1000ms
local-bootstrap hold; Android owns its splash removal.
The pending opening frame uses the final branded layout, never a spinner-only
intermediate layout. Disabled motion means static branding without the glow.
Startup does not play audio or
request audio focus.
A return waiting for existing playback uses the stable ring and status over the
same gently pulsing ambient glow, without the logo. The glow appears with
visible waiting feedback; the hidden grace period remains flat. Recovery focus, Back and key
consumption keep their existing contracts.

Startup is one compact centered title card on the native-splash field
`#0F1014`, within 48dp horizontal/32dp vertical safe bounds. No footer or
dashboard. Branded waiting preserves the accepted symbol/wordmark composition,
with a larger 44dp circular indicator, 3dp stroke and theme-primary colour 32dp
below the wordmark, then 16dp to centered native TV titleMedium status (max
560dp, two lines). On the reference canvas its centre is (480,340dp). A brand-free
playback return keeps the player's white 44dp ring at (480,270dp); aligning that
return must not move the branded startup composition. There is no linear bar,
counter line or visual promotion. Recovery may
move the lockup up to fit: typed TV titleLarge 22/28 problem, short bodyLarge
16/24 guidance, then 32dp to the existing TV Retry/Outlined Settings actions,
16dp apart. Credential/configuration guidance is not generic network advice;
action policy, focus restoration, directional boundaries and Back are unchanged.

Status follows the connection stage, not each interleaved metadata domain or
record update. Hide spinner and status for the first 400ms of any blocking wait.
Feedback is independent of the assembly clock, including static/no-intro and
reduced-motion entry. Readiness,
recovery and Back are never deferred; retain the same visible indicator through
connecting, synchronization and tuning.
One launch request owns the real uptime clock across bootstrap, phase, automatic
reconnection and metadata changes; a server reconnect does not hide already-visible
feedback. Recovery, request replacement, navigation and Back retire the clock;
an explicit retry starts a fresh grace period. This UI timer uses no SDK progress
counters or session identity; playback readiness keeps its separate session/tune fences.
Reduced motion uses settled branding and truthful status on the same
deadline, with no frozen spinner. Announce stage changes politely, not individual
metadata changes. Do not invent a percentage, record ceiling or ETA.
Relevant loading labels have no ellipsis: Preparing app, Connecting, Syncing
channels and guide, Preparing channels, Starting playback for initial autoplay,
Reconnecting, Tuning and Buffering. Resuming playback requires an existing service
captured at request entry; a fresh tune is not a resume.

Cached browse may enter without dwell under existing rules, but only current
authoritative channels authorize autoplay. For initial autoplay, mount only the
exact entering live route and persistent video surface under the opaque title
card; its chrome, focus and keys remain inactive. Route existence or video Playing
alone does not release startup. The current request/session/tune must present its
first video frame, or positively audio-only playback must be playing. Then fade
only the opaque presentation layer over 200ms, with no extra hold or surface
remount. Retain the outgoing message, feedback/branding visibility and exact
assembly frame; dismissal does not introduce text, reveal hidden feedback or
complete assembly. A visible indeterminate ring may keep rotating. Reduced motion
skips the fade. An already-playing matching warm target
is adopted without retuning, another frame or startup fade. Recovery, failure,
cancellation and Back release the blocking layer immediately. Later buffering
never reintroduces startup.

#### Deferred destination-loading direction

Revisit Channels loading with its unfinished layout, and apply the same principles
to whichever destination eventually receives the startup handoff. Evaluate
layout-matched skeletons or equivalent placeholders once that layout is settled.
One shared, non-focusable skeleton component is planned with the Channels rework
(roadmap U4); its design is decided in that brief, not here. Show usable cached content while
refreshing, keep available navigation and recovery accessible, and preserve stable
layout and D-pad focus as content arrives. Distinguish browsable cached data from
content that can currently be activated. This follow-up does not extend the
branded startup wait or change internal screen loading in the present work.

## 3. Components and indication

### Shared patterns → component

Paths below are relative to `app/src/main/java/at/bernhardberger/tvhplayer/ui/`.

| Shared pattern | Component |
|---|---|
| Row/section emphasis | `components/RowEmphasis.kt` — one 300 ms position clock, deferred header scale/alpha and content dimming |
| Progress bar | `components/ProgressStrip.kt` |
| Channel title | `components/ChannelTitle.kt` — `channelTitleText` |
| Picon | `components/PiconBox.kt` — dimmed TV icon while loading, normal-strength TV icon when there is no logo or it fails |
| Image load fade | `../images/TvheadendArtworkLoader.kt` — the shared loader fades every logo and programme picture in over 200 ms when actually loaded; memory-cache hits show at once |
| Reading box | `components/ProgrammeDetailsLayout.kt` — `ProgrammeDetailsFullDescription` |
| Context menu | `components/BrowseContextMenu.kt` |
| Two-column dialog | `components/TwoColumnDialog.kt` |
| Centred prompt/confirmation card | `components/CentredDialog.kt` |
| Guided step (setup flows) | `components/GuidedStep.kt` — full-screen guidance and step line left, fields or choices right, both a browse column wide with independent scrolling. The actions viewport ends at the 27 dp bottom safe line with a 24 dp fade while content continues below; focused scale stays above the fade. Settings uses the same full-screen viewport. `GuidedStepAction` is the Wide button's look on a clickable `Surface`, because TV Material 1.1's `WideButton` cannot grow to a wrapped or translated subtitle. Busy actions retain native disabled focus, dim their content and replace the icon with a spinner; they do not use the enabled focused fill. |
| Details page | `components/ProgrammeDetailsPage.kt` |
| Quick-zap cards | `player/QuickZapPreview.kt` |

New screens use these shared patterns; a private copy of a size, timing or alpha
they own is a review finding.

Logos and programme pictures get their image loader (Coil's app singleton) and
artwork session (`components/ArtworkContext.kt`, `LocalArtworkContext`) from the
app root, not from parameters; a surface built from older data than the latest
observation (details, context menus, a departing tab frame, the recording
player) provides its own session with `ProvideArtworkSession`.
CompositionLocals are only for such app-wide context and the existing theme and
layout locals, never for screen data: channels, programmes, recordings and time
(`nowSec`/`nowSecProvider`) stay explicit parameters.

Focus, pressed, selected and disabled indication come from the TV Material
components and their `*Defaults` (scale, colour, border, glow, shape). The app
uses **library defaults**. Call sites do not override `scale(...)`,
`border(...)`, `glow(...)`, `shape(...)` or colours, do not force
`focusedScale = 1f`, and do not draw their own outlines. Containers reserve the
scale overflow (cross-axis padding in lazy lists, inset from safe edges) instead
of clipping or cancelling it.

Any different treatment is a **global** decision recorded here and applied once
(theme or shared defaults helper), never per screen. Until then, a per-call-site
override is a defect.

Embedded-progress cards use the shared `embeddedProgressCardBorder` treatment
(revised 2026-09-22): retain the native 3dp focused outline, shape and scale,
use `onSurface` for its colour to match native focused buttons, and move its path
2dp **outside** the card. The outline radius includes that offset: TV Material
1.1.0's native 8dp card radius becomes 10dp on the expanded path, keeping the
corners concentric. Native pressed behaviour remains unchanged. The bottom
progress strip remains in its designed flush, full-width slot; the outline must
not paint over it. Layout reserves the additional outer extent.

Component commit model is set by the component:

| Component | Commits on |
|---|---|
| Page-scope tabs | focus — content below slides with tab direction |
| Standard navigation drawer | focus — destination changes as focus moves |
| Lists, grids, choice rows | OK |

Pressed feedback needs the component to receive the key: parents do not consume
OK/DPAD_CENTER/ENTER on KeyDown and activate on the component's behalf. Parents
may own direction keys, Back and key-cycle relocation.

All tabs use `AppTabRow` + `TabContent`: Page/pill for page navigation, Section/underline within a content area.
Content slides in the tab's direction, honours RTL order, and
rapid changes interrupt toward the latest target. Headers stay stationary.

## 4. Shell, safe area and background

- Logical canvas 960×540 (1920×1080 at 2×). Safe inset 48dp horizontal, 32dp
  vertical from the screen edge; the shell owns insets and passes them down.
  Screens do not add their own safe-area padding.
- One `SideRail` hosts the standard **push** drawer; content translates and
  clips at its trailing edge, it does not reflow.
- The accepted browse grid uses twelve 52dp columns, 20dp gutters and 58dp
  nominal margins on the reference canvas. Channels, Archive and Settings use
  an active column at x130, width340, and passive content at x562, width340
  (92dp separation, 432dp depth stride). Native focus growth has its own reserve;
  grid gutters do not replace it. Keep the existing rail and global safe-padding
  inputs; apply these browse coordinates inside the owning layouts.
- **Warm playback**: when live playback continues behind an ordinary
  destination, one global black scrim sits above the video and below
  navigation, content and notices at **0.84** across ordinary destinations.
  The shell animates changes to the target using
  Compose `animateFloatAsState` with a 300ms default-easing tween; both opacity
  and timing are product choices, not Material requirements. Without
  playback there is no full-screen scrim; the normal themed background shows.
  The player route is excluded; player chrome owns its own gradients. Screens
  never draw their own video scrim.
- **Shared drawer overlap (accepted on G10):** departing depth columns may draw beneath the rail;
  clipping is at the screen edge. A shell-owned black gradient protects the
  navigation, continuously from 0.95 at the leading screen edge to transparent
  128dp beyond the drawer edge, without a stop at the drawer boundary. The drawer remains
  above it and retains standard push behaviour. These are product values, not
  Material requirements. The backing applies to every browse destination;
  the active sheet's content paints **above** this gradient, while departing
  sheets remain beneath it. Do not dim the current sheet's headings, icons,
   rows or focus fill. The shell draws the backing first; the outermost departing
   contribution receives screen-aligned black source-atop attenuation. This
   preserves its alpha and transparent gaps without dimming active content or
   applying the backing twice to nested outgoing layers.
  The shared sidebar scene and scope-tab transitions do not clip at the drawer
  edge. Each scrolling list still owns its vertical viewport clipping.

## 5. Navigation drawer

Anatomy per Material for TV / design kit:

- Edges 12dp on all four sides. Closed rail **80dp**
  (12 + `CollapsedDrawerItemWidth` 56 + 12), expanded **280dp**
  (12 + `ExpandedDrawerItemWidth` 256 + 12).
- Top section 56dp: the transparent diamond brand symbol on the item icon axis,
  plus the branded wordmark when expanded. It is decorative: non-focusable,
  non-interactive, never first focus, outside Back. The wordmark announces the
  app name once and reveals/hides with the same transitions as item labels.
- Destinations (Channels, Guide, Recordings) are centred between the top section
  and the bottom section; Settings is the bottom section. Nothing in the rail
  moves when it expands: rows keep their positions and icons and the brand mark
  stay on the kit's fixed icon column (12 + 16dp; the library's 4dp leading-slot
  growth is cancelled). Only the sheet widens and labels/wordmark reveal.
- A collapsed drawer dims its **unselected** destinations and the brand mark;
  the **selected** destination keeps its full content and container so the rail
  still reports where you are. Expanding restores full emphasis. This is TV
  Material's own behaviour: `NavigationDrawerScope.hasFocus` means
  "drawer is open", and a closed drawer resolves items to
  `inactiveContentColor` (`onSurface` @ 0.4) while passing the selected pair
  through untouched.
  One deviation is required to make it visible. `ListItem` publishes its leading
  slot as `LocalContentColor.current.copy(alpha = 0.8f)`, and `copy` *replaces*
  alpha instead of scaling it, so an alpha-based inactive colour reaches an
  icon-only rail as `onSurface` @ 0.8 — pixel-identical to the active state.
  The drawer therefore composites the library's own inactive colour against the
  surface, preserving the 0.4 ratio in a form the fixed slot alpha cannot erase.
  No app-owned alpha constant and no whole-rail overlay: focus, press, selected
  container and every other colour stay with the library.
- Items are library primitives: 48dp one-line, pill, 24dp icons, with that
  shared `NavigationDrawerItemDefaults.colors()` configuration.
- Drawer entry focuses the current destination. Focus preview changes the
  destination but does not form a Back stack; the previewed screen's saved
  state is restored on return.

## 6. Focus, keys, Back, accessibility

- Every focusable container declares its entry target (`focusRestorer` or an
  explicit initial focus): first entry lands on the active/selected item;
  re-entry returns to the item that last held focus; a missing item falls back
  to a deterministic neighbour. Route-keyed effects are not an entry contract.
- While the drawer owns focus, a newly composed destination defers its
  automatic initial focus until focus leaves the drawer.
- A key that reveals, replaces or relocates UI is consumed for its whole
  KeyDown/KeyUp cycle so the same press cannot activate the new target.
- Back unwinds local layers first (dialogs, sheets, depth stacks, scope tabs),
  then focuses the global drawer on the current destination. From a non-root
  destination the next Back goes to Channels; Back from Channels follows the
  warm-player / activity-exit policy.
- Long localized text and 1.3× font scale must not clip or move anchors; every
  actionable element has an accessible name; status changes use polite live
  regions; loading, empty, error and recovery states are explicit.
- Static captures prove composition only. Motion, focus feel, readability over
  video, overscan and remote-repeat behaviour are physical-TV gates.

### Page motion

The player shares one linear master progress: Down 500 ms, Up/Back 400 ms,
reversing the same per-element windows continuously. Sections stay at their rest
positions; there is no full-viewport pull. Controls rise 96dp and fade (0–220 ms;
Program info fades by 140 ms). Details tabs rise
120dp over 120–420 ms; the whole details or schedule body rises 160dp over
150–480 ms, without column or row staggering. Exits use emphasized
acceleration, entrances emphasized deceleration. The clock stays pinned above
the dim; details clear it by at least 8dp. Tabs retain their horizontal 300 ms step.

The player keeps its Program info cue. The rail preview rises 120dp (0–200 ms);
tiles leave above the viewport (60–360 ms), fading by 260 ms. Its real schedule
headline alone peeks at 55% alpha on the rail keyline, then rises below the top
band (60–360 ms, emphasized (0.3,0,0,1)), no faster than the tiles departing above
it. The centered up affordance fades in over 300–500 ms (standard deceleration).
Scrims are stationary: constant gentle top gradient, details dim in 0–300 ms
(standard deceleration; linear crossfade from the rail veil), controls bottom
gradient out 200–350 ms linearly. No gradient edge travels with content.

Only the section at rest accepts focus. Departing focus and semantics drop
immediately, then details focus its first action (schedule: Now row), or the
invoking control/rail channel is restored after return. The rail stays expanded
and does not replay its sideways reveal. The departing details retain their
channel/event until return settles, then dispose; section 1 stays composed.
Opening side panels, dialogs, auto-hide and zap reveals retain their own motion.

## 7. Settings • sliding-depth navigation (Variant C)

Accepted 2026-09-15; static design in Penpot page
`411cd6b7-a446-8042-8008-a3866b9562cf` (contract board
`622ff396-eb20-80fd-8008-a3b20fa5b114`).

- One active list column; the next level is a read-only **preview** to its
  right. Entering a level slides the child into the active slot and moves the
  parent off to the left; only its trailing edge stays beside the rail, dimmed
  to 0.25 (below the preview's 0.6) so its folder arrows do not read as rail
  marks (accepted 2026-10-23). The container supports arbitrary depth and is
  reused by Archive folder browsing (§9).
- Shared-grid geometry on the 80dp shell: active column x130, width340; preview
  x562 (92dp gap), 432dp step. Playback and the warm scrim stay stationary; only
  columns move.
- Motion and emphasis follow AOSP TvSettings' two-panel transition: preview
  opacity 0.6; the slide is a long decelerating tween (1000ms, cubic-bezier
  0.18, 1, 0.22, 1); the column entering the active slot brightens 0.6 → 1 over
  200ms while the outgoing one dims. Headings are identical in the active and
  preview slots (no back chevron), so a column changing role never shifts text.
  Settled sibling preview switches crossfade different siblings overlapping at the
  preview slot (AnimatedContent fadeIn/fadeOut over config_longAnimTime, enter
  0.12,1,0.40,1 / exit 0.40,1,0.12,1, separate from the slide); push/pop never
  draws the same level twice.
- Level 1 uses standard TV list rows with meaningful icons; deeper levels use
  icons only when meaningful; second lines carry values/status, never
  descriptions of a submenu. Section headings group rows. Every row that opens
  a level ends in a chevron, with or without a leading icon, as Recordings
  folders do (revised 2026-10-23).
- Up/Down browse; Right/OK enter a level; Left/Back return one level and restore
  the parent's item and viewport. Holding Left repeats that one level per key
  repeat, as AOSP TvSettings does (held Left at the root goes on to the drawer);
  a held Right enters one level only, because on the TV its repeats outran the
  new level and escaped to the tabs (revised 2026-10-23). A held Back acts once,
  and a held Left that leaves an editor stops there. Focus alone never commits a setting. Switches
  act in place; choice lists (e.g. App language) are a level, not a dialog, and
  OK on a choice applies it and returns.
- Entering a level again opens it on the row and viewport it was left on, as
  Kodi returns to a folder's last selected item, and its preview shows that same
  place. A row that is gone gives way to its nearest neighbour. The 64 most
  recently left levels are remembered while the app runs, across switches to
  other screens, but not over a restart. Choice levels always open on their current choice (accepted 2026-10-24).
- **Connection** enters a normal overview (Status, Server, HTSP port, `Edit
  connection` with an edit icon). The edit action opens the existing secure
  editor as a dedicated flow; no inline text inputs, per-field rows or
  credentials in lists or previews. The overview and editor exist
  (`ui/screens/settings/SettingsConnectionOverview.kt`,
  `SettingsConnection.kt`); their visual presentation is still to be specified here.
- Cache clearing is one row action (title, size line, trailing delete icon);
  outcomes surface through the global snackbar, not inline paragraphs.

## 8. Global snackbar

Transient feedback has one owner: typed `Notice` and `NoticeCenter` in `:client`;
`NoticeFormatter` and `AppNoticeHost` in `:app` own copy, icons and presentation.
Post notices, not Toasts, local snackbars or inline action-result labels. Persistent
status stays on its owning surface; a blocking failure uses `TvRecoveryOverlay`.
`TransientFeedback` static checks enforce the presentation/construction boundary
and reject raw DVR subscription-error names (use their shared localized label).

One passive host in the app shell, above ordinary destinations including playback;
independent dialog windows use the same center and deadline. **Bottom centre**, 28dp
from the bottom, content-sized up to 556dp (eight kit columns), 44dp one-line or
64dp two-line minimum. Kit appearance: `inverseSurface`/`inverseOnSurface`, 12dp
corners, no border, and the kit's dark/4 shadow (black: 15% opacity, 10dp blur,
4dp spread, 6dp down; then 30% opacity, 3dp blur, 2dp down). `labelLarge`,
16dp start/24dp end and 12dp vertical padding; optional 32dp
icon badge with a 16dp icon and 8dp gap. Details are ellipsized (two detail lines at
large font scale); the merged polite announcement retains the complete text.
No focus target, action or key interception. Notices keep the same fixed bottom
anchor when player controls appear, resize or disappear; they may temporarily
overlap the footer. Dialog-window hosts use the same fixed anchor.

FIFO with per-key pending replacement, at most eight pending entries and a 30s
pending lifetime. INFO displays for 4s, FAILURE for 6s, extended by accessibility
timeouts. Display waits for the resumed, focused window with no IME. Notices
survive navigation with one identity and deadline; context changes invalidate them.
Expiry never alters domain state or removes durable recovery content.

DVR successes come only from SDK server changes, including this client's actions;
command failures post a separate failure notice. Same-rule series scheduling groups
distinct entries within 2s. Individual DVR notices include the supplied subtitle
after the title when nonblank and distinct; grouped notices omit episode subtitles.
External removal of an archive recording is silent;
removing a scheduled entry announces cancellation. Connection loss is debounced 3s
after a Ready baseline, restoration only follows a posted loss, and login rejection
only follows Ready in the same profile generation. Startup and profile changes are
silent. Cache clearing and background-playback outcomes use the same center;
tuner loss and interruption are failures, limit expiry is informational.

## 9. Lists, cards, progress

- Rows and cards are TV Material `ListItem`/`Card` with library indication.
  The playing-item marker, shared by live channels/programmes and recordings on
  every surface (including context menus and the Guide headline), is three small animated sound bars in the existing
  neutral content colour and icon slot, with its existing accessible description.
  Animation is read in the draw phase, not row composition, and stays still when
  the system animator duration scale is zero. Pause and tuning keep their own marks.
  Recording and playing markers are separate data states shown by badge/icon,
  never by overloading focus or selection styling.
  In context menus and on details pages it is the icon of Continue watching, without
  changing the action or button colour; menus draw no separate mark beside the title.
  Recording list rows show it independently of selection.
- Passive on-air progress on cards and rows is cyan and has no terminal marker
  that implies completion; watched progress (resume position) is orange, per the
  progress-colour rule in §2. The interactive playback timeline is a separate
  component with orange playback position.
- Unknown timing is shown as unknown; never interpolate across unrelated
  coordinates or imply seekability that is not verified.
- **Browse list edges** (accepted 2026-10-23, after the TV Design Kit's Cards
  example). The Channels list, Recordings Archive (active, preview and parent
  columns), Schedule and Problems lists and Settings depth lists run to the
  screen bottom. While rows continue below, the list fades out over its bottom
  140dp; while rows have scrolled away above, a 48dp top fade appears. Focus
  never settles in either fade: the TV pivot works inside the band between them,
  with 8dp breathing room for the focus scale. At the first-row boundary the
  list holds still for a pivot move shorter than the extra top room scrolling
  would reserve, so the third row does not nudge the list under the top fade.
  `ui/components/ListEdgeFade.kt` owns the sizes, mask and scroll policy. The
  Guide keeps its own fades; modal side panels keep theirs (§10).

### Channels • layouts, headline and backdrop

Visual baseline accepted for implementation 2026-10-04 after the coordinated
browse concept review, revised in the U4 polish rounds through 2026-10-21. View
options offer three layouts, in this order: **Immersive** (the default), **List**
and **Grid**. Immersive and Grid each keep their own **Program art** switch, off
by default; off, cards and backdrops use channel logos instead of programme art.
List always shows programme art (its pane picture is the point), so it has no
switch (owner, 2026-10-25). The switch sits below a divider under its own
heading, "Immersive options" or "Grid options", since it applies to that layout
only.

- The header draws no title: the drawer already says where you are, and the
  destination's name ("Channels", "Guide") is the row's pane title for
  TalkBack (accepted 2026-10-24). Its Tags and View options buttons end on the
  trailing inset, 20dp apart, on the header row at y28 of the 960×540 reference
  canvas. Content begins at y80. At larger text the header grows from its
  actual text measurements. Header actions appear only when backed by working,
  authorized behavior.
- Every browse header icon button rests on a 40dp disc of `surfaceVariant` at
  40%; the Guide's date pill shares that fill. Focus keeps TV Material's focused
  container, content and scale. Tags always names its set: "All channels" on
  the resting disc, any other set in a `secondaryContainer` pill. Filter is an
  icon until a genre is chosen, then the same pill with the genre. Pills show
  the icon and value (cut at 160dp), and TalkBack reads the name with the value
  ("Tags, Entertainment"). The header has no scrim of its own.
- Tags opens the channel sets: the app's sets, a divider, then the server's
  tags. It shows only with a choice of sets, the same on Channels and Guide.
  Panels open on the checked entry; OK applies and closes, Back closes, and
  either returns focus to the button that opened the panel. A panel list fades
  only at its top; its bottom edge cuts the next row at full contrast.
- Live video remains behind the content under the shell-owned warm-playback
  scrim in §4. There is no duplicate screen-local video scrim or rail/list seam.
- The list's leading axis includes the shell's 24dp inset and a further 26dp,
  with its 12dp focus reserve inside that additional inset. Standard rows are
  340dp wide, with a 92dp visual gap to the passive headline pane at x562.
  The list follows the browse list edges in §9: it runs to the screen bottom
  behind the 140dp fade, and the 48dp top fade appears only while earlier rows
  exist; returning to the first-row boundary removes it. The header and the headline pane
  are outside these masks. Rows scroll out under the fades; **settled focus never
  enters them** — native scrolling and explicit restoration share the readable
  viewport, including focus-scale breathing room. The TV pivot policy must settle
  at the top boundary without alternating scroll requests or flickering the fade.
- Rows are the library's standard `ListItem`: native padding, height and growth
  at 1.3× fonts, with the logo on a 72×40 charcoal plate in the leading slot
  (4dp corners, aspect preserved, as the player's channel chip), native 16sp
  headline and the library's 12sp `bodySmall` supporting text, as on the channel
  cards, indication and focus scale. Rows are 4dp apart. The
  row is one D-pad target; the title is `[number]  [name]`.
- The supporting line is `HH:MM • programme title` for the **current** programme;
  there is no minutes-left label. Start time keeps its natural width and only
  the title ellipsizes, from real text layout. Unknown EPG shows the no-EPG label
  with no timing and no progress.
- Passive progress is a 2dp strip under the supporting line, spanning the text
  column, identical on focused, unfocused, playing and recording rows.
- Playing and recording markers sit beside the title and appear only when they
  apply; no slot is reserved for an absent state. They are decorative and
  non-focusable, and they shorten only the title.
- **List picture and headline.** The pane beside the list shows the same thing
  in every state, so nothing moves or resizes when playback starts or stops. At
  its top, level with the first row (y80), a 268×151 picture (the kit's
  four-column card; the headline below keeps to its width, so picture and text
  form one column) shows the focused programme's 16:9 artwork without its logo
  plate. Without artwork, it is a logo card's picture: the
  logo on the logo cards' ambient colour from the logo, which here keeps its
  strength to the bottom, since no text sits on it. The headline that Immersive also
  uses hangs 20dp below it, start-aligned and at most 480dp wide. In the details
  page's order: an eyebrow with the 36×20 picon and `[number] [name]` (plus the
  player's REC badge while recording), the title in the kit's 32sp `headlineLarge`
  (two lines; `titleLarge` beside the list, so it doesn't compete with the rows),
  a supplied subtitle in `bodyLarge` (one line; absent subtitles leave no gap),
  the facts line with the age-rating box, the start–end time with a 160dp on-air
  strip, then the synopsis in the kit's 16sp `bodyLarge` (`bodyMedium` in the
  narrow column beside the list). Eyebrow, facts, time and Next are metadata in the
  kit's 12sp `labelMedium`. The list alone ends on a quiet
  `Next • HH:MM title` line, with the scheduled clock when that
  programme is to be recorded. The lines form groups (accepted 2026-10-25): the
  eyebrow 2dp above the title, so it reads as the title's label, and the subtitle
  4dp under it; 12dp to the facts, with the time 4dp under them; 20dp before the
  synopsis and before Next. The Guide and Recordings share this headline.
  The synopsis takes up to three whole lines of the room left above the bottom
  safe inset (y512), and is left out rather than cut when no whole line fits.
  Without EPG the title reads the no-EPG label and the other programme lines are
  omitted.
- **List backdrop.** Without warm playback, List uses the Grid's blurred
  backdrop below: the focused programme's art, or without art the logo colour
  behind a blurred logo. A sharp picture in the corner would
  repeat the pane's picture, so the corner backdrop stays with Immersive. Over
  warm playback there is no backdrop.
- The headline pane is passive and never an additional D-pad target. It
  **crossfades** rather than hard-cutting as the browsed row changes: a 160ms
  dissolve, short enough that fast row-to-row browsing still feels immediate. Each
  state resolves its own EPG, so the outgoing copy keeps the channel it was written
  for. Missing programme metadata does not itself make a channel unwatchable. Do
  not add a persistent OK reminder. While the guide is still on its way, the title
  and time hold skeleton lines rather than claiming no EPG.
- **Immersive.** One row of 196dp compact cards, 20dp apart (the kit gutter),
  rests at the bottom of the screen with its first card on the x130 keyline. The
  row runs under the rail and the opened drawer to the screen edge; cards under
  the opened drawer dim to at most the kit's 50% modal scrim. The same headline
  sits 40dp above the cards, with two synopsis lines and, last, the List pane's
  quiet Next line (trial 2026-10-22). Down stays on the row, Up reaches the header and Left at
  the row's start reaches the rail.
- **Immersive backdrop.** With programme art, the focused programme's
  picture fills the top-end corner from the screen's top edge, as the kit's
  immersive list does, under its cinematic scrim over the programme's ambient
  tint. Without art, the channel's logo colour fills the screen behind a blurred
  copy of the logo, and the 240×135 logo stands at the top end. The backdrop stays
  in place when the drawer opens; only the sharp logo moves with the content.
- **Grid.** Three columns of channel-first cards (`[number] [name]`, then
  `HH:MM • programme`). Cards are 16:9 unless the last visible row would be mostly
  cut off; then they shrink so a further whole row fits, never flatter than 2:1 or
  below the card's text height. The header scrolls away with the rows and returns
  with the first row; the focused row is held below the visible half of the row
  above. The backdrop is the focused programme's blurred art, or with Program art
  off the logo colour behind a blurred logo.
- **Cards.** In Channels, programme-art cards darken towards their text with a
  linear scrim in the kit's colour (80% at the text, 95% at the bottom), and the
  channel logo stands over the art inside a soft dark halo. That scrim is baked
  into the card's bitmap, since live gradients over a dozen pictures were too slow
  on the G10. Logo cards use the logo's ambient colour. The player rail keeps the
  kit's card scrim.
- **Backdrop timing.** Every focus-following backdrop changes as Leanback's does:
  it settles for 300ms, changes at most every 500ms, waits up to 2s for its
  picture and then fades in over the old one in 400ms, so there is no dip to grey.
  Before a session, saved logos give their stored colour and blur from the first
  frame.

### Guide and Recordings • browse cohesion

The shared-grid follow-up extends the 2026-10-04 visual baseline. Archive now
uses the depth model below; destination entry, scope selection, details-first
activation, DVR eligibility, confirmations and current-session guards remain.

- Headings share x130/y32 and scopes y80 on the reference canvas. Use native TV
  Material rows/cards and indication with reserved focus-growth space. Text scale
  changes the available rows/columns, never the native font sizes.
- Guide keeps date/Now/Search together using existing working actions, with
  trailing focus clearance. Its header (pane title "Guide") holds the date pill, Tags
  and Filter 8dp apart, and View options, 20dp between the groups. Filter shows
  once the guide carries genre codes (or while a genre is chosen) and opens
  "Filter by genre": All genres and the genre families, each with its color dot
  while View options' Genre colors is on. The active genre's pill adds that dot.
  The passive headline of the settled programme (View options' Headline) sits
  below the buttons-only header row, bottom-aligned to the ruler gap and at most
  480dp wide. Its fixed height includes room for a subtitle under a one-line title
  without displacing the facts; that extra line scales with the subtitle's text
  style and top padding. The facts row reserves at least 22dp, growing with
  `labelMedium` line height plus the age rating's 4dp padding. The headline
  keeps a 12dp gap above the ruler, with no extra grid spacer; when hidden,
  clearance above the ruler reserves the now pill's upward overflow plus half
  the header button's growth at TV Material's 1.1 focused scale, rounded up to a
  whole pixel. There is no synopsis, and accessibility reads the
  headline after the buttons. With the headline shown, headline, ruler and grid
  drop below the header row by up to half a row, so the row the screen's bottom
  edge cuts shows its title and a third of the rest
  (`ui/screens/guide/GuideHeadline.kt` `guideHeadlineDrop`); hidden, they keep
  their place. A 104dp channel column and an 8dp gap lead into the
  timeline, which extends to the physical edge, with the ruler above the
  programme rows (`ui/screens/guide/GuideViewport.kt` `GuideChannelWidth`, `GuideChannelGap`). Timeline content continues to the physical
  trailing/bottom edges and is simply cut there; only the edge beside the channel
  column (left in LTR, right in RTL) and the top edge under the ruler fade, over a
  local 48dp and only when more content exists there. Headers, controls and scopes remain clear.
  Settled focused cells remain inside the unfaded area; real data/history bounds
  must not imply unavailable overflow. Preserve physical Left earlier/Right later.
- At a terminal physical edge, reserve 8dp breathing room
  rather than letting focus touch the screen edge. This is blank spatial padding,
  not additional time availability. Ruler, cells and Now share its mapping;
  normal cells retain their full duration allocation. Apply it on physical right
  for LTR future bounds and physical left for RTL history bounds. Continuing
  edges retain overflow and the inner fade, and time capacity is not reduced a second time.
- Guide's continuous 2dp current-time marker paints above cells and their focus
  treatment using the same time mapping as event widths and the ruler. Its ruler
  marker stays visible during vertical scrolling; the line follows the local
  content fade (`ui/screens/guide/GuideViewport.kt:78-88`). In the ruler band
  the marker carries a fully rounded primary pill with the current clock time,
  and a time label the pill would touch is hidden rather than half covered. Each ruler time
  label starts at its half-hour's subtle boundary line, which runs the ruler
  band's height where that time's programmes start; the times share one
  baseline centered in the band. The band is `labelSmall` line height plus 4dp
  (20dp at default text size). The pill keeps its 4dp lift above the labels,
  drawing unclipped into the space above the band; its line starts at its bottom.
  Programme cells have no row track:
  they sit on the page like the channel tiles, one 4dp gutter apart across and
  between rows, so a schedule gap shows as nothing on air. A focused cell does
  not grow: it keeps its genre tint (or plain fill) and light text and gains a
  2dp `onSurface` ring in the small shape. A channel row with
  nothing to draw says why in one line, in a passive cell across the row: no guide
  data for the channel, no programmes in the window, partial or stale data,
  reconnecting, permission denied, server failure or an empty filter
  (`ui/screens/guide/EpgGridContent.kt:538-552`).
- Guide rows are dense list items. Regular rows (the default) are 56dp high and
  show the title and start time; Dense rows (View options) are 40dp high and show
  only the title, as the ruler and headline carry the time. Both scale with the
  font scale and grow to fit the channel header and the cell's text
  (`ui/screens/guide/EpgGridContent.kt` `guideTimelineRowHeight`). The header shows the channel
  number (`labelMedium`) beside a picon of at most 48×28dp, or beside the
  single-line `titleSmall` name when there is no picon or it failed to load
  (`:380-420`); never all three. Measure both header and programme typography
  when growing lanes; do not shrink fonts or distort logos.
  The visible time span adapts to measured labels (normally three hours, two
  with enlarged text). This does not change the three-hour acquisition window
  or the existing history/future availability policy.
- Recordings uses native growing rows, with date/duration/status below titles.
  Archive, Schedule and Problems remain separate; Schedule and Problems are flat
  lists with full-row native focus clearance. Reserve trailing layout space so
  the complete focused shape remains at least 48dp from the physical screen edge;
  an unclipped shape alone is not sufficient. Preserve larger caller safe insets.
  There is no Continue watching tab (tried and dropped 2026-10-25: TV apps put
  continuing on a home row, and Recordings is storage, not activity); partly
  watched recordings carry their clock mark in the Archive.
- Archive shares Settings' depth owner, geometry, motion and parent restoration.
  One folder level is active; the right column is an inert preview of the focused
  folder's immediate children, or passive metadata for a recording. It is not a
  recent-descendant shortcut and never receives focus independently. Both preview
  forms are excluded from accessibility traversal as well as D-pad actions.
- OK/Right enters a folder. Left/Back returns to its parent and invoking row with
  the parent viewport retained. OK on a recording opens existing details; Right
  does not activate it. Details/confirmations return to the same recording. At
  root, Back reaches the Archive tab and Left reaches the drawer; Up from the
  first row reaches mode tabs without losing the folder location.
- Keep a 32dp level-heading slot above the rows, growing with text metrics. Root
  leaves it blank; nested levels show their current folder. Do not add a focusable
  breadcrumb or duplicate Up action. Preview and active headings share geometry.
- Removed paths reconcile to a surviving ancestor; valid empty levels retain an
  explicit empty state and usable Back. Refreshes cannot steal focus from tabs,
  the drawer or a dialog. Rendered-visit ownership and permission to request focus
  are separate; departing visits cannot reconcile the shared navigation state.
  Folder identity, recording order and permission/session fences
  come from the existing domain/SDK owners.

## 10. Player

Player chrome owns its gradients. Play/Pause and seek semantics, programme
window, timeshift and scene-marker behaviour follow the playback safety skill;
only the colour and indication rules above apply here. Remote keys follow §6 and
"Key cycles, Back and auto-hide" below.

### Player chrome (revised 2026-09-30)

- The page top band shares the clock's center line: passive now-playing strip
  left at the 58dp grid keyline, up affordance centered, clock right. The strip
  always identifies the watched channel, with a small picon, two-line identity
  and programme text, and programme progress. It stays pinned between rail and
  rail schedule; controls-to-details fades it in over 200–400 ms (reversed on Back).
  Program-details tabs start on the 58dp keyline; only their body keeps the 72dp
  content inset (x=130dp). Rail-schedule heading and rows remain at x=58dp.
- Live TV and recordings share one chrome with four modes: hidden; the passive
  **Banner** (clock, info bar, timeline; nothing focusable); the **Banner step**
  (a quick step's preview inside the Banner, without a thumb); and the
  **controls** (the Banner's layout plus a focusable seekbar with thumb and the
  action row). Banner to controls is layout continuity: the info bar and timeline
  stay, the group rises by one action row and the action row fades in; the bar
  keeps its right end and grows left over the Banner's state glyph with the rise.
  Reveal motion starts after the first frames. Hidden chrome is not kept composed and
  nothing animates while idle.
- Keys follow §6 and TV-PC: Center toggles pause and shows the Banner, which stays
  while paused; where live cannot pause, Center shows the Banner with the reason.
  Left/Right step inside the Banner; Up/Down open the controls; Back hides one
  layer at a time (the Banner too, also while paused) and then closes. Entering
  the player or a zap shows the Banner; it hides 5 s after the tune's first
  presented frame (a positively audio-only service lets its playing state stand in).
- The wall clock stands alone at the top right over its scrim; there are no status
  tags. The info bar holds the identity card and the programme block.
- The identity card is a 16:9 TV Material compact card, 160×90dp for every channel
  and programme. The picon (or the channel name) is centred in it; the channel
  number stands at its bottom start. Behind them is the channel's own colour or,
  when the programme has artwork, that picture dimmed so the picon and the number
  read over any of it.
- The programme block has four lines that keep their places whether they have
  content or not, so nothing moves between programmes. It is bottom-aligned with
  the fixed-size card and may be slightly taller. The lines are a metadata row
  (live: programme times and genre; recordings:
  `Recorded <date> • Ends HH:MM`, or `Recording until HH:MM` while growing), the title,
  the subtitle, and Next. A recording's episode stands under its title
  and its last line stays empty. Nothing in the info bar shows tuning or
  buffering.
- The programme block groups its title and subtitle closely: 4dp from the
  metadata row to the title, 2dp from title to subtitle, and 8dp before Next.
  The live subtitle uses body-medium type; Next and recording episodes use
  label-large type, retaining their medium weight for scanning. Recordings retain the
  same overall block height with the 8dp gap before their empty final line.
- The timeline row is `[state] [start] bar [end]` and nothing else. The label boxes
  have fixed widths (measured once per player kind from tabular-digit templates),
  the end is end-aligned on the content edge, and the bar never moves within a
  layer: not while tuning, stepping, scrubbing, ticking, falling behind live or
  losing EPG.
  - State: the Banner shows a passive play or pause glyph left of the start label.
    The controls have none; the bar grows left into that space as the controls rise.
  - Live with EPG: the bar is the current programme between its start and end clock
    times. The rewindable span and live edge are drawn on the bar; within the 5 s
    live-edge tolerance, or while the server confirms playback at live despite
    decoder latency, the fill meets the edge. Paused playback and previews keep
    their sampled or target position.
  - Distance behind live: at the live edge the chrome draws no status, neither
    `Live` nor a placeholder. Behind live, also while paused, the explicit label
    (`3:23 behind live`, localized) stands in the bar's timeshift colour directly
    above the end clock, flush with its right edge and 8dp above the row, on the
    height of the info bar's last line. It is an overlay: no row of its own and no change to any
    resting bounds. Its fixed box is always reserved, so the last info line (and
    the feedback line) ends before it whether or not it shows. While the step
    readout overlaps it, it is not drawn. The reserved width includes the localized
    wording and an hours-length duration at the current font scale.
  - Live without EPG but with timeshift: the bar is the local-clock half-hour slot
    (`21:00`–`21:30`) with the buffer as its available span and the same distance. The
    slot is never programme information: no title, no Next, no record or details
    target. Without EPG and without timeshift there is no track.
  - Recordings: the start label is the playback position, never a step target; the
    end is the length. Completed recordings have no remaining-time label; their
    information row retains `Ends HH:MM`. A growing recording shows its current
    length and the same `3:23 behind live` annotation above it when behind its
    recording head; the `REC` badge names it. At the head the annotation is hidden.
    Without a known length there is no end or behind-live annotation.
  - A zap shows the new channel's airing programme (bar, title, Next) from the
    channel change until the committed programme window resolves; only a pause or
    a timeshift command ends that. A change of axis snaps, so the fill never slides
    back or drops to zero. While timeshift is available or expected and timing is
    momentarily unknown, the row holds what this live request last presented; with
    nothing held it is an empty track without a distance, never a borrowed
    broadcast axis or a live claim.
- The step readout is an opaque chip at the target. It shows the target (live:
  distance behind live; recordings: position) led by rewind or fast-forward from
  the step's net movement, and `▶ Live` when the target is the live edge. In the
  controls it sits 8dp above the thumb and the info bar fades; in the Banner step
  there is no thumb, it rests on the bar row and the info bar stays.
- There is no Go live control: stepping or scrubbing forward reaches live.
- The action row is Play/Pause and Stop, with Settings at the right end
  (`ui/player/PlayerActionRow.kt:63-67`). There is no Record button and no Info
  button: the identity card opens Info. Controls focus starts on
  Play/Pause. Seekbar: Left/Right scrub, Down to the last focused action, Up to
  the card. Actions: Left/Right along the row without wrapping, Up to the seekbar,
  or to the card where the timeline takes no focus. Center toggles pause on
  Play/Pause and the seekbar. Where live cannot pause (no grant, timeshift off)
  Play/Pause is dimmed but focusable and states the reason in the timeline
  feedback line.
- The card takes focus in the controls only; in the Banner it is a picture. OK
  opens Info and closing Info returns focus to the card, also when the INFO key
  opened it. Down leads to the seekbar, or to Play/Pause where the timeline takes
  no focus; Left, Right and Up lead nowhere. Above a recording's markers, Up from
  the seekbar opens the markers and Up again closes them and moves on to the card.
- Numeric entry is a state of the top identity's place: the identity's chip,
  the typed digits and the destination's name on one line, with the identity's
  start edge, top edge and height at every font scale (the digits share the
  name's style). The chip holds an empty tile while pending or for `No channel`,
  and the match's picon; a logo-less match leaves it empty, its number standing
  once, in the field. The digits stand right-aligned in a field sized for the
  lineup's longest number (at least three digits), starting where the identity's
  text starts; the name follows one space later, one line, ellipsized within the
  identity's text width. A plate grows out of the content into the identity's
  top edge and height (300ms, emphasized decelerate), as wide as the entry's
  content; it never shrinks while the entry is open, and its end grows only when
  the content needs it. The plate is a style that changes only colours: dark
  (shipping; dense dark panel, tile fades from behind a picon), light (the
  identity's focus container with a soft shadow) or none. Each new digit pushes
  the earlier ones start-ward by one digit while it fades in at the end (150ms);
  each digit keeps its own push and fade, so a digit during any of this motion
  carries the others on from where they stand, never restarting the plate.
  Destination changes cross-fade in place. Commit slides the channel's own number
  (`01` settles as `1`) and the name into the identity's rest alignment while the
  plate recedes (200ms); cancel fades the content where it stands while the plate
  recedes. The entry is announced politely once per change
  (`12, Harbor Sport HD`, `999, No channel`), not per frame and not while it
  leaves. Entry keeps the
  chrome it interrupted: controls or Banner stay up, the identity fades out under
  the entry, is not read out meanwhile, and fades back in place when the entry
  leaves, without the chrome travelling. The root takes the keys during entry,
  and focus then returns to the control that had it. Entry never reveals hidden
  chrome; the entry then stands alone at the same spot. Tuning never waits for
  animation. An unknown number shows `No channel` for two seconds before
  dismissal; it has no shake, flash or success-style confirmation.
- Tuning and buffering show as one thin ring centred over the video, without a
  panel, scrim or text: 44dp, plain white 3dp stroke with no outline, 180 ms fade. It
  appears after 500 ms of an owned tune that has not presented and after 1 s of
  continuous buffering, identically in every chrome layer, and layer changes do
  not restart the delays. It is not focusable, moves nothing, is not composed while
  idle and yields to the centre message of unavailable channels and failed tunes
  and to the recovery presentation. A paused player with hidden chrome keeps a
  small chip at the bottom left (`❚❚ −3:23`, `❚❚ Live` at the live edge).
- Accessibility: the ring announces `Tuning` or `Buffering` once per start as a
  polite live region; playback state and the live state are polite live regions
  that announce each change (paused, playing, live, behind live) once, never the
  ticking number, and the live state is announced at the live edge although nothing
  is drawn there. Each clock is spoken once per layer. Progress semantics sit on
  the track and exist only in the controls.
- Scrims cover only what the chrome needs. The bottom scrim belongs to the footer:
  clear 56dp above its first line, 0.60 black at that line, 0.80 52dp further
  down, 0.92 at the bottom edge; it rises with the footer when the controls open.
  The top fade behind the clock is 112dp (0.64 at the edge, 0.40 at 56dp). The
  quick-zap tray keeps its own bottom-anchored scrim.
- Action controls are 48dp. The reveal alpha is part of the focus-emphasis layer,
  whose expanded bounds keep native focus growth uncropped mid-animation.
- Channels rows and quick-zap cards share neutral content-colour play/pause
  markers; an indeterminate spinner occupies the same slot during an owned tune.

### Key cycles, Back and auto-hide

- The Compose key dispatcher owns every player key. The `PlayerView` runs with its
  controller off and is neither focusable nor clickable
  (`ui/player/PlayerVideoSurface.kt:84-89`).
- A key that reveals the controls, peeks the Banner, opens the channel drawer,
  Info or the playback options begins an opening cycle for its key code. The
  first non-repeat KeyDown acts once; repeats and the matching KeyUp are
  consumed, so they can neither act again nor move focus inside the layer that
  just appeared (`core/PlaybackKeyPolicy.kt:103-125`,
  `ui/player/LivePlayerLayerState.kt:293-299`). Stop with no active target
  uses the same suppression (`ui/player/PlayerStop.kt:71-86`).
- Hidden-chrome keys (`core/PlaybackKeyPolicy.kt:191-277`): Center, Enter and
  Numpad Enter toggle Play/Pause and reveal the controls on recordings and on
  live with Pause available; where live cannot pause they peek the Banner with
  the reason. Up and Down reveal the controls without changing playback.
  Left/Right step on recordings and on live with timeshift, and pass through on
  live without it. Info opens Info; TV Contents Menu, TV Number Entry and
  Bookmark open the live channel shelf and pass through on recordings.
- Back acts on the topmost foreground layer, in this order
  (`core/PlayerBackPolicy.kt:60-79`, actions `:99-122`): confirmation, Info,
  the Audio/Subtitles quick list (restores the earlier selection and closes),
  options detail (returns to the options root), options root, numeric entry
  (clears it), channel drawer, recovery, terminal error, controls, pending seek
  preview, dispatched seek preview, Stats. With no layer, Back hides the Banner
  when it is visible and otherwise closes the player to warm browse. A pending
  step cancels; a dispatched one only dismisses its feedback and claims no
  undo. Recovery and terminal-error Back close the player at once. Back is not
  Stop.
- Controls auto-hide only when they are visible, playback is playing and ready,
  and the player is stable (`core/PlayerAutoHidePolicy.kt:13-32`). A pending
  seek, a modal layer, recovery and an actionable error each prevent it. The
  delay is 5 s (`ui/player/LivePlayerLayerState.kt:31`,
  `RecordingPlayerScreen.kt:94`), extended to at least 30 s while focus rests on
  the identity card or its recent row (`core/PlayerAutoHidePolicy.kt:19-23`). Auto-hide never reveals anything.

### Retained programme history (revised 2026-09-22)

- On app start or entry from another destination, Guide opens near Now on the playing channel,
  or the last channel focused in Guide when playback is absent or outside the active
  group. With neither available in the group, it falls back to last played, selected,
  then the first channel. It keeps the group and genre filter, not the remembered time.
  Changing groups within Guide likewise prefers the last focused Guide channel over
  last played when playback is absent or outside the new group. Rail trips within
  the same destination and closing panels or details keep the exact spot. Live-player
  Back keeps its return target; recording-player Back restores the programme, time
  window and channel viewport left in Guide. Guide permits navigation into the preceding six hours of
  retained programme metadata. Left/Right navigate programmes; Up/Down preserve
  the time anchor; Now returns to the current schedule.
- Past programmes use SDK-owned display history. No historical coverage request
  or fabricated event fills missing data. History accumulates from received EPG;
  a cold start or new session may have none.
- Timeshift programme lookup includes that history, so an expired live-schedule
  event can still label buffered playback. Programme metadata alone does not
  establish that video remains rewindable.
- Historical-only events cannot authorize programme recording or live watch
  actions. An independently available DVR entry may still provide recording
  playback. Pending recording confirmation/configuration is revalidated against
  current live metadata, and old-session details close when ownership changes.

### Quick-zap cards (accepted 2026-09-21, rail revised 2026-10-10)

- Use the compact channel-card anatomy: 196×110dp at default font scale, a
  centered picon on a flat surface without the kit's text scrim, the current
  programme title (with the playing and recording markers) as the main line,
  `<number> <name> • Nm left` below it, and a 2dp cyan progress strip. Use TV
  Material `CompactCard` indication; card height grows with localized text scale.
- The rail follows the kit's immersive list: focus stays on the first slot and a
  preview of the focused card stands above the rows, its text on the start
  keyline and the programme artwork trailing on the text's last line. Single
  Left/Right steps crossfade the preview; while Left/Right is held it is hidden and
  returns 250 ms after release. Reopening previews the remembered card from the
  first frame.
- Two rows (the current implementation, `ui/player/PlayerChannelBrowser.kt:82-111`,
  and still a trial against an in-place rail; roadmap U1 decides): the first is
  titled with the active channel group (or All channels),
  the second is Recent channels and is absent while empty. The quiet row's title
  shrinks to title-small at the passive alpha and its cards dim to 40%; Down and
  Up change rows over 300 ms and each row keeps its card and viewport. Up from the
  first row returns to the controls. The top band's centred up hint names what
  Up leads to: Back to TV on the first row, the first row's title on Recent.
- Short OK tunes; holding OK opens the programme details as a full-screen
  two-column dialog that fades in and out, without tabs or the up hint, and only
  Back closes it.
- While player controls are visible, the upper 24dp of the cards peeks above the
  bottom screen edge. The peek is passive; Down opens the tray. First entry
  focuses the playing channel (or a deterministic available fallback).
- Re-entry restores the last browsed card and persistent viewport, keeping a
  middle/right card in its screen position rather than snapping it left. Scroll
  only enough to bring an offscreen target into the safe area. An external channel
  change while closed reanchors to the confirmed playing channel; a delayed
  confirmation of the rail's own pick does not erase subsequent browse focus.
  Temporary loss of playback confirmation preserves both browse and local-pick
  identity. Missing anchors fall back to playing, selected, then first available
  channel; focus alone never tunes.
- Opening slides the cards into the bottom third while player chrome slides up
  and fades away in the same transition. Closing reverses it. Invisible chrome
  and peeking cards cannot own focus or accessibility actions.
- The row viewport spans the screen width. Horizontal safe insets are **content
  padding**, not a clipping container: neighboring cards remain partially visible
  at either screen edge, while focused cards stay inside the safe area with room
  for native focus scale.
- Selecting another channel keeps the tray, focused card and scroll position.
  Back, Up from the first row, or selecting the confirmed playing channel returns to player controls
  and restores the invoking action. The tray does not auto-hide during zapping.
- A confirmed external change while the tray is open (a completed numeric tune)
  never moves browse focus; it reanchors the tray when it closes. Digits,
  unconfirmed tunes and re-confirmation of the same channel do not reanchor.
- Back from a live player opened from Channels focuses the playing channel's row
  (after CH+/CH-, numeric or tray changes), skipping the first-entry scope tabs.
  Back to the Guide focuses the programme airing now on the playing channel, in
  a window at the current hour, not the programme used to open playback; a
  channel without current EPG uses the guide's nearest-row entry rule. Neither
  changes the selected group: outside it, Channels falls back to the row that
  opened the player, then the first row, and the Guide keeps its prior position.

### Modal side panel (accepted 2026-09-25)

- Player options and programme info float as one panel:
  320dp wide (programme info 400dp), inset 24dp from the end, top and bottom
  edges, 16dp corners, 20dp padding and a level-3 shadow, over a 60% black
  scrim.
- A scrolling list fades out over its bottom 128dp only while it can scroll
  forward; the focused row stays above the fade.
- The Audio and Subtitle short lists: focus switches the track after 300 ms,
  OK keeps it and closes, Back reverts, and the list closes 5 s after the last
  key and keeps the track. Menu opens the full panel.

### Context menus and dialogs (accepted 2026-10-15)

- **Context menu** (hold OK or Menu on any card): full-screen, opaque app
  background, rail hidden. Kit Two Column / JetFit more-options layout: item
  card 268dp left, 268dp WideButton column right, columns top-aligned, the
  container centred. Channel and programme menus share one order on every
  surface (Channels, Guide, airings): Watch, Details, the recording action,
  Show in Guide, so a held OK always lands on the same action. An ended
  programme whose recording the SDK admits for playback adds Play recording
  right after Watch, as on its details page. On the watched channel, the
  programme at the viewer's position, and while behind live also the one on
  air, offer Continue watching in Watch's place, with Watch live right after
  it while behind live; a playing recording's menu offers Continue watching in
  place of Watch, Resume or Play, and never Watch live. An action that
  needs a confirmation closes the menu for good; Keep, Cancel and Back return focus to the originating card. Details
  opened from a menu return to the card on Back, never to the menu.
- **Prompts and confirmations** (resume or start over; cancel, stop or delete a
  recording): kit Standard card centred over a 60% black scrim, drawn in the
  window so the screen beneath stays visible. Text only, buttons in one row.
  Destructive confirmations focus the safe choice; Back equals it.
- **Record** acts immediately, without a confirmation; the recording profile
  comes from Settings → Recordings.
- Programme and recording details use the same actions whether opened from
  Channels, Guide, Recordings or the player. Availability depends on the item,
  server permissions and current playback, not the entry screen. The playing
  item offers Continue watching in place of Watch or Resume; this returns to
  the existing player without a restart, seek or resume prompt. Other actions,
  including Play recording and Show in Guide, remain available when applicable.
- A recording has one details page for its synopsis, watched progress and
  actions, including during playback. Recorded date metadata uses the current
  locale in both player bars; the top bar's channel logo has no synthetic chip
  background or frame.
- Option lists inside the player stay in the modal side panel above.

## 11. Icons

Material Symbols, Outlined family, imported as vector drawables. Icons carry
meaning or are omitted; decorative icons have `contentDescription = null`.
Directional icons mirror in RTL.

## 12. Open items

Not decided; do not treat existing code as the decision:

- Panel opacity tiers (`TvPanel*Alpha`) versus Material roles.
- Player action-row idle colours.
- Card grid widths and aspect ratios.
- Universal single-line headline rule.
- Connection editor presentation.
