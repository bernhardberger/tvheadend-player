# TVHeadend Player · TV design contract

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
| recording red | `#FF5449` | REC state only; distinct from `error` |

Surface ladder (roles TV Material lacks): lowest `#0C0F10`, low `#191C1E`,
container `#1D2022`, high `#282A2C`, highest `#323537`, bright `#37393B`.

Meaning of colour:

- **Cyan** leads: selected/active navigation, ambient progress, selective emphasis.
- **Orange** is the contrasting accent: playback position and deliberate
  secondary emphasis. Not restricted to the seekbar, but sparse enough that the
  UI stays cyan-led. Ordinary panels, cards and buttons stay neutral.
- **Recording red** means "a recording exists or runs". **`error`** means "a
  failure the user must act on". No component declares its own red.
- Brand artwork keeps the exact brand cyan `#00BCFA` and orange `#FA7F00`.
- State is never colour alone: shape, label or icon reinforce it.

Typography is the TV Material scale with Roboto; no custom scale. Logos are
drawn artwork and do not follow font scale.

## 3. Components and indication

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

Page-level scopes (Channels, Guide, Recordings) use `TabRow` with the default
`PillIndicator`. Content slides in the tab's direction, honours RTL order, and
rapid changes interrupt toward the latest target. Headers stay stationary.

## 4. Shell, safe area and background

- Logical canvas 960×540 (1920×1080 at 2×). Safe inset 48dp horizontal, 32dp
  vertical from the screen edge; the shell owns insets and passes them down.
  Screens do not add their own safe-area padding.
- One `SideRail` hosts the standard **push** drawer; content translates and
  clips at its trailing edge, it does not reflow.
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
  the shared sidebar scene and scope-tab transitions do not clip at the drawer
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

## 7. Settings · sliding-depth navigation (Variant C)

Accepted 2026-09-15; static design in Penpot page
`411cd6b7-a446-8042-8008-a3866b9562cf` (contract board
`622ff396-eb20-80fd-8008-a3b20fa5b114`).

- One active list column; the next level is a read-only **preview** to its
  right. Entering a level slides the child into the active slot and moves the
  parent fully off-screen. The container supports arbitrary depth and is
  reusable (recording folders are a candidate consumer; not designed yet).
- Geometry on the 80dp shell: active column x128, width 352; preview x588
  (108dp gap), 460dp step. Playback and the warm scrim stay stationary; only
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
  descriptions of a submenu. Section headings group rows.
- Up/Down browse; Right/OK enter a level; Left/Back return one level and restore
  the parent's item and viewport. Focus alone never commits a setting. Switches
  act in place; choice lists (e.g. App language) are a level, not a dialog, and
  OK on a choice applies it and returns.
- **Connection** enters a normal overview (Status, Server, HTSP port, `Edit
  connection` with an edit icon). The edit action opens the existing secure
  editor as a dedicated flow; no inline text inputs, per-field rows or
  credentials in lists or previews. Its presentation is pending design.
- Cache clearing is one row action (title, size line, trailing delete icon);
  outcomes surface through the global snackbar, not inline paragraphs.

## 8. Global snackbar

One passive host in the app shell, above ordinary destinations including
playback. **Bottom centre**, inside the safe margins, content-sized up to 324dp
with natural wrapping. Kit appearance: `inverseSurface`/`inverseOnSurface`,
12dp corners, `labelLarge`, 16/12dp padding. No focus target, action, key
interception, relocation or collision avoidance; it may overlap content. Notices
survive navigation with one identity and deadline; expiry never alters domain
state or removes durable recovery content. Cache clearing is the first producer.

## 9. Lists, cards, progress

- Rows and cards are TV Material `ListItem`/`Card` with library indication.
  Recording and playing markers are separate data states shown by badge/icon,
  never by overloading focus or selection styling.
- Passive progress on cards and rows is cyan and has no terminal marker that
  implies completion. The interactive playback timeline is a separate component
  with orange playback position.
- Unknown timing is shown as unknown; never interpolate across unrelated
  coordinates or imply seekability that is not verified.

### Channels · standard immersive browse

Revised by operator acceptance 2026-09-19 after G10 viewing: standard rows replace
the dense version; the implemented picon/content/marker arrangement is retained
rather than restoring the earlier Penpot anatomy. Static design in Penpot page
`411cd6b7-a446-8042-8008-a3866b9562cf`, board C3c
`baab38e6-8768-8070-8008-a9c62d36a24c`. C3b
`baab38e6-8768-8070-8008-a9c12c3a31a7` is the superseded dense variant.

- Channels is **immersive**: no destination headline, live video visible behind
  the content, and readability comes from the shell-owned warm-playback scrim in
  §4. There is no duplicate content-local scrim and no rail/list boundary seam.
- Scope tabs sit at the list's leading axis (x116 on the 960×540 canvas: the
   shell's 24dp inset plus the list's 12dp focus reserve) at y32. The standard list
  starts at y80, is 340dp wide, and runs to the bottom of the screen behind a
  48dp fade. Rows scroll out under the fade; **focus never enters it** — the
  list's bring-into-view policy shortens the viewport by the fade plus breathing
  room so a focused row is never clipped by it or by its focus scale.
- Rows are the library's standard `ListItem`: native padding, height and growth
   at 1.3× fonts, with a 60×36 bare picon in the leading slot (aspect preserved,
   no logo box or background), native 16sp headline and Material 14sp `bodyMedium` supporting text,
   indication and focus scale. Rows are 4dp apart. The
  row is one D-pad target; the title is `[number]  [name]`.
- The supporting line is `HH:MM · programme title` for the **current** programme;
   there is no minutes-left label. Start time keeps its natural width and only
   the title ellipsizes, from real text layout. Unknown EPG shows the no-EPG label
  with no timing and no progress.
- Passive progress is a 2dp strip under the supporting line, spanning the text
  column, identical on focused, unfocused, playing and recording rows.
- Playing and recording markers sit beside the title and appear only when they
  apply; no slot is reserved for an absent state. They are decorative and
  non-focusable, and they shorten only the title.
- Focused-programme detail is immersive text, not a panel: bottom-anchored at
  x492, 420dp wide, with channel label, title, timing and metadata, a 4dp
   progress strip, a description of up to five actual lines and the next programme.
   The block grows upward above the next line, including at 1.3× font scale;
   bounded text cannot overlap. It holds no focus target and no
  artwork; there is no poster or fanart API in this composition.
- Because the block holds no focus, it **crossfades** rather than hard-cutting as
  the browsed row changes: a 160ms dissolve, short enough that fast row-to-row
  browsing still feels immediate. Each state resolves its own EPG, so the
  outgoing copy keeps the channel it was written for. Height differs between
  programmes, so the size snaps and the taller copy is left unclipped instead of
  animating text baselines against the bottom anchor.

## 10. Player

Player chrome owns its gradients. Play/Pause and seek semantics, programme
window, timeshift and scene-marker behaviour are specified in
`docs/player-ui-ux-overhaul-plan.md` and the playback safety skill; only the
colour and indication rules above apply here. Remote keys follow §6.

### Player chrome (revised 2026-09-30)

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
  `Recorded <date> · Ends HH:MM`, or `Recording until HH:MM` while growing) with a
  red `REC` badge while the channel or recording records and the stream badges,
  the title, the subtitle, and Next. A recording's episode stands under its title
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
- The action row is Play/Pause and Stop, then Record and Settings at the right.
  There is no Info button: the identity card opens Info. Controls focus starts on
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
- Numeric entry is a compact, single-line badge at the top left (56dp start,
  48dp top). Digits are centred within a slot sized for at least three digits;
  a known destination extends the badge with an optional picon and one-line name.
  Pending input shows only the digit slot. The whole badge fades in and out over
  100ms, without travel or scale. Destination-width changes settle over 150ms,
  anchored at the start edge with fixed height; new digits and labels update
  immediately, including during an interrupted transition. Cancel and commit use
  the same exit, retaining the last complete badge while it fades. Tuning never
  waits for animation. An unknown number shows `No channel` for two seconds before
  dismissal; it has no shake, flash or success-style confirmation.
- Tuning and buffering show as one thin ring centred over the video, without a
  panel, scrim or text: 44dp, 3dp stroke with a fine dark outline, 180 ms fade. It
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

### Retained programme history (revised 2026-09-22)

- Guide opens near Now and permits navigation into the preceding six hours of
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

### Quick-zap cards (accepted 2026-09-21)

- Use the Penpot compact channel-card anatomy: 196×110dp at default font scale,
  centered picon above channel identity, one current-programme line with start
  time and minutes remaining, and a 2dp cyan progress strip. Use TV Material
  `CompactCard` indication; card height grows with localized text scale.
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
  Back, Up, or selecting the confirmed playing channel returns to player controls
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

- Player options, recording options and programme info float as one panel:
  320dp wide (programme info 400dp), inset 24dp from the end, top and bottom
  edges, 16dp corners, 20dp padding and a level-3 shadow, over a 60% black
  scrim.
- A scrolling list fades out over its bottom 128dp only while it can scroll
  forward; the focused row stays above the fade.
- The Audio and Subtitle short lists: focus switches the track after 300 ms,
  OK keeps it and closes, Back reverts, and the list closes 5 s after the last
  key and keeps the track. Menu opens the full panel.

## 11. Icons

Material Symbols, Outlined family, imported as vector drawables. Icons carry
meaning or are omitted; decorative icons have `contentDescription = null`.
Directional icons mirror in RTL.

## 12. Open items

Not decided; do not treat existing code as the decision:

- Text/panel opacity tiers (`TvText*Alpha`, `TvPanel*Alpha`) versus Material
  roles.
- Player action-row idle colours.
- Card grid widths and aspect ratios.
- Universal single-line headline rule.
- Connection editor presentation.
- Recordings folder navigation (candidate for the depth container).
