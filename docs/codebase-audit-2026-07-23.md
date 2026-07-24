# TVHeadend Player predecessor-fork technical audit

Date: 2026-07-23

## Implementation update: 2026-07-24

The hardening work was implemented on `hardening/audit-findings` without
changing the HTSP extractor, stream readers, decoder selection policy, or
accepted appliance-entry behavior.

| Finding | Implementation status |
|---|---|
| AUD-001 HTSP lifecycle deadlock | Fixed with real-socket no-hello and idle-disconnect regression tests |
| AUD-002 false-green verifier | Fixed; verification now includes native/tool checks, JVM tests, lint, Android-test compilation, APK assembly/identity, and 16 KB alignment |
| AUD-003 credential lifecycle | Fixed; transient password input, explicit unavailable state, backup exclusion, clear action, secure-window handling, and a debug-only app-private test-device provisioning path |
| AUD-004 repository race | Fixed for per-channel flow creation with concurrent-map regression coverage |
| AUD-005 player command ordering | Fixed with serialized suspend commands and ordered non-cancellable cleanup |
| AUD-006 Back navigation | Fixed with tested parent/nested policy and no forced process exit |
| AUD-007 TV UI framework | Migrated focusable UI to TV Material 1.1.0, added safe areas, lifecycle-aware state, localization, and media-key policy |
| AUD-008 native provenance | Integrity manifest/tooling added; signed release remains blocked because exact sources, notices, and toolchains are unknown |
| AUD-009 stale public/release metadata | Replaced with accurate README/privacy text and read-only SHA-pinned CI; inherited deployment automation removed |
| AUD-010 production device safety | Fixed with tested production/test roles and four-property live identity matching for mutations, including credential provisioning |
| AUD-011 UI/data boundaries | Partially improved through lifecycle-aware collection; broader ViewModel boundary work remains incremental P2 work |
| AUD-012 dependencies | Dead Firebase/Security Crypto declarations removed and TV Material added; version upgrades remain isolated future work |
| AUD-013 memory/footprint | Not yet measured on a test TV; release shrinking and baseline profiles remain future measured work |
| AUD-014 transport security | Trusted-LAN constraint is now explicit in README and privacy documentation |
| AUD-015 governance | Direct main iteration is an accepted owner workflow and CI is read-only; repository-host settings remain an owner operation |

No physical playback, focus, wake/reboot, or motion-quality claim was added by
this code-only pass. A dining-room TCL was subsequently assigned as the test TV,
but the complete runtime matrix is still required. Signed release publication is
additionally blocked until `./tools/check-native-libs --release` passes with real
provenance rather than placeholders.

The product identity was subsequently migrated from the temporary package and
TVHStream-shaped source namespace to `at.bernhardberger.tvhplayer`. Historical
findings below intentionally retain names and paths that existed on the audit
date; current paths are recorded in `AGENTS.md`.

## Executive conclusion

The fork is a viable base for continued development. It already has a modern
Kotlin and Compose toolchain, a single-activity UI, constructor injection,
DataStore persistence, bounded HTSP parsing, useful pure policy tests, and a
playback path that has passed the household TCL's progressive and interlaced
motion checks.

The audit found that it was not ready for broad feature work or a public
release without a focused stabilization pass. The highest-risk findings were an
HTSP timeout/cancellation deadlock, a verification command that passed while
the instrumentation test source did not compile, unsafe handling around saved
credentials, stale release automation and privacy claims, and extensive use of
mobile Material 3 instead of Compose for TV. The implementation update above
records the resulting remediation and remaining gates.

The recommended direction is evolutionary, not a rewrite:

1. Preserve the accepted Media3 extractor and decoder behavior.
2. Fix lifecycle, concurrency, test-gate, and security defects around it.
3. Establish the final product identity and distribution model.
4. Migrate the operator UI to Compose for TV in small, device-tested slices.
5. Improve layer boundaries only where they make behavior testable or remove a
   demonstrated race.

## Current stack

| Area | Current implementation |
|---|---|
| Platform | Android TV, min SDK 28, target/compile SDK 36 |
| Language/build | Kotlin 2.3.10, AGP 8.13.2, Gradle 8.13, Java toolchain 21 |
| UI | Jetpack Compose with `androidx.tv:tv-material` for focusable TV UI and a bounded mobile Material fallback for unsupported primitives |
| Navigation | Navigation Compose 2.9.7 with string routes and a nested settings graph |
| State/injection | StateFlow, DataStore Preferences, Koin 4.1.1 |
| Playback | Media3 1.9.2, custom HTSP data source/extractor, bundled native decoders |
| Images | Coil 3.3.0 with a custom HTSP picon fetcher |
| Tests | JVM policy, lifecycle, concurrency, repository, and navigation tests plus Compose instrumentation test sources |
| Appliance integration | HOME candidate plus a narrowly scoped accessibility service |

## Priority definitions

| Priority | Meaning |
|---|---|
| P0 | Fix before further feature development because the current correctness gate is unreliable or core operation can hang |
| P1 | Fix before a signed/public release or broad refactor |
| P2 | Stage as maintainability, TV quality, or product maturity work |

## Findings

### AUD-001 - HTSP disconnect and timeout paths can deadlock

**Priority:** P0
**Classification:** Generic, upstream candidate

`HtspService.connect()` holds `connectMutex` while it calls `request()`
(`app/src/main/java/cz/preclikos/tvhstream/htsp/HtspService.kt:115-186`). A request
timeout calls `failAll()`, which tries to acquire the same non-reentrant mutex
(`HtspService.kt:241-254` and `401-445`). That path can suspend forever.

Both `disconnectInternal()` and `failAll()` also cancel and join the reader job
before closing its socket (`HtspService.kt:371-389` and `413-436`). The HTSP
codec catches socket timeouts and continues reading indefinitely without a
cancellation check (`app/src/main/java/cz/preclikos/tvhstream/htsp/HtspCodec.kt:181-203`
and `217-233`). A reader blocked on an idle or partial frame therefore need not
finish, so the join can prevent the socket from ever being closed.

This needs fake-server regression tests for no hello response, a partial frame,
an idle connected socket, explicit disconnect, and request timeout. Close the
transport before joining the reader and avoid re-entering the connection mutex
from a request made while that mutex is held. Do not combine this with extractor
or decoder changes.

### AUD-002 - The advertised verifier is green while Android tests do not compile

**Priority:** P0
**Classification:** Fork tooling plus a generic test repair

`./tools/verify` runs only `testDebugUnitTest assembleDebug` and APK metadata
checks (`tools/verify:30-31`). It does not run lint or compile instrumentation
tests. `./gradlew compileDebugAndroidTestKotlin --no-daemon` currently fails in
`ChannelRowTest.kt:25-34` because `imageLoader` and `piconPath` are missing after
the production component signature changed.

Repair the test and make instrumentation-test compilation and lint part of the
normal gate. Device execution can remain a separate, explicitly configured
step.

### AUD-003 - Saved credential handling needs hardening

**Priority:** P1
**Classification:** Generic

The password at rest is encrypted with AES-GCM using Android Keystore, which is
a sound foundation. The surrounding lifecycle has gaps:

- `SettingsConnection.kt:42-55` loads the decrypted password into
  `rememberSaveable`, serializing it into activity saved-instance state.
- `AndroidManifest.xml:12-19` does not opt credentials and server settings out
  of Android backup and device transfer.
- `SecurePasswordStore.kt:29-40` silently turns every decrypt or key failure
  into an empty password. `ConnectionPolicy.kt:17-25` then skips authentication,
  which hides corruption and may unexpectedly try anonymous access.
- The operator screen loads the real password and offers a reveal control. A
  shared-TV threat model should decide whether connection settings need an
  operator gate or whether re-entry is preferable to revealing the stored
  secret.

Keep password UI state transient, add explicit backup/data-extraction rules,
surface key/decryption failure as a recoverable credential error, and document
the shared-device threat model. Consider protecting the connection screen from
screenshots while a secret is present.

### AUD-004 - Repository state crosses threads through unsynchronized maps

**Priority:** P1
**Classification:** Generic, upstream candidate

`TvhRepository` mutates `epgByChannel`, `epgCoverage`, and related mutable
collections on its IO scope under `stateMutex`, while `epgForChannel()`,
`nowEvent()`, and `nextEvent()` access `epgByChannel` without that lock
(`TvhRepository.kt:123-151`, `171-193`, and `472-480`). UI calls can therefore
race reconnect/reset and EPG ingestion.

Give the repository one authoritative execution context or expose immutable
snapshots through StateFlow. Do not make composables reach into the repository
to create per-channel flows.

### AUD-005 - Player commands and lifetime are not serialized

**Priority:** P1
**Classification:** Generic, high playback-regression sensitivity

`PlayerSession` owns an uncancelled independent main scope and starts separate
coroutines for play, stop, release, and unsubscribe (`PlayerSession.kt:29-43`
and `77-179`). A rapid channel change can leave older `playService()` work
waiting on DataStore and able to resume after a newer command. The
`lateinit dataSourceFactory` is used by stop/release paths without an
initialization guard, and `release()` can invoke a blocking unsubscribe before
moving work off the caller thread. No unit or component tests cover command
ordering or lifecycle.

Serialize player commands and make resource ownership explicit. Preserve the
current extractor, renderers, decoder selection, and playback watchdog while
doing this work, then rerun the progressive/interlaced and audio matrix on a
test TV.

### AUD-006 - Back and nested settings navigation violate TV expectations

**Priority:** P1
**Classification:** Generic

`SettingsScreen` installs a nested `BackHandler` whose start destination is
`settings/connection`; `nav.popBackStack()` returns no useful parent at that
destination, but the handler still consumes Back (`SettingsScreen.kt:33-53`).
Its cases for outer routes cannot occur in the nested graph. Its navigation
builder also calls `popUpTo(Routes.CHANNELS)`, a destination not owned by that
controller (`SettingsScreen.kt:60-68`).

At the app root, Back calls `finishAffinity()` followed by `exitProcess(0)`
(`AppRoot.kt:93-105`). Android should own process termination. Replace the
nested behavior with an explicit callback to the parent graph and test Back
from every top-level and settings destination.

### AUD-007 - The UI uses mobile Material instead of Compose for TV

**Priority:** P1 for new UI work, P2 for full migration
**Classification:** Generic product UI

The app imports mobile Material 3 throughout and does not depend on
`androidx.tv:tv-material`. Android's current guidance explicitly recommends TV
Material for TV-optimized focus, scale, glow, navigation, and remote behavior,
and warns against mixing the two themes.

The current information architecture is directionally sound: a collapsed rail,
two-pane channel detail, dark playback overlays, and D-pad-first interaction
all fit TV patterns. Implementation issues include:

- Most screen edges use 14-16dp padding instead of the roughly 48dp horizontal
  and 27dp vertical TV safe area.
- Focus behavior is hand-built and inconsistent across rows, buttons, dialogs,
  text fields, and rails.
- Some clickable nodes also implement custom key handling, increasing the risk
  of duplicate or trapped actions.
- Several strings and content descriptions are hardcoded.
- Fixed sizes and no font-scale/TalkBack evidence leave accessibility unknown.
- Player center and media play/pause key behavior do not yet meet the TV quality
  checklist and need an explicit live-TV interaction decision.

Start with one TV `MaterialTheme`, focus tokens, TV buttons/cards, safe content
bounds, and the main navigation drawer. Migrate one screen at a time and do not
mix mobile and TV Material themes in the same subtree.

### AUD-008 - Bundled native decoder provenance is insufficient

**Priority:** P1 before distribution
**Classification:** Generic supply chain and GPL compliance

`app/libs` contains four tracked decoder AARs totaling about 7.3 MB compressed.
Each contains four ABIs. `app/libs/README.md` documents only one FFmpeg decoder
list and a generic build command. It does not identify the exact AndroidX Media
source revision, patches, NDK/compiler, build flags for every AAR, checksums,
licenses/notices, or reproducible build procedure.

Create a native dependency manifest and corresponding-source procedure before
distributing binaries. Any Media3 update must use matching rebuilt extensions
and a dedicated playback regression pass.

### AUD-009 - Release automation and public metadata still describe upstream

**Priority:** P1 before enabling Actions or publishing
**Classification:** Fork-specific

The inherited workflows use Java 17, SDK/build tools 35, Firebase secrets, old
package assumptions, and deployment/release jobs that are not aligned with the
fork. `fastlane/Appfile` still names `cz.preclikos.tvhstream`. The master build
workflow has unnecessary `contents: write`; release workflows invoke third-party
actions without immutable SHA pinning. Actions are enabled in the public fork,
although no fork runs were present during this audit.

`privacy-policy.md` is materially inaccurate: it says the app has no login
credentials and uses Firebase, while the fork stores TVHeadend credentials and
removed Firebase. The README and all artwork remain upstream-branded.

Disable deployment until the final app ID, signing, distribution channel, and
privacy text are approved. Replace the master workflow with read-only CI first.

### AUD-010 - Device tooling does not enforce the production-TV boundary

**Priority:** P1
**Classification:** Appliance-specific tooling

The engineering guide says the household TV is production-only, but
`tools/device` has no device role or expected-identity policy. A local config
pointing at that TV can run `install-debug`, `force-stop`, `smoke`, POWER, and
synthetic key commands without a production refusal.

Add explicit `production` and `test` roles, verify expected model/serial data in
`doctor`, and reject mutating/debug actions for production by default. Keep
bounded output and the prohibition on broad UI dumps and app-data export.

### AUD-011 - UI/data boundaries are only partially implemented

**Priority:** P2
**Classification:** Generic

The project should remain a single Gradle app module for now; its size does not
justify modularization. Within that module, composables directly inject
DataStore stores (`AppRoot`, `SettingsConnection`, `VideoPlayerScreen`) and a
repository (`EpgGridScreen`), and the player ViewModel passes Android Context
through to the session. State is collected with `collectAsState()` rather than
`collectAsStateWithLifecycle()`.

Move screen state and events behind screen-level ViewModels as each screen is
touched. Use lifecycle-aware collection and immutable UI state. Avoid a broad
"clean architecture" rewrite or one-use interfaces.

### AUD-012 - Dependencies contain both lag and dead declarations

**Priority:** P2, except security fixes
**Classification:** Mostly generic

Low-risk patch updates and dead dependency removal should be separate from
major toolchain and playback changes. As of the audit date:

| Dependency | Current | Current stable observed | Treatment |
|---|---:|---:|---|
| Compose BOM | 2026.02.00 | 2026.06.01 | Low-risk UI/tooling slice |
| activity-compose | 1.12.4 | 1.13.0 | Low-risk slice |
| core-ktx | 1.17.0 | 1.19.0 | Low-risk slice |
| DataStore | 1.2.0 | 1.2.1 | Patch slice |
| lifecycle | 2.10.0 | 2.11.0 | Pair with lifecycle collection |
| navigation-compose | 2.9.7 | 2.9.8 | Patch slice |
| Coil | 3.3.0 | 3.5.0 | Test picon loading/cache |
| Koin | 4.1.1 | 4.2.2 | Separate DI slice |
| coroutines | 1.10.2 | 1.11.0 | Pair with concurrency tests |
| Kotlin | 2.3.10 | 2.4.10 | Separate toolchain slice |
| AGP | 8.13.2 | 9.3.1 | Major migration, not routine update |
| Media3 | 1.9.2 | 1.10.1 | Dedicated native/playback project |
| tv-material | absent | 1.1.0 | Add for TV UI migration |

`androidx.security:security-crypto` is unused. Firebase, Google Services, and
Crashlytics catalog entries remain after their removal. AppCompat, mobile
Material Components, Media3 DASH/HLS, and Coil's OkHttp network component need
usage confirmation; several appear unnecessary for the current pure-HTSP path.

### AUD-013 - Memory and release footprint need a measured budget

**Priority:** P2
**Classification:** Generic

The debug APK is about 99 MB. Release shrinking is disabled. Playback can hold
an 8192-message mux SharedFlow buffer, a 10 MB ring buffer per data source, up to
4096 pre-start mux messages, Coil's 128 MB disk cache, Media3 buffers, and native
decoder memory. These are bounds, not proof of excessive resident memory, but
no low-RAM TV profile or long-session memory evidence exists.

Measure total memory during startup, channel surfing, long playback, Back, and
reconnect. Remove unused native extensions only after confirming required
formats, enable release shrinking with keep-rule tests, and add a baseline
profile after startup/navigation behavior stabilizes.

### AUD-014 - Transport security is an explicit product constraint

**Priority:** P2 documentation, P1 if used across an untrusted network
**Classification:** Generic

HTSP currently uses a raw TCP socket. Challenge-response authentication avoids
sending the plain password, but it does not authenticate or encrypt the server,
metadata, or stream. `usesCleartextTraffic="true"` does not secure or govern raw
Socket traffic and appears unnecessary for the pure-HTSP icon path.

Document that direct HTSP is for a trusted LAN or a protected tunnel. If remote
access becomes a product goal, define a supported VPN/TLS tunnel model rather
than inventing protocol encryption in the player.

### AUD-015 - Repository governance still behaves like a temporary fork

**Priority:** P2
**Classification:** Fork-specific

Development is directly on an unprotected `master`, the checkout is shallow,
GitHub Issues and dependency vulnerability alerts are disabled, and all GitHub
Actions are allowed without SHA-pinning enforcement. Secret scanning and push
protection are enabled, which should be retained.

The owner accepts direct `main` development during rapid local iteration.
Keep commits small and buildable, and use branches for upstream contributions,
parallel work, or risky experiments. Enable issues only if the project wants
public support, enable dependency alerts, and fetch full history before
preparing non-trivial upstream ranges.

## Positive security and quality observations

- No credential, signing key, Firebase configuration, or private device address
  was found in tracked application configuration during this audit.
- The app requests only INTERNET plus the system-protected accessibility binding.
- The accessibility service requests key filtering, no event types, and no
  window-content access; non-entry keys pass through by policy and tests.
- Password data at rest uses AES-GCM with a non-exportable Android Keystore key.
- HTSP root message size and nesting depth are bounded.
- Debug Timber logging is planted only for debug builds.
- Current native libraries include both 32-bit and 64-bit ABIs.
- Current APK native packaging passes 16 KB zip alignment; ARM and ARM64 ELF
  load segments inspected in this audit use 0x4000 alignment.
- The existing pure appliance policies have useful JVM regression coverage.
- Google/TCL and alternate playback clients remain available as rollback paths.

## Verification evidence

| Check | Result |
|---|---|
| `git fetch origin && git fetch fork` | Passed |
| `./tools/check-ai-harness` | Passed |
| `./tools/verify` | Passed: JVM tests, debug APK, identity, SDK and ABI assertions |
| `./gradlew lintDebug --no-daemon` | Passed with 62 warnings and no errors |
| `./gradlew compileDebugAndroidTestKotlin --no-daemon` | Failed: `ChannelRowTest` missing two arguments |
| 16 KB APK zip alignment | Passed |
| ARM/ARM64 ELF load alignment | Passed at 0x4000 for current APK libraries |
| Vulnerability scan | Not available locally; GitHub vulnerability alerts are disabled |
| Physical TV pass | Not run; the configured household TV is production-only |

## Target architecture

Keep the target deliberately small:

1. One Android app module.
2. One app-scoped HTSP connection owner with an explicit close lifecycle.
3. Serialized connection and player commands.
4. Repositories that own mutable data on one execution context and publish
   immutable StateFlow snapshots.
5. Screen-level ViewModels exposing UI state and receiving UI events.
6. Lifecycle-aware collection in composables.
7. Pure policy functions for navigation, launch, and key behavior.
8. Compose for TV components and one TV theme for focusable UI.
9. AndroidView only at the Media3 PlayerView boundary until a tested reason to
   replace it exists.

Do not split modules, replace Koin, migrate navigation, or redesign the HTSP
extractor merely to satisfy a diagram. Each change needs a behavior or risk it
directly addresses.

## Staged remediation roadmap

### Stage 0 - Product decisions

- Decide whether this is a household-only appliance, a public TVHeadend client,
  or a public client with an optional appliance profile.
- Decide GitHub/sideload-only versus eventual Google Play distribution.
- Decide whether the existing application ID and installed app data must remain
  upgrade-compatible.
- Select and clear the final name before stable signing and broad UI work.

### Stage 1 - Restore trustworthy correctness gates

- Fix the Android test compilation failure.
- Add lint and instrumentation-test compilation to `tools/verify` and read-only CI.
- Add HTSP timeout, partial-frame, disconnect, and reconnect tests, then fix
  AUD-001 without changing playback decoding.
- Fix settings Back behavior and remove `exitProcess`.

### Stage 2 - Secure state and runtime ownership

- Remove credentials from saveable UI state and backups.
- Make credential failure explicit.
- Serialize connection and player commands.
- Make repository state thread-confined or immutable.
- Add tests before each behavior change and rerun device playback gates.

### Stage 3 - Distribution and supply-chain baseline

- Disable/replace inherited release workflows and stale Fastlane metadata.
- Correct the privacy policy.
- Document native AAR provenance, sources, licenses, hashes, and reproduction.
- Enable dependency alerts and establish a release-signing procedure outside Git.
- Add repeatable ABI, 16 KB, signature, and artifact checksum checks.

### Stage 4 - Product identity migration

- Execute `docs/product-identity-plan.md` after the name and application-ID
  continuity decisions are final.
- Update README, attribution, privacy text, project metadata, launcher assets,
  banner, theme names, package namespace, tools, and AI harness as one planned
  sequence of small commits.

### Stage 5 - TV design system and screen migration

- Introduce TV Material and product design tokens.
- Migrate navigation and common focusable components first.
- Apply safe zones, focus feedback, localization, font scaling, and semantics.
- Migrate channel list, EPG, settings, dialogs, and player overlays separately.
- Add D-pad navigation tests and physical 720p/1080p TV checks per slice.

### Stage 6 - Dependency and performance program

- Remove dead dependencies and take low-risk patches.
- Upgrade Kotlin, AGP, Koin, and coroutines in isolated changes.
- Treat Media3 plus native extensions as a dedicated compatibility project.
- Profile low-RAM behavior, release size, startup, and long-running playback.
- Add baseline profiles only after primary navigation is stable.

## AI harness and tooling implications

The current harness passes its own validator and has strong credential/device
safety language. It is too narrowly encoded around the temporary household
appliance milestone to be the long-term product harness.

After Stage 0 decisions:

- Replace the default appliance framing with a product-level Android TV engineer;
  keep appliance behavior as an optional integration specialty. This was
  completed during the product identity migration.
- Keep an independent read-only reviewer, but add explicit concurrency, TV
  Material, accessibility, privacy, native provenance, and release checks.
- Split the durable product specification from TCL/household deployment notes.
- Parameterize package/label assertions in tooling after the final identity is
  selected.
- Extend `tools/verify` with lint and Android-test compilation.
- Add a native dependency/16 KB/provenance checker.
- Add production and test device roles to `tools/device`.
- Replace inherited release workflows with read-only CI before adding any new
  signing or deployment automation.
- Keep session sharing disabled, automatic subagents disabled, and bounded ADB
  output unless there is a reviewed reason to change those controls.

## References

- Compose for TV: https://developer.android.com/training/tv/playback/compose
- Android TV design: https://developer.android.com/design/ui/tv
- TV app quality: https://developer.android.com/docs/quality-guidelines/tv-app-quality
- TV memory guidance: https://developer.android.com/training/tv/get-started/manage-memory
- Android architecture recommendations: https://developer.android.com/topic/architecture/recommendations
- Compose state saving: https://developer.android.com/develop/ui/compose/state-saving
