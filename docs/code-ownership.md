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
| Shared channel-tag selection, scope visibility, hydration and ordered persistence | `app/src/main/java/at/bernhardberger/tvhplayer/viewmodels/ChannelsViewModel.kt`, created once above `NavDisplay` in `AppRoot.kt` and passed to Channels, Guide, Settings and live playback |
| Recordings browsing and DVR actions | `app/src/main/java/at/bernhardberger/tvhplayer/ui/screens/RecordingsScreen.kt` |
| Server profile persistence, credential edit lifetime, and stream-profile selection | `app/src/main/java/at/bernhardberger/tvhplayer/settings/AppProfileOwner.kt` |
| Onboarding and connection editing | `app/src/main/java/at/bernhardberger/tvhplayer/ui/screens/OnboardingScreen.kt` and `app/src/main/java/at/bernhardberger/tvhplayer/ui/screens/settings/SettingsConnection.kt` |
| Appliance launch, HOME/GUIDE, wake, and Simple TV | Start from `docs/appliance-mode-spec.md`, then `app/src/main/java/at/bernhardberger/tvhplayer/ui/MainActivity.kt`, `app/src/main/java/at/bernhardberger/tvhplayer/accessibility/ApplianceEntryAccessibilityService.kt`, and `app/src/main/java/at/bernhardberger/tvhplayer/core/` |
| SDK public playback behavior | Owned by `tvheadend-sdk`; the app calls `TvheadendPlaybackCoordinator` through `AppPlaybackRuntime` and must not reproduce SDK state machines |
| HTSP wire behavior | Owned by `tvheadend-htsp`; do not add an application workaround for an attributed protocol defect |

## Browsing state boundaries

Tag input updates the shared in-memory scope synchronously. DataStore loads it
once and persists the latest intent in order; completed writes do not feed back
into active selection. Only current catalog authority may normalize unavailable
preferences. Screen callbacks validate the scope snapshot that produced their
rows, rather than comparing two freshly read values around stale rendered rows.
Channels focus handoffs compare the active tag and ordered channel IDs, so a
metadata-only refresh cannot cancel a page scroll's pending focus transfer.

Native row/programme focus remains screen-local. The Guide viewport owns arrows
and bridges native focus while cells are replaced; programme focus acknowledges
selection and saves the shared browse position. Hidden-screen initialization does
not write that position. An OK press begun during a handoff stays consumed through
its release, even when the replacement cell has become ready.
Channels and Guide enter through their active scope tab; content Back returns to
that tab before the global drawer. Already-attached Guide neighbours receive
native focus in the key dispatch, while new windows retain the viewport bridge.
The browse scene owns the same root crossfade for Channels, Guide, Recordings and
Settings; Settings categories retain their distinct navigation/saveable-state keys.
AppRoot also registers browse Back with the activity dispatcher: a singleton
Channels root cannot rely on NavDisplay's pop handler to unwind focus layers.

## Tool boundaries

- Start with the CLI-first workflow in `android-tooling.md`. For specialized
  operations, use `./tools/device --help` and documented commands. Do not read the
  implementation of `tools/device` unless the package edits it or a reproduced
  failure has been attributed to that tool.
- Every `./gradlew` entry is serialized by one repository-wide flock. This
  includes `./tools/verify` and commands run through the repository-bundled
  `gradle-run` skill. A waiting command must wait rather than
  bypass the lock or start another daemon.
- Use the `gradle-run` skill for standard bounded execution and private diagnostics.
  No workflow ledger, sibling checkout or diagnostic child is required.
- Authorized offline emulator work uses `docs/android-tooling.md`; the physical
  device wrapper is not required for ordinary explicit-serial install and capture.
- `./tools/verify` remains the final application gate. Device, credential,
  signing, and release authorization remain separate.
