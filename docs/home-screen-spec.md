# Home screen specification

## Status and scope

Roadmap item U3. Product rules written with the owner on 2026-10-30; the owner
decided every point in [section 6](#6-owner-decisions-2026-10-30). The earlier
Home implementation was rejected and removed; implementation starts fresh from
this specification and an accepted mockup.

Phase 1 includes On now discovery. Tonight/Primetime is deferred to phase 2.

Still open, to be decided with the mockup:

- Labels on the recent-channel tiles: placement and visibility. The earlier
  requirement "text beneath the focused tile only" was rejected and must not be
  carried forward.
- How much programme information the On now row shows, starting from the
  Channels immersive view and possibly leaner for Home's vertical space.

## 1. Purpose and navigation

- Home is a live-TV-first starting point: return to what is playing, revisit a
  channel, discover a programme, or reach useful recording content.
- Home replaces Channels as the **non-autoplay landing destination and browse
  navigation root**. Autoplay still goes directly to playback.
- Home is the **first item in the navigation rail**, above Channels, and the rail
  opens with Home selected.
- **Back** from Channels, Guide or Recordings goes to Home. Back on Home exits the
  app. Local UI (panels, dialogs, the drawer) still unwinds first.
- Finishing the **setup wizard** lands on Home.
- Channels remains the complete channel browser, with its existing immersive,
  list and grid layouts. Guide remains the full timeline; Recordings remains the
  recording-management destination. Home complements them.
- Preserve warm live and recording playback returns. Selecting the current
  playback card returns to that session without retuning or reopening it.
- The Appliance HOME, GUIDE and wake behavior does not change in this work; it is
  decided when the Appliance build is polished.

## 2. Header

- No "Home" heading.
- Top right: quiet date, wall clock, then View options at the far right.
- Use locale and the existing clock-format preference; no seconds.
- **The header scrolls with the content. It is not fixed.**
- View options is a working Home-context control, not a placeholder or a feature
  available only through global Settings.

## 3. Content order and hierarchy

Sections from top to bottom; an empty section is omitted, never shown as an
empty heading or placeholder:

1. Current activity
2. Recent channels
3. On now
4. Browse by genre
5. Continue watching
6. Recently recorded
7. Upcoming recordings

### 3.1 Current activity

- Prominent return card for **currently playing** content, not merely the last
  watched channel. Supports live TV and recording playback, including paused
  playback where the session still exists.
- Active recordings join this area.
- One item is not stretched into filler for a full row.
- Live viewing stays the main priority. Recording states don't all get equal
  prominence.
- **One card per programme:** when the watched channel is also recording, or the
  running recording itself is playing, show one playback card with a recording
  mark, not two cards.

### 3.2 Recent channels

- Compact square picon tiles in **viewing-recency order**, newest first.
- The same history as the player's recents (one store, see
  [section 4](#4-data)). Home shows up to 8 tiles, with no "More" card.
- Filtered by the shared channel scope, like the player's recents.
- The channel that is currently playing is left out; it is on the current
  activity card.
- No favourites dependency; the app has no favourites feature.
- No progress bars on these tiles.
- No separate description panel or blank details area for this row.
- Labels and focus effects stay visible without overlapping the next section.
  Missing picons need an identifiable fallback; accessibility names always remain
  available.
- Focusing a tile does not tune or change recent history.

### 3.3 On now (immersive row)

- **The existing Channels immersive view is the composition and interaction
  reference**, with small adaptations for Home:
  - Home has another row underneath.
  - Programme artwork takes priority.
  - Programme information may be reduced for vertical space (open, decided with
    the mockup).
- The immersive list and its motion follow the
  [Android TV immersive-list guidance](https://developer.android.com/design/ui/tv/guides/components/immersive-list)
  and the design kit, together with the Channels reference. No separate
  shelf-local backdrop restriction or new preview composition.
- **Backdrop:** with nothing playing, the focused programme's artwork fills the
  backdrop as in Channels. With warm playback, the video stays the background
  under the usual browse scrim and no artwork backdrop is drawn, as in Channels
  (`ChannelsImmersiveList.kt`, `LocalWarmPlayback`). Cards keep their artwork.
- **No On now/Primetime tabs in phase 1.** This row shows On now content.
- Geometry and motion follow [3.3.1](#331-immersive-row-reference), which is
  measured from Google's guidance, not interpreted.

#### 3.3.1 Immersive row reference

Measured 2026-10-30 from the guidance page's spec image and its two videos
(`immersive-list-motion.mp4`, 4.7 s, and `immersive-list-scroll-motion.mp4`,
5.4 s, both 1920×1080 at 60 fps). Local copies with frame sheets:
`build/reference/immersive-list/` (ignored; re-download from the guidance page).
Values are dp on the kit's 960×540 canvas; durations are measured from frame
differences and are approximate (±1 frame of 17 ms).

**Expanded layout (row focused):**

- Background image 758×426, anchored top-right (starts 202 dp from the left
  edge), 16:9, under the kit's cinematic scrim; the subject sits top right and is
  never cropped full-screen under the content.
- Content block (eyebrow with rating and facts, title, two-line description) at
  the 58 dp start inset, above the row.
- 40 dp between the content block and the card row.
- Card row at about 344–454 dp from the top: 16:9 cards about 196×110, the
  focused card scaled 1.1 with a border (our shared focus glow).
- 86 dp below the row: the next row peeks in at the bottom edge.

**Moving within the row (Left/Right):**

- The focused card stays at the row's start position; the row scrolls under it
  (about 600 ms, settling slowly).
- Content block crossfades to the new item in about 150–230 ms, starting with the
  key press.
- Background crossfades to the new image in about 270–450 ms, starting with the
  key press; there is no blank or black frame between images.

**Entering the immersive row from the row above (Down):**

- The page does **not** scroll the row to the top. The immersive row stays
  where it is (it moves down by only about 20 dp).
- At the same time, the row above fades out while sliding up, and the background
  plus content block fade in above the row, the content block sliding up into
  place. The row's own heading ("Trending" in the video) is replaced by the
  content block.
- Most of the change happens in about 130 ms; everything settles by about
  330 ms.
- Rows that aren't focused are dimmed.

**Leaving the immersive row upward (Up):**

- The exact reverse: the background and content block fade out (the content block
  sliding down), the row above slides down and fades in, and the immersive row
  returns to an ordinary dimmed row with its heading. Most of it happens in about
  100–130 ms; settled by about 350 ms.
- The row above shows its focused card's title under the card.

**Not shown in the videos:** leaving the immersive row downward to the row below
(on Home, the genre row) and entering it from below. Rule for Home: the same
transitions mirrored. Down collapses the immersive row in place (background and
content block fade out, heading returns, row dims) while the row below moves up
into the focus position; Up from below reverses it. The owner confirms this with
the mockup.

**Checks a capture or review must pass:** the immersive row doesn't jump to the
top of the screen on entry; the row above disappears by fading and sliding, not
by a page scroll; no frame shows the background without the content block or
the reverse; Left/Right keeps the focused card at the start position.
- Programme artwork and title lead; channel identity, broadcast time and progress
  are supporting information.

### 3.4 Browse by genre

- Compact, understated, rounded **text cards in genre colours**, using the Guide
  palette (`TvGenreColors`). Not neutral pills, image cards or icon cards.
- The genre families are the Guide filter's (`ProgrammeCategory`), in the Guide
  filter panel's fixed order.
- Show only families with at least one programme in the shared scope within the
  next 24 hours of cached EPG. If the server sends no genre codes, the row is
  omitted.
- Genre shortcuts replace proposed separate sports shelves.
- OK opens the Guide at now with that genre selected, using the Guide's existing
  visible, clearable genre filter; no second filter engine. Focus moves to the
  first matching programme, on now or the nearest upcoming one, in channel order,
  because the Guide dims rather than removes nonmatching programmes.
- The Home shortcut **replaces** the Guide's genre selection; the previous one is
  not restored afterwards.
- Returning to Home restores the initiating genre card and viewport.

### 3.5 Recording shelves

- Ordinary recording artwork cards, not another immersive section.
- **Continue watching:** recordings that offer a resume position, with exactly
  the rule the Recordings resume dialog uses (`offeredResumePosition`). Newest
  recording first, up to 10. Progress shows the viewer position (orange).
- **Recently recorded:** finished recordings from the last 7 days that haven't
  been started, newest first, up to 10. A recording in Continue watching is not
  repeated here.
- Both rows end with an **All recordings** card that opens Recordings → Archive.
- **Upcoming recordings:** one quiet summary card, not a row, for example
  "Next: Tatort · Today 20:15 · 3 more scheduled". Omitted when nothing is
  scheduled.
- Preserve existing recording admission, resume/start-over choices and
  unavailable-recording behavior. A current recording playback return stays warm.
- Preserve existing progress semantics: viewer position (orange) and broadcast or
  recording time (cyan) are never confused.

### 3.6 OK and Hold OK on each card

Each card behaves like the screen it comes from.

| Card | OK | Hold OK / Menu |
|---|---|---|
| Current playback | Return to the running session, warm | As in the player |
| Recent channel | Tune | The channel's menu, as in Channels |
| On now | Tune, as in Channels immersive | The programme's menu (Details, Record, …) |
| Active recording | The recording choice dialog (below) | Details, Stop recording, … |
| Continue watching, Recently recorded | As in Recordings (resume or start over as today) | As in Recordings |
| All recordings | Recordings → Archive | None |
| Upcoming summary | Recordings → Schedule | None |
| Genre | Guide at now with that genre | None |

**Recording choice dialog:** OK on an active recording opens the existing
centred resume dialog (`RecordingsScreen.kt`, `CentredDialog`) with up to three
buttons: **Watch live** (focused first; tunes the channel), **Resume from …**
(only with a resume position) and **Play from start**. The same dialog replaces
the current OK behavior on items in Recordings → **Recording now**.

## 4. Data

- **Channel scope:** the shared selected channel scope, not a Home-only copy. If
  the selected scope becomes invalid, fall back to All channels; use a first-valid
  fallback only if the catalog can lack All. An intentionally empty but valid
  scope is not invalid and must not silently become All. Scope selection is in
  View options.
- **On now:** current programmes in ascending channel-number order, not recency or
  recommendation ranking. Keep the existing comparator for ties, subchannels and
  unnumbered channels. Ended programmes must not stay actionable as current
  broadcasts.
- **Discovery size:** the full eligible scope, loaded lazily with bounded SDK
  requests rather than an arbitrary top-N. Paging and batch sizes are
  implementation choices.
- **Recent-channel history:** one store (`LastPlayedChannelStore`) for the player
  and Home, kept **per server profile** and holding up to 20 channels. Today it is
  global and holds 8 (defect D70); fix that first. The player's recents row
  also shows up to 8 instead of 3.
- No recommendation engine, inferred "live event" taxonomy, predictive tuning,
  preview subscriptions or additional Home video surfaces. Predictive tuning and
  optional live previews are after 1.0.

### Phase 2: Tonight/Primetime

Deferred, including the evening-window setting and Tonight/Tomorrow presentation.
Data rules kept for that phase:

- Programmes overlapping a configurable evening window, ordered by channel number,
  then start time; multiple programmes per channel are allowed.
- Handle midnight-crossing windows, timezone/date boundaries and DST.
- Tonight changes to Tomorrow after the window ends, not when it overlaps On now.
- Do not move focus automatically at rollover.
- Short programmes and news stay in; no minimum-duration filter was approved.

How phase 2 is presented remains open; the removed tabs are not a requirement.

## 5. Options, loading and interaction

- Home View options: show/hide for the sections and the shared scope selection,
  persisted through existing preferences. No row reordering, row editor or
  dashboard framework.
- Layout-matched skeletons only for genuinely unavailable data. Cached content is
  never covered by blocking loading UI during refresh.
- Distinguish no history, no recordings, missing EPG, missing artwork,
  disconnected, loading and unavailable content. Recovery stays reachable even
  when optional sections are hidden. Offline follows the shared pattern (one-off
  notice through `NoticeCenter`, rail Settings badge, point-of-use errors).
- **Initial focus:** the first card of the topmost visible section. With playback,
  that is the playback card; without playback but with a running recording, the
  recording card; otherwise the first recent channel; with no history, the first
  On now card; with no channels, the recovery or Connection settings action.
- **Returning** from the player, Guide, drawer, options or a dialog restores the
  card and viewport left from. If that card is gone: the next card in the same
  section, else that section's first card, else the initial-focus rule. A delayed
  restoration must not steal focus from the drawer or recovery controls.
- Focus never starts playback or recording actions. A key that reveals or moves
  focus is consumed so it cannot also activate the new target.
- Stable focused items survive refresh.
- Complete D-pad reachability, predictable Back, accessibility names, long
  localized labels, 1.3× font scale and reduced motion are part of the TV floor.
- Foreground, session, profile and capability guards still apply. EPG work does
  not keep running because a stopped Activity's composition is alive.

## 6. Owner decisions (2026-10-30)

1. Home is the first rail item and opens selected; Back from Channels, Guide and
   Recordings goes to Home; Back on Home exits; the setup wizard lands on Home.
   Appliance HOME key: deferred to Appliance polish.
2. OK and Hold OK per card as in [3.6](#36-ok-and-hold-ok-on-each-card); On now
   tunes on OK.
3. Active recordings (Home and Recordings → Recording now) open the choice dialog
   Watch live / Resume from … / Play from start.
4. Warm playback keeps the video as background; no artwork backdrop then.
5. Recents: one history, per server profile, up to 20 stored; Home and the
   player show up to 8; shared-scope filter; playing channel left out.
6. Genres: Guide families in the Guide's order, only those with programmes in the
   next 24 hours; Guide opens at now and focuses the first match; Home's choice
   replaces the Guide filter.
7. Recording shelves: Continue watching and Recently recorded (7 days) up to 10
   each, ending with All recordings; Upcoming as one summary card; section order
   as in [section 3](#3-content-order-and-hierarchy).
8. Initial focus and duplicate activity as in [section 5](#5-options-loading-and-interaction)
   and [3.1](#31-current-activity).

## 7. Shared components

Reuse, don't copy: `RowEmphasis`, `BrowseContextMenu`, `ProgrammeDetailsPage` /
`ProgrammeDetailsHost`, `CentredDialog`, `ProgressStrip` and the watching-progress
locals, the card focus glow, the picon plate and logo fallback, the Channels
immersive background, `TvGenreColors`, the Guide filter, `NoticeCenter`.

## 8. Engineering consequences

- Home becomes the cold landing screen: the splash rule ("until cached channels
  are drawn") needs its Home equivalent, the startup and baseline profile journey
  is regenerated, and G10 cold-start numbers are measured again (numbers only,
  no budget).
- New strings in US English, then the translation pass. RTL stays deferred.
- Navigation tests, Back tests and the startup composition tests change with the
  new root.
