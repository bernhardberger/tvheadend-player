# Appliance TCL deployment findings, 2026-07

Status: Dated field record from the July 2026 TCL bring-up (observations 2026-07-23
to 2026-07-27), moved out of `appliance-mode-spec.md` and the archived appliance
plan on 2026-10-18. It is not current acceptance; recheck on the actual build and
firmware. Current behavior is in `appliance-mode-spec.md`.

## Accessibility service and wake

- The enabled accessibility service and the `APP_AUTO_START=allow` app-op
  survived three standby/wake cycles and one approved Android reboot while Google
  stayed the default HOME (2026-07-23, TCL test target).
- Pressing the physical TV button during playback no longer restarted the player.
  The service filters only Android GUIDE and TCL's private code `4001`, and
  requests no accessibility events or window-content access.

## HOME role

On the TCL, `cmd package set-home-activity` and affirmative selection in Android's
Home app screen both stored Tvheadend Player as preferred, but Google Basic TV still
resolved and opened. The system launcher has privileged priority `2`; Android caps
the third-party filter at `0`. Google stayed enabled and selected, and no HOME-role
standby/wake or cold-reboot success was claimed. The decision to keep the GUIDE
accessibility entry as the 1.0 behavior is roadmap item A1.

## Initial channel list

Before atomic publication, the channel list was observed at about 30, 84 and 50
entries across reconnects. The atomic initial snapshot is implemented with JVM
regressions; the stable count across reconnects was still to be rechecked on the
TCL before the final appliance deployment.

## G08 intermittent render mode (roadmap P6)

The owner approved a constrained G08 production installation while the G10 was
unavailable. Runtime validation found an intermittent interlaced-motion failure:
720p50 stayed smooth, but direct 1080i25 AVC playback first looked less smooth and
less effectively deinterlaced than the prior production build. The G08 reported
`c2.mto.avc.decoder`, zero dropped output buffers and a 3840x2160 60 Hz display
mode. Disabling timeshift did not help.

- Build `0.1.1` restored only the earlier H.264 format/SAR update behavior and
  `0.1.2` restored route-owned fullscreen `PlayerView` composition. Neither made a
  visible difference on the same service, so both hypotheses were dropped.
- Both the old and the rebuilt decoder AARs contain only a stub experimental FFmpeg
  video renderer that reports every format unsupported, and the affected service
  uses AC3 rather than the rebuilt MP1/MP2/MP3 audio path. Speculative decoder
  changes stopped pending a same-broadcast reference comparison.
- The G10 had previously entered a similar bad mode intermittently and recovered
  after retuning. On the G08, switching away and back and force-stopping `0.1.2`
  did not restore quality; on 2026-07-27 the operator reported fluid ServusTV
  playback after rebooting the G08 with the unchanged build, and the good mode
  survived standby/wake and a channel switch.
- This points to device or decoder initialization state rather than a
  deterministic application format or surface regression. The fault stays open until
  later cold starts show when the good and bad modes recur.
