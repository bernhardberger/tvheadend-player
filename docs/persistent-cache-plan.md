# Persistent cache plan

Status: implemented, verified and independently reviewed on the app base
`8097141`. Closes defect-ledger rows D16 (picons refetched every
start), D19 (cold start stuck on "Loading channel information…"), and D23
(no "Clear cache" control). Consumes published SDK 0.8.0 from Maven Central.

## Boundary

The SDK owns every mechanic: on-disk layout, restart-stable namespace,
serialisation, atomic writes, corruption recovery, eviction, cold-start
publication, and the artwork fetch path. The app owns policy and presentation:
whether persistence is on, where the root lives, retention and size, the clear
action, and how a not-yet-current catalog is shown. The app must not add cache
files, keys, TTL buckets, or fetch throttles of its own; the pre-refactor
`HtspPiconFetcher` approach is not coming back.

## Policy decisions

| Policy | Value | Reason |
|---|---|---|
| Root | `context.cacheDir` | App-private; Android may reclaim it under pressure, which is the correct failure mode for a cache. `filesDir` would survive "Clear cache" in system settings, which users expect to work. |
| Metadata retention | 7 days | Equals `GUIDE_EPG_COVERAGE_POLICY` (`core/TimelineEpgPolicy.kt:23`); anything older is beyond the guide horizon anyway. |
| Artwork retention | 30 days | Picons rarely change; a stale picon for a renamed channel is acceptable and self-heals on eviction. |
| Artwork budget | 64 MiB | Same order as today's dead Coil disk cache; the 32-bit TCL target has limited storage. |
| Enabled | Always, no user toggle | A cache with no user-visible downside does not need a switch; "Clear cache" covers recovery. |

## Slices

### Slice A: wire the policy and remove dead app caching

- `di/AppModule.kt` passes `appMetadataCachePolicy(cacheRoot)`, where
  `cacheRoot = androidContext().cacheDir`,
  alongside `GUIDE_EPG_COVERAGE_POLICY`; the policy lives in `core/` and its
  retention/budget values are covered by `GuideRenderingCostContractTest`.
- `images/TvheadendArtworkLoader.kt` disables Coil disk caching. The removed
  custom disk cache was not used by the SDK fetcher; SDK artwork persistence now
  owns that data. `removeLegacyCoilCache` removes only the former app-owned
  `coil_disk_cache` directory on IO, without touching SDK or platform caches.
- `core/IconResolver.kt` / `ui/components/PiconBox.kt`: unchanged. Coil memory keys become stable through the SDK keyer; `remember(currentSession, …)` stays because the fetch still needs a current observation.
- `GuideRenderingCostContractTest` covers composition-root wiring, policy
  constants and idempotent legacy cleanup that preserves unrelated files.

### Slice B: enter on a cached catalog (D19)

`AppConnectionViewModel` derives separate browsing and tuning readiness from
the SDK observation. A cached catalog can be displayed before the socket is open.

- `core/CurrentChannelReadiness.kt` returns `Browsable(channels)` for a non-empty
  catalog whose authority is `SYNCHRONIZING_WITH_RETAINED_DATA` or `STALE`.
  `Ready` requires both `CURRENT` and `SessionState.Ready`.
- `core/MainStartupPresentation.kt`: `Browsable` permits the channel list.
  `AppRoot` cancels the pending startup request and selects Channels with the
  existing expected-state transition. That request cannot unexpectedly autoplay
  when synchronization later finishes. Fresh starts without a cache retain the
  existing Ready-only autoplay; tuning still requires `SessionState.Ready`.
  Automatic reconnect backoff permits browsing; credential/configuration and
  explicit-retry failures retain their actions.
- `ui/screens/ChannelsScreen.kt` shows the inline refresh banner for a retained
  catalog even when the connection UI is Ready; the empty list keeps loading text.
- `MainStartupPresentationTest`, `CurrentChannelReadinessTest` and
  `CachedStartupTransitionTest` cover retained/empty/current authority, actionable
  failures, cancel-and-route, duplicate transitions and no later surprise autoplay.
- Physical-TV gate: cold start with a warm cache shows the channel list within
  2 s of the app window; `./tools/device screenshot` at 1 s intervals. Cold
  start with a cleared cache behaves exactly as today.

### Slice C: Clear cache (D23)

- Settings > General (`ui/screens/settings/SettingsGeneral.kt`), new
  `SettingsSectionTitle` "Storage" with one action row "Clear cache" and a
  support line from `SessionCache.statistics` ("12 MB, 340 images"). No
  paths, no host names.
- Action: `session.cache.clear()` from the view-model scope. An accepted clear
  finishes even if Settings is closed, without cancelling the SDK writer restart.
  Repeated activation is ignored while busy. A four-second confirmation or a
  retryable error stays in the focused row; artwork refetches on demand.
- Not a "forget server" action; connection settings stay as they are.
- `SettingsStorageViewModelTest` covers statistics formatting/overflow, clear,
  coalescing, retry, confirmation timers and completion after view-model disposal.
  `StorageScreenshotTest` covers production General focus, action and Back.

### Slice D: release pins

SDK 0.8.0 is published. `gradle/libs.versions.toml`, `app/build.gradle.kts`,
`tools/check-native-libs`, `tools/prepare-release` and release-tool tests agree.
Hashes were calculated from downloaded Maven Central artifacts, not a local
publication. Public tag `v0.8.0` identifies SDK source
`25b96c1ccabf1bc00d36ca111cb56e5e67e41f44`.

## Acceptance

- Physical-TV gate: warm cold start shows the channel list before
  `SessionState.Ready`; cached picons do not need another HTSP file fetch.
- `Clear cache` deletes persisted namespaces but preserves active in-memory
  data. A connected SDK session can immediately repersist current metadata, so
  zero usage is not a lasting guarantee. Artwork is fetched again on demand.
- No app code touches SDK cache files, keys or namespaces. The only file cleanup
  is removal of the obsolete app-owned Coil directory.
- `./tools/verify` passes; independent Astra and Opus engineering reviews plus
  a screenshot-first `tv-ux-reviewer` pass cover the changed boundaries.

### Verification evidence (2026-09-07)

- Final plain `./tools/verify` passed against Maven Central, including
  `:app:verifyExternalSdkConsumption`, JVM tests, lint, Android test compilation,
  debug APK and `tools/check-native-libs`. No `--staged-sdk` evidence was used.
  Managed log: `/tmp/gradle-run/456c6a9f3379c7023415774f2dadc8aa/0009.log`.
- Static rules, 124 Python tool-policy tests and documentation authority passed.
- Authorized LXC119 Google TV API 36 emulator: live identity verified, 16/16
  final Storage cases passed, including four Up/Down steps back through the pane.
  The existing Settings/startup matrix also passed
  18 tests. App/test packages were uninstalled and the emulator stopped.
- Final production-composable captures: ignored
  `captures/p26-a1/final-v3/storage-captures/`, 1920x1080 at density 2, EN/DE,
  font scales 1.0/1.3, focused Clear action in idle/clearing/cleared/failed states.
  All 16 are byte-identical to reviewed `final-v2/storage-captures/`; the final
  focus guard only disables the fade when navigating away from Clear cache.
- Astra engineering closure `ses_f851d6a1dffeQ46d2uy6eUTokD`: CLEAN. Opus second
  `ses_f851d6a20ffewb6UjGRKAwbx5i`: NON_BLOCKING, named gaps closed. Primary
  checked the remaining evidence questions: published `MetadataCacheRuntime`
  wraps deletion in `withContext(ioDispatcher)`; the base artwork loader names
  `coil_disk_cache` exactly. No extra dispatcher wrapper or speculative deletion
  is needed. The unused local and warm-return naming suggestion are pre-existing;
  test relocation is optional cleanup, not an acceptance defect.
- Screenshot-first Opus closure `ses_f8517f23affeMFi8TFqhYBUiP6`: DESIGN_READY.
  Busy ring/track and emphasized text, distinct result glyphs, viewport fade and
  localized wrapping pass. Each Opus dispatch followed a fresh eligible route.
- The pre-existing operator `ChannelsScreenshotTest.kt` remains untracked,
  regular mode 0644 and byte-identical to its admitted preservation baseline.
- Physical-TV warm-start latency, actual artwork reuse and remote/overscan feel
  remain open human gates. Offline screenshots cannot establish them.

## Deferred

Cached EPG for the timeshift-position programme (D20) is a presentation
question and does not need this cache. DVR entries are never cached (SDK
decision).
