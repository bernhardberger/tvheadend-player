# Persistent cache plan

Status: approved design, not started. Closes defect-ledger rows D16 (picons
refetched every start), D19 (cold start stuck on "Loading channel
information…"), and D23 (no "Clear cache" control). Depends on SDK 0.8.0
implementing `tvheadend-sdk/docs/persistent-cache-design.md`.

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

- `di/AppModule.kt:46`: `createTvheadendSession(GUIDE_EPG_COVERAGE_POLICY, MetadataCachePolicy.create(androidContext().cacheDir))`. Keep the policy constants in `core/` next to the EPG policy so a JVM test can assert them.
- `images/TvheadendArtworkLoader.kt:27-31`: delete the Coil `DiskCache` block. It has never held a picon (custom fetchers do not write Coil's disk cache) and its directory `coil_disk_cache` should be removed on first start after upgrade.
- `core/IconResolver.kt` / `ui/components/PiconBox.kt`: unchanged. Coil memory keys become stable through the SDK keyer; `remember(currentSession, …)` stays because the fetch still needs a current observation.
- Test: `GuideRenderingCostContractTest.kt:34-36` currently pins the one-argument call; update it to the two-argument form and assert the policy constants.

### Slice B: enter on a cached catalog (D19)

Today `AppConnectionViewModel.kt:45-56` and `MainStartupPresentation.kt:57-68`
hold the startup screen until `channelCatalogAuthority == CURRENT`. With a
cached catalog the SDK publishes `Synchronizing(previousCatalog)` before the
socket is even open.

- `core/CurrentChannelReadiness.kt`: add a `Browsable(channels)` outcome for a
  non-empty catalog whose authority is `SYNCHRONIZING` or `STALE`. `Ready`
  keeps meaning `CURRENT`.
- `core/MainStartupPresentation.kt`: `Browsable` enters the channel list. It
  does not autoplay; autoplay and any tune still require `SessionState.Ready`
  (`appliance` auto-start and `LastPlayedChannelStore` consumers wait for
  `Ready`, unchanged).
- `ui/screens/ChannelsScreen.kt:414-451` already renders a non-current
  catalog with the inline progress banner; verify the banner text says the
  list is being refreshed, not loading.
- Tests: `MainStartupPresentationTest.kt`, `CurrentChannelReadinessTest.kt`
  gain the `Browsable` cases; the empty-catalog `AUTHORITATIVE_NO_CHANNELS`
  branch must still require `CURRENT`.
- Physical-TV gate: cold start with a warm cache shows the channel list within
  2 s of the app window; `./tools/device screenshot` at 1 s intervals. Cold
  start with a cleared cache behaves exactly as today.

### Slice C: Clear cache (D23)

- Settings > General (`ui/screens/settings/SettingsGeneral.kt`), new
  `SettingsSectionTitle` "Storage" with one action row "Clear cache" and a
  support line from `SessionCache.statistics` ("12 MB, 340 images"). No
  paths, no host names.
- Action: `session.cache.clear()` on the view-model scope; on completion show
  a transient confirmation in the row's support text; the picons re-fetch on
  demand. Keep the row focusable and D-pad reachable per `docs/tv-design-spec.md`.
- Not a "forget server" action; connection settings stay as they are.
- Tests: JVM test for the statistics formatting; androidTest for row presence
  and focus order in `SettingsGeneral`.

### Slice D: release pins

After SDK 0.8.0 is on Central: `gradle/libs.versions.toml:20`,
`app/build.gradle.kts:243`, `tools/check-native-libs:16-23`,
`tools/prepare-release:7-8`, `tools/tests/test_release_metadata.py:190`,
`tools/tests/test_native_publication_evidence.py:20-39`; then plain
`./tools/verify`.

## Acceptance

- Warm cold start: channel list visible before `SessionState.Ready`; picons
  appear without any HTSP `fileOpen` for cached ids (SDK statistics show hits).
- `Clear cache` empties the SDK statistics and the next start behaves like a
  first start.
- No app code touches files, keys, or namespaces for cache purposes.
- `./tools/verify` passes; independent Astra review of slices A–C together
  (non-trivial, non-UX except slice C's row, which also needs a
  `tv-ux-reviewer` screenshot pass).

## Deferred

Cached EPG for the timeshift-position programme (D20) is a presentation
question and does not need this cache. DVR entries are never cached (SDK
decision).
