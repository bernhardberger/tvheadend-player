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

The product is dark-only with a static, brand-derived scheme. Every Material for
TV role is explicitly pinned so a dependency update cannot silently repaint
inherited roles. UI primary is generated cyan T80; brand artwork retains its seed.

| Role | Value | Notes |
|---|---|---|
| `primary` / `surfaceTint` | `#79D1FF` | Cyan T80 from brand seed `#00BCFA` |
| `onPrimary` | `#003549` | Dark text/icons on primary |
| `primaryContainer` | `#004C68` | Cyan T30 |
| `onPrimaryContainer` | `#C3E8FF` | Cyan T90 |
| `secondary` | `#B5C9D7` | Lower-chroma cyan T80 |
| `onSecondary` | `#20333D` | Lower-chroma cyan T20 |
| `secondaryContainer` | `#364955` | Lower-chroma cyan T30 |
| `onSecondaryContainer` | `#D1E5F4` | Lower-chroma cyan T90 |
| `background` / `surface` | `#111416` | Neutral T6 |
| `onSurface` / `onBackground` | `#E1E2E5` | Neutral T90; focused button fill |
| `inverseOnSurface` | `#2E3133` | Neutral T20; focused button content |
| `surfaceVariant` / `borderVariant` | `#41484D` | Neutral variant T30 |
| `onSurfaceVariant` | `#C0C7CD` | Neutral variant T80 |
| `error` | `#FFB4AB` | Error T80; see 1.3 |

`Theme.kt` uses Material Color Utilities 0.3.0 palettes and explicit role tones,
matching the Penpot static dark theme sheet. Its mobile scheme mirrors the TV
roles, plus the surface-container ladder, for permitted mobile primitives.

`TvSurfaceColors` supplies the roles not exposed by TV Material: lowest `#0C0F10`,
low `#191C1E`, container `#1D2022`, high `#282A2C`, highest `#323537`, bright
`#37393B`. Browse/settings/guide panels use container; settings sub-navigation
uses low; dialogs, notices and guide cells use high; selected guide channel
headers use highest. Settings C uses transparent lists over either the stationary
warm-playback scrim specified in 5.2 or the normal themed app background; its
dedicated Connection editor retains its existing surface. Apply existing video
opacity tiers separately. Native TV
controls retain their role-based defaults; do not add elevation tint to explicitly
chosen container tones.

### 1.2 Cyan-led static theme and selective orange emphasis

Accepted colour direction (2026-09-14): use one static, brand-derived dark
Material scheme. Cyan leads the product; orange is the tertiary accent, available
for selective emphasis rather than restricted to the seekbar. This supersedes
the earlier orange-only-for-playback guidance. The app theme and the Penpot
static dark theme sheet use the family below.

The target tertiary family is `tertiary` T70 `#FF8E32`, `onTertiary` `#502400`,
`tertiaryContainer` `#723600`, and `onTertiaryContainer` `#FFDCC6`. Preserve the
exact brand orange `#FA7F00` in artwork; UI uses palette tone 70 rather than
the pastel tone 80 (`#FFB786`).
Playback-position tokens reference tertiary; supporting orange roles use their
paired foregrounds. Validate contrast in the actual composition.
Dark orange foreground/container roles remain available, but ordinary panels,
cards and buttons use neutral surfaces. Do not give every available colour role
equal visual prominence; use tertiary containers only for intentional tonal accents.

Use neutral Material for TV focus defaults for ordinary buttons: `onSurface`
container and `inverseOnSurface` content. Primary does not imply a cyan-filled
focused button. A separate orange-primary player theme is not the selected
direction. Keep generated tonal ramps as reference tokens; screen design uses
semantic roles, including tone-based surface containers. Surface tone and
video-overlay opacity are separate decisions.

| Colour | Means | Appears on |
|---|---|---|
| `primary` cyan | leading product accent | selected/active navigation, ambient progress, selective emphasis |
| `tertiary` orange T70 `#FF8E32` | contrasting accent | playback position and selective secondary emphasis |
| recording red | a recording exists or is running | REC badges and indicators |
| `error` | a failure the user must act on | error text, failed-recording state |

Keep orange sparse enough that the interface remains cyan-led. Ambient progress
strips on cards and rows remain cyan (see 6.2); allowing other tertiary uses is
not a reason to recolour every active element. Check orange emphasis beside red
REC indicators at viewing distance. State differences use shape and label, with
colour as reinforcement rather than their only signal.

### 1.3 One recording red

There are two warm roles: `TvRecordingColor` `#FF5449` for recording state and
`error` `#FFB4AB` for actionable failures:

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
| `WarmPlaybackScrimAlpha` | 0.76 | global scrim over warm playback behind ordinary destinations |
| `TvScrimModalAlpha` | 0.76 | dialogs and confirmations |
| `TvTrackAlpha` | **0.20** | progress and seekbar track |
| `TvGhostFillAlpha` | 0.40 | ghost/rewindable regions |

The track remains at 0.20 opacity so progress separates from its neutral backing.
Contrast over video must be checked after compositing, not from token values alone.

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
drawer. Down or OK enters that scope's remembered content, except for the fresh
Channels tag-switch anchor specified in 6.4; Back from the list or
grid returns to its active scope tab. With no scope tabs, Channels enters its list
and Guide uses its header. Back from a scope row activates the global drawer on
the current destination. Other browse content activates that drawer directly. From
a non-root drawer destination, the next Back focuses Channels; Back from
Channels then delegates to the existing warm-player or activity-exit policy. Settings
adds an arbitrary-depth local stack: each Back/Left returns one level and restores
the invoking item and exact parent viewport. At its root, focus returns to the
global drawer on Settings. Focus-previewed drawer destinations do
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

Browse destination content moves along the navigation axis: vertically for the
main drawer's destination order and horizontally for the content below Channels,
Guide and Recordings tabs. A moving tab indicator alone does not satisfy the
[TV tab-content motion guidance](https://developer.android.com/design/ui/tv/guides/components/tabs).
The [standard drawer motion example](https://developer.android.com/design/ui/tv/guides/components/navigation-drawer)
also demonstrates vertical destination movement.

Both the departing and arriving content move and fade, as shown in the
[tab-page reference](https://developer.android.com/static/design/ui/tv/guides/components/images/tabs/tab-page-transition.mp4)
and [standard-drawer reference](https://developer.android.com/static/design/ui/tv/guides/components/images/navigation-drawer/standard-navigation-drawer-motion.mp4).
The replacement candidate uses one third of the content viewport and a critically
damped spring with stiffness 400, calibrated against those examples. These are
local implementation parameters, not numeric requirements published by Google;
physical acceptance remains open. The earlier incoming-only 32dp/150ms candidate
did not meet the operator's requested appearance.

Headers and tabs stay stationary during tab-body motion. Retain only presentation
values for the departing body: it must not own input, shared viewport state,
requester registrations, selection writes, paging jobs or delayed focus effects.
Screen controllers and command authority stay outside the transition. Direction
follows displayed tab order, including RTL; rapid changes interrupt motion and
settle on the latest destination without reactivating an old visit. Metadata
refresh, same-tab focus and empty-to-populated updates do not replay entry.
Initial/restored content and external fallback destinations start settled. Settings
depth transitions have their own owner and parameters in 5.2.

**Shared implementation contract:** Page-motion parameters and transforms belong
to `ui/BrowseMotionPolicy.kt`. Main destinations use the single
`SidebarGuideScene` navigation owner; replacing tab/section bodies use
`BrowseTabContent` with `rememberBrowseContentMotion`. Screens supply their order,
accepted selection and presentation values, not their own page springs or fades.
`BrowseTabRow` animates only the selector and is not a substitute for the body host.
Settings depth navigation is an explicit separate transition scope.

When adding or changing a destination or section:

- Register main destinations with `SIDEBAR_SCENE_DESTINATION` in the existing root
  entry provider and include them in the scene's displayed destination order.
  Extend `BrowseDestinationTransitionTest` to cover the new destination's entry,
  exit and rapid retargeting. A route using only the generic navigation transition
  is not a completed main-destination integration.
- Pair a body-replacing selector with `BrowseTabContent`, keep its header/selector
  outside that host, and pass the actual rendered presentation into its content
  slot. Keep controllers and commands outside; outgoing visits must use the shared
  focus, viewport and deferred-read helpers and guard delayed callbacks by owner.
- Add the real screen/section to the integrated motion checks, following
  `SidebarGuideNavigationTest.allThreeTabBodiesMoveWhileTheirHeadersAndFocusStayPut`.
  Verify outgoing/incoming content, fixed headers, native focus and the latest
  destination—not merely that a scalar animation value changes.
- Change shared motion policy once when adjusting page motion. Keep per-frame
  values in graphics-layer/draw reads; layout and composition should observe only
  relevant visibility/identity changes. Validate both hosts after a policy change.

These are the standard integration APIs and review/test requirements, not a
compiler-enforced prohibition on using lower-level Compose animation APIs.

The logical route and visible target update immediately; the existing focus owner
remains governed by section 4.2, so an open drawer or active Settings level keeps focus
until the viewer enters content. Motion must not debounce navigation, wait for
data preparation, or defer Down/OK until completion. Do not use Navigation
Compose's generic 700ms default. The persistent player surface is owned below
destination UI and does not participate in destination motion. Route feedback must
not re-request drawer focus while the drawer is already open; D-pad focus may be
ahead of an intermediate route update during rapid retargeting. While open, the
drawer selection and Back policy follow that latest focus intent. If root focus
is already requested but route feedback has not converged, Back is consumed
rather than escaping to player or app-exit policy.

Settings is exempt from the browse title/tab rhythm. Its active level has its own
heading, replaced together with that level, without a duplicate page header.

### 5.2 Settings Variant C

Accepted design authority: Penpot Settings page `411cd6b7-a446-8042-8008-a3866b9562cf`
in app file `8aa8c9a5-d7b1-8076-8008-a1e6cc530f69`, contract board
`622ff396-eb20-80fd-8008-a3b20fa5b114`, plugin data `variant-c-contract-v1`.
Root/General, General/language and language-choice boards respectively:
`622ff396-eb20-80fd-8008-a3ac05f58e99`,
`622ff396-eb20-80fd-8008-a3acb318666a`,
`622ff396-eb20-80fd-8008-a3acb4033b66`.
Host: `https://penpot.int.leoville.at`. The connected product-owned
`tvheadend-player-design-kit` supplies semantic roles and typography. Broadcast
art in the study is reference imagery, not a production asset.

- One active column and an inert, subdued child preview; the reusable depth owner
  supports any number of levels with stable level/item identities. Settings owns
  its content, actions and permission/session guards. Recordings are only a future
  possible consumer, not part of this implementation.
- Use standard unscaled TV Material list rows, meaningful root-category icons,
  concise titles and section headings. Deeper icons need a meaningful purpose.
  Supporting text gives current values/status, not prose inventories. Use
  `headlineMedium` (Roboto Regular 28), `titleMedium` (Medium 16) and `bodyMedium`
  (Regular 14). Focus uses `inverseSurface` / `inverseOnSurface`; no 16.8sp kit
  typography or focused row scaling.
- Reference geometry on 960×540: active x164, width352; preview x588, a 424 step
  and 72 gap. Implement these as logical layout dimensions through the shell's
  inset, never device pixels. Keep the active slot/width at every depth. Preview
  may overflow the active container to the screen edge.
- Entering replaces the active level and takes its parent wholly offscreen.
  Warm playback and its single full-viewport black scrim (alpha .76) stay
  stationary behind Settings. The shell draws this scrim only when active playback
  is mounted behind an ordinary non-player destination; without warm playback,
  Settings uses the normal themed app background and no full-screen video scrim.
  Player routes do not use this layer. Preview alpha is .8. The existing global
  push drawer is independent and retains
  its current widths and optical scrim; expanding it does not resize columns.
- Up/Down changes focus and previews children. Right/OK enters a submenu;
  Right on a leaf does not commit. OK activates a leaf or switch. Focus alone
  never changes settings or starts playback. Left/Back pops one local level,
  restoring the exact parent item and viewport; missing items choose a stable
  nearest remaining row, empty/error/loading states retain local recovery.
  Consume complete relocation key cycles, including repeats and release. Stale
  outgoing/preview visits cannot act, steal focus or publish viewport state.
- General starts on App language. Language is a third-level radio-choice list;
  OK applies the locale and returns to App language with the updated value.
  Save the parent stack before locale recreation. Switches act in place.
- General places Storage after Navigation. Clear cache is one row action with
  a trailing trash icon, decimal-MB supporting text (such as `0.0 MB`),
  and no independently focusable icon button. It remains focusable while clearing,
   ignores repeated activation until completion, and uses the global passive
   snackbar for completion/error results. Retain existing storage/session guards. Clearing
  does not change connection settings, active playback or the in-memory catalog.
   Support is usage only in every state. A guarded in-progress indicator replaces
   the trailing icon while clearing; row geometry stays stable. Completion/error
   feedback and its accessible announcement are separate from the row. Notification
   expiry never clears the domain failure state or removes the Clear cache retry action.
- Appliance's full accessibility disclosure stays on the page in a separate
  reading region below the concise service-status/action row, not inside its
  focused container. Down enters that region; Up/Down scroll long text and Up at
  its start returns to the action. Use the existing Info reading treatment with
  a focus outline and scroll-edge fades. All disclosure text remains available
  to accessibility. The action still opens system settings directly; no new
  disclosure dialog or confirmation step is introduced.
- Connection enters a normal second-level overview: Status (current state),
  Server (host only), HTSP port, then Edit connection. Only Edit is interactive
  and receives initial/restored focus. It has a trailing Material edit/pencil
  icon, not a chevron: OK opens the dedicated secure editor; Right does not.
  The root preview shows the same safe overview inertly, never editor state.
  Username was explicitly removed: SDK 0.15.0 has no safe username accessor.
  Do not decrypt/read editor credentials for the overview, add authentication
  summaries, diagnostics or connection commands. Captures use artificial values.
  Reference board `622ff396-eb20-80fd-8008-a4331e718ea3` contains an earlier
  Username row; the later operator removal supersedes that part of the sketch.
  Reuse the existing editor presentation and preserve draft/validation, explicit Save,
  clear-saved-password, unsaved-edit, keyboard and Back behavior. The existing
  form body scrolls when necessary to keep its actions reachable; its heading
  stays fixed. Left first traverses the editor's own controls, then pops at its
  boundary using the surviving depth owner's complete key-cycle guard. Successful
  Save and cancel/Back return to the overview's Edit action and viewport; failed
  Save remains in the editor. Back from the overview restores root Connection.
  The dedicated editor has an independently supplied sanitized outgoing label,
  with no credentials or editor-state collection. On exit, replace the
  live editor with that sanitized presentation immediately. Dedicated editor
  styling/placement is pending design; “wizard” does not authorize a new Activity
  or multistep state machine.

Single-line Settings rows use a 48dp minimum when supporting content is absent;
the native leading-icon and two-line minimums remain authoritative. In TV Material
1.1.0 `ListItem.kt`, `BaseListItem` centers headline/trailing content in its internal
minimum-height Row. The former app-only 56dp switch minimum enlarged the Surface
without that Row: measured label/switch centers24.5/24dp versus surface center28dp.
The unmodified default centers24.5/24dp in48dp, as does the corrected app row.
No compensating Text/Switch offset or component fork is used. Regression evidence
compares the installed default, explicit56dp and production row, retaining a real
two-line case. Source: Google's Maven `androidx.tv:tv-material:1.1.0:sources`,
`androidx/tv/material3/ListItem.kt` and `Switch.kt`.

Implementation choices, not design-approved motion measurements: 220ms
FastOutSlowIn non-overshooting horizontal transitions, immediate logical target
publication and focus handoff once the target row is attached. Interrupted input
retargets the current visit without waiting for animation completion. The operator
waived the Penpot interactive prototype and separate design-stage motion validation
after the contract was written. Those are not implementation blockers. Runtime
regressions, screenshot-first review and honest physical-TV gates remain required;
static captures do not accept motion, remote feel or readability over real video.
Compare locales using matched content, focus, viewport, backdrop and font scale:
English/German at 1.0, with separately labelled matched 1.3 stress evidence.
Penpot comparisons show the actual source export alongside the actual Compose
capture and name remaining differences. The abstract debug backdrop is geometry
evidence, not equivalent broadcast-backed visual evidence. Approved comparison
stills remain test-only local assets, never production resources. Reviewer
readiness is not operator acceptance.

AOSP TvSettings `TwoPanelSettingsLib/TwoPanelSettingsFragment.java` and
`TwoPanelListPreferenceDialogFragment.java` are navigation inspiration, not a
claim of equivalence to all Google TV versions. Its dedicated Wi-Fi connection
activity is precedent for a leaf editor, not an architecture requirement here.

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
| Fill | **orange** seekable elapsed playback; off-white noninteractive elapsed | `primary` cyan |
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
playback. Cross-midnight edges include dates. Off-white (`onSurface`) fills elapsed
programme time outside the seekable range. Orange fills only from the available
timeshift start to playback position, following the target during seek preview.
Buffered content ahead is brighter gray; future schedule remains a quiet dark track.
The historical buffer-start boundary retains an SDK wall-clock mapping snapshot
for its stream segment, so estimate refreshes do not move unchanged buffered
content. Actual history eviction advances it through that same SDK mapping;
segment replacement discards the snapshot. Playback/programme identity and the
buffer-end ghost continue using their current mapping. No app clock mapping or
seek authority is derived from the retained display snapshot.
Off-white is drawn only before the history-start boundary, not beneath orange.
An available sampled playback position may momentarily lead the latest history
status; its orange fill still reaches that sample without exposing a white sliver
or extending verified seek bounds. Unavailable preview targets do not gain fill
beyond verified history.
Non-timeshiftable schedule progress uses the same off-white, never cyan.
Rewinding shortens the orange fill, including in completed programmes. The orange
edge indicates position; a thumb appears only while the seekbar is focused. Only an interior
timeshift-start boundary is indicated by the colour change, without a tick;
programme edges and live also have no ticks.
The thicker active bar preserves remote focus. The existing
Go live action remains reachable outside the live window. The estimate has no accuracy
guarantee. Player owns no server clock machinery.

`-Pplayer.programmeWindowB=false` restores the relative-history timeline at build time;
there is no public preference. Missing estimates, EPG gaps and out-of-range events
use the relative-history fallback. Preview retains the SDK mapping snapshot and
opaque media target, so late EPG/estimate updates and eviction cannot retarget a
command. Current runtime history alone authorizes dispatch.

The fallback live timeshift seekbar displays the collected buffer, like a growing
recording, rather than reserving empty timeshift capacity. Buffer start anchors
the left edge and live anchors the right; orange fills from available buffer start
to playback, with neutral buffered content ahead. Growing history updates this
same full-width axis instead of expanding a narrow region leftwards from live.
Capacity changes do not rescale it. Remote and accessibility seek bounds remain
the observed buffer start and live edge. Recordings retain their elapsed/duration
geometry.

The no-EPG fallback's committed, unpaused Live fill reaches the right endpoint
using the existing server-shift-aware Live classification; delivery/decode latency
must not leave a gap beside a Live label. Paused playback and seek previews keep
their sampled/selected position. Its displayed history end advances on monotonic
time between verified updates, using the same five-second maximum extrapolation
budget as growing recordings. Repeated unchanged bounds do not renew that budget.
Segment replacement or unavailable timing discards the display estimate. This
estimate never changes observed history, opaque targets or seek permissions.

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
measured live position. Keep the buffer display and observed history, but omit
the position marker, progress semantics and seek focus/actions until timing is
known. Show **Playback timing unavailable** without disabling an independently
available pause capability. Explicitly measured zero is distinct from missing
timing; relative durations alone do not establish a wall-clock timestamp.

### Player composition

Inline timeline endpoints remain neutral text without cyan filled labels. Focus
thickens the track and shows a thumb, including in the programme window.
The focused thumb remains solid white without a coloured focus ring, including
temporarily unavailable seek targets; their existing error feedback conveys availability.
Playback fill and thumb share one
180ms non-overshooting ease-out animation. Animated values are read in layout,
not in the screen composition. Seek input, target labels and accessibility
values update immediately; motion never delays commands or changes their bounds.
Initial/unknown timing and programme-axis changes reset visual motion instead of
interpolating across unrelated coordinates. System animation scaling applies.
Unavailable-target feedback has an error-toned surface above the timeline.

Live TV and recordings share one composition: artwork and identity at top left,
current wall time at top right, and a timeline above the bottom action strip.
Programme clock endpoints and recording elapsed/duration sit directly below the
full-width bar, aligned to its ends; long readouts never shorten the track.
For a currently growing recording, the displayed end advances locally from the
last verified media duration for at most five seconds without fresh evidence,
including while paused. This presentation estimate never extends D-pad or
accessibility seek permissions; previews and commits use verified recorded
content. Completion, unavailable timing, or a source change discards the estimate.
Finished recordings retain their fixed media duration. Accessible descriptions
qualify an extrapolated endpoint as estimated.
Recordings with real scene markers show thin ticks inside this existing track.
Only the SDK's `SCENE_MARKER` end coordinates strictly inside verified media
duration are navigation targets; other cutpoint actions do not create chapters
or automatic skips. Up from the seekbar shows a single focused timestamp label
anchored above the selected tick, clamped within the track's horizontal bounds.
This borrows the Material 3 slider value-label treatment while retaining TV focus
and D-pad input. There is no instruction panel, reserved space or movement of the
track, readouts or actions; the seekbar remains continuous rather than snapping
ordinary seeks to discrete marker steps.
During marker selection, the orange fill remains at actual playback position.
A thin, taller line through the track identifies the selected timestamp beneath
the label. There is no ghost dot or preview thumb. Ordinary ticks are subdued
1dp lines. Marker navigation also offers the recording's 0:00 boundary, without
claiming it is a supplied chapter. Selection alone never seeks; OK commits.
Left/Right selects a timestamped marker; OK seeks while preserving pause.
Down or Back dismisses and restores seekbar focus without a marker seek.
The overlay suspends auto-hide and consumes complete opening/closing key cycles.
Missing or unavailable markers retain ordinary seeking. There are no thumbnails,
synthetic intervals or coarse-skip controls. Generic labels must not imply
programme names or commercial boundaries that the metadata does not supply.
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
supplies an off-white, noninteractive schedule-elapsed strip and schedule endpoints
in the footer. It has no thumb, focus, or seek actions and is not a playback-position
estimate. Missing or out-of-date EPG omits the strip rather than inventing progress.
The header does not add a temporary timeband while tuning or waiting for history.

The player's in-playback Settings overlay and Programme/Recording Info use one
full-height, edge-attached right panel with a deliberate video scrim and no
competing chrome/focus. This compact playback Settings overlay has
at most a category root and one choices/details level, using TV Material list
rows with current values, explicit selection and unavailable/loading states.
It is separate from the full Settings destination governed by Variant C in 5.2.
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
- Settings uses the recursive C hierarchy in 5.2, not a second app drawer or a
  permanently visible parent category rail.

Channel and Guide scope tabs commit on focus. Rapid Left/Right changes accept the
latest destination without waiting for an earlier scope to finish preparing.
Obsolete positioning and focus requests must not win afterward.

On an actual Channels tag change, position the unfocused list at the currently
playing channel if it belongs to the destination scope, otherwise at the top.
The detail preview and Down/OK entry use that same anchor; do not retain an
overlapping channel's old lazy-list key or a prior visit's browse position across
this switch. Keep native focus on the tag until explicit content entry. Back to
the tag and re-entry without changing tags still restore the current browse
position. If the destination has no rows yet, retain tag focus and apply the
playing-channel-or-top rule when its rows become available.

Down or OK enters the latest scope's appropriate content item when it is ready.
While Guide is still resolving a changed scope,
the same key is consumed and focus stays on the scope rather than moving backward
to the header. Guide Up returns to the last-focused date/Now header control, and
empty/error entry falls back deterministically to Retry or that header. Settings
focus previews a child without committing or entering it; Right/OK enters.

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
icon rail instead of removing it. Its depth-column width remains fixed and must
not introduce another collapsing drawer.

The adjacent-plane model does not permit a bare hard seam. The shell overlays a
non-focusable, semantics-free leading-edge veil above departing browse content
and below drawer focus surfaces. This is an optical fade at the seam, not content
physically scrolling under a modal drawer; it must not alter measurement, focus,
Back, or key dispatch.

### 6.6 Icon assets

UI icons are packaged Android vector drawables under
`app/src/main/res/drawable/ic_*.xml`, rendered through Compose
`painterResource` and the TV Material `Icon` API. They come from Google's
Material Symbols Rounded set at the fixed export (weight 400, grade 0, optical
size 24), each symbol as one `ic_{symbol}` resource. Do not reintroduce the
Compose `material-icons` dependency as a source of new symbols; add the vector
asset instead. Icons carry no vector-level `android:tint`, so the caller's
`tint`/`ColorFilter` remains authoritative, and directional symbols that mirror
for RTL keep `android:autoMirrored="true"`. Source revision, export choices and
modifications are recorded in `artwork/material-symbols-LICENSE.txt` and
`NOTICE.md`.

The explicit exception is Open accessibility settings: Material Symbols Outlined
`settings_accessibility`, FILL0, weight400, grade0, optical size24, rendered at24dp
with the current Material tint. Its source revision is recorded with the other
vectors. Do not substitute a generic person or settings icon.

---

### 6.7 Global passive snackbar

One app-shell host renders above ordinary destination navigation, including
playback. Cache clearing is its first producer; recording-started/completed and
scheduled-recording integrations remain future scope. Producers retain operation
and result authority. Emit from the accepted operation's outcome, never by
collecting a restored UI result state. Navigation does not invalidate notices.
Connection/account replacement and SDK session replacement invalidate old context.

Use the product kit's plain Snackbar reference in file
`8aa8c9a5-d7b1-8076-8008-a23201c2b02c`, board
`fb586898-078b-5bc5-b73a-1a3ac624c854`: component
`d168f29c-8fe0-569d-b4d0-1a7fd6185e9c`, main-instance shape
`bbde9ddc-d8bb-596c-8e20-52626464d01f`. The installed TV Material 1.1.0 has
no Snackbar API; compose its non-interactive `Surface` and `Text` primitives.
Use `inverseSurface` / `inverseOnSurface`, 12dp corners, `labelLarge` (Roboto
14 Medium), and 16dp horizontal / 12dp vertical internal padding. The plain
variant has a 44dp minimum height and grows for wrapping/enlarged text.

The final operator placement is **bottom center**, inside the existing full-screen
safe margins (bottom32, sides48). Width wraps content up to 324dp, constrained by
the viewport, with natural localized/enlarged text wrapping. No focus target,
action, close button, key interception, list reflow, route allowlist, collision
solver, subtitle exclusion zone or automatic relocation. This supersedes the
earlier right-side and measured-avoidance proposals. A snackbar may temporarily
overlap underlying content, including player controls or positioned subtitles;
this is a known consequence of the chosen stable anchor, not a universal safety
claim that physical testing could establish. Playback rendering remains unchanged.

Initial implementation parameters: one visible notice plus two pending; coalesce
pending notices by producer/event key, evict oldest overflow, and expire unshown
notices after 30 seconds of monotonic time. Display success for 4 seconds and failure
for 6 seconds, extended by the accessibility recommended timeout. Once shown, its
pending deadline no longer applies. Retain one identity and the original display
deadline across navigation and locale recreation; never restart or re-enqueue it.
No process-restored inbox or OS notification is created.

Present only in a resumed, focused app window with the IME hidden. Existing
window focus handles separate modal windows. A temporarily hidden visible notice
keeps its original deadline; an unshown notice keeps its original pending expiry.
Use a polite accessible live region. Notification expiry must not alter domain
state or erase durable recovery/disclosure content.

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
