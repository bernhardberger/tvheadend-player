# Changelog

## 0.3.0 (unreleased)

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
  (Off). It needs **Pause and rewind live TV**.
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
