# `:client` module

Normative ownership rule for the UI-free Android library `:client`
(namespace `at.bernhardberger.tvhplayer.client`).

## Purpose

`:client` holds the reusable TVHeadend client layer: runtime ownership, settings
persistence and pure domain policy. A future mobile app or another TV front end
can reuse it without inheriting this app's UI or UX. `:app` depends on
`project(":client")`; `:client` never depends on `:app`.

Package names stay `at.bernhardberger.tvhplayer.*` (move-only extraction).
Kotlin `internal` is module-scoped, so declarations `:app` uses across the
boundary are `public`; keep everything else `internal`.

## Dependency rules

Allowed: released TVHeadend SDK artifacts with the same strict version pin as
`:app`, AndroidX core, DataStore preferences, kotlinx coroutines and
serialization, and Media3 APIs the SDK already exposes. Test code may use JUnit,
coroutines-test, `sdk-testing`, and Robolectric with AndroidX Test for the
existing Android-context runtime tests.

Forbidden: Compose (any), Material or mobile Material, `androidx.tv:*`, Koin,
navigation, Coil, palette, app resources or `R`, activities and views.

`coil-core` arrives transitively through `sdk-android`'s API; do not use it
directly. `:client` mirrors the app build types (`debug`, `release`, `profile`,
`profileServer`) so `BuildConfig.PROFILE_TRACE` matches the app variant.

## What lives here

- `playback/`: `AppPlaybackRuntime`, `LiveRecoveryBackoff`, `SessionAudioSelection`,
  `PlaybackRuntimePolicy` (with `PlaybackTrace`), and the runtime support files
  `AppPlaybackModels`, `PlaybackTargetCommandSerialization`, `PlaybackStatePolicy`,
  `LiveRecoveryAttempts`, `ForegroundPlaybackLifecycle`, `RecordingMarkerQuery`
  and `PlaybackPresentation`.
- `di/SdkRuntimeOwner`.
- `settings/`: `AppProfileOwner`, `AudioChoiceStore`, `ChannelTagSettings`,
  `PlayerSettings`, `ServerSettings` (owns the single `Context.dataStore`
  delegate for `tvhplayer_settings`).
- `stores/`: `LastPlayedChannelStore` (owns `tvhplayer_appliance`), `ChannelSelectionStore`.
- `profiling/`: `ProfileTrace`, `ProfilePlayback`, `ProfilePlaybackTrace`.
- `data/FrontendModels`.
- `core/` domain policy: connection state, channel readiness, DVR library,
  growing timeline end, last-played channel, live-info recording, metadata
  cache, programme actions and recording targets, recording markers, seekbar,
  timeline EPG, timeshift presentation and channel scope visibility.

## What stays app-owned and why

- Compose UI, screens, view models, notifications, theme and resources: UI.
- `di/AppModule` (Koin wiring) and `profiling/ProfileLayout` (Compose).
- Key, focus, Back, navigation, appliance, accent, label, formatting and
  presentation policies: TV interaction and product UX.
- `settings/UiSettings`, `AppLanguage`, `ConnectionFormState` and
  `stores/GuidePositionStore`: app UI preferences, Compose state or Guide UI
  state. `core/StartupBootstrapPolicy` depends on `UiSettings`, so it stays too.

## Placement rule

New UI-free runtime, persistence or domain code goes to `:client`.
TVHeadend-generic protocol, catalog, EPG, DVR or playback knowledge goes to the
SDK. Anything that needs Compose, `R`, views, Koin or TV interaction stays in `:app`.

## Tooling

`tools/static_rules.py` applies the connection-probe rule to `client/src/main`
as well; theme rules stay app-only. `:app:verifyExternalSdkConsumption` checks
both modules' debug, release, profile and profileServer compile/runtime classpaths
and inherited dependency declarations. Only the in-repo `:client` project
dependency is allowed; local file dependencies and substituted SDK components
are rejected. Both modules must declare the released SDK with strict version
pins, resolve the expected release graph, and use only the settings-owned public
repositories (project repositories are forbidden). The app's offline profile
variant additionally permits the strictly pinned released `sdk-testing` artifact.

## Runtime policy

`AppPlaybackRuntime` owns the playback mechanics: target command serialization,
live recovery, audio focus, background keep/release, and the entry points the
media session uses. Hosts supply a `PlaybackRuntimePolicy` as a required
constructor argument: the keep-tuned limit, the live timeshift period, a
`PlaybackTrace` and the seek-diagnostics switch. The runtime does not read
`BuildConfig` or `profiling/` directly. `PlaybackRuntimePolicy.fromPlayerSettings()`
reproduces the player-settings values (keep-channel minutes, fixed timeshift
period when timeshift is enabled); the app passes `ProfilePlaybackTrace` and
its `BuildConfig.DEBUG`, tests pass the defaults.

The runtime has no autostart. Startup channel choice stays with the front end
(`StartupBootstrapPolicy` in `:app`, `LastPlayedChannelPolicy` in `:client`
`core/`). Mobile background audio would be a separate future mode, not a
policy value of this TV runtime.

## System media controls

The activity creates a Media3 session on start and releases it on stop; there is
no service, notification or background playback. Its `:client` wrapper publishes
only names (current programme/channel or recording/channel), without artwork,
and routes play/pause, seek and stop through the runtime's serialized, focus-aware path.
Live Play/Pause remains unavailable without timeshift, matching the live player's
remote-key policy; only seekable recordings expose seek-in-current-item. Stop is
available whenever a live channel or recording is active. A session Stop (Assistant,
Now-playing card, `cmd media_session dispatch stop`, a remote's Stop key) is fenced
to the viewing intent current when it arrived, performs the same explicit stop as
the player's Stop button (clearing the warm-return opportunity) on whatever target is
active when it runs, and then publishes its intent as the latest session Stop on
`AppPlaybackRuntime.sessionStops`, which a player screen subscribing late still sees
while that intent is current. Viewing intent is:
a live or recording start, a launch request (appliance entry or startup autoplay, which
open the live player without a runtime call), a warm opening action that starts nothing
(a click on the channel already playing, noted at the click before its coroutine is
dispatched; a warm return to the player), a retry (including the live screen's automatic retry
after a reconnect), a channel the live screen accepted (one intent, which its delayed
start after the zap settles carries instead of minting another), and opening a player
screen unless a user stop is the latest playback event. Recovery, route restore and
automatic stops (connection lost, a rejected start) are not intent. Intent noted
after the Stop arrived wins: before the Stop runs it cancels it outright, after it
runs it drops the announcement, so a Stop never closes the player the viewer just
asked for. A Stop after a selection wins over it: the selection's delayed start then
does nothing and is neither a failure nor a second stop. User stops (the Stop
buttons, the no-target Stop key, a session Stop, a finished recording, the root exit)
are recorded under the intent they arrived with; automatic stops record nothing. A
player screen's first step, before any playback, retune or restore work, closes it
and starts nothing when a user stop is the latest playback event, and an automatic
recording route restore then does nothing either. A session Stop registers as pending
the moment it arrives; a player screen entering while a Stop under the current intent is
still pending joins that intent instead of noting a new one, so the Stop still stops what
the screen would play and closes it (the click that opened the screen came before the
Stop). The registration ends when the Stop runs or is dropped (session detached, app
in the background, no active target, newer intent, runtime closed). When it runs, the
Stop's final fence is: session still attached, app in the foreground, some target
active, and no intent newer than the Stop's; a target installed or retuned after the
Stop arrived under the same intent (the start it was queued behind, a recovery retune,
a route restore) is the one it stops. On a session Stop's
announcement a showing live or recording player only closes (playback is already stopped), through the same close-once guard as its Stop button, Back and
overlay Close, so a player that is already closing or fading out ignores it; with no
player showing (warm video behind browse) the stop alone happens and nothing
navigates. While a target is active the player screens leave the remote's Stop key
to the session. Without one (recovery, error, or before a target started) the session
does not offer Stop, so the showing player handles the Stop key itself: its first key
down runs the screen's Stop button path and the screen consumes that key's repeats
and key up. Activity
startup handling and Compose retain key priority: player media actions consume
the opening down/repeat/up cycle, while unhandled keys on browse screens reach
the system session once.
When the player info/options layer is open, both player screens leave media keys
unhandled so they reach the session as the single handler.
The appliance accessibility service still handles only
its appliance-entry keys, not media keys. No next/previous, playlist, volume,
device or speed commands are exposed by the session.

## Deferred work

- Split `AppProfileOwner` into generic profile ownership and app policy.
- Rename packages to a client namespace once the boundary is stable.
