# Code ownership map

Use this map before broad searches or locator/map delegation. Start at the
named path and search directly for its callers or tests. Spawn independent
children in parallel when they improve quality or throughput after the known
entry points are exhausted; avoid asking them to rediscover this table.

| Concern | Application owner and starting point |
|---|---|
| Process composition, top-level routes, profile guards, and destination rendering | `app/src/main/java/at/bernhardberger/tvhplayer/ui/AppRoot.kt` |
| Typed Navigation 3 keys and stack policy | `app/src/main/java/at/bernhardberger/tvhplayer/ui/AppNavigation.kt` |
| Player/service lifecycle and SDK command orchestration | `app/src/main/java/at/bernhardberger/tvhplayer/playback/AppPlaybackRuntime.kt` |
| SDK construction and ordered shutdown | `app/src/main/java/at/bernhardberger/tvhplayer/di/SdkRuntimeOwner.kt` and `app/src/main/java/at/bernhardberger/tvhplayer/di/AppModule.kt` |
| Live-player presentation, focus, keys, overlays, and timeline | `app/src/main/java/at/bernhardberger/tvhplayer/ui/player/VideoPlayerScreen.kt` |
| Recording-player presentation | `app/src/main/java/at/bernhardberger/tvhplayer/ui/player/RecordingPlayerScreen.kt` |
| Guide grid, programme details, filtering, and Guide-local DVR actions | `app/src/main/java/at/bernhardberger/tvhplayer/ui/screens/EpgGridScreen.kt` |
| Channel browsing and focus restoration | `app/src/main/java/at/bernhardberger/tvhplayer/ui/screens/ChannelsScreen.kt` |
| Recordings browsing and DVR actions | `app/src/main/java/at/bernhardberger/tvhplayer/ui/screens/RecordingsScreen.kt` |
| Server profile persistence, credential edit lifetime, and stream-profile selection | `app/src/main/java/at/bernhardberger/tvhplayer/settings/AppProfileOwner.kt` |
| Onboarding and connection editing | `app/src/main/java/at/bernhardberger/tvhplayer/ui/screens/OnboardingScreen.kt` and `app/src/main/java/at/bernhardberger/tvhplayer/ui/screens/settings/SettingsConnection.kt` |
| Appliance launch, HOME/GUIDE, wake, and Simple TV | Start from `docs/appliance-mode-spec.md`, then `app/src/main/java/at/bernhardberger/tvhplayer/ui/MainActivity.kt`, `app/src/main/java/at/bernhardberger/tvhplayer/accessibility/ApplianceEntryAccessibilityService.kt`, and `app/src/main/java/at/bernhardberger/tvhplayer/core/` |
| SDK public playback behavior | Owned by `tvheadend-sdk`; the app calls `TvheadendPlaybackCoordinator` through `AppPlaybackRuntime` and must not reproduce SDK state machines |
| HTSP wire behavior | Owned by `tvheadend-htsp`; do not add an application workaround for an attributed protocol defect |

## Tool boundaries

- Use `./tools/device --help` and documented commands. Do not read the
  implementation of `tools/device` unless the package edits it or a reproduced
  failure has been attributed to that tool.
- Every `./gradlew` entry is serialized by one repository-wide flock. This
  includes `./tools/verify` and commands run through the repository-bundled
  `gradle-run` skill. A waiting command must wait rather than
  bypass the lock or start another daemon.
- Use the `gradle-run` skill for compact output and failure fingerprints. It is
  bundled at `.opencode/skills/gradle-run`; no sibling checkout or diagnostic
  child is required.
- `./tools/verify` remains the final application gate. Device, credential,
  signing, and release authorization remain separate.
