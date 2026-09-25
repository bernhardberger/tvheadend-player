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

- `playback/`: `AppPlaybackRuntime`, `LiveRecoveryBackoff`, `SessionAudioSelection`.
- `di/SdkRuntimeOwner`.
- `settings/`: `AppProfileOwner`, `AudioChoiceStore`, `ChannelTagSettings`,
  `PlayerSettings`, `ServerSettings` (owns the single `Context.dataStore`
  delegate for `tvhplayer_settings`).
- `stores/`: `LastPlayedChannelStore` (owns `tvhplayer_appliance`), `ChannelSelectionStore`.
- `profiling/`: `ProfileTrace`, `ProfilePlayback`.
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
as well; theme rules stay app-only. `:app:verifyExternalSdkConsumption` allows
exactly the `:client` project and still rejects every other project, file or
substituted SDK component.

## Deferred work

- Split `AppPlaybackRuntime` into reusable mechanics plus app-supplied product
  configuration.
- Split `AppProfileOwner` into generic profile ownership and app policy.
- Rename packages to a client namespace once the boundary is stable.
