# Bounded G10 field-feedback corrections

Status: P33-A1 source correction and bounded triage, based on
`6fc7fc066d656287868d1bbc80611737b54ba970`. Original observations concern
0.2.1 (`fac8789944d4934f770ec5db549b156d60bd1055`), not independently reproduced
device diagnoses. Original observations and approved order remain in the integration
workspace's `control_plane/context/player-g10-field-notes-2026-09-08.md` and
`control_plane/context/player-easy-wins-order-2026-09-08.md`; neither is rewritten.

## Ordered disposition

| Order / ID | Disposition | Evidence and remaining boundary |
|---|---|---|
| FIRST G10-05 | Fixed presentation | No buffer: current valid EPG supplies cyan schedule-elapsed progress and endpoints in the footer. No thumb, focus or seek action. Missing/future/ended EPG supplies no progress. `PlayerOverlayCompositionTest.passiveScheduleProgressIsTruthfulAndNeverSeekableAcrossTuning` protects these cases and buffer arrival. Runtime seek authority and SDK segment fences are unchanged. |
| FIRST G10-09 | Fixed presentation | Removed conditional header timeband. The same regression asserts unchanged channel/title/Next/clock bounds across buffer arrival; matched tuning/playing production captures cover the visual transition. Honest changes when metadata itself becomes unavailable are not suppressed. |
| FIRST G10-15 | Fixed upper-scrim presentation | Gradient ends at zero alpha, with stronger protection behind supporting text. `PlayerVisualStylePolicyTest` protects the curve constraints. Bright/dark production captures show static falloff, not moving-content quality. |
| NEXT G10-01 | Fixed narrow configured-state contradiction; original field trigger unproven | Local initialization already waits in `ResolvingLocal` for resolved settings. The session mapper used only after an Available profile incorrectly interpreted Disconnected as missing configuration. It now presents Connecting; the new `MainStartupPresentationTest` regression demonstrates passive pending startup without setup actions. Existing bootstrap, genuine configuration/credential failures, retry and cancellation policies remain unchanged. This is not evidence that the original physical flash was reproduced or that every possible startup flash is closed. |
| THEN G10-08 | Bounded-triage deferral: product precedence | `MainStartupPresentation` and `AppRoot.enterCachedChannelList` deliberately enter retained Channels and cancel pending autoplay before current readiness. `CachedStartupTransitionTest` proves later readiness cannot revive it. Decide whether configured autoplay should instead wait for current metadata, with explicit Back cancellation, or whether immediate cached browsing retains precedence. Do not merely preserve autoplay after exposing browsing: that would surprise the viewer. No last-channel-store defect was established. A restored launch versus fresh request is also an unresolved field discriminator. |
| THEN G10-02 | Bounded-triage deferral: trigger and retention | `PlayerHelpers.selectAudioTrack` assigns the chosen group/index override once; settings update existing parameters without clearing that override. The options sheet reads selected flags and does not clear them on dismissal. No immediate app reset was demonstrated. Establish whether selection changes within unchanged player/groups, at stream replacement/reconnect, or at restart, and distinguish focused row from selected marker/audible output. Agree retention lifetime before introducing persistence. |

## Evidence

- Final `./tools/verify` passed (exit 0, 57.396 seconds); unchanged runtime/overlay
  behavioral evidence is reused after removing the unreachable header branch.
- Focused startup/bootstrap/connection/cached-transition/launch/audio-policy JVM
  tests pass. The new startup assertion fails under the original Disconnected
  mapping by returning actionable configuration rather than passive Connecting.
- LXC119 production-composable instrumentation: 18 overlay tests and 62 screenshot
  cases passed. Captures use 1920x1080, 320 dpi, fixed clock, English/font scale 1.0
  for the field matrix, bright white and dark synthetic backgrounds, Info focused.
  Existing large-text scenarios remain in the suite.
- `field-disabled` and `field-unavailable` intentionally share the production
  `available=false` boundary. They prove rendering of that state, not independent
  runtime diagnoses of why history is absent. Missing-EPG variants omit progress.
- Final generated PNGs remain ignored under `captures/p33-a1-contrast/`; no credentials or
  live-server connection were used. Static captures do not establish motion,
  SurfaceView visibility, remote feel, overscan or physical-TV acceptance.

## Review adjudication

- Independent Astra `ses_f7f18f351ffek6vtd92V2vVVVE`: CLEAN on the bounded diff.
- Guarded Opus `ses_f7f1842dcffeYf3LRyC35p3m1Y`: NON_BLOCKING. Removed the unused
  header timeband parameters/branch. Kept the small schedule calculation inline
  rather than creating a one-use policy abstraction; its behavioral guard and
  semantics are exercised in production-composable instrumentation. Disabled and
  unavailable capture duplication is explicit state-boundary evidence, not a
  claim of distinct runtime reproductions; changing `liveAvailable` would test
  a different capability and would not establish the missing runtime cause.
- Screenshot review `ses_f7f1a0a8dffe1imjjd119qf7c3` accepted the upper fade,
  passive strip and header anchors, but identified weak Live-label contrast.
  Strengthened the existing lower gradient without moving controls. The final
  screenshot test asserts at least 4.5:1 against the sampled synthetic background
  in every field state, including the playing transition; all 62 cases pass.
- Bounded visual closure `ses_f7f139781ffeok3fSkBrytswDQ`: DESIGN_READY; UX-01
  closed. Prior packet `ses_f7f1b753dffe2sPyhCXjaVqqEV` was incomplete and is
  not an approval. No physical-motion claim is made.

## Preserved queue and artifacts

No G10 operation, server access, public release, signing or tag change is part of
this work. The independent D24 note remains deferred and untouched. G10-07/06
EPG/general performance, G10-04 uncertain red error, G10-10/11/12 rail work,
G10-13 TCL reference, G10-14 wall-clock and G10-03 codec preference remain outside
this correction. Original observations and their uncertainty remain intact.

The installed 0.2.1 debug identity remains SHA256
`b0ae9ded3b1f80aa4c62e38493f949bf2c4719be3b7b195b10a57769a29c5c2a`.
Those bytes were preserved before local rebuilding at ignored
`captures/p33-a1-0.2.1-fac8789-b0ae9ded-debug.apk`; signed artifacts are untouched.
Rebuilt test APKs are source-validation candidates, not replacements for those
0.2.1 bytes or an authorized installation/publication handoff.
