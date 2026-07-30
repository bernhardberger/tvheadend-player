# Fullscreen player UI/UX inventory

**Status:** Current player evidence and source inventory.  The original visual
session was performed on 2026-07-29 against `90f7178c0592-dirty`; the complete
inventory was revalidated and a replacement screenshot set was captured on
2026-07-30 against `51a4f3abb2d6-dirty`.  Current source, tests,
`docs/tv-design-spec.md`, and the matching normative specification take
precedence.

**Scope:** Fullscreen Live TV, timeshift, and recording playback.  This is
product-specific evidence about a mostly generic TV-player foundation.

## Evidence boundary

The current set contains 20 screenshots captured on the designated TCL G10
using the debug synthetic-video backdrop.  They are owner-only ignored artifacts
under `captures/device/51a4f3abb2d6-dirty/`; exact paths are listed below and
were visually checked after capture.  Other files in that directory are not
current evidence by default.  The original 18-file set was removed during
repository cleanup and has been superseded by this replacement set.

The synthetic backdrop established overlay composition only.  It did not prove
real `SurfaceView` visibility, video contrast, HDR, deinterlacing, motion
quality, overscan, focus feel, or remote-repeat behavior.  Those remain physical
TV gates.

All behavior claims below were rechecked against current source.  Do not treat
any other capture or historical handoff as current evidence.

## Current implementation map

### Player routes and shared UI

- `ui/player/VideoPlayerScreen.kt` owns Live TV keys, controls, number entry,
  channel drawer, Info, options, Stats, timeshift, tuning, and recovery.
- `ui/player/RecordingPlayerScreen.kt` owns recording startup/resume, controls,
  direct seek feedback, Info, options, status, and terminal failure UI.
- `ui/player/OverlayControlsTv.kt` and `RecordingOverlayControls.kt` render the
  cinematic headers, timelines, and action rows.
- `ui/player/PlaybackOptionsSheet.kt` renders Audio, Subtitles, Display, Stats,
  and Simple TV owner actions.
- `ui/player/PlaybackSeekbar.kt` and `PlayerTimeline.kt` render interactive and
  ambient timeline geometry.
- `ui/player/ChannelDrawer.kt` provides the edge-attached normal picker and the
  Simple TV card grid.
- `ui/player/PlayerIdentityHeader.kt` is shared by Live TV and recordings.
- `ui/player/PlaybackStatsOverlay.kt` renders bounded diagnostics.
- `ui/player/PlayerVideoSurface.kt` deliberately creates a passive Media3
  `PlayerView`: controller disabled, non-focusable, non-clickable, and excluded
  from accessibility.  `AppRoot` owns its persistent surface lifetime.

### Pure policies and owners

- `core/PlaybackKeyPolicy.kt`
- `core/RecordingPlaybackPolicy.kt`
- `core/PlaybackOptionsPolicy.kt`
- `core/TimeshiftPolicy.kt`
- `core/SeekbarPolicy.kt`
- `core/PlaybackStatusPresentation.kt`
- `core/PlaybackRecoveryPolicy.kt`
- `core/RecordingProgressPolicy.kt`
- `player/PlayerSession.kt`
- `repositories/DvrRepository.kt`

## Current interaction contract

### Hidden controls

- Center/Enter/Numpad Enter toggles Play/Pause and reveals controls for
  timeshift Live TV and recordings.  Non-timeshift Live TV reveals controls
  without a playback change because pause is unavailable.  This follows Android
  TV TV-PC and playback-control guidance.
- Up/Down reveals controls without changing playback.
- The player consumes the acting/revealing key cycle so the same press cannot
  activate the newly focused action.  Current source covers matching key
  suppression, but complete down/repeat/up/route-disposal Compose coverage is an
  overhaul task.
- Hidden Left/Right seeks seekable media.  Recording playback has a compact
  target/delta preview.  Timeshift currently queues the seek without revealing
  full controls and without an equivalent compact preview.
- Live TV without timeshift uses hidden Left to open Channels outside Simple
  TV.  Right passes through.
- Media Play, Pause, Play/Pause, Rewind, and Fast Forward act directly where the
  player supports them.

### Visible controls and focus

- Live TV without timeshift starts at the controls cluster; timeshift and
  recording controls restore their last semantic action, falling back to the
  primary cluster/Play-Pause action.
- Up from the action row enters a seekable timeline.  Left/Right scrub with
  repeat acceleration.  Down returns to the primary transport action.
- Live action order is Channels, conditional timeshift transport, Info,
  Playback options, and spatially separated Stop.
- Recording action order is Rewind 30, Play/Pause, Forward 30, Info, Playback
  options, and spatially separated Stop.
- Controls currently auto-hide after five seconds even in several non-stable
  states; only Playback options reliably suspends the timer.

### Live TV

- `CH+`/`CH-` tunes adjacent visible channels and wraps.  While the drawer is
  open, the same keys page without tuning.
- Picking the playing channel closes the drawer without retuning.
- Number entry accepts one to three digits, commits after a delay or immediately
  on OK, and cancels with Back.
- Programme Info is an explicit action.  If current EPG disappears or is absent,
  the current implementation closes Info silently.
- Record from Info currently calls `DvrRepository.scheduleEvent()` immediately;
  it has no safe confirmation or in-flight duplicate guard.
- Timeshift uses a programme-anchored axis and conditional Forward/Go Live
  actions.  It does not yet present one explicit, shared LIVE-edge tolerance or
  a readable behind-live offset in every relevant state.
- Ordinary tuning is delayed, compact, and non-modal.  Full recovery is reserved
  for connection loss or active playback retry.

### Recordings

- Playable completed recordings can offer Resume or Play from beginning.
- Rapid Left/Right seeks accumulate and dispatch after a short pause.  Compact
  feedback remains through buffering and a grace interval.
- Recording Info exists and uses the shared details language.
- Growing, progress-sync degraded/read-only, unavailable, failed, and natural
  end states exist.
- An unknown duration is currently normalized to a range ending at the current
  position in `recordingSeekbarRange()`, which can falsely imply a complete
  finite timeline.
- Recording read failures show terminal status but no genuine reopen/resume
  action.

### Playback options and Stats

- Root categories are Audio, Subtitles, Display, and Stats.  Simple TV hides
  Display/Stats and exposes its owner exit flow.
- Detail Back returns to root; root Back closes the sheet.
- Track choices show human-readable metadata and selected checkmarks.
- Empty Audio/Subtitles currently renders an enabled-looking, focusable no-op
  row.  Selected tracks outside the composed lazy viewport are not scrolled into
  composition before focus is requested.
- Options is visually modal, but source does not establish complete focus
  containment or restoration to the invoking More action.  The replacement
  recording session reproduced focus on the seekbar behind the sheet; Back did
  not dismiss it.
- Stats is enabled in options but rendered only after controls and all competing
  foreground overlays are absent.  Current Back policy checks the enabled flag,
  so Back can silently disable obscured Stats rather than dismissing the visible
  layer.

### Back and warm return

The intended visual order is Info, options detail/root, number entry/drawer,
recovery/error or seek feedback, controls, rendered Stats, then warm browse.
Current policy is split between screens and `PlaybackOptionsPolicy` and does not
derive one complete foreground layer.

Normal player Back returns to browse while keeping the foreground session warm;
Back is not Stop.  Explicit Stop serializes teardown and clears warm return.
Simple TV Back may dismiss a visible layer but cannot leave playback.  A
layerless Simple TV Back is consumed without changing playback.

## Current screenshot manifest

The Markdown links intentionally point to ignored local evidence rather than
tracked product assets.

### Live TV

| Current screenshot | Verified observation |
|---|---|
| [Controls hidden](../captures/device/51a4f3abb2d6-dirty/20260729T225708Z-livetv-current-baseline.png) | Video-only Live state. |
| [Controls visible](../captures/device/51a4f3abb2d6-dirty/20260729T225727Z-livetv-controls-visible.png) | Programme identity, timeline, Channels, timeshift transport, Info, More, separated Stop. |
| [Paused](../captures/device/51a4f3abb2d6-dirty/20260729T225741Z-livetv-paused.png) | Play focused after pausing. |
| [Behind live](../captures/device/51a4f3abb2d6-dirty/20260729T225756Z-livetv-behind-live.png) | Forward and Go Live become available; state still lacks a readable offset label. |
| [Channel drawer](../captures/device/51a4f3abb2d6-dirty/20260729T225852Z-livetv-channel-drawer.png) | Edge-attached list with playing/selected state, picons, programme and progress. |
| [Playback options root](../captures/device/51a4f3abb2d6-dirty/20260729T230040Z-livetv-playback-options-root.png) | Opaque root panel; Audio initially focused. |
| [Audio tracks](../captures/device/51a4f3abb2d6-dirty/20260729T230056Z-livetv-playback-options-audio.png) | Language/layout/rate/codec metadata and selected checkmark. |
| [Subtitles unavailable](../captures/device/51a4f3abb2d6-dirty/20260729T230117Z-livetv-playback-options-subtitles.png) | Off is selected, but the inert unavailable row receives the strongest focus surface. |
| [Display mode](../captures/device/51a4f3abb2d6-dirty/20260729T230155Z-livetv-playback-options-display.png) | Auto/original, Fill 16:9, and Fill 4:3. |
| [Stats enabled](../captures/device/51a4f3abb2d6-dirty/20260729T230221Z-livetv-stats-enabled.png) | Stats switch enabled while options remains open. |
| [Stats rendered](../captures/device/51a4f3abb2d6-dirty/20260729T230242Z-livetv-stats-overlay.png) | Non-focusable two-column playback/tuner/queue/system diagnostics. |
| [Programme Info](../captures/device/51a4f3abb2d6-dirty/20260729T230443Z-livetv-programme-info.png) | Full current EPG details, Close focused safely, and direct Record action without confirmation. |
| [Restored Live tuning](../captures/device/51a4f3abb2d6-dirty/20260729T231257Z-app-restored-live-after-capture.png) | Test TV restored to Live TV with delayed compact tuning feedback. |

### Recording/DVR

| Current screenshot | Verified observation |
|---|---|
| [Recording details/actions](../captures/device/51a4f3abb2d6-dirty/20260729T230809Z-recording-details-actions.png) | Long recording metadata with Play safely focused, Close, and secondary Delete. |
| [Controls visible](../captures/device/51a4f3abb2d6-dirty/20260729T230836Z-recording-controls-visible.png) | Shared cinematic layout; long two-line title competes with the clock. |
| [Controls hidden](../captures/device/51a4f3abb2d6-dirty/20260729T230902Z-recording-controls-hidden.png) | Video-only recording state. |
| [Hidden seek feedback](../captures/device/51a4f3abb2d6-dirty/20260729T230924Z-recording-hidden-seek-feedback.png) | Target timeline and cumulative `-0:30` feedback without full controls. |
| [Paused](../captures/device/51a4f3abb2d6-dirty/20260729T230946Z-recording-paused.png) | Play focused after hidden Center toggled and revealed controls. |
| [Playback options root](../captures/device/51a4f3abb2d6-dirty/20260729T231038Z-recording-playback-options-root.png) | Shared root with Audio initially focused. |
| [Options focus escaped](../captures/device/51a4f3abb2d6-dirty/20260729T231213Z-recording-options-focus-escaped.png) | No options row has visible focus; the seekbar thumb behind the modal is focused and Back failed to dismiss the sheet. |

## Revalidated overhaul findings

1. Complete key-cycle sequence tests are still missing even though the accepted
   Center and vertical-reveal mapping exists.
2. Auto-hide is not conditional on stable progressing playback.
3. Back uses Stats-enabled state rather than the actually rendered layer.
4. Playback options has inert focus targets and incomplete modal entry,
   containment, off-screen selection, and restoration contracts.  Focus escape
   to the obscured recording seekbar and ineffective Back are now
   screenshot/device-reproduced, not merely a source hypothesis.
5. Live Info silently closes without EPG and recording scheduling lacks safe
   confirmation and duplicate protection.
6. Hidden timeshift seek lacks immediate compact feedback and a Back contract.
7. Unknown/growing recording timelines can imply a false finite endpoint.
8. Header consolidation and two-line limits exist, but maximum localized
   metadata/clock geometry still needs tests and physical validation.
9. Automatic Live retry exists; manual Live/connection and recording retry
   actions do not yet have a complete serialized owner/action model.
10. Fullscreen focus targets and Stats semantics/bounds need an accessibility
    pass.

## Existing coverage

Relevant JVM tests include `PlaybackKeyPolicyTest`,
`RecordingPlaybackPolicyTest`, `PlaybackOptionsPolicyTest`,
`TimeshiftPolicyTest`, `SeekbarPolicyTest`,
`PlaybackStatusPresentationTest`, `PlaybackRecoveryPolicyTest`,
`RecordingProgressPolicyTest`, `DvrActionPolicyTest`, and `PlaybackExitTest`.

Relevant instrumented tests include `PlayerOverlayCompositionTest`,
`RecordingOverlayCompositionTest`, and `PlayerVideoSurfaceLifecycleTest`.
They establish selected geometry and passive-surface invariants, not physical TV
feel or the missing behavior above.

## Physical-TV gates

- complete key cycles and held-key repeat feel;
- options, Info, confirmation, recovery, and error focus containment/restoration;
- numeric entry and actual remote key delivery;
- live-edge and timeshift readability over moving video;
- long localized title/clock geometry, focus scale, safe edges, and overscan;
- tuning and failure contrast over bright and HDR video;
- connection loss, retry-now, recording read failure, growing recording, and
  progress-sync states;
- TalkBack order and transient-announcement frequency;
- `SurfaceView` visibility, progressive/interlaced playback, deinterlacing, HDR,
  and motion quality.
