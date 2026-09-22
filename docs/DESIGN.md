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

## 10. Player

Player chrome owns its gradients. Play/Pause and seek semantics, programme
window, timeshift and scene-marker behaviour are specified in
`docs/player-ui-ux-overhaul-plan.md` and the playback safety skill; only the
colour and indication rules above apply here. Remote keys follow §6.

### Shared compact player footer (revised 2026-09-22)

- Live TV and recordings use the same timeline and seek-preview layout. Endpoint
  labels sit inline on either side of the track; their values retain the domain's
  clock-time or elapsed/duration meaning. Recording scene-marker targets use the
  actual inset track coordinates. Pause is announced accessibly, never prefixed
  to a visible time endpoint; live fallback offsets remain numeric. Without EPG,
  endpoints describe the displayed buffer axis (oldest offset to `0:00`), while
  the floating seek label describes the selected position on that same axis.
  Server Live status must not override the sampled track fill.
- One compact, right-aligned passive status row sits beneath the wall clock,
  with subtle muted dark filled tags (tight padding, small corners, no outline or
  focus). Blue identifies “Live”; amber identifies explicit localized behind-live
  time (for example “3m 23s behind live”). A 14dp play/pause icon follows playback
  intent; durations use localized units.
  Status reflects committed playback, not a seek preview. Unavailable timing
  must not claim Live, and enabling timeshift alone does not imply behind-live.
  A separate red dot + “REC” tag on the same row independently indicates active
  recording of the current live channel. Completed recording playback has no status.
  Growing recording playback uses amber play/pause + “Recording in progress” alone, only while
  current-session DVR state confirms recording. Accessibility retains full intent,
  behind-live and “Recording now” descriptions without duplicate announcements.
- Empty status content reserves no row. Go live, seek-target labels
  and feedback occupy the gradient run-out above the timeline without moving the
  timeline or action-row anchors. Go live remains above the timeline, reachable
  from it with Up; it is not an action-row item.
- Both footer gradients have a 56dp run-out. Plain status text has a local backing
  for contrast over bright video. Action controls remain 48dp; a 4dp timeline/action
  gap and 36dp bottom inset move the actions upward without moving the timeline.
  Standalone seek previews use the same timeline anchor.
- Pause has a transparent idle container, like the other icon actions. Native
  focused and pressed indication remains intact. The fading action layer reserves
  8dp of rendering overflow so native focus growth is not cropped mid-animation;
  measured control positions stay fixed.
- Channels rows and quick-zap cards share neutral content-colour play/pause
  markers. An indeterminate spinner occupies the same slot during an owned
  channel tune. Presented buffering is not a new tune; pause follows playback
  intent. Recording indicators remain independent.
- Timeline focus brightens endpoint labels to full emphasis and fades surrounding
  actions and the passive channel peek to 0.55 over 180ms. The expanded
  channel tray stays fully visible. Identity-header and quick-zap geometry remain
  unchanged.
- While actively seeking with timeline focus, actions and the passive channel
  peek fade out fully. The top programme header and clock remain visible in both
  full controls and standalone previews. Programme identity and schedule endpoints
  follow the preview target; passive Live/behind-live status remains committed.
   Ended programmes show “Ended at HH:mm” beneath the title, based on the current
   server clock, in both full controls and standalone previews; timeline endpoint
   labels retain their original meaning. Cancelling restores the actual playback
   programme. Missing historical metadata
  uses an unavailable-programme presentation and numeric buffer axis, never the
  current broadcast's title or schedule. Leaving timeline focus restores the destination even if the seek is
  still pending; hidden content exposes no accessibility actions. Back retains
  the existing preview-cancel/dismiss and controls-hide sequence. Physical-TV
  judgment of this treatment is still pending.
- An uninterrupted playback stall shows the existing compact tuning-style spinner
  with “Buffering…” after one second, for live and recording playback. Initial
  tuning retains its own status. Pause, inactive playback and recovery/error
  presentation suppress buffering feedback; target changes reset its delay. It
  neither takes focus nor moves controls.

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
