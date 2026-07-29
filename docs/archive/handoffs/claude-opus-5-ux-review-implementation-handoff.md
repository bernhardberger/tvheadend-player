# Claude Opus 5 UX/UI review implementation handoff

> **Status: historical implementation record.** The evidence, model guidance,
> and work sequence below are frozen to the 2026-07-26 review programme. Current
> authority is `docs/tv-design-spec.md`, current source, and current tests. Do not
> execute this handoff unless an assignment explicitly requests historical work.

## Agent selection

Use the project `android-tv` agent with **gpt-5.6 Sol High** effort for the
complete implementation. Sol Medium is suitable only when assigning one bounded
implementation slice per session. The complete scope includes cross-screen
Compose focus, navigation state, custom playback interaction, accessibility,
and physical-TV validation, so Sol High is the safer single-agent choice.

The implementation must be delivered as small, independently verified commits.
Do not attempt the plan as one large edit or commit.

## Authority and evidence

Treat
`artifacts/ux-screen-inventory/review/2026-07-26_12-05-40/claude-opus-5-ux-review.md`
as the authoritative UX/UI requirements baseline.

Do not relitigate, dismiss, or downgrade findings because current source appears
to contain related code. The review is based on current screenshots, physical
device context, interaction analysis, source confirmation, and product-owner
clarification. Source inspection determines where and how to implement a remedy;
it does not invalidate evidence-based UX findings.

The screenshot inventory contains household programme metadata. It is local
review evidence and must not be committed or published without separate review
and redaction.

## Accepted scope

Implement:

- Simple TV findings S1-S4.
- Findings 1-28.
- D1, the alternative large-card channel grid.
- D2, the in-app Home/dashboard destination.
- D3, removal of the global navigation rail from Settings routes.
- D5, appropriate use of the Alert, Content Details, overlay, and Actions layout
  templates.
- D6, the coherent player overhaul.
- Equivalent English and German localization.
- Automated tests and designated G10 physical-TV validation.

Defer only:

- Android/Google TV HOME takeover or interception.
- Device Owner and Lock Task integration.
- Foreground-loss monitoring or accessibility-based HOME recovery.
- Android/Google TV launcher channels, Watch Next, `TvProvider`, preview
  channels, or other launcher feature integration.

The in-app Home/dashboard in D2 remains in scope. It is distinct from Android or
Google TV launcher integration.

## Owner decisions

- Simple TV remains a strict player-only mode.
- Remove its inert granular EPG, recordings, Stop, Settings, and app-exit fields
  instead of exposing them as owner configuration.
- Disclose honestly that Android HOME can leave Simple TV.
- Explain that TV/GUIDE can return to the app when the appliance-entry service is
  enabled.
- Do not expand the accessibility service to subscribe to window events, inspect
  foreground applications, retrieve window content, or become general UI
  automation.
- Treat the current uncommitted recordings work as an unrelated intermediate
  hotfix. Stabilize it separately before UX-review implementation.

## Non-negotiable boundaries

- Preserve the accepted Media3/HTSP extractor, data-source, renderer, decoder,
  and playback baseline.
- Do not alter TVHeadend server accounts, tuners, OSCam, recordings storage,
  stream profiles, or network infrastructure.
- Use TV Material for focusable controls. Do not add focusable mobile Material
  controls.
- Keep mobile Material only for primitives that TV Material 1.1.0 does not
  provide, including the existing progress, text-field, and selected dialog
  boundaries.
- Every screen and overlay needs deterministic initial focus, complete D-pad
  reachability, visible focus, predictable Back, and focus restoration.
- Consume a key event when it reveals, replaces, or moves focus to new UI so the
  same key cycle cannot activate the newly focused control.
- Keep policy code pure and JVM-testable where practical.
- Write a failing test before each behavior change.
- Keep English and German resources behaviorally equivalent.
- Do not commit credentials, device addresses, signing material, screenshots,
  household metadata, or generated review artifacts.
- Do not mutate or run development tests against the production G08.

## Baseline gate

Before UX-review implementation:

1. Read `AGENTS.md`, `docs/appliance-mode-spec.md`,
   `docs/device-targets.md`, `docs/appliance-mode-plan.md`,
   `docs/codebase-audit-2026-07-23.md`, and
   `docs/product-identity-plan.md`.
2. Read the complete Claude review and this handoff.
3. Run `git status -sb`, inspect `git log --oneline -10`, and run
   `git fetch --all --prune`.
4. Inspect all existing worktree changes. Do not revert or overwrite changes not
   created for this plan.
5. Review and verify the current recordings hotfix independently.
6. Stage only its intended recording implementation, tests, strings, and
   corresponding recording specification changes.
7. Do not stage `artifacts/` or unrelated AI-harness changes.
8. Run focused recording tests and `./tools/verify`.
9. Commit the baseline hotfix separately, for example with
   `Refine recordings schedule and problem views`.
10. Re-read `git status -sb` and the resulting diff before starting UX work.

## Interaction contracts

### Root Back

- Non-root destinations pop normally.
- The root destination with no active playback finishes the activity.
- The root destination with warm live or recording playback may return to that
  player once.
- Consume the warm-return opportunity before navigating to the player.
- Returning from the player to browse must not re-arm an immediate loop.
- Deliberate navigation or newly started playback may re-arm one warm return.
- Simple TV never exits through Back.
- Explicit Stop completes serialized teardown before navigation and clears the
  warm target.
- When the in-app Home destination becomes the root, these rules remain
  unchanged.

### Player keys

| State | Center/OK | Left/Right | Up/Down | Back |
|---|---|---|---|---|
| Live, no timeshift, controls hidden | Reveal controls and consume the key cycle | Left opens channels; Right has no hidden action | Reveal controls | Return to browse unless Simple TV is active |
| Live, timeshift available, controls hidden | Toggle pause/play and reveal controls | Seek within the server buffer | Reveal controls | Return to browse unless Simple TV is active |
| Recording, controls hidden | Toggle pause/play and reveal controls | Seek the recording | Reveal controls | Return to Recordings |
| Controls visible | Activate the focused control | Normal focus movement | Normal focus movement | Hide controls |
| Seekbar focused | No implicit playback action | Scrub with acceleration | Leave the seekbar toward adjacent UI | Unwind visible player UI |
| Simple TV | Same playback behavior | Same available channel/seek behavior | Same control behavior | Dismiss overlays only |

Replace the old hidden recording Up/Down ten-minute seek mapping with the
reviewed information/control behavior. Preserve dedicated media keys, CH+/CH-,
numeric entry, and current-channel no-retune behavior.

## Implementation plan

### Slice 1: Back and navigation policy

Primary files:

- `app/src/main/java/at/bernhardberger/tvhplayer/core/BackNavigationPolicy.kt`
- `app/src/test/java/at/bernhardberger/tvhplayer/core/BackNavigationPolicyTest.kt`
- `app/src/main/java/at/bernhardberger/tvhplayer/ui/AppRoot.kt`

Implementation:

- Extend root Back policy with an explicit one-shot warm-return state.
- Support active live and recording playback targets.
- Consume the return token before player navigation.
- Do not re-arm it merely because player Back returns to browse.
- Re-arm only for deliberate navigation or actual new playback.
- Preserve Simple TV restrictions and serialized Stop behavior.

Acceptance:

- Repeated Back never forms a Channels/Home to Player loop.
- Normal users eventually reach the Android launcher.
- Simple TV remains restricted.
- Explicit Stop prevents warm-player redirection.

### Slice 2: Navigation shell and safe layout

Primary files:

- `app/src/main/java/at/bernhardberger/tvhplayer/ui/components/SideRail.kt`
- `app/src/main/java/at/bernhardberger/tvhplayer/ui/components/SettingsSubRail.kt`
- `app/src/main/java/at/bernhardberger/tvhplayer/ui/screens/SettingsScreen.kt`
- `app/src/main/java/at/bernhardberger/tvhplayer/ui/TvLayout.kt`
- corresponding Compose tests

Implementation:

- Replace the reflowing drawer composition with fixed-content-width overlay
  behavior.
- Continue using TV Material `NavigationDrawerItem` for focusable destinations.
- TV Material 1.1.0 in this repository does not expose
  `ModalNavigationDrawer`; implement modal behavior around official drawer items
  rather than assuming that API exists or adding a mobile drawer.
- Overlay expanded navigation on content with a scrim without resizing content.
- Preserve focus transfer to the active destination when opening.
- Add destination content descriptions to collapsed icons.
- Keep icon artwork and focus bounds inside the TV-safe region.
- Add symmetric `TvFullScreenPadding` for rail-less screens.
- Remove the global rail while Settings is open; retain only the Settings
  category rail.
- Replace the duplicated Connection list icon with a network/cloud icon.

Acceptance:

- Content bounds are identical before and after drawer expansion.
- Settings reclaims the global-rail width.
- Every drawer state has one visible focus target.
- Onboarding and unlock screens use symmetric safe margins.
- Existing video-backdrop opacity tokens remain unchanged unless physical-TV
  evidence requires adjustment.

### Slice 3: Settings comprehension and focus

Primary files:

- `app/src/main/java/at/bernhardberger/tvhplayer/ui/components/SettingsPane.kt`
- `app/src/main/java/at/bernhardberger/tvhplayer/ui/screens/settings/`
- `app/src/main/java/at/bernhardberger/tvhplayer/core/SimpleTvCapabilityPolicy.kt`
- `app/src/main/java/at/bernhardberger/tvhplayer/settings/SimpleTvSettingsStore.kt`
- `app/src/main/java/at/bernhardberger/tvhplayer/viewmodels/SettingsPlayerViewModel.kt`
- settings tests and EN/DE strings

Implementation:

- Reserve teal selected containers for routes and selected values.
- Set switch rows to `selected = false`; communicate on/off with the switch.
- Add heading semantics to page and section titles.
- Use bare page names matching sub-rail labels.
- Move explanatory copy into the corresponding row's `supportingContent`.
- Divide Appliance entry into app-open autoplay and accessibility-service
  sections.
- State explicitly that autoplay does not enable the accessibility service.
- Keep rail focus after category activation until the user presses Right.
- Give each page a deterministic Right-from-rail content target.
- Keep Player focus stable before, during, and after asynchronous profile
  loading.
- Provide a focusable fallback row when no audio or subtitle tracks exist.
- Present `htsp` as Direct streaming with the exact server profile secondarily.
- Present arbitrary profiles without guessing their behavior; preserve their
  exact names as secondary information.
- Add visible per-control reasons for a disabled PIN action and the last enabled
  channel group.
- Give PIN success and failure distinct icon, color, semantics, and polite live
  feedback.
- Remove inert `epg`, `recordings`, `stop`, `settings`, and `appExit` fields,
  setters, reads, strings, and tests from Simple TV.
- Leave obsolete pre-release preference keys unread; no migration bridge is
  required.

Acceptance:

- Exactly one target is visibly focused after each Settings transition.
- Focus, selected value, selected route, enabled switch, and disabled state are
  distinguishable.
- Switch rows do not use selected-container color.
- English and German copy describe the same behavior.
- Simple TV no longer advertises unsupported configurability.

### Slice 4: Guide and shared Content Details

Primary files:

- `app/src/main/java/at/bernhardberger/tvhplayer/ui/screens/EpgGridScreen.kt`
- `app/src/main/java/at/bernhardberger/tvhplayer/ui/screens/ChannelsScreen.kt`
- a reusable programme-details composition under `ui/components/`
- EPG policy tests, Compose tests, and EN/DE strings

Implementation:

- Use Guide consistently in English navigation, title, and Settings copy.
- Always render a programme label, including narrow cells.
- Lower the time-range threshold to approximately 90dp.
- Preserve proportional widths and the no-overlap invariant.
- Increase the current-time line and add a marker in the time ruler.
- Replace fixed synopsis truncation with a D-pad-scrollable details region.
- Adopt the Content Details layout: title, metadata, complete synopsis, and
  actions form one coherent horizontal composition.
- Keep past programmes focusable because their information remains useful.
- Replace dead-end no-action copy with an explicit already-aired explanation.
- Share metadata and synopsis composition among Guide details, Channels details,
  and player Info.
- In Channels, show summary with description fallback, genre, season/episode,
  and next programme without bottom-pinning it.
- Keep the existing Channels panel footprint and backdrop intent.

Acceptance:

- No focusable EPG cell is visually blank.
- Long descriptions can be read fully with the D-pad.
- Closing details restores the exact programme focus.
- Channel details use data already in memory.
- The current-time indicator is visible in the ruler and programme rows.

### Slice 5: Recordings review remediation

Primary files:

- `app/src/main/java/at/bernhardberger/tvhplayer/ui/screens/RecordingsScreen.kt`
- `app/src/androidTest/java/at/bernhardberger/tvhplayer/ui/screens/RecordingsScreenTest.kt`
- recording strings

Implement only after the unrelated recordings baseline is committed:

- Remove hardcoded `onSurfaceVariant` from focused date/time content.
- Let date/time columns size within bounded ranges rather than consuming a fixed
  excessive width.
- Allow recording titles two lines where needed.
- Raise enabled-unselected tab contrast.
- Keep selected and focused tab states distinct.
- Preserve the baseline hotfix's full-width Schedule/Problems views, grouping,
  details actions, state restoration, and focus behavior.
- Convert destructive confirmation composition to the Actions template while
  retaining the safe default.
- Add heading semantics and test focus restoration after details and
  confirmations.

Acceptance:

- Focused date/time text is readable.
- Similar long titles remain distinguishable.
- Archive, Schedule, and Problems all appear enabled and reachable.
- No baseline recordings behavior is reverted.

### Slice 6: Player key and seek foundation

Primary files:

- `app/src/main/java/at/bernhardberger/tvhplayer/core/PlaybackKeyPolicy.kt`
- `app/src/main/java/at/bernhardberger/tvhplayer/core/RecordingPlaybackPolicy.kt`
- `app/src/main/java/at/bernhardberger/tvhplayer/core/TimeshiftPolicy.kt`
- new pure seekbar policy and tests
- new `app/src/main/java/at/bernhardberger/tvhplayer/ui/player/PlaybackSeekbar.kt`
- `VideoPlayerScreen.kt`
- `RecordingPlayerScreen.kt`

Implementation:

- Encode the player-key contract as pure policy.
- Suppress Center key cycles that both act on playback and reveal controls.
- Preserve CH+/CH-, numeric entry, dedicated media keys, and serialized player
  commands.
- Split non-seekable programme progress from seekable playback.
- Hide programme progress when no current EPG event exists.
- Render programme progress as thin, unthumbed, non-focusable information.
- Build one custom focusable seekbar for timeshift and recordings.
- Use buffer start to live edge as the timeshift domain.
- Show buffer start, playhead, live edge, `LIVE`, and EPG boundary ticks.
- Use zero to duration as the recording domain with elapsed and total labels.
- Reuse existing debounced/coalesced seek mechanisms.
- Add deterministic repeat acceleration, such as 30 seconds initially, two
  minutes after sustained repeats, and five minutes for long holds.
- Ensure Up/Down leaves the seekbar rather than trapping focus.
- Add range and state accessibility semantics.

Acceptance:

- Center immediately pauses seekable media and reveals controls.
- Held Right can traverse a long recording without hundreds of presses.
- Timeshift never seeks outside the server-reported buffer.
- Non-timeshift live playback never implies seekability.
- No extractor, data source, decoder, renderer, or stream-profile behavior is
  changed.

### Slice 7: Player composition and options

Primary files:

- `app/src/main/java/at/bernhardberger/tvhplayer/ui/player/OverlayControlsTv.kt`
- `app/src/main/java/at/bernhardberger/tvhplayer/ui/player/RecordingOverlayControls.kt`
- replace or rename `PlaybackOptionsSheet.kt` as appropriate
- `app/src/main/java/at/bernhardberger/tvhplayer/ui/player/PlayerHelpers.kt`
- `app/src/main/java/at/bernhardberger/tvhplayer/ui/player/ChannelDrawer.kt`
- `app/src/main/java/at/bernhardberger/tvhplayer/ui/player/PlaybackStatsOverlay.kt`
- player tests and EN/DE strings

Implementation:

- Make programme/recording title primary, channel identity secondary, and time
  plus up-next tertiary.
- Keep the live-TV clock and channel identity.
- Remove the small player picon if necessary to achieve the agreed hierarchy.
- Place controls in one tight right-aligned cluster.
- Show a label anchored to the focused icon control.
- Use Channels, Play/Pause where applicable, Info, More, and Stop where allowed.
- Replace the full-height options sheet with an anchored opaque popover.
- Support lateral Audio, Subtitles, Display, and Diagnostics categories.
- Show current values without requiring submenu entry.
- Humanize language names with `Locale` and explicit `und`, `mis`, and `zxx`
  fallback.
- Use Mono, Stereo, 5.1, 7.1, or localized channel-count labels as primary
  information.
- Keep sample rate, codec, and exact server values as secondary technical text.
- Keep a focusable empty row when no tracks exist.
- Add an Info affordance backed by the shared Content Details composition.
- Add a persistent On now marker in the channel drawer.
- Narrow the channel drawer gradient to its actual content.
- Hide or compact Stats while ordinary controls are visible.
- Preserve diagnostic privacy and one-second sampling.

Acceptance:

- Right always moves to an adjacent control.
- Opening More keeps focus local.
- Controls do not ghost through the options surface.
- Back closes category/popover, Stats, Info, controls, and player in a
  deterministic order.
- Audio and subtitle choices are understandable at ten feet.

### Slice 8: Simple TV accessibility completion

Primary files:

- `app/src/main/java/at/bernhardberger/tvhplayer/ui/screens/settings/SettingsSimpleTv.kt`
- `app/src/main/java/at/bernhardberger/tvhplayer/ui/screens/SimpleTvUnlockScreen.kt`
- `VideoPlayerScreen.kt`
- `TvRecoveryOverlay.kt` or a new blocking Alert component
- shared channel-card grid
- Simple TV tests and EN/DE strings

Implementation:

- Explain before activation that Channels, Guide, Recordings, Settings, Stop,
  and normal app exit are unavailable.
- Explain the More to Exit Simple TV path and optional PIN.
- Disclose that Android HOME can leave the app.
- Explain that TV/GUIDE can return when appliance entry is enabled.
- Separate startup preference from Start Simple TV now.
- Confirm immediate entry with the safe cancellation action initially focused.
- Add a low-prominence persistent Simple TV chip naming the exit route.
- Hide Display and Stats during Simple TV; retain household-useful Audio and
  Subtitles.
- Separate Exit Simple TV with an Owner label, divider, and lock icon.
- Increase player metadata type in Simple TV.
- Replace dense quick select with a shared 3x2 large-card channel grid.
- Add a full-screen Alert-template recovery state with plain-language copy and
  one focused Retry action.
- Retry through existing serialized connection and playback owners.
- Keep Back from leaving Simple TV.
- Do not add HOME interception, foreground monitoring, accessibility events,
  Device Owner, or Lock Task behavior.

Acceptance:

- A protected user always has a visible useful action in recovery and failure
  states.
- A carer can discover the exit route without weakening PIN and confirmation.
- HOME limitations are accurate before activation.
- Existing TV/GUIDE scope and service privacy remain unchanged.

### Slice 9: Alternative channel grid

Primary files:

- shared `ChannelCardGrid`
- `ChannelsScreen.kt`
- `PiconBox.kt`
- `UiSettings.kt`
- `SettingsOptions.kt`
- tests and EN/DE strings

Implementation:

- Add persisted List with details and Large cards choices.
- Keep list with details as the default.
- Reuse the 3x2 Card composition introduced for Simple TV.
- Show picon, channel number, channel name, current programme, and now-playing or
  recording state.
- Add channel-name initials or text fallback for missing picons.
- Allow bounded card focus scale with enough spacing to avoid clipping.
- Preserve focus-only browsing and OK-to-tune.
- Preserve CH+/CH- paging without tuning.
- Respect active channel-group scope.

Acceptance:

- Both layouts preserve selection, channel scope, and tuning semantics.
- Card focus does not clip.
- Missing picons do not create anonymous cards.

### Slice 10: In-app Home dashboard

Primary files:

- new `app/src/main/java/at/bernhardberger/tvhplayer/ui/screens/HomeScreen.kt`
- new `app/src/main/java/at/bernhardberger/tvhplayer/viewmodels/HomeViewModel.kt`
- new pure Home content policy and tests
- `AppRoot.kt`
- `SideRail.kt`
- `LastPlayedChannelStore.kt` or a small recent-channel store
- DI and EN/DE strings

Implementation:

- Add an in-app Home route and make it the normal start destination.
- Keep autoplay and Simple TV startup bypassing Home.
- Add Home as the first global navigation destination.
- Make active playback the largest first element.
- Show bounded session/persisted recently played channels.
- Show On now, latest playable recordings, recording now, and upcoming
  recordings.
- Omit empty rows instead of rendering empty placeholders throughout the screen.
- Persist recent channels only after playback actually succeeds.
- Selecting live content tunes explicitly.
- Selecting playable recordings opens recording playback.
- Upcoming items open the appropriate recording context rather than pretending
  they are playable.
- Give the active/resume card deterministic initial focus.
- Preserve focus and row position when returning from playback.
- Re-run one-shot warm-player Back tests with Home as root.
- Do not add `TvProvider`, Watch Next, preview channels, launcher metadata, or
  manifest launcher integration.

Acceptance:

- Normal launches land on a useful dashboard.
- Autoplay behavior remains unchanged.
- Home Back eventually exits and never loops with playback.
- The dashboard remains useful when only some data categories are available.

### Slice 11: Templates and accessibility polish

Primary files:

- `OnboardingScreen.kt`
- shared Actions-template composition
- visual title surfaces and related Compose tests

Implementation:

- Convert onboarding introduction to an Actions layout with guidance left and
  Continue right.
- Use the same Actions language for Simple TV exit and destructive recording
  confirmation.
- Add heading semantics to screen, panel, dialog, popover, and section titles.
- Verify switch state descriptions and traversal order.
- Exercise the 960x540dp design canvas, 1.2x font scale, and long German strings.
- Preserve `FLAG_SECURE` on credential surfaces.

Acceptance:

- No important action or text leaves the safe region.
- Long German strings do not displace controls or clip focus.
- TalkBack receives headings, destination names, selected states, and progress
  semantics.

## Review traceability

| Review items | Implementation slices |
|---|---|
| S1 | Honest HOME disclosure in Slice 8; platform integration deferred |
| S2 | Player simplification and card grid in Slices 7-8 |
| S3 | Remove inert capability state in Slice 3 |
| S4 | Blocking recovery Alert with Retry in Slice 8 |
| 1 | One-shot warm-player Back policy in Slice 1 |
| 2 and 13 | Recording color, width, title, and action treatment in Slice 5 |
| 3, 11, 12, and 19-22 | Settings state, copy, focus, icons, and feedback in Slice 3 |
| 4, 5, 10, 25, 26, and 28 | Guide and shared Content Details in Slice 4 |
| 6, 8, 9, 17, 18, 23, 24, and 27 | Player overhaul in Slices 6-7 |
| 7 | Simple TV chip, entry confirmation, and owner hierarchy in Slice 8 |
| 14-16 | Overlay navigation, semantics, and safe padding in Slice 2 |
| D1 | Shared large-card channel grid in Slices 8-9 |
| D2 | In-app Home/dashboard in Slice 10 |
| D3 | Settings without the global rail in Slice 2 |
| D4 | Explicitly deferred |
| D5 | Alert, Content Details, overlays, and Actions across Slices 4, 5, 7, 8, and 11 |
| D6 | Player foundation and composition in Slices 6-7 |

## Verification strategy

Run focused tests while iterating. At the end of every slice run:

```bash
./gradlew testDebugUnitTest --no-daemon
./gradlew compileDebugAndroidTestKotlin --no-daemon
./tools/verify
```

Before physical-device work, load the `android-tv-device-testing` skill and
follow it exactly. Use only the configured G10 test TV:

```bash
./tools/device doctor
./tools/device install-debug
./tools/device launch
./tools/device current
```

Capture screenshots only after confirming that no credential or other
secret-bearing screen is visible:

```bash
./tools/device screenshot --confirm-safe-screen
```

Screenshots cannot prove SurfaceView video visibility, overscan, animation or
focus-transition feel, remote-repeat behavior, or motion quality. Ask the user
one focused question and wait whenever human observation is required.

## Physical-TV matrix

- Drawer expansion without content reflow.
- Initial focus and restoration across every route and overlay.
- Back through Home, Channels, players, popovers, Stats, Info, and Simple TV.
- Narrow EPG cells and long programme descriptions.
- Archive, Schedule, Problems, details, and destructive confirmations.
- Normal and Simple TV channel grids.
- Recording and timeshift seekbar presses, holds, acceleration, buffering, and
  boundaries.
- Player overlays over bright, dark, and fast-moving video.
- Simple TV loading, empty, reconnecting, playback failure, Retry, HOME
  limitation, and TV/GUIDE recovery.
- 1.2x font scale and long German labels.
- Progressive playback.
- Interlaced sports playback with direct human motion-quality review.
- Google Basic TV and rollback clients remain installed and usable.

## Definition of done

- Every review ID is closed by implementation, an explicit accepted limitation,
  or the agreed launcher/HOME deferral.
- Every slice is independently buildable and committed.
- `./tools/verify` passes after every slice.
- Physical G10 evidence exists for focus, Back, player controls, recovery, and
  readability.
- Progressive and interlaced playback remain regression-clean.
- `docs/appliance-mode-spec.md` and `docs/appliance-mode-plan.md` describe the
  final interaction contracts.
- No secrets, private addresses, screenshots, signing material, or household
  metadata enter Git.
- Signed release publication remains blocked by the existing native-provenance
  gate.

## Standalone implementation prompt

```text
Use the project android-tv agent with gpt-5.6 Sol High effort.

Implement the complete UX/UI remediation in
docs/archive/handoffs/claude-opus-5-ux-review-implementation-handoff.md end-to-end, one
independently verified slice at a time.

Treat
artifacts/ux-screen-inventory/review/2026-07-26_12-05-40/claude-opus-5-ux-review.md
as the authoritative UX/UI requirements baseline. Do not relitigate, dismiss,
or downgrade findings because current source appears to contain related code.
Use source inspection only to locate the smallest correct implementation.

Implement findings S1-S4 and 1-28 plus D1, D2, D3, D5, and D6. Defer only
Android/Google TV HOME takeover, Device Owner/Lock Task, foreground monitoring,
and Android/Google TV launcher feature integration. The in-app Home/dashboard
remains in scope.

Keep Simple TV player-only. Remove its inert granular capability fields. Disclose
that HOME can leave the app and that TV/GUIDE can return when appliance entry is
enabled. Do not broaden the accessibility service or inspect window content.

First inspect and stabilize the existing worktree. The current recordings work
is an unrelated hotfix and may be committed as a separate verified baseline.
Stage only its intended files. Do not commit artifacts or unrelated harness
changes. Never revert or overwrite changes you did not make.

Follow the slice order, interaction contracts, acceptance criteria, tests,
physical-TV matrix, and definition of done in the handoff document. Write a
failing test before each behavior change. Keep commits small and buildable. Run
focused tests while iterating and ./tools/verify after every slice.

Preserve the accepted Media3/HTSP extractor, data-source, renderer, decoder, and
playback behavior. Do not modify TVHeadend server configuration, credentials,
network infrastructure, stream profiles, or the production G08.

Before device work, load the android-tv-device-testing skill, read
docs/device-targets.md, run ./tools/device doctor, and confirm the configured G10
test identity. Ask the user for human observations when screenshots cannot prove
focus feel, video-overlay readability, remote-repeat behavior, overscan, or
progressive/interlaced motion quality.

Persist until the current slice is implemented, tested, verified, reviewed for
scope and secrets, and committed. Then continue to the next slice. If a slice is
blocked by a genuine product decision not settled in the handoff, stop and ask
one focused question rather than guessing.

At completion, report commits in order, review findings closed by each commit,
automated verification results, physical-TV checks and evidence level, remaining
human checks, accepted deferrals, confirmation that Media3/HTSP and decoder
behavior were not changed, and final git status -sb.
```
