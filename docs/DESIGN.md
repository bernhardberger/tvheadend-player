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

### Startup

**Scope (2026-10-02):** app launch, returning to the app and the handoff to
usable content. Destination-level loading is separate, deferred design work.
Channels is the current non-autoplay landing destination, not a permanent
assumption of the startup experience; a future Home destination remains an
option rather than an approved routing change.

Startup branding uses a silent, calmer 1850ms adaptation of the selected
hybrid-synced choreography (accepted 2026-10-01). Keep the 80dp symbol, 14dp gap and
28sp/36sp Outfit 550 wordmark (not font-scaled). On the 960×540dp reference
canvas, the passive symbol starts at y=156dp, independent of feedback.
Under the content, one original creative cyan/orange diagonal light-sweep frame
is graded to light-only values above `#0F1014`, with feathered edges so it cannot
darken the field or reveal a rectangular rim. The light fades in gently, drifts
diagonally, then breathes between 20% and 30% opacity over a four-second cycle
for the remaining startup wait. Each two-second rise and fall uses sinusoidal easing,
with softly rounded turns and no pause. The plate and logo stay still; the sweep does not
replay. It fades out with the startup layer at first picture.
Recovery and reduced motion retain the exact flat field. The shared-clock choreography,
asset recipe and provenance are recorded in
[`artwork/README.md`](../artwork/README.md#startup-artwork).
Motion belongs only to the first non-restored foreground Activity opportunity
in a process, when a real startup wait continues past the 400ms request-owned grace.
Connection stage and retained disk cache do not decide admission: Connecting,
local preparation and cached cold entry can all animate while a wait remains.
A terminated intro cannot later start assembly.
This is never an extra screen or delay. The native splash remains static, with a maximum 1000ms
local-bootstrap hold. Android owns its splash removal; Activity entrance-animation
completion enables the intro once resumed/focused;
static entry uses fully opaque logo/wordmark and an immediately held glow once
its image is prepared, without an app-owned arrival fade.
The pending opening frame uses the final branded layout, never a spinner-only
intermediate layout or a settled-to-opening rewind.
It skips entirely if no wait remains. Disabled motion means static branding.
Startup does not play audio or
request audio focus.
Presented content, recovery, navigation, background or window-focus loss ends it;
warm resume, recreation, later Activities, reconnect and retry never replay it.
A return waiting for existing playback uses the stable ring and status over the
same gently pulsing ambient glow, without logo or sweep replay. The glow appears with
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
layout-matched skeletons or equivalent placeholders once that layout is settled;
the placeholder design is not decided here. Show usable cached content while
refreshing, keep available navigation and recovery accessible, and preserve stable
layout and D-pad focus as content arrives. Distinguish browsable cached data from
content that can currently be activated. This follow-up does not extend the
branded startup wait or change internal screen loading in the present work.

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

## 7. Settings · sliding-depth navigation (Variant C)

Accepted 2026-09-15; static design in Penpot page
`411cd6b7-a446-8042-8008-a3866b9562cf` (contract board
`622ff396-eb20-80fd-8008-a3b20fa5b114`).

- One active list column; the next level is a read-only **preview** to its
  right. Entering a level slides the child into the active slot and moves the
  parent fully off-screen. The container supports arbitrary depth and is
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
  Recording and playing markers are separate data states shown by badge/icon,
  never by overloading focus or selection styling.
- Passive progress on cards and rows is cyan and has no terminal marker that
  implies completion. The interactive playback timeline is a separate component
  with orange playback position.
- Unknown timing is shown as unknown; never interpolate across unrelated
  coordinates or imply seekability that is not verified.

### Channels · immersive browse and programme preview

Visual baseline accepted for implementation 2026-10-04 after the coordinated
browse concept review. This replaces the former headline-free, no-artwork C3c
composition while retaining its native rows, focus and playback contracts.

- The **Channels** headline shares the browse heading axis at x130, y32 on the
  960×540 reference canvas. Scope tabs sit at x130, y80; rows begin below them.
  At larger text, header/scope bands grow from their actual text measurements.
  Header actions appear only when backed by working, authorized behavior; the
  concept's Search/View placeholders are not inert production controls.
- Live video remains behind the content under the shell-owned warm-playback
  scrim in §4. There is no duplicate screen-local video scrim or rail/list seam.
- The list's leading axis includes the shell's 24dp inset and a further 26dp,
  with its 12dp focus reserve inside that additional inset. Standard rows are
  340dp wide, with a 92dp visual gap to the passive 340dp preview at x562.
  The list runs to the screen bottom behind a
  48dp fade. A matching local top fade appears only while earlier rows exist;
  returning to the first-row boundary removes it. Headings, tabs and the preview
  are outside these masks. Rows scroll out under the fades; **settled focus never
  enters them** — native scrolling and explicit restoration share the readable
  viewport, including focus-scale breathing room. The TV pivot policy must settle
  at the top boundary without alternating scroll requests or flickering the fade.
- Rows are the library's standard `ListItem`: native padding, height and growth
  at 1.3× fonts, with a 60×36 bare picon in the leading slot (aspect preserved,
  no logo box or background), native 16sp headline and Material 14sp `bodyMedium`
  supporting text, indication and focus scale. Rows are 4dp apart. The
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
- The preview starts beside the list and stays within the trailing/bottom safe
  insets. Use available programme artwork in a **16:9** slot, otherwise a contained
  picon over restrained ambient color, otherwise the ordinary channel glyph over
  a neutral surface. Loading/error retain the same slot and fallback. Artwork may
  be deliberately cover-cropped; logos stay uncropped. Use existing session-bound
  artwork loading, never an external artwork-discovery service.
- Short channel identity, start–end time and truthful programme progress occupy
  the image's bottom content area over a local black gradient. Unknown timing and
  progress are omitted. This image-local gradient is separate from the shell's
  video and navigation scrims. Keep fallback imagery clear of the embedded text.
- Programme title and a separately supplied subtitle remain outside the image.
  Absent subtitles leave no gap; never derive them from title punctuation.
  Use native `titleLarge` (22/28) for the title, at most two lines;
  `titleMedium` (16/24) for the subtitle; and `bodyMedium` (14/20) in
  `onSurfaceVariant` for the synopsis. These passive roles replace the earlier
  oversized headline/body treatment; user text scaling remains intact.
  Image/title and title/subtitle use 4dp gaps. Following G10 feedback, artwork is
  **constant across programmes**: 340×191.25dp at the reference viewport, including
  1.3× text. Only viewport/type configuration may reduce that 16:9 slot to reserve
  at least one title line, one subtitle line and the footer; metadata never resizes it.
- **Next stays at the bottom** of the detail column (y508 at the reference viewport).
  Reserve its one-line footer and 8dp separation even when no Next metadata exists;
  leave it empty rather than inventing a programme. Fit measured whole text lines
  above it: synopsis yields first (up to three lines, two at 1.3×, including complete
  omission), then title/subtitle ellipsize while retaining at least one line each.
  Preserve native type sizes and the stable artwork/title anchors.
- The preview is passive and never an additional D-pad target. It **crossfades** rather than hard-cutting as
  the browsed row changes: a 160ms dissolve, short enough that fast row-to-row
  browsing still feels immediate. Each state resolves its own EPG, so the
  outgoing copy keeps the channel it was written for. Layout adapts without
  animating text baselines. Missing programme metadata does not itself make a
  channel unwatchable. Do not add a persistent OK reminder.

### Guide and Recordings · browse cohesion

The shared-grid follow-up extends the 2026-10-04 visual baseline. Archive now
uses the depth model below; destination entry, scope selection, details-first
activation, DVR eligibility, confirmations and current-session guards remain.

- Headings share x130/y32 and scopes y80 on the reference canvas. Use native TV
  Material rows/cards and indication with reserved focus-growth space. Text scale
  changes the available rows/columns, never the native font sizes.
- Guide keeps date/Now/Search together using existing working actions, with
  trailing focus clearance. Its 172dp channel gutter and 8dp gap put the timeline
  at x310, extending 650dp to the physical edge, with the ruler above
  the programme rows. Timeline and channel content may continue to the physical
  trailing/bottom edges beneath local 48dp fades. Leading/top fades appear only
  when more content exists there. Headers, controls and scopes remain clear.
  Settled focused cells remain inside the unfaded area; real data/history bounds
  must not imply unavailable overflow. Preserve physical Left earlier/Right later.
- At a terminal physical edge, reserve 48dp plus native focus-growth allowance
  rather than letting focus touch the screen edge. This is blank spatial padding,
  not additional time availability. Ruler, cells and Now share its mapping;
  normal cells retain their full duration allocation. Apply it on physical right
  for LTR future bounds and physical left for RTL history bounds. Continuing
  edges retain overflow and fades, and time capacity is not reduced a second time.
- Guide's continuous 2dp current-time marker paints above cells and their focus
  treatment using the same time mapping as event widths and the ruler. Its ruler
  marker stays visible during vertical scrolling; the line follows the local
  content fade. Gaps are explicitly labeled without implying available programmes.
- Guide's normal native rows are 80dp high; larger text increases that height.
  The channel number and contained 44×20dp picon share a compact upper line.
  The name uses native `titleSmall` (14/20), up to two lines, across the 156dp
  inner width below it. Measure both header and programme typography when growing
  lanes (104dp at the reference 1.3× scale); do not shrink fonts or distort logos.
  The visible time span adapts to measured labels (normally three hours, two
  with enlarged text). This does not change the three-hour acquisition window
  or the existing history/future availability policy.
- Recordings uses native growing rows, with date/duration/status below titles.
  Archive, Schedule and Problems remain separate; Schedule and Problems are flat
  lists with full-row native focus clearance. Reserve trailing layout space so
  the complete focused shape remains at least 48dp from the physical screen edge;
  an unclipped shape alone is not sufficient. Preserve larger caller safe insets.
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
window, timeshift and scene-marker behaviour are specified in
`docs/player-ui-ux-overhaul-plan.md` and the playback safety skill; only the
colour and indication rules above apply here. Remote keys follow §6.

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
