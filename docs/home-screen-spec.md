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
- Top right: wall clock with the date below it (smaller, muted), then the channel
  group button (shared with Channels and Guide), then View options at the far
  right.
- Use locale and the existing clock-format preference; no seconds.
- **The header scrolls with the content. It is not fixed.**
- View options is a working Home-context control, not a placeholder or a feature
  available only through global Settings.

## 3. Content order and hierarchy

Sections from top to bottom; an empty section is omitted, never shown as an
empty heading or placeholder:

1. Current activity (heading "Right now": playback, running recordings or both)
2. Recent channels
3. On now
4. Browse by genre
5. Continue watching
6. New recordings
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
  [section 4](#4-data)). Home shows up to 8 tiles, then an **All channels** end
  card that opens Channels.
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
- Programme artwork and title lead; channel identity, broadcast time and progress
  are supporting information.
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
- Content block crossfades to the new item in 225 ms (`components/FocusedInfo.kt`), starting with the
  key press.
- Background crossfades to the new image in about 225 ms once focus has settled for 300 ms, settling from
  2% larger toward its top end corner as on the Google TV launcher; there is no blank or black frame between images.

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

**Device reference, all four directions.** The videos don't show the row below,
so the remaining transitions were measured on the G10's own Google TV home
screen (2026-10-30, `screenrecord` 1280×720, variable frame rate, about 60 fps
during motion). Its immersive row is "Top picks for you": a hero carousel above
it, ordinary rows ("Your apps", then poster rows) below. Durations run from the
first changed frame; positions are dp on the 960×540 canvas. Recording and frame
sheets: `build/reference/immersive-list/g10/` (ignored).

The rule that ties all four together: **the expanded immersive row has one fixed
slot** (its cards at about 356 dp, background top right, content block above),
and **an ordinary focused row has the top slot** (heading at about 76 dp, the row
above peeking dimmed at the top edge). Each move scrolls the page by whatever
distance puts the newly focused row into its slot. The immersive row never
collapses in place while the rest of the page stays still.

- **Down into it from above** (G10, from the hero): the page scrolls up about
  140 dp until the cards sit in the slot (row from about 496 to 356 dp, in place
  after about 200 ms). The hero's text fades out while moving up (gone after about
  170 ms), the background crossfades, the content block fades in from about
  230 ms and is opaque by about 300–370 ms; the facts line appears last. Settled
  after about 470 ms. In Google's video the row above is an ordinary row and the
  row was already almost in its slot, which is why it moved only about 20 dp
  there.
- **Down out of it to the row below:** in the first frame (about 35 ms) the
  content block is gone and the immersive row starts to fade and move up. The page
  scrolls the next row from the bottom peek (heading about 480 dp) to the top slot
  (about 76 dp), decelerating, in place after about 370 ms. The collapsed former
  immersive row ends dimmed, peeking at the top edge. The background fades to the
  plain page background, gone after about 450 ms. Settled after about 480 ms.
- **Up into it from the row below:** the reverse. The page scrolls the row below
  from the top slot back down to the bottom peek (heading about 478 dp); the
  background starts fading in after about 50 ms, the content block fades in
  between about 150 and 300 ms. Settled after about 450 ms.
- **Up out of it to the row above** (G10, to the hero): the page scrolls down,
  the hero text comes in from the top, the cards move back to about 500 dp, and
  the background crossfades from about 250 ms. Settled after about 550 ms.

On Home the row below is the genre row, so Down from On now uses the
"Down out of it" transition: genres take the top slot, On now collapses to a
dimmed row at the top edge, and the background fades out.

**Home rules (owner 2026-10-31, from the G10 frames; these override the
measurements above where they differ):**

- The row's heading ("On now") stays small directly above its cards while the
  row is expanded; it does not grow and is not replaced by the content block.
  Only ordinary focused rows get the larger heading.
- While On now is expanded the header row (clock, buttons) and everything above
  have scrolled off the top; a small muted up chevron at the top centre shows
  that more is above.
- Down into On now: the rows above scroll up off the top as one page movement,
  fading as they leave (about 90 ms to clear), and the row moves up into its
  slot with them. The content block is anchored at its bottom just above the
  heading and grows upward: it slides up while fading in, its lines appearing
  bottom-anchored; the background fades in behind it. Settled within about
  470 ms. The page move uses 410 ms cubic-bezier(0.46, 0, 0, 1), fitted to
  G10 frames 81–103 (PTS 5.142678–5.642578 s); the 240×135 row track runs
  from y≈125 to 91 px, with 0.87 px RMS fit error. The fitted onset is 5.150 s;
  the variable-frame-rate reference has a 100 ms gap before frame 82, so onset
  is approximate. Background/block retain their 100 ms delay and 370/270 ms
  fades. Layout changes in the departing caption are compensated at the row
  anchor, not eased again into the page destination. Up out travels once
  back to the ordinary slot, without overshooting. Up into On now from genres
  starts the info block 16 dp above rest after 150 ms; it moves down and fades
  in over 300 ms, after the rows have started. It is never a crossfade in place.
- Slot: cards at about 344 dp, with 16 dp between heading and cards, so the next row's heading and the top of its
  cards peek at the bottom edge.
- Every shelf fills the content width with whole cards and the next card peeks
  into the right margin: 4 cards on 16:9 shelves, 6 square tiles plus the end
  card on Recent channels, 2 half-width cards on Right now.

**Checks a capture or review must pass:** entering the immersive row puts its
cards in the fixed slot, never at the top of the screen; leaving it downward
scrolls the next row to the top slot and leaves the collapsed row peeking at the
top edge; settled frames show the background and content block together or
neither; the content block is never more opaque than the background behind it
(the background may lead on entry and trail on exit, as measured, e.g.
leaving downward the block is gone in the first frame and the background fades
over about 450 ms); Left/Right keeps the focused card at the start position.

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
- **Upcoming recordings:** next-first scheduled entries, up to 10, in the
  text-card kit (56 dp high) on the same neutral grey surface as the other
  end cards, each with a title and quiet day/time • channel line. Genres retain
  their coloured surfaces. End with **All scheduled** and its event icon,
  opening Recordings → Schedule.
  OK on an entry opens Schedule with that row focused and scrolled into view;
  if it has left the schedule, use the tab's normal initial focus. Back follows
  the existing drawer → Home path and restores the same Upcoming card.
  Omit the row when nothing is scheduled.
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
| Upcoming recording | Recordings → Schedule, focused on that entry (normal initial focus if gone) | None |
| All scheduled | Recordings → Schedule | None |
| Genre | Guide at now with that genre | None |

**Recording choice dialog:** OK on an active recording opens the existing
centred resume dialog (`RecordingsScreen.kt`, `CentredDialog`) with up to three
buttons: **Watch channel** (tunes the channel), **Resume from …**
(only with a resume position) and **Watch from beginning**. Without a resume
point the start action is **Play recording**, with the play icon; with one,
Watch from beginning uses the replay icon. Resume takes initial
focus when offered; otherwise Play recording does. That default action is
filled, and the other buttons are outlined. The same dialog replaces
the current OK behavior on items in Recordings → **Recording now**.
The focused action's Ends at time belongs in the eyebrow, not a body line;
changing focus must not resize the dialog. Context-menu Resume keeps its label
and shows the offered position as a smaller quiet second line ("from 12:34").
Its icon and compact text stack are vertically centred; single-line actions
keep their existing height. Running recording menus (Home and Recordings)
offer **Watch channel**, with the live-TV icon, after the play/resume actions.
It uses the choice dialog's channel-tune path, including channels outside the
Home scope; finished and scheduled recordings do not offer it.
Right now uses only the REC badge's dot as its running-recording mark, alongside
the animated playback icon when playing. Recording-only cards also use one REC;
finished recording playback has the neutral Recording badge, not REC or a red dot.

## 4. Data

- **Channel scope:** the shared selected channel scope, not a Home-only copy. If
  the selected scope becomes invalid, fall back to All channels; use a first-valid
  fallback only if the catalog can lack All. An intentionally empty but valid
  scope is not invalid and must not silently become All. Scope selection is the
  header's channel group button. Open (owner 2026-10-31): how channel groups
  should work across Home, Channels, Guide and the player's surfing is to be
  rethought after Home; until then Home follows the shared group.
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

- Home View options: show/hide for the sections; the shared channel group has
  its own header button like Channels and Guide. Section visibility is
  persisted through existing preferences. No row reordering, row editor or
  dashboard framework.
- Rows that have not arrived show nothing for the first 1.5 s, then a spinner with a
  short line under it. No layout skeleton: sections can be hidden and rows come and
  go, so the page's shape is not known yet. Cached content is never covered by
  blocking loading UI during refresh.
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
- Up from the first row reaches the channel group button. Last-row scroll
  targets stop at the content end; no artificial trailing room pulls a bottom
  row down when focused.
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
   Watch channel / Resume from … / Watch from beginning.
4. Warm playback keeps the video as background; no artwork backdrop then.
5. Recents: one history, per server profile, up to 20 stored; Home and the
   player show up to 8; shared-scope filter; playing channel left out.
6. Genres: Guide families in the Guide's order, only those with programmes in the
   next 24 hours; Guide opens at now and focuses the first match; Home's choice
   replaces the Guide filter.
7. Recording shelves: Continue watching and Recently recorded (7 days) up to 10
   each, ending with All recordings; Upcoming as text cards plus All scheduled; section order
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
