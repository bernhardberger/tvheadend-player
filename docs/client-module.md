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
  and `PlaybackPresentation`, plus the start-up buffer files
  `StartupBufferLoadControl`, `StartupBufferController` and `StartupBufferPolicy`.
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

### Start-up buffer

`StartupBufferLoadControl` wraps the SDK's `createTvheadendLoadControl()` in
`AppModule`. It answers only `shouldStartPlayback` for a live start: playback
starts once the buffered media reaches the start-up threshold, still capped by
half the target live offset like `DefaultLoadControl`. Recordings, unknown
periods and every other rebuffer go to the SDK load control unchanged. Every
other `LoadControl` method is forwarded explicitly, because Kotlin class
delegation does not forward Java default methods and their defaults throw. The
threshold and the skip mark are read on the playback thread, so a new value
applies to the next start without rebuilding the player.

The load control reads the kind from the queried period itself: a period is
live when its window's media item carries the SDK live source's media id
(`tvheadend-live`). Nothing the runtime announces reaches the load control, so a
recording that first selects its tracks while a live install is suspended or
fails keeps delegating, and a live restart in a new period is a live start.
Growing recordings can report a dynamic timeline, so the timeline alone cannot
tell them apart. The SDK does not publish that id; if it changes, every start
delegates, which is the SDK's own behaviour. `StartupBufferController` keeps
the runtime's target kind only to decide whether a start arms a window.

A timeshift seek or return to live is a server skip, not a player seek: the SDK
resets the period's queues, Media3 drops to buffering as if rebuffering, and the
skip surfaces as an internal discontinuity. The runtime reports the skip before
it asks the server (`timeshiftSeeking`) and its outcome afterwards, also when the
request throws or is cancelled. The load control stamps the request and the
answer with the elapsed-realtime clock. A rebuffering start of a live period is
treated as a live start only when Media3's last rebuffer began after the request
and no later than 3 s after the answer; it is used once. A skip pressed while the
player is already buffering (during an earlier skip's restart or a stall) takes
over the rebuffer in progress, because Media3 keeps that rebuffer's start time.
A rejected skip or a new
target install clears the mark, and so does a grace that ends with playback
running: the controller then opens the armed window. A skip the player absorbed
without buffering therefore never takes over a later genuine rebuffer.

`StartupBufferController` sets the threshold from the player setting (Automatic
or a fixed 0.5, 1, 1.5, 2 or 3 s) and learns the Automatic level per server,
between 0.5 and 3 s. Automatic favours fast tuning over a stutter-free start: a
different server profile identity starts over at 0.5 s, and the level rises only
when a server keeps struggling. A live start the viewer
waits for (play intent applied after a tune, or a server skip while playing)
arms a window; a paused start, one denied audio focus or one with audio
disabled arms nothing. The
window watches the first 30 s of playback (`StartupBufferPolicy`, plain Kotlin):

- Trouble is a rebuffer (buffering while playback is wanted, not the buffering a
  server skip causes, a pause, target change, background or stop) or three
  audio underruns. A single trouble never raises the level; a trouble that makes
  two of the last five verdicts trouble raises it by 0.5 s, up to 3 s.
- A window that plays 30 s without trouble counts as clean; 5 clean windows in
  a row lower the level by 0.5 s, down to 0.5 s.
- Every raise or lowering starts the verdict history over.
- A window cut short by zapping, pausing, a server skip, muting or unmuting
  audio, a change to the setting or server identity, background, stop or an
  error gives no verdict.

A verdict is stored only for a window armed in Automatic whose level is still
the learned level. Level and the last five verdicts are stored next to the
player settings but outside the `PlayerSettings` flow, so learning never
re-applies player settings; a storage failure keeps the previous level and does
not interrupt playback. The stored learning carries a rules version: learning
stored under earlier rules (such as a level raised by the old
raise-on-first-trouble rule) starts over once at 0.5 s with an empty history,
and an unreadable history counts as empty.

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
