# Changelog

## 0.3.0 (unreleased)

<!-- TODO: add Tvheadend SDK 0.34.0 and the frame rate that streams declare once that work is merged. -->

Each release now has two builds, Play and Appliance, and the app consumes Tvheadend
SDK 0.33.0 and HTSP 1.0.0-beta.4.

### Added

- Play and Appliance builds from one commit. The Appliance build (launcher name
  "Tvheadend Player Appliance") alone carries the HOME entry, the optional
  accessibility service and the Appliance section in Settings. The Play build has
  none of them. The two install side by side.
- Saved channel logos show at startup and while the server is unreachable. The
  player opens at once, before the server is ready, and the startup screen works
  without saved data.
- Free recording space in Recordings and Settings.
- Hold OK (or press Menu) on a channel, programme or recording to open a
  full-screen context menu. Record starts at once; Stop, Cancel and Delete ask
  for confirmation in a centred dialog.
- One programme details page, shared by the Guide, Recordings and the player.
- A single notice when the connection is lost, a badge on Settings while the app
  is offline, and a Try again action.
- Match content frame rate setting (Always, Only if seamless, Off). Playback
  statistics show the output mode, the display modes and the measured frame rate.
- Video decoder counters and a classification of playback failures in the
  diagnostics.
- A two-row channel rail in the player, with a live preview and a programme
  dialog. The channel identity now sits at the top of the player.
- Audio and subtitle preferences for language, format and description tracks.
  Audio and subtitle lists open from the remote's own keys, and every audio track
  is listed with a name and a format line.
- System playback controls through a media session, audio focus handling and
  recovery after headphones are unplugged.
- Live TV stays tuned for a while after the app moves to the background.
- Recordings that are still running can be stopped. In-progress recordings resume
  where you stopped, and recordings have scene-marker navigation.
- Notices that follow what the server reports about recordings.
- AC-3, E-AC-3 and MP3 fall back to software decoding through an app-owned
  Media3 FFmpeg extension with pinned sources and native binaries. Platform
  decoders stay first.
- Channels use the standard channel order and show full channel numbers.
- 12-hour or 24-hour clock that follows the system, with an override in Settings.
  Dates, times and numbers follow the app language.

### Changed

- Playback no longer starts when the app opens unless you enable **Start playback
  when the app opens** in Settings, under Player.
- The Guide search entry is hidden for the 1.0 release.
- Channels, Guide, Recordings and Settings follow the Material for TV layout
  margins. The Guide has denser 64 dp rows, and Settings uses depth navigation.
- The live start-up buffer is tuned per server.
- Logos and programme pictures fade in when they load, and the player eases in
  from the screen that opened it.
- The German interface addresses users informally.
- English text uses American spelling.
- Release builds are minified with R8, and each release ships the mapping files.
- Tvheadend SDK 0.33.0 and HTSP 1.0.0-beta.4, including SDK-owned retry of failed
  Player cleanup while preserving unrelated playback items.

### Fixed

- Live buffers that run dry recover only when playback can advance, and keep
  pause, audio interruption and newer channel-selection intent. Viewer pause
  stays available only with server timeshift.
- A live stream that the server stops without reporting an issue is handled.
- A Pause pressed during a channel change is kept, and after a channel change the
  old or frozen picture stays covered until the new channel's picture is moving.
  The busy ring fades out once the sound plays.
- Audio interruptions recover and resume reliably.
- A recording continues where it stood after Retry or a restore.
- Unreadable recording-action responses are reported as uncertain outcomes
  instead of confirmed failures, and servers that need an HTSP upgrade are
  identified.
- Stale recording confirmations no longer apply, and a held OK that closes one
  no longer triggers the item behind it.
- Programme details list each credited name once per role.
- 12-hour times stay whole in seekbar rows and narrow Guide cells.

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
