# Changelog

## 0.2.13

- Adopt published TVHeadend SDK 0.13.0 for anchored timeshift selections,
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

The first GitHub release of TVHeadend Player for TV, an independent Android TV
client for TVHeadend servers. Requires Android 9 or newer and a TVHeadend server.

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
- Published TVHeadend SDK 0.10.0 includes cache/metadata fixes and same-subscription
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
  TVHeadend project.
