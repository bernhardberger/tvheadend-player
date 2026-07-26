# UX/UI overhaul implementation review handoff

Date: 2026-07-26

## Purpose

This handoff records the first engineering review of Grok's UX/UI overhaul and
defines the next independent visual review. The engineering findings below are
confirmed implementation defects. They are not a substitute for the requested
visual and interaction-design pass.

The next reviewer must evaluate the resulting product as a TV interface, not
merely check whether source elements corresponding to the original findings now
exist. A requirement is not closed just because a composable, string, policy, or
test was added.

## Audited revision

- Branch: `feature/ui-ux-overhaul`
- Baseline: `1e421ae` (`Add TV UX review agent`)
- Audited head: `25400e7` (`Replace options sheet with anchored popover and control labels`)
- Range: `1e421ae..25400e7`
- Worktree at review: clean, 13 commits ahead of
  `fork/feature/ui-ux-overhaul`
- Automated gate: `./tools/verify` passed on 2026-07-26
- Release gate: still blocked by the existing native-library provenance warnings
- Physical G10 evidence for this implementation: none found in the commits or
  documentation

Authoritative requirements remain:

- `artifacts/ux-screen-inventory/review/2026-07-26_12-05-40/claude-opus-5-ux-review.md`
- `docs/claude-opus-5-ux-review-implementation-handoff.md`
- `docs/appliance-mode-spec.md`

## Confirmed engineering findings

### E1. Simple TV recovery Retry cannot receive remote input

**Severity:** High

While recovery is visible, the player parent preview handler consumes Center,
Enter, and every D-pad direction before the focused Retry button can receive
them. The new recovery action is therefore not actionable by remote and S4
remains open.

References:

- `app/src/main/java/at/bernhardberger/tvhplayer/ui/player/VideoPlayerScreen.kt:528-536`
- `app/src/main/java/at/bernhardberger/tvhplayer/ui/components/TvRecoveryOverlay.kt:83-90`

### E2. Start Simple TV confirmation is not modal

**Severity:** High

The complete Settings form remains composed and focusable when confirmation is
shown. The Actions composition is appended after a scrolling form instead of
replacing it or appearing in a focus-contained overlay. Focus can leave the safe
Cancel action and return to the underlying settings; the confirmation can also
be pushed or clipped by the preceding content.

References:

- `app/src/main/java/at/bernhardberger/tvhplayer/ui/screens/settings/SettingsSimpleTv.kt:68-195`

### E3. Recording playback has no Info surface

**Severity:** High

Hidden Up maps to `OPEN_INFO`, but the recording implementation only reveals
controls. The recording control cluster has no Info action and no shared Content
Details surface. The D6 Info contract is implemented only for live TV.

References:

- `app/src/main/java/at/bernhardberger/tvhplayer/ui/player/RecordingPlayerScreen.kt:194-198`
- `app/src/main/java/at/bernhardberger/tvhplayer/ui/player/RecordingOverlayControls.kt:189-243`

### E4. Home lacks deterministic focus and actionable error recovery

**Severity:** High

The Home `FocusRequester` is attached only to the now-playing card. When there is
no active player, recent, on-now, recording, and upcoming rows have no explicit
initial target. The empty/error text receives the requester but is not focusable.
`onRetryConnection` is accepted by `HomeScreen` but never used, leaving the new
root destination without a Retry action on connection failure.

References:

- `app/src/main/java/at/bernhardberger/tvhplayer/ui/screens/HomeScreen.kt:48-53`
- `app/src/main/java/at/bernhardberger/tvhplayer/ui/screens/HomeScreen.kt:105-108`
- `app/src/main/java/at/bernhardberger/tvhplayer/ui/screens/HomeScreen.kt:132-212`

### E5. Focused seekbar does not accelerate on held keys

**Severity:** Medium

The pure policy supports repeat acceleration, but `PlaybackSeekbar` always calls
it with `repeatCount = 0`. Holding Left or Right on the focused seekbar therefore
continues in fixed 30-second steps. The policy unit test does not exercise the UI
wiring.

References:

- `app/src/main/java/at/bernhardberger/tvhplayer/ui/player/PlaybackSeekbar.kt:77-86`
- `app/src/test/java/at/bernhardberger/tvhplayer/core/SeekbarPolicyTest.kt:35-42`

### E6. Timeshift programme-boundary ticks are never supplied

**Severity:** Medium

`PlaybackSeekbar` exposes `epgBoundaryFractions`, but no caller passes values.
The required EPG boundary ticks do not appear on the timeshift timeline.

References:

- `app/src/main/java/at/bernhardberger/tvhplayer/ui/player/PlaybackSeekbar.kt:57`
- `app/src/main/java/at/bernhardberger/tvhplayer/ui/player/OverlayControlsTv.kt:225-233`

### E7. Home routes unavailable and duplicate recordings as playable content

**Severity:** Medium

The Latest section treats every failed or actively recording entry as playable
without checking for an available recording file. Selecting any such row opens
recording playback. Active recordings are also repeated in both Recording now
and Latest recordings.

References:

- `app/src/main/java/at/bernhardberger/tvhplayer/core/HomeContentPolicy.kt:99-129`
- `app/src/main/java/at/bernhardberger/tvhplayer/ui/screens/HomeScreen.kt:170-181`

### E8. Maximum player popover height violates the TV-safe top margin

**Severity:** Medium

On the 960 x 540 dp reference canvas, a popover with `bottom = 108.dp` and
`maxHeight = 420.dp` reaches 12dp from the top edge. This is inside the required
27dp vertical safe area. A populated track list can reach this bound.

Reference:

- `app/src/main/java/at/bernhardberger/tvhplayer/ui/player/PlaybackOptionsSheet.kt:145-150`

### E9. Player focus labels are not anchored to their controls

**Severity:** Low

Every focus label is drawn at one fixed right-aligned position above the entire
cluster. Its position does not follow the focused icon, so it can visually label
the wrong control. This does not implement the requested anchored label chip.

References:

- `app/src/main/java/at/bernhardberger/tvhplayer/ui/player/OverlayControlsTv.kt:259-280`
- `app/src/main/java/at/bernhardberger/tvhplayer/ui/player/RecordingOverlayControls.kt:168-188`

## Automated-evidence gap

`./tools/verify` is green, but no instrumentation coverage was added for:

- Home initial focus, empty/error actions, row restoration, or long-page D-pad
  navigation.
- Recovery-overlay focus and Retry activation through the parent key handler.
- Focused seekbar repeat events and acceleration.
- Recording Info opening, Back behavior, and focus restoration.
- Simple TV start-confirmation focus containment.
- Player popover safe bounds, long track lists, or focus restoration to More.

The pure policy tests therefore produce a false sense of completion for several
Compose integration requirements.

## Independent UX/UI review assignment

Use the project `tv-ux-reviewer` agent for a read-only, screenshot-first visual
and interaction pass. Do not edit code, run builds, or use ADB during that pass.

The reviewer must read:

1. `AGENTS.md`
2. `docs/device-targets.md`
3. `docs/appliance-mode-spec.md`
4. `docs/claude-opus-5-ux-review-implementation-handoff.md`
5. The authoritative Claude Opus 5 review named above
6. This handoff
7. The UI source needed to confirm visual or interaction mechanisms after the
   screenshot-first pass

Treat E1-E9 as already established. Confirm their user-facing consequences where
visual or device evidence permits, but do not spend the review merely rediscovering
them. The primary task is to identify deeper UX/UI failures introduced or left
unresolved by the overhaul.

### Required visual-review questions

#### Product structure and Home

- Does Home read as a useful TV dashboard, or as a mobile-style full-width list
  of repeated rows?
- Is the largest element actually the active/resume experience?
- Are content sections visually differentiated and horizontally scannable at ten
  feet, or does the page become a long undifferentiated vertical agenda?
- Are On now, recent channels, active recordings, latest recordings, and upcoming
  recordings meaningfully distinct rather than duplicate entry points?
- Does Home remain useful with each partial-data combination and with no active
  playback?

#### Player hierarchy and overlays

- Is programme or recording title unmistakably primary over channel and technical
  metadata?
- Does the right-aligned cluster feel balanced, reachable, and spatially coherent
  in live, timeshift, recording, and Simple TV states?
- Are focused labels attached to the controls they explain and stable for long
  German labels?
- Is the options popover genuinely compact and anchored, or still a large floating
  panel with unsafe or awkward geometry?
- Are lateral categories understandable without instructions? Do current values,
  check states, empty tracks, and owner-only actions form a clear hierarchy?
- Do Info, channel quick select, Stats, seek feedback, numeric entry, recovery, and
  controls unwind in a visually predictable order?
- Does Simple TV look intentionally simplified, not merely like the normal player
  with actions removed?

#### Channel layouts

- Does the large-card option actually present a readable 3 x 2 TV grid on the
  960 x 540 dp canvas?
- Do card aspect ratio, picon area, metadata, spacing, and 1.05 focus scale fit
  without clipping or reducing the grid to one visible row?
- Are picon fallbacks recognizable, and are now-playing/recording badges legible
  in focused and unfocused states?
- Does Simple TV quick select prioritize fewer, larger, simpler choices than the
  normal channel browser?

#### Settings and confirmation surfaces

- Does removing the global rail produce a balanced Settings composition, or leave
  an oversized category rail and narrow form pane?
- Are headings, explanatory copy, switches, selected values, disabled reasons,
  and PIN feedback visually grouped rather than merely present?
- Does the Simple TV page explain consequences before its actions without becoming
  a dense wall of text?
- Do Actions layouts look like deliberate ten-foot decisions, with guidance and
  actions balanced, or like ordinary content rearranged into two columns?
- Are confirmations clearly modal, with the underlying task visually and
  interactively suspended?

#### Guide, recordings, and shared details

- Are narrow Guide cells useful rather than technically non-empty but visually
  unreadable?
- Is the thicker current-time line and ruler marker visible without dominating
  programme content?
- Can a user discover and operate synopsis scrolling, and is focus restoration
  obvious on close?
- Does shared Content Details fit each context, or does one generic composition
  create weak layouts in Guide, Channels, and player Info?
- Are recording tabs, two-line titles, date columns, details actions, and
  destructive confirmations balanced and readable in real content?

#### Visual system and accessibility

- Is spacing consistently based on the 960 x 540 dp TV canvas and 48/27dp safe
  region?
- Do focused, selected, enabled, disabled, current-playing, and recording states
  remain distinct without relying only on colour?
- Are panel opacity and scrims readable over bright, dark, and fast-moving video
  while preserving only the intended slight glimpse of playback?
- Do 1.2x font scale and long German strings preserve hierarchy, safe bounds, and
  complete focus rings?
- Do loading, empty, error, reconnecting, no-EPG, no-track, and unavailable-file
  states look intentional rather than like unfinished fallbacks?

### Evidence requirements

The pre-implementation screenshot inventory cannot prove the visual quality of
this implementation. The reviewer must use fresh post-overhaul evidence and ask
for the smallest missing capture or observation instead of guessing.

The `tv-ux-reviewer` must not run ADB or collect device evidence itself. Device
evidence must be supplied by the product owner or a separate engineering session.
That session must load the `android-tv-device-testing` skill, follow it exactly,
and use only the configured G10 after `./tools/device doctor` confirms role and
all four identity fields. It must never test or mutate the production G08.

Screenshots may assess static composition only after confirming that no
credential or secret-bearing screen is visible. They cannot establish focus feel,
SurfaceView video visibility, overlay readability over motion, overscan, remote
repeat, animation quality, or progressive/interlaced motion quality. Ask the
product owner one focused question for each required human-observation group.

At minimum, fresh evidence should cover:

- Home with active playback, no active playback, partial data, and connection
  error.
- Channels in list and large-card modes, including missing picons and long names.
- Guide with narrow cells and long details.
- Recordings Archive, Schedule, Problems, details, and confirmation.
- Live, timeshift, and recording controls; focused seekbar; Info; channel drawer;
  options categories; Stats; and recovery.
- Simple TV controls, quick select, owner exit path, activation confirmation, and
  recovery.
- Settings pages at normal and 1.2x font scale in English and German.

### Reviewer output

Report findings first, ordered by severity. Each finding must include:

- The affected state or screen.
- Evidence classification: screenshot-proven, code-confirmed, or physical-device
  reproduction required.
- Concrete impact on remote-only ten-foot use.
- File and line references where source confirms the mechanism.
- A remedy stated at interaction/layout level, without implementing it.

Explicitly distinguish:

- Original review findings correctly closed.
- Original findings only superficially or partially closed.
- Regressions introduced by the remediation.
- New visual or information-architecture findings.
- Questions that require product-owner judgment.
- Checks that still require human physical-TV observation.

Do not accept commit messages as closure evidence. Do not infer visual quality
from successful compilation or pure policy tests. Do not edit files, commit, or
push during the independent pass.

## Standalone reviewer prompt

```text
Switch to the project tv-ux-reviewer agent and perform a read-only, screenshot-
first independent UX/UI review of the completed overhaul at 25400e7.

Read docs/ui-ux-overhaul-implementation-review-handoff.md first, then all source
requirements and evidence it names. Treat E1-E9 as confirmed engineering defects,
but do not limit the review to them. The main purpose is a fresh visual and
interaction-design assessment of the resulting TV product: information
architecture, hierarchy, density, focus presentation, spacing, typography,
screen composition, overlays over video, Simple TV accessibility, long German
copy, and loading/empty/error states.

Inspect current screenshots at full resolution before source. Then inspect only
the UI source needed to confirm a visual or interaction mechanism. Do not edit
code, run builds, or use ADB. Do not treat the presence of a composable, policy,
string, test, or commit-message closure claim as proof that a UX requirement is
satisfied.

Use fresh post-overhaul visual evidence. If it has not been supplied, ask for the
smallest missing screenshots or human observations rather than guessing. The
reviewer must not perform physical-device work; a separate engineering session
may collect bounded evidence from the configured G10 under the
android-tv-device-testing skill, never from the production G08.

Report concrete findings first, ordered by severity, with screen/state, evidence
classification, impact, source references, and interaction/layout-level remedy.
Separate correctly closed findings, superficial closures, regressions, new
findings, product decisions, and remaining physical-TV checks.
```
