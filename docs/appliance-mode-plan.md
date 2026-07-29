# Implementation plan: TVHeadend Player appliance mode

**Status:** Active architecture and implementation record. Read only the
sections relevant to the current task; current specifications, source, and tests
take precedence over completed-work narrative.

## Architecture decisions

- Keep upstream's custom Media3/HTSP playback architecture unchanged because the
  live TCL test passed the human motion-quality gate. Upgrade Media3 only as a
  source-matched native compatibility slice with the full playback regression
  matrix.
- Use a distinct `at.bernhardberger.tvhplayer` application ID so the accepted
  diagnostic build and Headent remain installable rollback clients.
- Keep UI focus selection in memory, but persist the last channel actually sent
  to the player in Preferences DataStore.
- Represent appliance launches as one-shot requests owned by `MainActivity`.
  Compose creates the fresh-start request only when startup autoplay is enabled
  and consumes a request only after channels are available. Closing the player
  with Back does not create a new request.
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
- Use TV Material's standard navigation drawer and list-item geometry on a shared
  48/32 dp overscan-safe grid so expanded navigation translates the closed-width
  browse viewport rather than reflowing it narrower. Keep the collapsed global
  rail on Settings routes beside the temporary Settings category rail. Back from
  browse content focuses the current global destination before Channels/root policy;
  Settings content returns to its current category first. Disable focus scaling
  where rows sit in a clipped scrolling viewport, and present playback channel
  selection as a full-height edge sheet with a cinematic scrim rather than an
  inset card.
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
- Present EPG data as a widescreen TV timeline: channels are virtualized vertical
  rows, time runs horizontally on one shared ruler, programme widths remain
  strictly proportional, and compact date/Now/group controls replace the
  multi-row magazine header. Keep the existing bounded cache and details-first
  action model.
- Preserve HTSP DVR owner, path, channel, artwork, playback, and episode metadata.
  Build the Archive from sanitized server-relative file parents, preserve nested
  folders and focus/scroll restoration, sort each archive level newest-first,
  and present it as a non-scaling list with persistent full metadata beside it.
  Join DVR channel IDs to channel picons, prefer HTSP-cached programme artwork,
  and use recursive folder summaries with descendant count, size, and five
  directly selectable recent recordings. Keep Archive's persistent two-pane
  hierarchy, but use full-width, opaque lists for Schedule and Problems: Schedule
  groups active and upcoming entries by Recording now, Today, Tomorrow, and
  explicit calendar date; Problems groups entries into Failed and Cancelled.
  Reserve a trailing date/time column in every recording row, and expose details
  plus labeled actions in a right-side overlay with focus restoration. Move focus
  directly between folder levels without a navigation-rail fallback.
  Treat TVHeadend access control as the recording-visibility security boundary;
  client grouping is presentation, not authorization.
- Support app-specific German and English selection and persist the
  operator preference for showing the main EPG menu.
- Retry interrupted playback through the serialized player command gate with
  bounded 1/2/5/10/30-second backoff and visible, Back-cancellable recovery UI.
- Treat ordinary channel tuning as a non-blocking transition: preserve the video
  surface and remote input, and show only delayed compact feedback when tuning is
  slow. Reserve the full-screen recovery scrim for connection loss or actual
  playback recovery, not every non-playing state.
- Give recording playback the same auto-hiding cinematic control language as
  live TV, with recording metadata, a progress bar, icon-based transport and
  focus restoration. Hidden controls use Kodi-style direct
  seeks: Left/Right move 30 seconds and Down/Up move 10 minutes; visible controls
  retain normal D-pad focus navigation. Accumulate rapid fixed-step inputs and
  dispatch one seek after a short debounce, keeping feedback visible while the
  player buffers and briefly after playback resumes. Back hides visible controls
  before a subsequent Back returns to the recordings library while playback
  remains warm. Explicit Stop and natural end tear down the session and return to
  the recordings library. Preserve the previous recordings mode, folder, scroll,
  and focused item across that navigation.
- Keep transient player identity separate from transport: a large picon and
  programme or recording metadata occupy the top-left under a broad top scrim,
  with the wall clock at top-right on a shared first-baseline anchor. The bottom
  scrim presents one timeline first and a left-grouped
  navigation/transport/utility row beneath it, with Stop separated at the end.
  Timeshift keeps a programme-anchored axis in every focus and playback state,
  overlays the rewindable window and live edge, and exposes **Go live** in the
  transport cluster only while behind live. Successful return to live is conveyed
  by the timeline without a text notice. Keep **Up next** and its start time with
  top metadata, pair the clock with the programme end time, and center delayed
  unboxed ordinary tuning status over video.
  Map standard hardware Info to programme details and TV Contents Menu/TV Number
  Entry to the channel drawer. The G10 List/123 button is physically confirmed as
  app-visible `KEYCODE_BOOKMARK` and opens that drawer; validate other remote
  models independently. Keep drawer focus surfaces wholly inside its opaque
  content region and reserve the trailing width for the video fade.
- Use one shared compact Playback options overlay for live TV and recordings,
  anchored near More rather than using the independent-action right-overlay
  template. Present Audio, Subtitles, Display, and Stats as a structured root;
  replace it with category choices after selection, show current values in the
  root rows and detail header, and keep Exit Simple TV as a secondary
  owner action with the existing PIN and confirmation flow. Back closes the
  detail, overlay, then stats, before player Back. Player controls use recognizable icons
  and accessible descriptions without a dedicated visible label row. Keep Stop
  directly reachable at the terminal end, separated from the ordinary utility
  actions.
- Keep Stats for nerds session-only and non-focusable. `PlayerSession` samples
  Media3 formats, decoder names, playback timing, decoder counters, and audio
  underruns no more than once per second while enabled. Custom HTSP data sources
  count successful reads so the UI can report stream/file read rate rather than a
  misleading connection-speed estimate. The active live subscription also maps
  optional `signalStatus` and `queueStatus` messages without retaining or exposing
  the subscription ID; recordings omit tuner data. Sample display mode, thermal
  state, app PSS, and Android low-memory state in the same opt-in job. Omit scan
  type and deinterlacing unless future codec parsing or Android APIs can prove
  them; never infer either from resolution, frame rate, or decoder name. Use a
  multi-column overlay to remain inside the TV safe area as optional sections
  appear. Diagnostic snapshots contain no server, path, credential, identifier,
  raw-error, or log fields.
- Consume OK and D-pad Down when they reveal hidden playback controls so the same
  key event cannot activate a newly focused control. Treat selection of the
  current playback channel as a drawer-close action rather than a tune request.
- Keep the active service warm while Back exposes the foreground Channel List.
  A same-service player request is idempotent, while `MainActivity.onStop` remains
  the hard boundary that stops playback for HOME or other background transitions.
- At the browse root, route Back to the warm fullscreen live or recording player
  at most once. Consume the warm-return token before player navigation so
  player→browse→Back cannot loop. Re-arm only on deliberate browse navigation or
  newly started playback; player Back alone must not re-arm. Preserve normal
  Android root exit when the opportunity is spent or playback is idle rather than
  adding a routine confirmation dialog or non-standard Quit menu item. Simple TV
  never exits through Back.
- Treat the player Stop control as explicit serialized teardown: await the stop
  command before leaving the player route so navigation cannot cancel cleanup,
  and clear the warm-return opportunity with the torn-down session.
- Mount the Media3 `PlayerView` at the app root so operator screens retain live
  video as well as audio under a dark navigation scrim. Player controls remain a
  player-route concern; navigation does not detach or recreate the stream surface.
  Implemented on 2026-07-29: the no-benefit route-owned diagnostic was removed,
  one activity-composition-owned view now survives warm player/shell transitions,
  and only player/shell destination edges bypass the default navigation fade.

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

## Simple TV mode

Use **Simple TV mode** as the user-facing name and a restricted appliance
profile as the internal concept. Do not call the first implementation Android
kiosk mode: Android lock-task/device-owner kiosk behavior controls the whole
device, can suppress HOME and other apps, and conflicts with the current
reversibility and rollback requirements.

Simple TV mode is an app-level, player-only session profile:

- Persist **Start in Simple TV mode** independently from whether the current
  foreground session is restricted. Changing the startup toggle never enters
  the mode immediately.
- A fresh launch with that preference enabled uses the existing one-shot launch
  and last-played-channel policies, enters the restricted session, and plays the
  last valid channel. **Start Simple TV now** performs the same entry explicitly.
- While active, retain fullscreen live TV, channel keys, number entry, the player
  channel drawer, and optionally timeshift. Do not expose the Channel List, EPG,
  recordings, Settings, Stop, or app-exit navigation.
- Back dismisses player overlays but cannot leave fullscreen playback. HOME
  remains normal Android behavior; this is not lock-task or device-owner kiosk
  mode.
- Provide a visible **Exit Simple TV** player action. If an owner PIN is
  configured, verify it first. Always show a separate cancellable confirmation,
  including when no PIN is configured, with the safe keep-watching action focused.
- Confirmed exit restores the full UI only for the current app session. It does
  not change the persisted startup preference; the owner can re-enter through
  **Start Simple TV now**, and the next fresh launch restricts the app again.
- Treat the optional PIN as protection against casual UI access, not as a
  security boundary. Do not use a hidden sequence or long-press-only escape.

Physical-TV validation remains required for cold launch, explicit start, Back,
exit cancellation, correct/incorrect PIN, HOME, wake, reboot, and focus behavior.

## Phase 5: Durable release and deployment

### Task 7: Sign and install the release build

**Status:** Native provenance and two-stage non-secret release tooling implemented
on 2026-07-27. The stable owner key is isolated on LXC 117 and signed packaging
passes. The owner explicitly approved a constrained G08 production installation
while the G10 is unavailable, then separately approved removal of the legacy
client after launching the new app. Runtime validation found an intermittent
interlaced-motion failure: 720p50 remained smooth, but direct 1080i25 AVC
playback initially looked less smooth and less effectively deinterlaced than
the prior production build. The G08 reports `c2.mto.avc.decoder`, zero dropped
output buffers, and a 3840x2160 60 Hz display mode; disabling timeshift did not
improve motion. A
`0.1.1` diagnostic candidate restores only the pre-`44ed28c` H.264 format/SAR
update behavior while retaining the audited FFmpeg audio dependency. It made no
visible difference on the same G08 service, falsifying that hypothesis. The next
diagnostic restores route-owned fullscreen `PlayerView` composition while
retaining the warm root surface only behind browse UI; `0.1.2` also made no
visible difference. Both old and rebuilt AARs contain only a stub experimental
FFmpeg video renderer that reports every format unsupported, and the affected
service uses AC3 rather than the rebuilt MP1/MP2/MP3 audio path. Stop speculative
decoder changes pending a direct same-broadcast reference comparison or new
evidence from the G10. The G10 has previously entered a similar bad render mode
intermittently and recovered after retuning; on the G08, switching away and back
and fully force-stopping/reopening `0.1.2` did not restore quality. On 2026-07-27,
the operator reported fluid ServusTV playback after rebooting that same G08 with
the unchanged `0.1.2` build. This points to device/decoder initialization state
rather than a deterministic application format or surface regression. The
operator then confirmed that the good mode survived both standby/wake and a
switch to another channel and back. Current motion therefore passes direct human
review, but keep the intermittent startup fault open until later cold starts
establish when the good and bad modes recur.

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
