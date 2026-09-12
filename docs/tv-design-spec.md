# TV design specification

Status: normative from 2026-07-28. Supersedes ad-hoc values in `ui/`.
Historical evidence and rationale: `docs/tv-design-audit-2026-07-27.md`.
Archived implementation sequence:
`docs/archive/handoffs/tv-design-consolidation-handoff.md`.

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

The product is dark-only; it exposes no light scheme. `primary` is the product's
own cyan. Every Material for TV role is explicitly pinned so a dependency update
cannot silently repaint inherited roles; `surfaceTint` is product cyan.

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
  carried by shape and label, with colour as reinforcement only.

### 1.3 One recording red

There are two warm roles: `TvRecordingColor` `#FF5449` for recording state and
`error` `#F2B8B5` for actionable failures:

- **Recording** — `TvRecordingColor`, used by every REC badge, dot, scheduled
  label and "recording now" label.
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

Use semantic opacity tiers rather than screen-specific near-duplicates. Gradient
control points are scoped visual curves, not additional reusable panel tiers.

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

The collapsed navigation darkening scrim uses one continuous black curve across
the closed drawer width plus a 32dp runout: `0%/0.78`, `25%/0.72`, `55%/0.55`,
`78%/0.25`, and `100%/0.00` (position/alpha). The expanded drawer uses a
stronger full-width black curve so labels retain a stable foundation while the
player emerges at the content boundary: `0%/0.92`, `35%/0.88`, `70%/0.72`,
`90%/0.35`, and `100%/0.00`. Opaque browse content requires this optical curve
to be rendered as coordinated drawer backing and a foreground veil on the
leading edge of the adjacent browse plane; a shell background alone does not
soften a scrolling viewport's hard clip. These are curve controls rather than
surface tiers.

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
- When an installed TV component such as `TabRow` has no content-padding API,
  retain the leading safe inset but leave its trailing viewport edge-to-edge;
  never leave a blank trailing strip that clips a partially visible tab or focus
  treatment.

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

Channels, Guide and Recordings scope tabs share a horizontally travelling pill.
Its foreground contrast follows the actual moving shape on every Left/Right
transition and reversal. Selection still commits on focus;
Down or OK enters the selected scope's content.

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

Back unwinds focus layers before changing top-level destination history. Channels
and Guide enter through their active scope tab when arriving from the global
drawer. Down or OK enters that scope's remembered content; Back from the list or
grid returns to its active scope tab. With no scope tabs, Channels enters its list
and Guide uses its header. Back from a scope row activates the global drawer on
the current destination. Other browse content activates that drawer directly. From
a non-root drawer destination, the next Back focuses Channels; Back from
Channels then delegates to the existing warm-player or activity-exit policy. Settings adds one
local layer: content returns to the current category before category focus
returns to the global drawer on Settings. Focus-previewed drawer destinations do
not form a Back stack, but their saved screen and focus state is restored when
the viewer returns. Remote key dispatch consumes the complete Back key cycle at
the nearest focused layer; dispatcher-backed handling remains available for
accessibility and system Back actions without a focused key target.

Recordings archive folders add a local layer: opening a folder retains list
focus, and Back returns exactly one level to the parent folder row and its saved
viewport, including scroll offset. Missing items choose a deterministic remaining
local row; an empty list uses the local mode or recovery action. At the archive
root, Back retains the global drawer behavior above.

Channels and Recordings page-down reveal later rows from below as outgoing rows
move upward; page-up reverses this motion. The focus handoff must not scroll back
against the requested direction. Partial final pages and interrupted reversals
retain the latest requested target.

---

## 5. Typography roles

No new type scale. Roles are fixed so slots stop being re-chosen per screen.

| Role | Style | Alpha |
|---|---|---|
| Screen title | `headlineMedium` | primary |
| Section heading | `titleLarge` | primary |
| Item title | `bodyLarge` / bounded two-line `headlineSmall` in the player | primary |
| Eyebrow / channel identity | `titleMedium` / `labelLarge` in the player | secondary |
| Metadata / supporting | `labelLarge` | tertiary |

Long localized text must not change a layout's anchors. See 6.1.

### 5.1 Top-level browse rhythm

Channels, Guide, and Recordings share one stable header plane so moving through
the global drawer does not make the page title, scope selector, or body jump:

- the shell supplies the 32dp top safe inset;
- a 40dp header slot vertically anchors the `headlineMedium` title and any Guide
  actions without allowing those actions to move the title baseline;
- a dedicated leading-aligned scope `TabRow` follows after 8dp;
- Channels and Recordings leave 16dp between that selector and their browse
  panels, while the denser Guide leaves 8dp before its ruler;
- Recordings mode tabs are a scope row, not trailing title actions.

Root destinations and nested Settings categories use one explicit 150ms linear
crossfade. The logical route and visible target update immediately; the existing
focus owner remains governed by section 4.2, so an open drawer or Settings rail
keeps focus until the viewer enters content. The fade is only the visual handoff
and must not debounce or block rapid D-pad navigation. Do not use Navigation
Compose's generic 700ms default. The persistent player surface is owned below
destination UI and does not participate in this crossfade. Route feedback must
not re-request drawer focus while the drawer is already open; D-pad focus may be
ahead of an intermediate route update during rapid retargeting. While open, the
drawer selection and Back policy follow that latest focus intent. If root focus
is already requested but route feedback has not converged, Back is consumed
rather than escaping to player or app-exit policy.

Settings is intentionally exempt. Its fixed category rail and detail pane are a
local master-detail hierarchy whose outer panels begin at the shell's top inset;
do not add a redundant top-level Settings header above them.

### 5.2 General storage

General places Storage after Navigation, with one TV Material "Clear cache"
action. Support copy shows combined metadata/artwork usage in decimal MB and
the artwork count, without paths or server identity. Clearing does not change
connection settings, active playback or the in-memory catalog. Current metadata
may be written again immediately; images refresh on demand.

General retains first entry on Follow system. Down reaches Storage after the
language choices and Guide-menu switch; the action remains focusable while
clearing and ignores repeated activation until completion. Completion is an
in-row transient confirmation; failure stays actionable for retry. Back returns
to the General category, then to the existing shell owner. Enlarged English and
German text wraps inside the scrolling pane rather than truncating the action.

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
| Where | player overlays, including passive schedule | cards, channel rows, hero |
| Interactive | only with known playback timing and seek capability | no |
| Fill | **orange** playback; `primary` cyan passive schedule | `primary` cyan |
| Extras | thumb, labels, ghost fill, history boundary tick | none |
| Component | `PlayerTimeline.kt` | `ProgressStrip` in `ui/components/` |

These are separate components and must stay separate. An ambient strip must not
be built from the player timeline, and must not use mobile Material's
`LinearProgressIndicator`, whose default stop indicator draws a mark at 100%
regardless of actual progress.

Player's passive schedule, seekable playback and unavailable states use the same
`PlayerTimelineBlock`, not the generic `ProgressStrip`. The block owns track,
`labelLarge` endpoints and preview readouts, spacing and feedback placement.
The playback adapter owns seek semantics and input, scoped to the track/readout
body; Live/Go live remains outside that focus target. Passive schedule coordinates
never become playback coordinates or seek grants. Missing EPG draws no progress
or clocks but retains the timeline geometry through tuning and history changes.

Prototype B displays the scheduled programme containing sampled playback, or the
selected target during preview, using SDK 0.9.0's immutable schedule-grade
estimate. Scheduled start/end clocks are display edges, not seek permissions.
Crossing a boundary changes the window without snapping or animation. The nearby
programme title follows preview; main identity describes sampled committed
playback. Cross-midnight edges include dates. Solid orange fills from programme
start to playback position, following the target during seek preview. Buffered
content ahead is brighter gray; future schedule remains a quiet dark track.
Rewinding shortens the orange fill, including in completed programmes. The orange
edge indicates position; a thumb appears only while the seekbar is focused. Only an interior
timeshift-start boundary gets a thin tick; programme edges and live have no ticks.
The thicker active bar preserves remote focus. The existing
Go live action remains reachable outside the live window. The estimate has no accuracy
guarantee. Player owns no server clock machinery.

`-Pplayer.programmeWindowB=false` restores the capacity timeline at build time;
there is no public preference. Missing estimates, EPG gaps and out-of-range events
use the relative-history fallback. Preview retains the SDK mapping snapshot and
opaque media target, so late EPG/estimate updates and eviction cannot retarget a
command. Current runtime history alone authorizes dispatch.

The fallback live timeshift seekbar uses capacity for its display span. The
released SDK's finite positive `grantedPeriod` is preferred; when it
is unavailable the app's requested period defines only the display span. Expand
that span to include all observed history if history exceeds the grant/request.
A changed grant changes display scale, never actual seek permission. Live stays
at the right edge. Unavailable history is subdued, available history is neutral,
and orange marks playback position without filling unavailable history. Remote
and accessibility seek bounds remain the observed buffer start and live edge.
Recordings retain their elapsed/duration geometry.

The supported SDK timeline supplies absolute stream coordinates and opaque,
segment-scoped selection targets. Player retains the selected target through
the input debounce rather than rebuilding a relative seek at dispatch. Expired,
replaced and unavailable targets produce explicit feedback, never a clamped seek
on a successor subscription. Playback position comes from the SDK's sampled
Media3 mapping and its accessible description says so; the visible timeline
label shows only the distance behind live, and nothing at all at the live edge
because the action strip already says Live. History duration remains in the
accessible description, not a visible available-duration label. Server-reader shift decides whether
playback counts as live and is not displayed as playback position. A sampled position that outlives seekable history does not
extend seek permission. Its display span may expand to retain the position while
that expired history remains subdued.

Sample/history pairing and retained preview mappings use SDK segment identity,
not just subscription identity. On interruption or segment replacement, discard
queued previews and retained coordinates; do not retarget them onto the new
segment. Let already-dispatched SDK operations settle without cancelling them
merely to clear UI. Ordinary SDK-owned restart retains the Player and play intent;
nonterminal startup/buffering is not playback end. Uncertain-seek restart and
operation-cancellation teardown remain the SDK's intentional fail-closed paths.

When no estimated programme can be resolved and playback is behind the
live edge, or its position is unknown, the header states that
programme timing is unavailable instead of claiming that wall-clock Now/Next
describes the watched content. A merely available timeshift buffer is not
timeshifted playback: at the live edge the current broadcast is the watched
content and the header shows it. Live Info explicitly labels
its EPG and existing recording entry as **Current broadcast**, not historical
playback metadata. Current wall time remains
independent. Programme-time seeks from Guide remain unavailable;
supported recording-based Watch from start remains available. Neither EPG bounds,
packet coordinates nor local time minus server-reader shift may substitute for
an unavailable SDK estimate.

Missing, non-finite or contradictory buffer/position measurements are not a
measured live position. Keep the capacity display and observed history, but omit
the position marker, progress semantics and seek focus/actions until timing is
known. Show **Playback timing unavailable** without disabling an independently
available pause capability. Explicitly measured zero is distinct from missing
timing; relative durations alone do not establish a wall-clock timestamp.

### Player composition

Inline timeline endpoints remain neutral text without cyan filled labels. Focus
thickens the track and shows a thumb, including in the programme window.
Unavailable-target feedback has an error-toned surface above the timeline.

Live TV and recordings share one composition: artwork and identity at top left,
current wall time at top right, and a timeline above the bottom action strip.
Programme clock endpoints and recording elapsed/duration sit directly below the
full-width bar, aligned to its ends; long readouts never shorten the track.
Play/Pause and immediate Stop form the transport group; only Play/Pause has an idle
filled backing. Transport is left-aligned; Info, neutral Record and Settings form
one right-aligned utility group on the same band. Info's label is inside its TV Material pill.
Unavailable actions are omitted rather than leaving empty slots. Live/Go live
sits above the right end of the track, separate from the programme-end timestamp.
Up from the seekbar reaches actionable Go live; Down returns to the seekbar.
At passive Live, Up remains on the seekbar. Right from Settings also reaches Go
live, and Left returns to Settings. If the focused
Go live becomes passive Live, focus returns to Play/Pause (otherwise Info), consuming
the activating key cycle. Icons retain accessible names without floating captions;
necessary visible text belongs inside its control. Idle Stop and utility actions
have no filled backing. There is
no separate transport row or Actions-up hint. Header artwork fits an inset
96-by-64dp container at the shell's left safe edge, beside a readable channel
identity and programme text column. Opening the shelf retains that same header
composition and artwork; it never mounts a second compact header. The two-line headline aligns with the channel name and outranks timing
and Next. There is no competing header progress strip. The timeline repeats a
programme title only during preview, when it can differ from committed playback.
Seek previews place stream-relative targets above the fill edge, bounded inside
the actual track, without changing seek bounds.
Preview and genuine error feedback use a stable status row above the track at
`TvOverlayStatusRowHeight` (32dp minimum, growing to the current `labelLarge` line
height). Feedback is left-aligned; passive Live or compact Go live sits right.
The preview target replaces that content inside the same band, with no separately
reserved preview-label whitespace. Up commits preview before focusing Go live.
Starting, settling or cancelling a seek and missing programme metadata must not
move the status, bar or endpoint readouts. Compact previews share those anchors.
Focused seeking dims the header and
still-visible action band rather than uncomposing focused nodes; hidden-controls
previews reuse the same track and bottom anchor without hidden focusables. Missing
programme metadata gets secondary unavailable copy, not a duplicate channel title.

Live Now/Next describes the committed programme, not the uncommitted seek
preview. Shelf cards show both programmes with start/end times. SDK-supported playback coordinates and explicit timing authority
determine historical metadata; server reader position and relative durations
are not proof of a UTC timestamp. Unknown timing must remain explicit.

Center toggles playback directly on the playback surface/timeline, including
during preview. Left/Right previews repeat-accelerated steps immediately and
coalesces dispatch after 400ms of idle input. Up/Down commits pending preview
before navigation; Back cancels only undispatched preview. A neutral Up/Down
first reveals chrome, and a separate press enters actions or the channel shelf.
Consume the entire key cycle that reveals or relocates focus. Initial focus is
Play/Pause, otherwise Info; the timeline is one Up away and is never the
landing target of a reveal, so a reflexive Center after revealing chrome acts on
a visibly focused button rather than an unnoticed timeline. Up/Down from the timeline
commits pending preview; Up reaches actionable Go live (otherwise stays), and Down
returns to the transport group. Down from the action
strip opens Channels when channels exist. There is no Channels-down cue below
the strip. Recordings have no shelf. Restoration uses
semantic actions and never automatically chooses Stop or steals focus on routine
timing/metadata updates. Without a timeshift buffer, a current valid EPG event
supplies a cyan, noninteractive schedule-elapsed strip and schedule endpoints
in the footer. It has no thumb, focus, or seek actions and is not a playback-position
estimate. Missing or out-of-date EPG omits the strip rather than inventing progress.
The header does not add a temporary timeband while tuning or waiting for history.

Settings and Programme/Recording Info use one full-height, edge-attached right
panel with a deliberate video scrim and no competing chrome/focus. Settings has
at most a category root and one choices/details level, using TV Material list
rows with current values, explicit selection and unavailable/loading states.
Info retains existing recording actions and confirmations, not Settings
diagnostics. Back returns through panel levels and restores the invoking action.
Programme-backed Info and Recording Info initially focus a scrollable reading
region, never Record. Titles and synopsis scroll together; actions remain below
the reading region. Down at the end reaches the actions and Up returns to reading.
An explicit outlined Close is secondary to Back. Unavailable Info retains its
safe Close focus and bottom-end action placement. Scroll edges fade inside the
reading viewport, away from the focus border. Settings and Info share 32dp horizontal and 16dp inner vertical
padding inside the existing frame.

The channel shelf is horizontal and compact. It expands from the bottom as a
child below the action row, pushing the footer chrome upward, and shrinks back
down on dismissal. Shelf expansion/shrink and controls-layer fade use
`LIVE_PLAYER_LAYER_TRANSITION_MS` (180ms). Upper identity and clock remain pinned
and mounted throughout. While the rail is open, the timeline is the same
`PlaybackSeekbar`, with unchanged programme-window, timeshift and paused inputs;
only status, labels and focus are collapsed. Never substitute the passive
schedule strip for seeked-back playback. The action row remains visible with its
paused icon but cannot receive focus until the shelf closes. Back or Up dismisses only the
shelf, reveals ordinary player controls and restores the invoking safe action
(Play/Pause or Info fallback, never Stop). Consume the complete dismissal key cycle;
a separate Back from controls hides chrome. Entry scrolls to the playing
channel before requesting focus. Focus-following Now/Next is independent of
playing and channel-recording-now state. Selecting the playing channel closes
without retuning; CH+/CH- tunes directly even in the shelf. Digit entry supports
timeout, explicit confirmation and cancellation. The persistent playing identity
heads the composition. Each stable-size 288dp-wide card contains its own channel
identity and Now/Next titles with schedule ranges, or explicit missing-EPG copy.
Card height follows text line heights at the user's font scale; all cards have
the same dimensions: a substantial 96-by-64dp picon and prominent single-line channel
name dominate; Now is secondary with one title line; both ellipsize long text. Next is quieter
single-line support combining start time and title, with its full schedule in
accessibility semantics. The footer owns the scrim and safe-area padding; the
shelf adds an 8dp top gap and 8dp vertical focus overflow around the row.
There are no nested actions or focus scaling.
No separate focused-channel Now/Next block sits above the shelf.
The focus border, 24dp playing triangle and 16dp recording dot have separate meanings and
accessible labels; do not repeat channel identity or visible Playing labels.
An ordinary connected tune failure leaves channel access and navigation
available, shows no false playing
marker or borrowed buffer, and does not offer generic Retry without useful
runtime evidence. Connection loss and actual runtime recovery remain distinct.

Channel-recording-now belongs to channel identity; scheduling belongs to the
specific Next event. Do not duplicate badges or imply historical watched
content is being recorded. Record enters existing supported behavior, and Next
is informational. No new recording workflow or default-action preference is
introduced by this composition.

Recordings use actual elapsed/duration/capabilities, without a live shelf or
Go live. Unknown duration is passive. Back dismisses the foreground/chrome and
then returns to warm browse; it never means Stop. Explicit Stop awaits serialized
teardown and clears the warm return opportunity.

The retained player surface keeps the screen awake only while the host is started,
video is visible, a video track is selected, and Media3 reports active, error-free
playback. Pause, buffering, idle, ended/error, audio-only playback, and background
release that request without detaching the surface or changing playback commands.
Buffering/recovery does not add an application-owned grace timer. Clearing the
request permits Ambient Mode; the app does not activate it or control the system's
inactivity timing. Warm browsing over active video retains the request.

### 6.3 Cards

The app has no alternate card-based channel layout. If a future product surface
requires browse cards, use TV Material card containers rather than hand-built
focusable replicas.

- Widths come from the guidance grid at 20dp peek spacing:
  `844 / 412 / 268 / 196 / 124`.
- Row spacing is 20dp.
- 16:9 for programme and channel content.
- The content block never exceeds the media width.

### 6.4 Category pickers

Use the component and hierarchy appropriate to the scope.

- Horizontal browse scopes use `TabRow` in a dedicated full-width row. Do not
  squeeze a scrolling tab set into a trailing header slot or hide clipped focus
  surfaces behind an edge fade.
- An unfocused tab leaving the leading edge may pass through a coordinated
  foreground veil, but the focused/selected pill and its indication must always
  be fully visible beyond that veil.
- Settings categories are local master-detail navigation, not a second app
  drawer. Keep one fixed-width, focus-restoring, vertically scrollable column of
  TV Material `ListItem`s beside the detail pane. Its width must not change when
  focus crosses between categories and content.

Channel and Guide scope tabs commit on focus. Down or OK enters the restored
content item when it is ready. While Guide is still resolving a changed scope,
the same key is consumed and focus stays on the scope rather than moving backward
to the header. Guide Up returns to the last-focused date/Now header control, and
empty/error entry falls back deterministically to Retry or that header. Settings
category focus previews/commits the category, while Right or OK enters its
first/last-restored control.

### 6.5 Navigation drawer

The drawer starts with its destinations and keeps Settings and contextual owner
actions in the bottom section. Material for TV does not require a brand header;
do not spend the top safe region on a decorative product mark. A top action may
be added later only for a real product capability such as search or profile
selection.

Use the stronger continuous nonlinear black curve from section 1.5 behind the
expanded focusable items, preserving the darkest region through the icon and
label area before fading at the content boundary. In the collapsed state, use
its narrower companion curve across the complete rail and 32dp runout. Both
states must read like cinematic text scrims rather than grey panels with a
trailing dropoff. Do not add a hard divider, blur, or a second full-screen
navigation scrim.

Use the **standard push drawer**. Expanding the drawer changes the browse
viewport position while preserving its closed width; the trailing edge clips
rather than remeasuring each destination narrower. The shell passes the safe
content inset and keeps navigation and content as adjacent planes. Settings
remains in this global shell, so entering its content collapses the drawer to the
icon rail instead of removing it. Its local category pane remains fixed and must
not introduce another collapsing drawer.

The adjacent-plane model does not permit a bare hard seam. The shell overlays a
non-focusable, semantics-free leading-edge veil above departing browse content
and below drawer focus surfaces. This is an optical fade at the seam, not content
physically scrolling under a modal drawer; it must not alter measurement, focus,
Back, or key dispatch.

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
The debug build's optional synthetic video backdrop can make player-overlay and
warm-background geometry and contrast visible in a deterministic ADB capture,
but it is not evidence about live video, SurfaceView composition, HDR,
deinterlacing, or motion. Capture it only on the configured test device with:

```bash
./tools/device screenshot --synthetic-video-backdrop --confirm-safe-screen
```
