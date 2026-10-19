# Changelog

## 0.3.0 (unreleased)

### New

- Two builds from one release. **Play** is the normal app, and the one to use
  every day. **Appliance** is for a TV that should start straight into the app:
  HOME opens it, and the GUIDE key does too once you turn on its accessibility
  service in Android settings. The Appliance build is a work in progress: expect
  rough edges and changes between releases. It installs next to the Play build,
  and only it has that service.
- Hold OK, or press Menu, on a channel, programme or recording to open a menu.
  Record starts at once. Stop, Cancel and Delete ask you to confirm first.
- One programme details page, shared by the Guide, Recordings and the player.
- Free recording space on the server, shown in Recordings and in Settings.
- Match content frame rate in Settings, with Always, Only if seamless and Off. The
  player asks the TV for the frame rate the server declares for the stream, so a
  50 Hz channel can switch a TV that offers 50 Hz. Playback statistics show the
  output mode, the display modes, and the declared and measured frame rate.
- Saved channel logos appear at startup and while the server is unreachable, and
  the player opens right away instead of waiting for the server.
- When the server can't be reached, the app shows one notice and an Offline
  badge on Settings, and Settings → Connection offers Try again.
- Clearer error messages in the live player. A problem now shows a title, a
  reason, a hint when one helps, and a small error number you can quote when
  asking for help.
- Stop a recording that is still running. The app asks first and keeps what was
  recorded so far. Recordings, including ones in progress, resume where you
  stopped.
- Keep channel after leaving the app, in Settings under Player: live TV stays
  tuned for 10, 20 or 30 minutes after you leave the app, or not at all (Off).
  If it was stopped or restarted while you were away, the app tells you.
- The TV's system playback controls work with the app, through a media session.
- AC-3, E-AC-3 and MP3 audio play through a built-in software decoder when the TV
  has none. The TV's own decoders are used first.
- Tunnelled playback, an experimental setting that is off by default. It sends
  video straight to the TV when the decoder supports it and may briefly
  interrupt the picture.
- Audio and subtitle preferences for language, format and audio-description
  tracks. Every audio track is listed with a name and a format.
- A 12-hour or 24-hour clock. It follows the system unless you choose one in
  Settings.

### Changed

- Playback no longer starts when the app opens. To turn it back on, open Settings,
  go to Player and switch on **Start playback when the app opens**.
- The Guide search is hidden for now.
- Channels, Guide, Recordings and Settings follow the Material for TV margins, and
  the Guide has denser rows.
- Logos and programme pictures fade in as they load.
- The start-up buffer for live TV is tuned per server.
- The German interface now addresses you informally ("du").
- Under the hood: built on Tvheadend SDK 0.35.0 and HTSP 1.0.0-beta.4. Release
  builds are smaller, and each release ships the mapping files that make crash
  reports readable.

### Fixed

- After a channel change, the old or frozen picture stays covered until the new
  channel's picture is moving. A Pause pressed during the change is kept.
- Audio interruptions recover and playback resumes reliably. A held OK that closes
  a confirmation no longer triggers the item behind it.
- When the server stops sending a live channel, the player shows "Playback
  stopped".
- If the server's answer to a record, stop or delete cannot be read, the app says
  the outcome is uncertain and asks you to check the recording, instead of
  reporting a failure.
- Programme details list each credited name once per role, and 12-hour times stay
  whole in seekbar rows and narrow Guide cells.

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
