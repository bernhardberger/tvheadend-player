# Code ownership map

Use this map before broad searches or locator/map delegation. Start at the
named path and search directly for its callers or tests. Spawn independent
children in parallel when they improve quality or throughput after the known
entry points are exhausted; avoid asking them to rediscover this table.

| Concern | Application owner and starting point |
|---|---|
| Process composition, top-level routes, profile guards, and destination rendering | `app/src/main/java/at/bernhardberger/tvhplayer/ui/AppRoot.kt` |
| Typed Navigation 3 keys and stack policy | `app/src/main/java/at/bernhardberger/tvhplayer/ui/AppNavigation.kt` |
| Player/service lifecycle and SDK command orchestration | `client/src/main/java/at/bernhardberger/tvhplayer/playback/AppPlaybackRuntime.kt` |
| SDK construction and ordered shutdown | `client/src/main/java/at/bernhardberger/tvhplayer/di/SdkRuntimeOwner.kt` and `app/src/main/java/at/bernhardberger/tvhplayer/di/AppModule.kt` |
| Live-player presentation, focus, keys, overlays, and timeline | `app/src/main/java/at/bernhardberger/tvhplayer/ui/player/VideoPlayerScreen.kt` |
| Recording-player presentation | `app/src/main/java/at/bernhardberger/tvhplayer/ui/player/RecordingPlayerScreen.kt` |
| Guide grid, programme details, filtering, and Guide-local DVR actions | `app/src/main/java/at/bernhardberger/tvhplayer/ui/screens/EpgGridScreen.kt` |
| Channel browsing and focus restoration | `app/src/main/java/at/bernhardberger/tvhplayer/ui/screens/ChannelsScreen.kt` |
| Shared channel-tag selection, scope visibility, hydration and ordered persistence | `app/src/main/java/at/bernhardberger/tvhplayer/viewmodels/ChannelsViewModel.kt`, created once above `NavDisplay` in `AppRoot.kt` and passed to Channels, Guide, Settings and live playback |
| Recordings browsing and DVR actions | `app/src/main/java/at/bernhardberger/tvhplayer/ui/screens/RecordingsScreen.kt` |
| Server profile persistence, credential edit lifetime, and stream-profile selection | `client/src/main/java/at/bernhardberger/tvhplayer/settings/AppProfileOwner.kt` |
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

Channels keeps lifecycle-collected shared selection as a state handle. Its lazy
provider is read by input/restoration callbacks and the focused-details
composition, not while composing the screen root. Local focus and clock reads
also stay inside the details or lazy-item compositions. Ownership, scope and
metadata changes may still recompose the root; ordinary row focus must not.

An actual Channels tag change explicitly resets the lazy-list viewport to the
playing channel in that scope, or its first channel. This is a preview anchor,
not native focus or a playback/selection commit. The details and Down/OK entry
agree with it; same-tag Back/re-entry preserves the current browse position.
Tag reversals remain latest-intent-wins and do not wait for prior preparation.

Native row/programme focus remains screen-local. The Guide viewport owns arrows
and bridges native focus while cells are replaced; programme focus acknowledges
selection and saves the shared browse position. Hidden-screen initialization does
not write that position. An OK press begun during a handoff stays consumed through
its release, even when the replacement cell has become ready.
Channels and Guide enter through their active scope tab; content Back returns to
that tab before the global drawer. Already-attached Guide neighbours receive
native focus in the key dispatch, while new windows retain the viewport bridge.
`SidebarGuideScene.kt` owns vertical slide-and-fade transitions between Channels,
Guide, Recordings and Settings. Settings owns a saveable arbitrary-depth stack in
`ui/components/depth/DepthNavigation.kt` / `DepthStack.kt`; its product levels live
in `ui/screens/settings/`, including the dedicated Connection-editor exception.
`BrowseContentMotion.kt`
supplies horizontal outgoing/incoming motion below Channels, Guide and Recordings
tabs. Its direction history and retained render values are presentation-only:
existing scope/mode owners synchronously publish the accepted or fallback key;
its rows may render later. Only the current rendered visit may
act; outgoing lists use private viewport state and cannot register shared focus
requesters, publish selection/scroll state, or finish paging/preview focus work.
Headers, tabs, screen controllers and dialogs remain outside the moving bodies.
AppRoot also registers browse Back with the activity dispatcher: a singleton
Channels root cannot rely on NavDisplay's pop handler to unwind focus layers.

## Tool boundaries

- Start with the CLI-first workflow in `android-tooling.md`. For specialized
  operations, use `./tools/device --help` and documented commands. Do not read the
  implementation of `tools/device` unless the package edits it or a reproduced
  failure has been attributed to that tool.
- Every `./gradlew` entry takes a repository-wide flock slot. This
  includes `./tools/verify` and commands run through the repository-bundled
  `gradle-run` skill. There is one slot by default; a host with memory for
  more concurrent builds may set `TVHPLAYER_GRADLE_SLOTS` (1–4). A waiting
  command must wait rather than bypass the lock or start another daemon.
- Use the `gradle-run` skill for standard bounded execution and private diagnostics.
  No workflow ledger, sibling checkout or diagnostic child is required.
- Authorized offline emulator work uses `docs/android-tooling.md`; the physical
  device wrapper is not required for ordinary explicit-serial install and capture.
- `./tools/verify` remains the final application gate. Device, credential,
  signing, and release authorization remain separate.
