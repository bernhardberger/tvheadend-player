# Implementation plan: TVHeadend Player appliance mode

## Architecture decisions

- Keep upstream's Media3/HTSP playback path unchanged because the live TCL test
  passed the human motion-quality gate.
- Use a distinct `at.bernhardberger.tvhplayer` application ID so the accepted
  diagnostic build and Headent remain installable rollback clients.
- Keep UI focus selection in memory, but persist the last channel actually sent
  to the player in Preferences DataStore.
- Represent appliance launches as one-shot requests owned by `MainActivity`.
  Compose consumes a request only after channels are available. Closing the
  player with Back does not create a new request.
- Register for the HOME role without disabling the Google/TCL launcher. TCL's
  firmware currently blocks selection of third-party HOME apps with a
  privileged launcher priority, so boot/wake entry needs a separate safe
  follow-up before it can be considered complete.
- Use one consented accessibility service to filter TCL's globally intercepted
  `KEYCODE_GUIDE` and enter the app when the service connects or the display
  wakes. TCL exposes the physical button to Android as private key code `4001`,
  so the service recognizes that captured code too. It subscribes to no
  accessibility events or window content, and all other keys pass through.
- Treat initial channel metadata as one atomic snapshot: HTSP control delivery
  applies backpressure instead of dropping messages, and the repository publishes
  channels only after `initialSyncCompleted`.
- Reuse the process-scoped HTSP session when an activity is recreated with the
  same server endpoint. Run a full channel snapshot only for process startup,
  changed connection settings, or genuine connection recovery; apply TVHeadend's
  asynchronous channel and event changes incrementally afterward.
- Keep channel and EPG metadata in bounded process memory rather than adding a
  database or JSON cache without measured startup evidence. EPG queries maintain
  a 20-24-hour future horizon and six hours of history, count successful empty
  ranges as queried, observe a per-channel retry cooldown, and cannot disconnect
  the shared HTSP session when an optional guide request times out.
- Keep routine channel and EPG synchronization silent. Represent connection
  progress and actionable failures as typed state integrated into the current TV
  destination rather than a global transient banner. Preserve the last complete
  channel/EPG snapshot while a same-server reconnect stages its replacement.
- Use TV Material for focusable Compose controls. Retain mobile Material only
  for primitives not supplied by TV Material 1.1.0, under one coordinated theme.
- Use TV Material's standard navigation drawer and list-item geometry on a
  shared 48/32 dp overscan-safe grid. Disable focus scaling where rows sit in a
  clipped scrolling viewport, and present playback channel selection as a
  full-height edge sheet with a cinematic scrim rather than an inset card.
- Serialize HTSP teardown and player commands, keep repository flow creation
  thread-safe, and test timeout/Back/key policies independently of Android UI.
- Keep credential input transient, disable app backup/device transfer, and make
  Keystore/decryption failure explicit rather than silently trying anonymous auth.
- Permit repeatable development provisioning only for exact-identity test
  devices: stream an ignored owner-only local secret over stdin into a debug-only
  app-private startup importer, then retain the password only through the existing
  Android Keystore-backed store. Release builds expose no importer or component.
- Preserve TVHeadend channel numbers through the UI and direct-entry policy;
  fall back to one-based positions only for servers with no channel numbers.
- Persist a per-device channel-scope allowlist. An unconfigured device exposes
  All Channels and every current server tag; after customization, at least one
  scope remains enabled and newly introduced tags stay hidden until selected.
- Support app-specific German and English selection and persist the
  operator preference for showing the main EPG menu.
- Retry interrupted playback through the serialized player command gate with
  bounded 1/2/5/10/30-second backoff and visible, Back-cancellable recovery UI.
- Treat ordinary channel tuning as a non-blocking transition: preserve the video
  surface and remote input, and show only delayed compact feedback when tuning is
  slow. Reserve the full-screen recovery scrim for connection loss or actual
  playback recovery, not every non-playing state.
- Consume OK and D-pad Down when they reveal hidden playback controls so the same
  key event cannot activate a newly focused control. Treat selection of the
  current playback channel as a drawer-close action rather than a tune request.
- Keep the active service warm while Back exposes the foreground Channel List.
  A same-service player request is idempotent, while `MainActivity.onStop` remains
  the hard boundary that stops playback for HOME or other background transitions.
- At the root Channel List, route Back to the warm fullscreen player when one
  exists. Preserve normal Android root exit when playback is idle rather than
  adding a routine confirmation dialog or non-standard Quit menu item.
- Treat the player Stop control as explicit serialized teardown: await the stop
  command before leaving the player route so navigation cannot cancel cleanup.
- Mount the Media3 `PlayerView` at the app root so operator screens retain live
  video as well as audio under a dark navigation scrim. Player controls remain a
  player-route concern; navigation does not detach or recreate the stream surface.

## Hardening checkpoint: 2026-07-24

The repository-wide audit remediation is implemented on
`hardening/audit-findings`:

- HTSP no-response and idle-reader disconnect deadlocks have regression tests
  and transport-first cleanup.
- The full verifier covers native integrity, Python tool policy, JVM tests,
  lint, Android-test compilation, APK assembly/identity, and 16 KB alignment.
- Credential saved-state/backup exposure, repository flow races, player command
  ordering, nested settings Back, and forced process exit are fixed.
- Focusable UI is migrated to TV Material with safe-area spacing,
  lifecycle-aware state collection, localized navigation/status copy, and
  physical media play/pause key handling.
- Inherited Firebase/Play/GitHub release automation is removed; read-only CI,
  accurate README/privacy text, and production/test device roles are in place.
- Native AAR hashes, libraries, ABIs, and ELF alignment are recorded and
  checked. Exact source/toolchain provenance and complete notices are absent,
  so signed release distribution remains blocked by the strict native gate.

No runtime validation was performed during that code-only checkpoint. The
dining-room TCL Smart TV Pro has since been assigned as a temporary test target;
the hardened APK was installed and launched there, revealing that this branch
still needed the divergent localization/options/recovery feature set ported. The
corrected APK still requires the full playback, focus, remote, standby/wake, and
reboot matrix before deployment to the production household TV.

## Phase 1: Reproducible private build identity

### Task 1: Create the TVHeadend Player build identity

**Acceptance criteria:**

- Package ID and source namespace are `at.bernhardberger.tvhplayer`; the launcher
  label is `TVHeadend Player`.
- Firebase/Crashlytics and the Google services build requirement are absent.
- The GPLv3 license and upstream attribution remain intact.

**Verification:**

```bash
./tools/verify
aapt dump badging app/build/outputs/apk/debug/app-debug.apk
```

**Files:** Gradle configuration and string resources.  
**Dependencies:** None.

## Phase 2: Channel behavior

### Task 2: Persist the last played channel

**Acceptance criteria:**

- Starting or switching playback stores the channel ID in app-private
  Preferences DataStore.
- UI focus changes alone do not overwrite the last played channel.
- Missing/stale IDs fall back to the first current channel.

**Verification:** unit tests plus force-stop/relaunch on the TCL.

**Files:** a new store, Koin wiring, and player integration.  
**Dependencies:** Task 1.

### Task 3: Add physical channel-key switching

**Acceptance criteria:**

- During fullscreen playback, `KEYCODE_CHANNEL_UP` selects the next channel and
  wraps at the end; `KEYCODE_CHANNEL_DOWN` selects the previous channel and
  wraps at the start.
- In the root channel list and focused playback channel drawer, the same keys
  page by one viewport with one-row overlap: `CH+` pages toward earlier rows and
  `CH-` toward later rows. Paging stops at the list boundary and does not tune
  until the user confirms a row.
- Other player keys preserve current behavior.

**Verification:** pure navigation unit tests and physical remote checks.

**Files:** navigation helper/test and `VideoPlayerScreen`.  
**Dependencies:** Task 2.

### Task 3a: Add direct channel-number entry

**Status:** Implemented and verified on the TCL on 2026-07-23.

**Acceptance criteria:**

- Top-row and numpad `0`-`9` key codes build a visible 1- to 3-digit overlay
  during playback.
- Entered numbers select the matching TVHeadend channel number. One-based list
  positions are used only if the server supplies no channel numbers.
- One- and two-digit entries tune after 1.5 seconds or immediately on OK; a
  complete three-digit entry remains visible briefly before tuning.
- Back cancels pending entry, and invalid or out-of-range numbers do not change
  the current channel.

**Verification:** pure navigation unit tests and physical remote checks with
1-, 2-, and 3-digit channel numbers.

**Files:** navigation helper/test and `VideoPlayerScreen`.
**Dependencies:** Task 3.

### Checkpoint: Channel behavior

- Full unit suite passes and APK builds.
- Progressive ORF1 and interlaced ServusTV still play.
- Physical channel buttons work repeatedly.
- Numeric entry visibly accepts and tunes 1-, 2-, and 3-digit channel numbers.

## Phase 3: Appliance entry

### Task 4: Add one-shot autoplay launch requests

**Status:** Implemented and verified on the TCL on 2026-07-23.

The activity owns an identified pending request and creates a replacement only
for a new explicit launch. Compose waits for persisted state and non-empty
current channels, then consumes the matching request before navigating to the
player. Recomposition, resume, and player Back do not generate requests.

Runtime verification confirmed that force-stop plus launch restored ORF1 HD and
a new explicit launcher intent started ORF1 HD exactly once. Back originally
stopped playback; the later operator-UI redesign intentionally keeps that session
warm only while the activity remains foreground so returning from the Channel
List does not retune. ServusTV HD Oesterreich also passed the direct human
interlaced-motion regression check.

**Acceptance criteria:**

- Fresh process and explicit appliance intents wait for non-empty channels,
  then navigate to the persisted/first channel.
- A new request while settings/channel UI is visible starts playback.
- Back from player returns to UI and does not autoplay again.
- Back from the root Channel List returns to warm playback without retuning;
  root Back with no active playback exits normally.

**Verification:** launch-policy unit tests and ADB launch/Back/force-stop tests.

**Files:** launch-policy helper/test, `MainActivity`, and `AppRoot`.  
**Dependencies:** Task 2.

### Task 5: Register and validate the HOME role

**Status:** Candidate registration implemented; TCL selection blocked.

The packaged activity appears in Android's HOME candidate list. On the TCL,
both `cmd package set-home-activity` and affirmative selection in Android's Home
app screen store TVHeadend Player as preferred, but Google Basic TV still resolves
and opens. The system launcher has privileged priority `2`, while Android caps the
third-party app filter to `0`. Google remains enabled and selected; no
HOME-role standby/wake or cold-reboot success is claimed. The separate
accessibility entry fallback is validated under Task 6.

**Acceptance criteria:**

- Android lists TVHeadend Player as a HOME candidate.
- ADB can select it as HOME without disabling Google Basic TV.
- HOME, standby/wake, and cold boot enter playback through the one-shot launch
  policy.

**Verification:** package resolver, HOME key, standby/wake, and approved cold
reboot tests.

**Files:** Android manifest only unless TCL runtime behavior requires a bounded
receiver fallback.  
**Dependencies:** Task 4.

## Phase 4: TCL TV key

### Task 6: Add the scoped GUIDE accessibility service

**Status:** Implemented and verified on the TCL on 2026-07-23.

TCL initially stored the user-approved service component but left global
accessibility off because Safety Guard rejected the app's hidden
`APP_AUTO_START` operation. Setting that app-op to `allow` for TVHeadend Player only and
repeating the Android consent toggle bound the service. The setting, app-op, and
live autoplay then survived three standby/wake cycles from Google Home and one
approved Android reboot while Google remained the default HOME.

The physical remote reports Linux `KEY_EPG` with scan code `0x0c005b`, but TCL's
Android callback exposes private key code `4001`, not standard `KEYCODE_GUIDE`
(`172`). The service recognizes both codes. Physical TV launched playback from
TCL UI and TVHeadend Player's operator UI, and pressing it during playback no longer
restarts the player. The final metadata requests key filtering but no
accessibility events or window-content access.

**Acceptance criteria:**

- Service declares key filtering without window-content access or accessibility
  event subscriptions.
- GUIDE down launches/reorders TVHeadend Player and GUIDE up is consumed.
- An entry intent while the player is already visible does not restart playback.
- Every key other than standard GUIDE and captured TCL code `4001` returns
  `false`.
- Service connection after boot and `SCREEN_ON` after standby each create a
  coalesced appliance launch through the existing one-shot policy.
- Enabling requires affirmative selection in Android accessibility settings and
  the in-app disclosure describes the exact scope.

**Verification:** key-policy unit tests, Android service inspection, and the
physical TV button from Google Home, channel UI, and playback; then standby/wake
and an approved cold reboot with enabled-service and app-op state rechecked.

**Files:** service class, policy/test, manifest, XML metadata, and strings.  
**Dependencies:** Task 4.

### Checkpoint: Appliance behavior

- TV, boot, and wake reach live TV; direct HOME remains blocked by TCL.
- CH+/CH- work in live playback.
- Back still reaches operator UI.
- Google Basic TV and both rollback clients still launch directly.

## Future profile: Simple TV mode

Use **Simple TV mode** as the user-facing name and a restricted appliance
profile as the internal concept. Do not call the first implementation Android
kiosk mode: Android lock-task/device-owner kiosk behavior controls the whole
device, can suppress HOME and other apps, and conflicts with the current
reversibility and rollback requirements.

Simple TV mode is an app-level navigation and capability profile:

- Persist whether the profile is enabled and enter live TV on startup using the
  existing one-shot launch and last-played-channel policies.
- Define allowed destinations centrally with pure policy, initially live TV and
  the Channel List. Make EPG optional and add future recordings or other
  features as independent capabilities rather than scattered visibility flags.
- Remove unavailable destinations from navigation and focus traversal. Do not
  leave disabled or dead focus targets, and do not rely only on hiding buttons;
  route guards must reject disallowed deep or restored destinations too.
- Keep Back inside the profile: fullscreen playback returns to the allowed
  operator UI, and root UI returns to warm playback. Stopping playback may show
  the allowed Channel List, but must not silently unlock the full app.
- Provide a visible **Unlock controls** action in the restricted navigation
  surface. Unlock the full UI for the current foreground session; the profile
  becomes restricted again on the next fresh launch.
- Offer an optional owner PIN for households that need child resistance. Treat
  it as protection against casual UI access, not as a security boundary. Avoid
  hidden key sequences or long-press-only escape gestures.
- Allow permanent disable only from the full settings UI after unlocking. Keep
  normal Android Back exit available in the full profile and retain Google TV
  plus rollback clients.

Suggested implementation slices:

1. Add a JVM-tested `UiCapabilities`/profile policy and persisted profile store.
2. Add full-settings controls for enabled state and optional EPG/future feature
   capabilities, plus an explicit action to enter Simple TV mode now.
3. Enforce the route allowlist and deterministic startup/Back/focus behavior.
4. Add session-only Unlock controls, then optional local PIN verification.
5. Validate cold launch, stop, Back, HOME, wake, reboot, long labels, and every
   enabled/disabled capability on the physical TV.

Open product choices before implementation:

- Whether EPG is enabled by default in Simple TV mode.
- Whether Stop remains visible, and what idle screen it should reveal.
- Whether an owner PIN is required, optional, or omitted for the first slice.
- Whether the eventual public name is Simple TV mode, Restricted mode, or an
  appliance profile under the final product identity.

## Phase 5: Durable release and deployment

### Task 7: Sign and install the release build

**Acceptance criteria:**

- Release APK uses a stable private product key outside Git.
- Release package upgrades over itself and remains 32-bit compatible.
- APK SHA-256, signing fingerprint, source commit, and rollback commands are
  documented without secrets.
- `./tools/check-native-libs --release` passes with exact corresponding source,
  toolchain, license, and notice evidence for every bundled decoder AAR.
- The approved product identity is used before stable signing; inherited
  Play/Fastlane workflows must not be re-enabled unchanged.

**Verification:** unit suite, release build, `apksigner verify`, install/upgrade,
and complete TCL runtime matrix.

**Files:** non-secret signing configuration support and deployment docs.  
**Dependencies:** Tasks 1-6.

## Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| TCL resets HOME or accessibility after reboot | High | `APP_AUTO_START=allow` plus the user-enabled service survived three wake cycles and one reboot; retain rollback and recheck after firmware updates |
| TCL changes its private TV key mapping | High | Recognize standard GUIDE plus captured Android code `4001`; recapture after firmware or remote changes |
| Autoplay loops after Back | High | One-shot request counter with tests; never key autoplay directly to lifecycle resume |
| Persisted channel disappears | Medium | Validate against current channel IDs and fall back to first channel |
| Channel synchronization exposes partial lists | High | Lossless control delivery and atomic initial publication are implemented with JVM regressions; recheck a stable count across reconnects on the TCL |
| Media3 playback regresses | High | Do not alter extractor/rendering code; replay progressive and interlaced channels at each checkpoint |
| Fork diverges from upstream | Medium | Keep appliance changes narrow and maintain the upstream remote |

## Final checkpoint

- All spec success criteria pass.
- Repo diff has been reviewed for secrets, GPL compliance, and surgical scope.
- Source is committed and pushed to the public fork.
- Canonical homelab docs record the deployed package and rollback path.
