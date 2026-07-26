# Spec: TVHeadend Player appliance mode

## Objective

Provide an optional single-purpose live-TV profile and household integration for
TVHeadend Player users who should not need to navigate Google TV.

The app must keep the configured household TVHeadend account local to the device,
play the last selected channel after an appliance launch, support physical
channel and number buttons, reclaim TCL's globally intercepted TV/GUIDE button,
and enter live TV after boot or display wake through a narrowly scoped
accessibility service.

The upstream-shaped channel list, EPG, and settings remain available as an
operator path. The app must not immediately restart playback after the user
backs out to those screens.

## Tech stack

- Android API 28 minimum / API 36 target
- Kotlin 2.3.10, Jetpack Compose, and AndroidX TV Material 1.1.0 for focusable UI
- AndroidX Media3 / ExoPlayer 1.9.2
- TVHeadend HTSP through the existing custom extractor
- Preferences DataStore for non-secret last-channel state
- Android Keystore/AES-GCM for the TVHeadend password, with backup and device
  transfer disabled and password input excluded from saved-instance state
- Android `AccessibilityService` for GUIDE filtering plus boot/wake appliance
  entry, without subscribing to accessibility events or window content
- GPL-3.0; the public product retains predecessor copyright and license material

## Commands

```bash
./tools/verify
# Mutating commands require an explicitly configured test device.
./tools/device install-debug
./tools/device launch
```

Final release builds must use a private stable product signing key and must not rely
on the Android debug keystore.

## Device roles

- The deployed household TV is a production appliance. Do not use it for routine
  debug APK installs, ADB key injection, smoke tests, or development experiments.
- The dining-room TCL Smart TV Pro is the temporary debug/test target. Its address
  remains only in ignored local device configuration, and tooling must verify its
  manufacturer, model, device, and product before every mutation.
- `tools/device` enforces this boundary: production and unclassified devices
  reject install, launch, force-stop, smoke, screenshot, synthetic-key, and
  credential-provisioning actions.
- A designated test device may receive TVHeadend credentials through the
  debug-only app-private provisioning path after role and complete live identity
  validation. Secret values travel only over process stdin from an ignored,
  owner-only local file and are never entered through the TV UI.

## Project structure

- `app/src/main/java/.../player/` — Media3 playback and channel switching
- `app/src/main/java/.../stores/` — persisted last-channel selection
- `app/src/main/java/.../ui/` — launch policy and normal TV UI
- `app/src/main/java/.../accessibility/` — GUIDE filtering and boot/wake entry
- `app/src/main/res/xml/` — accessibility-service declaration
- `app/src/test/` — pure launch/navigation policy tests
- `docs/` — appliance behavior, build, and rollback documentation

## Code style

Match the existing Kotlin/Compose style. Keep appliance decisions explicit and
testable rather than scattering key codes or intent checks through composables.

```kotlin
fun adjacentChannelId(
    orderedIds: List<Int>,
    currentId: Int,
    direction: Int,
): Int? = when {
    orderedIds.isEmpty() -> null
    else -> orderedIds[(orderedIds.indexOf(currentId).coerceAtLeast(0) +
        direction).floorMod(orderedIds.size)]
}
```

## Testing strategy

- Unit-test channel navigation, wrapping, TVHeadend number entry, playback
  recovery backoff, UI defaults, language parsing, and launch-policy decisions.
- Run JVM tests, lint, instrumentation-test compilation, and build the APK.
- Verify audited native AAR hashes, ABI layout, ELF/APK 16 KB alignment, and
  release-provenance status.
- Install beside both stock Headent and the temporary upstream-package
  TVHStream diagnostic build.
- Runtime-test progressive and interlaced playback, `CH+`, `CH-`, 1- to 3-digit
  channel entry, persistence across force-stop, normal Back navigation, HOME
  launch, GUIDE/TV key, standby/wake, and a cold reboot.
- Treat visible motion quality on an interlaced sports broadcast as a mandatory
  human verification gate.

## Boundaries

### Always

- Keep credentials in app-private storage; never hardcode or commit them.
- Restrict automated credential provisioning to exact-identity test devices;
  keep it unavailable in release builds and on production/unclassified devices.
- Preserve a route to channel list, EPG, and settings through Back navigation.
- Keep Google Basic TV and stock Headent installed until all runtime checks pass.
- Use the distinct `at.bernhardberger.tvhplayer` application ID and a stable
  signing key.
- Keep the operator UI on one overscan-safe TV layout grid. Use TV Material
  navigation drawers and list items rather than hand-built focusable replicas;
  focused rows must remain unclipped, and the playback channel sheet must attach
  to the screen edge instead of floating like a dialog. The global navigation
  rail uses a modal overlay so expanding it does not reflow browse content.
  Settings hides the global rail and keeps only its category rail with symmetric
  full-screen safe margins; onboarding and unlock use the same full-screen
  padding. Collapsed rail icons expose destination content descriptions.
- Normal non-autoplay launches open an in-app Home dashboard. Channels may use
  List with details (default) or Large cards. Simple TV quick select uses the
  large-card grid. Player Info reuses the shared Content Details composition.
- Reveal hidden playback controls with OK or D-pad Down. Picking the channel that
  is already playing from the playback channel sheet closes the sheet without
  rebuilding or restarting the player session.
- Live and recording playback expose one **Playback options** action in the main
  controls. An opaque popover anchored above the bottom-end control cluster
  switches laterally among Audio, Subtitles, Display, and Stats categories and
  shows the current value without a nested drill-down. Opening the popover
  suspends control auto-hide; Back closes it and restores focus to the cluster.
  Focused player icon controls show an anchored label chip.
- Stats for nerds is a non-focusable, one-second diagnostic overlay. It may show
  playback state/timing, selected formats, decoder names, rendered/dropped frame
  counters, audio underruns, measured HTSP stream/file read rate, display output,
  thermal state, and app memory. Live TV conditionally adds TVHeadend tuner signal,
  SNR, reception errors, queue depth/delay, and server-side frame drops only when
  the active adapter reports them; recordings never imply a tuner. Unavailable
  scan type and deinterlacing details are omitted rather than guessed. The
  overlay uses a screen-safe multi-column layout and must not expose server
  addresses, recording paths, credentials, identifiers, raw errors, or logs.
  Back hides stats before normal player Back behavior.
- Simple TV mode is a strict player-only session. Configurable state is limited to
  startup enablement, optional timeshift, and an optional owner PIN. Granular
  EPG/recordings/stop/settings/app-exit flags are not offered. Its startup toggle
  affects only fresh launches, while **Start Simple TV now** enters it explicitly.
  Back may dismiss overlays but must not leave playback while the mode is active.
- Exiting Simple TV is deliberately secondary inside Playback options rather than
  a primary transport action. It requires optional owner-PIN verification and a
  separate cancellable confirmation even when no PIN is set. Confirmed exit
  unlocks only the current app session and does not change startup.
- Consume only Android GUIDE and the captured TCL TV key code in the
  accessibility service; boot/wake entry must not subscribe to accessibility
  events or inspect window content.

### Ask first

- Disabling any TCL or Google package.
- Removing either rollback client.
- Adding server-side transcoding or changing TVHeadend stream profiles.
- Publishing a signed release or upstream pull request.

### Never

- Embed TVHeadend credentials, signing material, or Firebase secrets in Git.
- Turn the accessibility service into general UI automation or key logging.
- Autoplay again merely because the player was closed with Back.
- Make server, tuner, OSCam, storage, or recording changes for this client work.

## Success criteria

1. The TVHeadend Player package installs and runs on the TCL's 32-bit `armeabi-v7a`
   Android TV 12 environment.
2. Interlaced-channel playback remains at least as good as the accepted
   TVHStream diagnostic result.
3. During fullscreen playback, physical `CH+` and `CH-` switch to adjacent
   visible channels and wrap at the ends of the list. When a channel browser is
   focused, they page that list without tuning until the user confirms a row.
   The owner can independently limit each TV to All Channels, selected TVHeadend
   channel tags, or a mixture, with at least one browsing scope always enabled.
4. Physical `0`-`9` keys show a channel-number overlay and select the matching
   TVHeadend channel number after 1 to 3 digits. Positional numbering is used only
   when the server supplies no channel numbers at all.
5. The last successfully selected channel survives process death and reboot.
6. When startup autoplay is enabled, a fresh app, HOME, boot, wake, or
   GUIDE-appliance launch waits for connection and channel data, then plays the
   persisted channel or the first channel. When it is disabled, a fresh app
   opens the normal UI. If playback is already visible, the entry intent must
   not restart it.
7. Back reveals the normal TVHeadend Player UI without an autoplay loop and keeps the
   foreground live or recording session and video surface warm behind a readable
   navigation scrim. Root Back may return to that warm fullscreen session once
   (one-shot). The return opportunity is consumed before navigating to the player
   so a subsequent root Back finishes the activity instead of looping. Returning
   from the player to browse does not re-arm the opportunity; deliberate browse
   navigation or newly started playback may re-arm one warm return. Selecting the
   same channel does so without retuning. Root Back exits to Google TV when no
   warm-return opportunity remains; leaving the activity stops the session.
   Simple TV never exits through Back.
8. The player Stop control completes serialized playback teardown before it
   returns to the operator UI. It clears the warm-return opportunity so root Back
   cannot redirect to a torn-down session.
9. The accessibility service ignores every key except Android GUIDE and the
   captured TCL TV key code, does not subscribe to accessibility events or
   window content, and does not interfere with keys while disabled.
10. Google Basic TV, Headent, and the diagnostic TVHStream package remain
   available as rollback paths during validation.
11. Unit tests pass, the release APK is signed with the stable private key, and
    installed package/signature/version details are recorded without secrets.
    The strict native release-provenance gate must also pass.
12. The operator UI can follow the system language or explicitly select German
    or English, and can hide the main EPG menu without disabling playback.
13. The EPG uses one shared horizontal time axis with fixed channel rows. Cards
    never overlap, the focused programme remains fully visible, Up/Down changes
    channel, Left/Right changes time, and CH+/CH- pages channel rows without
    tuning. OK opens programme details before any playback or recording action.
14. Recordings are separated into Archive, Schedule, and Problems. Archive shows
    only playable completed recordings in the sanitized TVHeadend folder
    hierarchy; Schedule isolates active and future entries; Problems isolates
    failed and cancelled entries. Recording, stop, and delete operations require
    confirmation with the safe action focused by default. Archive uses a
    newest-first folder list with an opaque persistent context pane; focused rows
    do not scale or clip, and folder changes transfer focus directly into the
    destination rather than falling back to the main navigation. Folder rows
    summarize descendant recording count and storage, while the context pane
    exposes five focusable recent descendants. Schedule is a full-width agenda
    grouped by Recording now, Today, Tomorrow, and explicit later dates. Problems
    is a full-width triage list grouped into Failed and Cancelled. Recording rows
    reserve a trailing date/time column so subtitles cannot displace time, and
    Schedule and Problems reveal metadata and labeled actions in a right-side
    overlay that restores row focus when closed.
    Returning from playback restores the previous mode, folder, scroll position,
    and focused item rather than resetting the recordings browser.
    Recording playback uses an auto-hiding TV overlay with metadata, elapsed and
    total time, a seek bar, icon-based transport, Playback options, and stable
    focus. With controls hidden, Left/Right seek 30 seconds and Down/Up seek 10
    minutes. Rapid steps accumulate on screen and dispatch as one seek after a
    short input pause; the seek timeline and cumulative step remain visible
    through buffering and briefly after playback resumes. With controls visible,
    D-pad navigation moves focus normally. Back dismisses visible controls before
    a subsequent Back returns to the recordings library without stopping
    playback. Explicit Stop and natural end tear down the recording session and
    return to the recordings library.

## Open questions

- How to make TVHeadend Player the TCL's HOME app without disabling Google Basic TV.
  The firmware gives its system launcher priority `2`, caps third-party HOME
  candidates to priority `0`, and ignores both shell and user role selection.
  Google must remain enabled until a safe reversible path is proven.

## TCL deployment findings

- TCL Safety Guard must allow the appliance package's hidden `APP_AUTO_START`
  app-op before the user-approved accessibility service will bind. The enabled
  service and app-op survived three standby/wake cycles and one Android reboot.
- The physical TV button emits Linux `KEY_EPG`, but this firmware delivers TCL
  private Android key code `4001` to accessibility services. The service also
  accepts standard Android `KEYCODE_GUIDE` for non-TCL input paths.
- Initial channel metadata is delivered without lossy buffering and staged until
  `initialSyncCompleted`, so the UI receives one complete channel snapshot rather
  than unstable partial lists (previously observed around 30, 84, and 50 channels).
  Recheck the stable count on the TCL before final appliance deployment.
- Activity recreation reuses a healthy process-scoped HTSP connection to the same
  endpoint. A full metadata snapshot is reserved for startup, changed settings,
  and real reconnects; live channel and EPG changes continue through HTSP deltas.
- Channel and EPG metadata remain a bounded in-memory cache. Guide top-ups are
  rate-limited, successful empty ranges are remembered, optional guide timeouts do
  not tear down the HTSP session, and routine completion does not show a banner.
- Connection progress and errors are persistent state within the channel screen,
  with TV-focusable Retry and connection-settings actions where applicable. A
  same-server reconnect keeps the last complete snapshot visible until its atomic
  replacement is ready.
- Normal channel changes do not show a full-screen loading scrim or block D-pad
  input. Delayed compact tuning feedback is sufficient; full-screen recovery is
  reserved for a lost HTSP connection or an active playback retry cycle.
