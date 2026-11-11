# Changelog

## Unreleased

- Play/Pause in the player pauses or plays and shows only the banner, like OK, instead of opening the full controls.
- Rewind and Fast forward move through live TV's timeshift buffer and through recordings while the picture keeps playing. Each press doubles the speed, from 2 up to 32 times. Holding the button skips like Left and Right. Play or OK continues from there, Back returns to where you were, and the other button turns around. Without a timeshift buffer, live TV says why, as OK does.
- With the controls hidden, Left skips back 10 seconds and Right forward 30 seconds. The seek bar still moves 30 seconds both ways.
- The Record button records the program you're watching, or the focused one in the guide, on Home, in Channels and on the details page. If it's already being recorded, the details open at *Stop recording* or *Cancel recording*. If it can't be recorded, a notice says why.
- The Recordings button opens Recordings from anywhere in the app.
- The Appliance companion has *Open recordings* under Functions, set to the Recordings button out of the box.
- The Appliance companion no longer opens Tvheadend Player when the TV turns on unless you turn on *Open when the TV turns on* under Functions, and turning on its accessibility service no longer starts the player. If you never changed that switch, turn it on again after the update.
- Switching connections shows its progress on its own page and can be canceled; leaving Settings doesn't stop it. If the connection can't be reached or rejects sign-in, the page says why and your previous connection stays in use. After a successful switch, *Go to Home* takes you straight there.
- With more than one connection, *Channel groups* and *Stream profile* name the connection they apply to.
- A missed or failed recording that never wrote a file offers *Remove entry* instead of *Delete recording*.
- Settings > Connection can keep several connections, including different users on the same server. Choose one to use it, give it a name, or remove it.
- A removed recording no longer hides another recording of the same program, and retention cleanup no longer repeats the “Recording deleted” notice.
- Recent channels, the *Continue watching* order, channel tags, channel aspect settings, the selected stream profile and audio choices start fresh after this update; they are now kept per connection.

## 0.4.14

### Recordings

- OK on a recording that's still running but can't be played yet, like a
  Matroska recording, now says it can be played once it's finished and offers
  *Watch live*. Before, it tried to play it and showed *Recording
  interrupted*, although the recording kept going (#20). Playing a recording
  while it's still running works with MPEG-TS recordings.
- The dialog doesn't offer *Play recording* for any recording that can't be
  played, and when playback is refused, the error says why instead of
  *Recording interrupted*.
- The red dot now only means recording now. Finished recordings show a
  library icon in the Guide, the channel schedule and the recording lists
  (#21).
- Scheduled recordings from a series recording or repeating timer show stacked
  clocks instead of a single clock.

## 0.4.13

### Home

- After an update, Home shows *What's new in 0.4.13* next to the clock. It
  opens *What's new*, so you can see what changed and what to try. It's there
  for the testing phase on Google Play and goes away once you open it, or after
  14 days. It replaces the dot and the *New* badges in Settings from 0.4.12.
- The *All channels* card at the end of *Recent channels* has the same caption
  as the channel tiles.

### Recordings

- OK on a recording that's still running offers playing it first: *Resume
  from …* or *Play recording*, then *Watch from beginning*, then *Watch
  live*. The dialog is only as wide as its buttons need.
- The timeline marks where the program starts and ends with a flag and a
  finish flag instead of dots.
- Up from the seekbar opens the marks at the nearest real mark instead of the
  start of the recording.

### Live TV

- In the channel schedule, day tabs that don't fit fade out at the edge
  instead of being cut off, and the edge always shows part of the next day
  when more follow.

### Media controls

- Apps that show what's playing on your TV, like the Home Assistant companion
  app, now get the channel, the program you're watching (also with timeshift),
  episode, start and end times, genre and artwork. With notification access
  they can also pause, play and switch channels.
- Next and Previous from media controls switch channels during live TV, in the
  same order as the remote's channel keys, including while the picture keeps
  playing behind the menus.

### Settings

- *About* shows *Version 0.4.13* under its name instead of the bare number.
- *About* has one *Licenses* entry for the GNU GPL v3 and the third-party
  notices instead of two, and the note about machine-translated text is gone.
- Entries that open a full-screen page no longer show an arrow. *Set up
  connection* and the appliance companion entry show where they take you.

## 0.4.12

### Settings

- *Settings → About → What's new* shows the changes of the last few versions.
  After an update, a dot on Settings in the side menu, and *New* on *About*
  and *What's new*, point you there until you open it once.

## 0.4.11

### Home

- *Continue watching* shows the recordings you played last first. Recordings
  this TV hasn't played yet follow by recording date, as before.

### Settings

- *Settings → Appliance* now opens like every other settings page. Without the
  appliance companion it shows *Not installed* and an *Appliance setup* entry
  that opens the setup steps full screen, with the QR code next to the text.
  Before, the page slid off the side of the screen and cut the QR code in half.

## 0.4.10

### Recordings

- The timeline shows ad breaks, cuts and chapters from Tvheadend's .txt and
  .edl files or the recording itself, and where the program starts and ends
  inside the recording's padding. Small symbols above the bar show them while
  the seekbar is focused (#8).
- Up from the seekbar opens the marks at the nearest one, Left and Right move
  between them, and each shows its name, such as *Ad break*, *Ads end* or
  *Program starts*. OK jumps there (#10).
- When playback reaches an ad break, a *Skip ad break* button counts down for
  10 seconds. OK jumps to the end of the break, Back dismisses it (#9).
- Settings → Playback has a new *Recording marks* group: skip ad breaks
  automatically or turn the button off, skip cuts, and choose where playback
  continues after a skip.
- Cuts marked in .edl files are skipped, and mute spans stay silent.

### Settings

- Back from a settings page now returns focus to the row that opened it.

## 0.4.9

### Player

- The *Audio* button in the player controls now opens a list where moving
  through the tracks no longer switches the sound; OK picks the track. The
  remote's own Audio button still switches as you move. The *Subtitles* button
  is gone from the controls; subtitles stay under *Playback options*.
- Leaving the player for a browse screen while the channel keeps playing no
  longer makes the TV switch its refresh rate back and forth, for example on an
  NVIDIA Shield with *Match content frame rate* (#14).

## 0.4.8

### Player

- Playback no longer pauses when the TV switches its refresh rate, for example
  on an NVIDIA Shield with *Match content frame rate* (#15).
- With *Audio passthrough* on, the sound now moves to headphones when you
  connect them, and back to the AV receiver when you disconnect them (#16).

### Settings

- The password field no longer suggests or learns words from the keyboard, and
  a *Show password* checkbox below it replaces the eye button.

## 0.4.7

### Player

- With *Match content frame rate* set to Always, the TV now switches its
  refresh rate on Android 11 and older too, for example on the NVIDIA Shield
  (#14).
- A Guide press from the appliance companion opens the channel schedule beside
  the picture during live TV, and the full Guide everywhere else.

### Settings

- Settings → Appliance is always there. Without the companion, it explains what
  the companion does and shows a QR code that leads to its setup steps.

### Appliance companion 0.2.0

- New Functions section: choose which remote button opens live TV and which
  opens the Guide, or set none. Choose a function, then press the button on
  the remote.
- Opening the player when the TV turns on can be switched off, if you only
  want the buttons.
- The TV button still opens live TV. The Guide button now opens the Guide.
- The companion now tells you when the TV keeps its accessibility service from
  running, and how to fix that.
- A downloaded update now says it installs in standby, or you can pick
  Update now.
- Setup steps and TV notes are in the README under Appliance companion.

## 0.4.6

### Appliance companion

- The appliance build is now a separate app, Tvheadend Player Appliance, that
  works alongside the regular player from Google Play or GitHub. It makes the
  player behave like the TV's own tuner app: it starts the player when the TV
  turns on, catches remote buttons the manufacturer keeps for its own apps, and
  can replace the home screen.
- The companion updates itself from GitHub releases while the TV is in standby.
- Settings → Appliance in the player opens the companion's setup screen when
  the companion is installed.
- On a TV with the old appliance build, uninstall it before installing the
  player and the companion. Otherwise Android turns the old app into the
  companion and keeps its data.

### Building

- Debug builds now install as `at.bernhardberger.tvhplayer.debug`, next to the
  release app, with their own icon and name.

## 0.4.5

### Player

- Holding Back stops playback while watching. With a panel or menu open, it
  just goes back one step (#13).

### Navigation

- Outside the player, Back now acts when you release it. Held presses act once,
  and canceled presses no longer move you back.
- Leaving the player with Back no longer opens the side menu on Home; focus
  returns to the card you played.

## 0.4.4

### Settings

- CH+/− now page through Settings lists a screen at a time, as they already do
  in Channels, the Guide and Recordings.

### Recordings

- Problems no longer lists recordings Tvheadend counts as removed: ones you
  deleted, and ones whose file is gone (#4).

## 0.4.3

### Channels and Home

- The Channels Grid is now a wall of cards, without the program info above it or
  a backdrop. Its header scrolls away with the rows. If you already use Grid,
  you'll get this layout on update. Immersive, List and Grid remain available.
- Home's channel logo now lines up with the page's right margin.

### Progress and time

- Progress bars are orange for what you're watching, cyan for other programs on
  air and red for running recordings. This applies to Home cards, Recordings,
  details, all three Channels layouts, the Guide headline and channel schedules.
- The program headline on Home's On now, Channels and Guide now shows the time
  left to the right of its progress bar.
- Start-up buffer amounts and reconnect countdowns now use the system's number
  and time-unit formatting for the app's language.

### Side panels

- Browse panel lists now fade at the bottom like the player's lists.
- Opening a panel near the top of its list no longer cuts off the first row
  under the title, for example in Guide's Day and time panel.

### Recordings

- Recordings you deleted no longer show as failed under Problems (#4).
- A running recording now has a progress bar in its info pane and menu.
- Navigation markers now also appear at commercial-break and cut boundaries
  when the server provides them. Nothing is skipped automatically (#6).
- MKV and MP4 recordings with chapters stored in the file now get navigation
  markers from those chapters, unless the server already provides commercial
  breaks or cuts for them (#7).

## 0.4.2

### Home and Channels

- Right at the end of a Home row now stays on the last card instead of jumping to
  the header.
- Holding a direction on Home's On now and on Channels now keeps the last programme
  info and picture on screen until you stop, instead of updating on every card
  along the way.
- Cards leaving under the side menu are now dimmed as a whole instead of fading out
  toward the screen edge, and their text is hidden so it can't clash with the menu.
- On now's Next line now shows when that programme is set to record.
- Returning to Home with the side menu still open now shows the info and backdrop of
  the On now card you left, not those of the row's first card.
- Programme info, the Channels list's picture, the Guide's headline and the backdrop
  now change more softly, ending together; a new backdrop starts right away after a
  single press, even to a card you haven't visited yet, and grows slightly into place.
- The channel logo above the Channels grid is smaller and no longer runs into the
  row behind the headline.
- While Home waits for its first rows it shows nothing at first, then a spinner
  with a short line, instead of a skeleton that often didn't match the page.
- The Channels grid now shows the bottom of the row above as a faint fade while
  you can scroll back, so you can tell you're down the list. Rows leaving at the
  top dissolve into it instead of flickering.

## 0.4.1

### Setup

- First setup opens with a new Welcome screen that grows out of the startup
  logo. The setup steps after it show the app's name and version in the corner.
- Finding servers is now a button on the Server step. Results get their own
  screen, and picking one goes straight on to Account. If the search finds
  nothing, you can go straight to entering the address.
- Long lists in setup now use the full height of the screen instead of fading out.
- The action a TV hands focus to when a step opens now always looks focused,
  including Start watching at the end of first setup.
- Pictures and channel logos no longer stay missing after first setup until the
  app is restarted.
- Pressing OK on a text field no longer skips to the next field on TVs with
  Gboard.
- Searching for servers no longer offers an address that can't connect on servers
  that also announce IPv6. It now finds their IPv4 address, including on Android
  12 and older.

### Home

- Coming back to Home now keeps your place instead of showing the top and then
  jumping to the row you left.
- Starting the app with something recording no longer opens the side menu by
  itself or leaves nothing focused.
- The programme info on On now no longer jumps while you scroll a row.
- Home no longer flashes the empty Start watching screen on launch while it waits
  for the guide.

### Channels and Home cards

- Cards keep their logo and programme until the picture has faded in, instead of
  going black while it loads.
- Cards leaving under the side menu now fade out properly.

### Guide

- Tall channel logos in the Guide's headline no longer run into the timeline.

### Player

- The Close button in an empty channel schedule now looks focused.

### Everywhere

- In German, the Guide is now called TV-Programm instead of Programmführer.

## 0.4.0

### Home

- The app now opens on a new Home screen, the first item in the side menu. It
  shows what's playing or recording right now, your recent channels, what's on
  now, genres, recordings to continue, new recordings and upcoming recordings.
  Sections without content stay hidden.
- OK on a card does what it does on the screen it comes from: a channel tunes, a
  recording plays, and a genre opens the Guide at the first matching programme.
- View options on Home hide the sections you don't want, and the channel group
  button chooses which channels Home draws from.
- Back from Channels, the Guide or Recordings now goes to Home, and Back on Home
  leaves the app.

### Guide

- Dense rows are the new default, so more channels fit on screen. Regular rows
  are still in View options, and a Guide view you already changed stays as it is.

### Recordings

- Choosing a running recording now asks whether to watch the channel, resume, or
  watch from the beginning. Its menu offers Watch channel too.
- Resume in a recording's menu shows the position it resumes from.

### Player

- The channel row in the player shows up to 8 recent channels instead of 3.

### Settings

- The Show Guide in the side menu setting is gone. The Guide is always in the
  side menu, even if you had turned it off.

### Everywhere

- Recent channels now belong to one server: changing the server clears them.
- Error messages show their title and icon in the error color, and browse screens
  no longer show a visible seam beside the side menu when an error appears.

## 0.3.2

### Settings

- Settings → General → Channel numbers turns channel numbers off in Channels,
  the Guide and the player, for remotes without number keys.

### Everywhere

- Channel and tag logos that the server lists as web addresses now show too,
  so logos appear even when the server's image cache is off. The TV loads
  them from those web hosts directly, as the privacy policy describes.
- After a cold start, the saved program guide appears sooner, and the app is
  ready sooner.
- Saved channel logos appear together right after start instead of one by one.

### Accessibility

- TalkBack says whether a switch is on or off in Settings, in the player's
  Stats for nerds, and in the View options of Channels and the Guide.
- Settings pages two levels deep, such as Playback, are no longer missing
  for TalkBack and other accessibility services.

## 0.3.1

### Settings

- Settings → About → Privacy policy shows the privacy policy on screen, in the
  same reader as the license.

### Everywhere

- In the side menu, the Full screen, Pause and Stop buttons for what is playing
  sit in a row under the title, and the title fades in beside the channel logo
  as the menu opens.

### Recordings

- Leaving a folder for the one inside it fades the parent list out, so its
  arrows no longer rest beside the menu.

### Fixes

- After a server restart, live TV keeps retrying to reconnect with its usual
  backoff instead of stopping after two attempts, and shows the reconnecting
  ring instead of "Playback stopped" between attempts.
- A channel kept tuned in the background is released when the server restarts
  and drops its timeshift, and the Interrupted notice follows.

## 0.3.0

### Getting started

- Two builds from one release. **Play** is the normal app, and the one to use
  every day. **Appliance** is for a TV that should start straight into the app:
  HOME opens it, and the GUIDE key does too once you turn on its accessibility
  service in Android settings. The Appliance build is a work in progress: expect
  rough edges and changes between releases. It installs next to the Play build,
  and only it has that service.
- First-time setup in three steps: Server, Account and Connect. The app looks for
  Tvheadend servers on your network first, and you can type an address if none
  answers. It tests the connection before saving and keeps your saved password
  until a new connection works. Settings → Connection → Set up connection opens
  the same steps to change an existing connection.
- Playback no longer starts when the app opens. To turn it back on, open
  Settings → Playback and switch on **Start playback when the app opens**.

### Everywhere

- Hold OK, or press Menu, on a channel, program or recording to open a menu.
  Record starts at once. Stop, Cancel and Delete ask you to confirm first.
  Program menus offer Play recording when a finished recording exists, and the
  menu of what is playing offers Continue watching, and Watch live while you are
  behind live.
- One program details page, shared by the Guide, Recordings and the player, and
  opened from the player's (i) button. It shows the program's picture across the
  screen and moves between its sections in one smooth scroll.
- The item you are playing is marked with animated sound bars, and your progress
  is orange, on every screen that shows it, also while paused.
- A focused card glows in the colors of its picture or logo and grows slightly.
- The side menu shows what is playing at the top, with full screen, pause and
  stop. OK on it takes you back to the picture.
- Logos and program pictures fade in as they load. Logos are recolored where they
  would vanish on the background behind them.
- Browse screens drop their titles, so more fits on screen.

### Channels

- View options: Immersive, Grid or List, a Program art switch for the picture
  layouts, and a Channel names switch for Immersive and Grid. Picture cards carry
  the channel's logo in the corner.

### Guide

- A reworked Guide. A headline above the grid shows the focused program with its
  picture. The header has Tags, Filter and a Today · Now button that opens a Day
  and time panel. View options sets the rows (Regular, or Dense with titles only),
  Show program info and Genre colors.
- Programs are tinted by genre, and Filter dims the other genres. Cells show start
  times, a focused cell gets a ring, and recording marks appear on programs that
  were recorded or whose recording failed.
- The Guide opens on the program that is on air on the channel you are watching,
  and Back from the player returns to where you were.
- Up and Down keep your place in time, Left at the first program opens the side
  menu, and Left and Right cross a gap in the program data.
- The Guide search is hidden for now.

### Player

- Type a channel number and it appears where the channel's name stands.
- Seek steps show how far you have moved from where you started, with the target
  time beside it, in live TV and in recordings.
- The channel list peeks out under the player controls, and Audio and Subtitles
  buttons sit beside Info.
- The playing channel's schedule opens in a panel beside the live picture.
- Standard-definition channels show at their intended 16:9 or 4:3 in Auto, and a
  picture format you choose is remembered per channel.
- Clearer error messages in the live player and the recording player. A problem
  shows a title, a reason, a hint when one helps, and a small error number you
  can quote when asking for help. An error screen has at most two buttons: the
  fix, and Close.
- The recording player shows the recording's date.
- The TV's system playback controls work with the app, through a media session.
- AC-3, E-AC-3 and MP3 audio play through a built-in software decoder when the TV
  has none. The TV's own decoders are used first.
- Every audio track is listed with a name and a format.

### Recordings

- Archive, Schedule and Problems are tabs in the header row. Each has a details
  pane beside the list, and a skeleton shows while it loads.
- Archive rows show whether a recording was watched, partly watched or stopped
  early, and what it actually recorded.
- A Recording now folder at the top gathers running recordings. One that runs
  with errors stays in Schedule until it finishes.
- Missed and failed recordings appear in Problems, and one that kept a file can
  still be played.
- A folder's menu marks everything watched or unwatched and deletes what you have
  watched, or everything, after asking.
- Stop a recording that is still running. The app asks first and keeps what was
  recorded so far. Recordings, including ones in progress, resume where you
  stopped.
- Free recording space on the server, shown in Recordings and in Settings.

### Settings

- Settings is ordered General, Connection, Channel groups, Playback, Audio &
  subtitles and About. Rows have two lines, and a row you can't use says why.
  Recordings and Settings reopen a level on the row you left it on.
- About shows the version, the server, the license and the notices.
- **While browsing** chooses what plays behind the menus: the picture, only the
  sound, or nothing.
- **Keep paused TV tuned**: if you leave the app while live TV is paused, the
  channel stays tuned for 10, 20 or 30 minutes so you can continue, or not at all
  (Off). It is available when **Timeshift**, which pauses and rewinds live TV,
  is on.
- **Match content frame rate**, with Always, Only if seamless and Off. The player
  asks the TV for the frame rate the server declares for the stream, so a 50 Hz
  channel can switch a TV that offers 50 Hz. Playback statistics show the output
  mode, the display modes, and the declared and measured frame rate.
- **Tunneled playback**, an experimental setting that is off by default. It sends
  video straight to the TV when the decoder supports it and may briefly
  interrupt the picture. A seek while paused shows the new picture.
- Audio and subtitle preferences for language, format and audio-description
  tracks.
- A 12-hour or 24-hour clock. It follows the system unless you choose one.

### Languages

- The app is available in Chinese (Simplified and Traditional), Croatian, Czech,
  Danish, Dutch, English, Finnish, French, German, Greek, Hungarian, Italian,
  Japanese, Korean, Norwegian Bokmål, Polish, Portuguese (Brazil and Portugal),
  Romanian, Russian, Spanish, Swedish, Turkish and Ukrainian. Choose one under
  Settings → General → App language, or follow the system language.
- Apart from English and German, the translations are machine-generated and
  reviewed by a second model, not by native speakers. Settings → About says so in
  those languages. Corrections are welcome.
- The German interface now addresses you informally ("du").

### Startup and performance

- Saved channel logos appear at startup and while the server is unreachable, and
  the player opens right away instead of waiting for the server.
- When the server can't be reached, the app shows one notice and an Offline
  badge on Settings, and Settings → Connection offers Try again. When the server
  is back, live TV that was playing behind the menus is tuned again.
- The start-up buffer for live TV is tuned per server.
- The app ships profiles that let Android prepare startup and common screens
  ahead of time.
- Under the hood: built on Tvheadend SDK 0.36.0 and HTSP 1.0.0-rc.1. Release
  builds are smaller, and each release ships the mapping files that make crash
  reports readable.

### Fixes

- After a channel change, the old or frozen picture stays covered until the new
  channel's picture is moving. A Pause pressed during the change is kept.
- Switching between live TV and a recording starts a fresh decoder, and a decoder
  that stops taking data is retuned or reopened once. The player shows the
  reconnecting ring while it recovers by itself.
- Audio interruptions recover and playback resumes reliably. A held OK that closes
  a confirmation no longer triggers the item behind it.
- When the server stops sending a live channel, the player shows "Playback
  stopped".
- If the server's answer to a record, stop or delete cannot be read, the app says
  the outcome is uncertain and asks you to check the recording, instead of
  reporting a failure.
- A failed recording request is always shown, also from the player's details. A
  missed or failed recording shows no length it never recorded.
- Back from the recording player returns to the recording you watched.
- Program details list each credited name once per role, and 12-hour times stay
  whole in seekbar rows and narrow Guide cells. Recording details say "Played
  once" instead of "Played 1 times".

## 0.2.13

- Adopt published Tvheadend SDK 0.13.0 for anchored timeshift selections,
  current-boundary clamping and moving live-edge selection. Reversing direction
  at a boundary responds immediately without accumulated overshoot.
- Preserve pause intent while the SDK buffers a seek, including H.264 recovery
  metadata and seek-correlated video output. Deliberate pause no longer triggers
  playing-stream recovery timers.
- Keep programme seek previews usable while playback timing settles, retain
  valid sampled positions beyond a delayed server-status edge, and remove the
  normal-boundary warning. Focused recording thumbs now have the same visible
  ring as live playback.
- Add real-runtime tests with production buffering thresholds, paced media,
  repeated paused/playing seeks and isolated fake credential storage.

Development-device checks confirmed paused-picture updates on tested SD and HD
streams. Programme-window colours and remaining historical-programme header
feedback are still under review; growing-recording playback is not added here.
Media3 1.11.0 and native decoder binaries are unchanged. Signed APKs retain the
product key and require a matching installed certificate for an in-place update.

## 0.2.0

The first GitHub release of Tvheadend Player for TV, an independent Android TV
client for Tvheadend servers. Requires Android 9 or newer and a Tvheadend server.

### Player

- Larger channel logos and clearer channel identity in the player and channel
  shelf, including enlarged text.
- Aligned playback controls and stable timeline layout during seek previews,
  cancellation, completion and unavailable-target feedback.
- A bottom-up channel shelf. Back and Up restore player controls and safe focus;
  Up from the seekbar reaches Go live, and Down returns to the seekbar.
- Consistent live and recording playback overlays, programme information and
  settings panels, with repeat-safe remote input.
- Keep the screen awake during visible, active video playback, and release that
  request when paused, buffering, stopped, backgrounded or playing audio only.
- SDK-owned stream restarts retain the Player and play/pause intent. Timeshift
  samples and previews are segment-scoped: a restart clears old preview
  coordinates instead of applying them to replacement content.

### Browsing And Settings

- Guide coverage recovery, remote focus and programme details handle delayed
  updates and session replacement more safely.
- Recording folders, selection and details retain predictable focus as metadata
  changes. Old action results cannot update a replacement details session.
- Long stream-profile lists remain reachable with the remote and enlarged text.
- Published Tvheadend SDK 0.10.0 includes cache/metadata fixes and same-subscription
  stream restart support, with HTSP 0.9.0. Playback remains on Media3 1.11.0.

### Limits And Installation

- Programme-window timeshift uses a schedule-grade estimate, not a guarantee of
  programme boundaries. Only available buffered history is seekable; missing
  timing or EPG data uses explicit fallback states.
- Automated and offline screenshot checks do not establish physical-TV motion,
  overscan or remote feel. Device acceptance is required before publication.
- The release APK uses the stable product signing key. It cannot update a
  debug-signed installation in place. Removing a debug installation erases its
  app-private settings; do not uninstall it without arranging that transition.
- APK, corresponding application/SDK/native source and checksums are distributed
  together under GPLv3. This project preserves the history and attribution of
  [TVHStream](https://github.com/Preclikos/tvhstream); it is not an official
  Tvheadend project.
