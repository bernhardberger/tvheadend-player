# Browse cohesion proposal

## Motion follow-up — October 5, 2026

- Current accepted Notebook preview: **159**, `0.2.90-browse-cohesion.8`.
  The operator accepted content slides and Channels tabs on 158, and described
  Guide tabs on 159 as good enough with recording stopped. Captured runs still
  contain stalls; this is not a claim of eliminated jank or physical-TV acceptance.
- The shared selector now drives native indication from focused tag identity
  while focused, falling back to authoritative selection on exit. Stable tag keys
  preserve identity through reordering. Eight selector regressions and the
  33-test focused gate passed; Astra and Claude Code Opus 5.5 reviews were clean.
  Optimized build/lint, SDK/native integrity, alignment and installed-byte checks
  passed. Guide-specific animation code was not added.
- Notebook preview **158**, `0.2.90-browse-cohesion.7`, uses the existing optimized,
  non-debuggable `profileServer` configuration described in `profiling.md`.
  Use that lane for subsequent motion judgments; ordinary debug previews exposed
  substantial frame stalls and are not representative of this configuration.
- The Problems recording marker is now an unboxed 20dp outline using native row
  content color. Its focused layout check passed.
- Native tab/overflow tests did not reproduce a logical animation discontinuity.
  The proposed visit-identity change was removed after dependency inspection
  contradicted its diagnosis. The eight focused motion tests remain useful
  coverage of settled/interrupted LTR/RTL movement and overflow.
- An experimental background Guide edge projection passed 55 focused tests but
  did not establish a performance win; it was reverted completely. Its patch and
  measurement caveats are retained privately in `/tmp/opencode/guide-edge-candidate/`.
- With the same eight-key Guide scope journey, two warm debug runs had p90 frame
  times of 300ms; two warm optimized runs measured 73ms and 69ms. Warm-up histories
  and live data were not identical. This is a build-configuration comparison,
  not an R8-only attribution or proof of smooth physical-TV motion.
- Optimized build, lint, external-SDK, native integrity and APK-alignment gates
  passed. Installed bytes matched. Subsequent operator feedback is recorded above.

**Status:** Initial visual baseline implemented, offline-verified and independently
reviewed 2026-10-04. The shared-grid, Archive-depth and refined Guide studies are
accepted as the working design baseline and implemented in native UI. Final Opus
review closed the production and test-truthfulness findings with a non-blocking
verdict. Final uninstrumented routine verification
passed with the plain Guide navigation regressions restored. Historical
intermittent test failures remain documented below; physical-TV acceptance
remains open. Further concept polish is paused.
Accepted baseline contracts are in `DESIGN.md`.
**Scope:** Product-specific Channels, Guide and Recordings presentation and guidance,
including shared browse alignment and the Settings/Archive depth-layout family.
**Evidence/base:** Initial baseline `f3180df`, 2026-10-03; current source assessment
`b576f79` plus uncommitted browse changes on `ux/browse-cohesion`.
**Lifecycle:** Complete the native batch's verification and review, then validate
the integrated experience on the designated preview/TV lanes. Retain baseline
evidence separately; revalidate if the browse shell,
navigation or SDK eligibility rules change.

### Remaining validation risk

The final routine `./tools/verify` run passed on 2026-10-05: 1,079 app tests and
662 client tests, lint, Android-test compilation, debug build, released-SDK/native
integrity and APK checks. Relevant focused visual captures passed; the full visual
matrix was not completed after an earlier resource-killed attempt. The final
uninstrumented log is `/tmp/opencode/browse-final-plain-verify.8ZeYqZ.log`.

Two earlier full-suite runs timed out moving Right from an oversized Guide
programme to its successor. A later run reported concurrent
`SnapshotStateObserver` access during recomposition, before the successor check.
The observer-failure case took 60.16 seconds, versus 0.02–3.4 seconds for the
other cases in its class; this records slow settling, not an established cause.
Neither cause, nor a relationship between these failures, has been established.
There is no speculative production workaround.

The plain one-Right regressions remain enabled for five-hour programmes at 1.3×
and seven-hour programmes at normal text size. They permit zero or one coverage
call when cached data lets navigation cancel pending acquisition, but still
require successor focus, readable bounds, Left return, and no playback/mutation.
Separate held-acquisition tests cover equal-content publication around the
handoff; they do not prove every dispatcher interleaving. Failure diagnostics
retain the original deadline. Passing runs do not establish that the historical
intermittent failures are fixed.

Preview and physical-TV acceptance must include repeated Right/Left navigation
across long programmes, refresh during navigation, and normal/enlarged text,
alongside the existing focus, motion, moving-video and overscan checks.

## Current follow-up: shared grid and Archive depth

**Operator direction:** remove Archive's focusable right-hand recent-recordings
list. Folder browsing should use the Settings-style depth model: one active
level, an inert child preview, and entry/return motion between those roles.
Preserving the current two-interactive-column navigator is no longer a constraint.
The 340/20/412dp Archive split considered during the grid assessment is superseded.

Google's [TV layout reference](https://developer.android.com/design/ui/tv/guides/styles/layouts)
uses twelve 52dp columns, eleven 20dp gutters and two 58dp margins on a 960dp
canvas. The shared layout reserves column 1 for the existing rail, places the
active region in columns 2–6 (x130, 340dp), and places passive details or a depth
preview in columns 8–12 (x562, 340dp). Column 7 and its adjacent gutters form the
92dp separation. Channels, Archive and Settings share this family, while
Guide remains time-based and Schedule/Problems remain flat lists.

These are working dimensions, not a blanket change to shell padding. Settings'
352/108dp column/gap has become 340/92dp, changing its slide stride from 460 to
432dp. Guide has a separate constraint: moving its current body 14dp right would
drop normal capacity from three hours to two. The accepted refinement uses a
172dp gutter and 8dp gap, leaving a 650dp timeline and three normal-text hours.
A compact number/logo line above the channel name gives names 156dp; native
titleSmall 14/20 is retained. Enlarged text keeps the two-hour view. Native focus
growth is outside nominal surface edges and must be reserved separately from
grid gutters.

Archive reuses the existing `ui/components/depth/DepthNavigation.kt` owner for
focus, key cycles and motion. The shared preview now supports passive recording
metadata as well as child lists. Root Back/Left handoff and removed-folder
reconciliation have focused regression coverage. Keep render-visit ownership
separate from permission to request focus: refreshes must not steal focus from
tabs, dialogs or the drawer. Preserve the existing shell/departing-layer scrim
contract when reusing the depth component.

Matched static studies cover Channels, Archive root and nested folders,
recording metadata, Settings, Guide and flat recording modes, including enlarged
text and expanded navigation. The working baseline retains the 32dp folder-path
slot. Native fake-state captures cover the corresponding layouts; automated
focus/restoration checks are distinct from physical motion and remote-feel gates.
The earlier concepts below document the preceding baseline; the shared-grid
study section records the accepted follow-up direction.

## View the proposal

- Present the rendered PNGs directly in conversation; the operator cannot view
  the local HTML. Do not make the viewer a prerequisite for product feedback.
- [Local scene viewer](../design/browse-cohesion.html): 21 selectable compositions
  retained as the editable source, with proposed remote behavior beneath each screen.
- [Current / proposed comparison](../app/build/outputs/browse-cohesion/proposal/comparison.png).
- Populated proposals: [Channels with headline](../app/build/outputs/browse-cohesion/proposal/channels-header.png),
  [Guide](../app/build/outputs/browse-cohesion/proposal/guide.png),
  [Recordings](../app/build/outputs/browse-cohesion/proposal/recordings.png).
- Channels stress states: [header action focused](../app/build/outputs/browse-cohesion/proposal/channels-header-actions.png),
  [German / 1.3×](../app/build/outputs/browse-cohesion/proposal/channels-header-large-de.png),
  [enlarged fallback](../app/build/outputs/browse-cohesion/proposal/channels-header-fallback-large-de.png)
  and [long title](../app/build/outputs/browse-cohesion/proposal/channels-header-long.png).
  [Ambient picon fallback](../app/build/outputs/browse-cohesion/proposal/channels-header-fallback.png)
  and [no image/picon](../app/build/outputs/browse-cohesion/proposal/channels-header-no-picon.png)
  keep the same programme composition.
- [Guide during a scroll](../app/build/outputs/browse-cohesion/proposal/guide-scrolled.png)
  shows content continuing under the top, bottom and timeline-edge fades.
  The [headline-free alternative](../app/build/outputs/browse-cohesion/proposal/channels.png)
  remains available for comparison.

The viewer is an **illustrative HTML design**, not a functioning TV prototype or
Android screenshot. It approximates native component shapes and focus scaling.
Its selector changes scenes; the drawn controls do not operate the app.
Generated images, metadata and local Roboto fonts are ignored under
`app/build/outputs/browse-cohesion/`; the HTML remains usable with a system-font
fallback if those local fonts are absent. Roboto was obtained from Google Fonts;
font files are not added to the application or repository.
The programme scenery in `design/browse-programme-artwork.svg` and the DOC HD
picon are original synthetic fixtures. They demonstrate the two image states,
not a programme-image discovery service or real TVHeadend metadata.

## The common language

Use one browse-and-inspect family: a clear active region, a passive information
region and native Material for TV focus. Shared
hierarchy should make the screens feel related without turning the Guide into
a library or Channels into a dashboard.

- Keep the shell's 80dp closed rail, native navigation, theme, and existing
  ownership of safe insets. The actual browse leading inset is **24dp after the
  rail**, plus 12dp focus reserve; align content at **x116**, trailing edge **912**.
  Do not apply a second 48dp inset to these screens.
- Preserve the existing navbar and its shell-owned scrim. Use the original brand
  and destination icons, dimmed inactive items/brand, and native selected emphasis.
  The rail has no solid backing: its continuous black gradient runs from **0.95**
  at the leading screen edge to transparent **128dp beyond the drawer** (208dp
  total when collapsed), **below the active sheet's content** and below navigation.
  Layer order is background/departing sheets → edge scrim → active sheet → drawer
  → modal scrim/dialog. In particular, headings, icons and focused rows on the
  active sheet must not be darkened. Keep the
  **280dp expanded push drawer** and fixed icon positions. The separate **0.84**
  warm-playback scrim remains shell-owned; these no-video scenes do not show it.
  The original concept substituted a flat rail and brighter icons; the earlier
  correction restored appearance but reproduced the app's active-content overdraw.
  This revision corrects that stacking order rather than preserving the defect.
- Use native TV `ListItem`, tabs, cards and buttons. Remove the recording rows'
  fixed-height custom treatment. Reserve space for native focus overflow.
- Align screen headlines at **116,32**, tabs at **116,80**. The proposed Channels
  headline variant now joins Guide and Recordings; the headline-free version is
  retained as a comparison, not silently removed from the accepted specification.
- Use the existing Roboto roles: 28/36 screen/inspector headline, 24/32 modal title, 16/24 body,
  14/20 metadata. Let native components grow; reduce visible rows at larger text.
- Focus uses inverse fill and matching content color. Selected navigation is
  distinct from focus; Playing and Recording require their own truthful icon
  and text. Never use focus styling to imply active playback.
- Use 8/16/24dp section spacing, sparse cyan progress/navigation and restrained
  surfaces. Preserve Channels' accepted 4dp row gap; tightly related image/title
  and title/subtitle groups also use 4dp in this proposal. Keep native typography metrics.

## Screen contracts

### Channels: choose something to watch

**Headline variant, proposed:** restore **Channels** at **116,32** and reserve
the top-right area for screen-level actions. Search and View settings are
illustrative candidates, not new features authorized by this visual pass. Use
native TV icon buttons with accessible names and a visible focused-action label.
Do not ship inert buttons. Keep the existing first-entry focus target; actions
must be reachable from scopes, with Down returning to scopes and then content.

In this variant scopes sit at **y80** and the list at **y128**. The current
hierarchy trial gives the list **396dp** (previously 340dp) and the passive
preview **360dp at x552** (previously 420dp at x492), with a 40dp column gap.
This makes the browsing list wider than its supporting preview. The ratio is
proposed for visual judgment, not a newly accepted fixed native layout.
The headline costs one complete row:
five instead of six at normal text; four at 1.3×. At 1.3×, scopes move to **y92**
and the list to **y144**. Keep text sizes and the bottom fade rather than packing
the old row count into less space. Search/view behavior and first-entry policy
are separate decisions after the visual baseline.

The headline-free alternative keeps the list at **116,80**, using the same
proposed column ratio. Both versions extend behind the same 48dp bottom fade;
focused rows must remain above that fade. Keep the 60×36 bare picon slot,
`number + channel` heading and start-time/program supporting line. The no-art
fallback must inherit focused content color rather than disappearing on the
inverse focus fill.

The passive preview begins alongside the list at **552,128,360w**, with content
kept above **y508**. Its top image slot is **16:9, 360×202.5dp** in the normal-text study:
use available programme artwork; otherwise contain the channel picon on a muted
ambient background. Loading or a failed image uses the same fallback without
moving the text. If neither image exists, use the ordinary channel glyph and a
neutral ambient surface. Keep logos uncropped; any artwork crop must be deliberate.
Derive restrained ambient color only from an available image, otherwise the theme.
The image is passive, never another D-pad target. Preserve 16:9 for artwork and
both fallback states; artwork may be cover-cropped without distortion, while the
picon remains contained. This corrects the preceding overly shallow 360×152dp slot.

Embed **channel identity · start–end time** and programme progress at the bottom
of the image over a local black gradient. Keep the picon/glyph above this metadata
in fallback states. This is a passive information slot, not another card action;
its local scrim does not replace either shell scrim or warm background playback.
The [compact-card guidance](https://developer.android.com/design/ui/tv/guides/components/cards)
favors brief text over images and a gradient for contrast,
so keep the programme **title and separate supplied subtitle** outside the image,
with 4dp internal spacing and 4dp after the image. The normal study now retains
**three synopsis lines** and Next, instead of the preceding one-line synopsis.
The subtitle is programme metadata, not the synopsis and not text parsed from
title punctuation. If absent, omit it and its gap. Do not add a persistent
**OK Watch** prompt.
The new image area and ratio intentionally revisit the accepted no-art, 340/420dp
Channels composition; they remain proposal values until the baseline is accepted.
Keep the existing 160ms crossfade and native row anatomy.

**G10 follow-up, 2026-10-04:** the earlier concept's content-dependent image sizes
are superseded. Production now keeps **360×202.5dp** artwork at both reference font
scales, independently of programme metadata and image loading/failure. Only a shorter
viewport/type configuration may reduce the slot, preserving 16:9 and minimum text
lines. **Next is anchored at y508**, with its footer reserved even when absent.
Synopsis lines yield first, including omission; longer title/subtitle then use
whole-line ellipsis. Native type sizes remain unchanged. The earlier HTML captures
illustrate the preceding study, not this revised native geometry.

The owner's density concern remains open for a separate typography/hierarchy trial.
The same feedback adds a local 48dp top fade while rows exist above the Channels
viewport; settled focus stays clear and returning to the top removes the mask.
Google's [TV list guidance](https://developer.android.com/design/ui/tv/guides/components/lists)
emphasizes scannability, restrained containers and two-line readability; it does not
mandate a list/detail ratio. Prefer reducing competing detail emphasis and synopsis
length before reducing native row hit areas or globally shrinking text.

Missing metadata says **Program information unavailable** and omits unknown
timing/progress. The channel is still watchable when streaming is independently
allowed. A metadata gap must not become a fabricated playback failure.

### Guide: read time, then choose an action

Keep date, Now and Search in a right-aligned control group. At normal text size,
use a 176dp channel gutter and safe timeline **x300–912**; ruler **y128–156**;
four approximately 72dp lanes between **y164–476**, with 8dp gaps. Content is not
boxed into that safe rectangle: more time columns continue to the physical edge
at **x960**, and another channel row continues below the fourth toward **y540**.
The duration determines cell width; the concept includes 30/60/90-minute entries.

Apply local **48dp trailing/bottom fades** over the overflowing scroll content,
plus leading/top fades when there is content on those sides. Reserve focus-growth
space before masking; a settled focused cell stays entirely in the unfaded safe
region. Scroll it into view before it could settle beneath a fade. Suppress the
overflow treatment at genuine dataset/history boundaries rather than suggesting
unavailable content. The ruler follows time horizontally; the channel gutter
follows channels vertically. Keep destination headings, controls, scopes and
navigation crisp. These are scroll-viewport fades, not a replacement for the
shell's gradient or an overlay darkening the entire active sheet.

The current-time marker is a **continuous 2dp line above every card**, including
focused cards, with a ruler marker. It is passive and uses the same time-to-x
mapping as the ruler and event allocations. Its ruler dot remains visible during
vertical scroll; the line follows the content's edge fade. Do not punch gaps over
the cards.
Use a quiet, explicitly labeled gap for missing guide information. Native focus
indicates the actionable cell; omit the persistent **OK Program details** footer.

At 1.3× text, keep controls and scopes readable, move lanes toward **y188**,
show three taller lanes, and approximately two hours instead of three. The
implementation must derive capacity from actual text/layout, not assume every
localized title has the same height.

### Recordings: one folder path

**Current operator direction, not yet implemented:** Archive uses one active
folder list and a noninteractive preview, sharing the Settings depth-navigation
model. Archive, Schedule and Problems remain separate; the latter two stay flat.

- Focusing a folder previews its immediate children in their existing order.
  It neither enters the folder nor creates a recent-descendant shortcut branch.
- OK or Right on a folder moves that child level into the active slot. The parent
  departs with its selection and viewport retained. Preview content never takes
  focus directly; it becomes interactive only as the active level.
- Focusing a recording shows passive metadata, including genuine resume state.
  OK opens the existing recording details; Right does not play or open a leaf.
- Back closes details to the same recording, then unwinds folder levels to their
  invoking rows. At Archive root, Back reaches the Archive tab; Left reaches the
  drawer. Up from the first active row reaches the mode tabs without losing depth.
- Preserve actual folder paths, recording identities, ordering, eligibility,
  confirmations and current-session guards. A removed path reconciles to a valid
  ancestor; a valid but empty folder has an explicit empty state and usable Back.
  Data refresh must not move focus away from its newer owner.

The previous 340/420dp baseline is evidence, not a width constraint on this
follow-up. Judge the common 340/92/340dp candidate with long recording metadata
and Settings at 1.3× before accepting it. Google's
[navigation guidance](https://developer.android.com/design/ui/tv/guides/foundations/navigation-on-tv)
supports clear axes and predictable Back; this specific one-active-level model
is a product decision, not a claim that the platform bans all two-pane interaction.

Rows put date, duration and status below the title rather than in a competing
trailing column. Folder rows have a directional affordance. Omit the persistent
**OK Open folder / Recording details** footer.

### One details family

Guide and recording details use a common modal composition: **168,48,704w**,
up to **444dp** high, 24dp internal padding, wrapping metadata, bounded information
and persistent actions. At large text use up to **136,32,760w,476h**, stacking
primary playback actions when needed.

**Proposed interaction change:** initial focus goes to the first valid primary
action, not the program description. Watch is primary for a currently watchable
program; Resume is primary only with genuine saved progress, otherwise Play.
When playback is unavailable, use the appropriate recovery action or Close.
Long descriptions become a secondary scroll region; actions must remain visible.
Retain action eligibility, fresh-state checks and DVR confirmations.

## Remote behavior

These are proposed contracts to test during implementation, not claims that the
HTML or current application already implements every transition.

| Region | Entry and movement | OK / Back |
|---|---|---|
| Shell | Drawer enters on current destination; destination and scope tabs commit on focus. Right leaves the drawer for restored content. Down from a scope enters its content; Up from the first row returns to that scope. | Back unwinds the local layer before the drawer. Preserve root Back behavior. |
| Channels | On first content entry, playing channel in scope, otherwise first available channel; later restore semantic row and viewport. Up/Down browse; Left reaches drawer. Right never enters the inspector. | OK watches. Back reaches scopes, then drawer. |
| Guide | First content entry is the current-time cell on the playing/first available channel. Left/Right follow programs; Up/Down preserve time. Up from scopes reaches controls; Now reanchors the same channel. | OK details. Back reaches scopes. At the earliest retained boundary, Left can reach the drawer. |
| Recordings | One active Archive level with semantic item/viewport restoration. Folder entry restores its remembered child or first child. Right enters a folder; previews are inert and Right on a recording does not activate it. | Folder OK enters; recording OK opens details. Left/Back within a folder returns to parent and invoking folder. At root, Back reaches mode tabs; Left reaches drawer. |
| Details | First eligible primary action; spatial action navigation. Up enters overflowing information when present; Down returns to the last action. Short copy is passive. | Back closes and restores invoking item/viewport. Confirmation is the topmost layer and retains its own cancel-first rules. |

Retain the current tab-entry target in the first implementation slice if changing
destination entry is unnecessary for the visual fix; content-entry and restoration
contracts above still apply. Do not combine a visual pass with a new shell policy.
Keys that reveal, replace or relocate UI consume their entire down/up cycle.
Focus alone never tunes, schedules or deletes. Removed items get deterministic
available fallbacks, and asynchronous data arrival does not steal focus.

For RTL, mirror shell, reading alignment and gutter, but keep timeline chronology
consistent: Left earlier, Right later. The right-hand drawer exit is at the latest
retained boundary, with Back via scopes always available. This is a proposed
RTL contract requiring an actual Compose key-navigation test.

## Guidance and recovery

Do not add permanent remote-button reminders for ordinary row/card activation.
[Google's TV navigation guidance](https://developer.android.com/design/ui/tv/guides/foundations/navigation-on-tv)
defines Select as activating the focused item and favors familiar navigation
that does not dominate content. It does not prescribe persistent **OK** prompts.
[Button guidance](https://developer.android.com/design/ui/tv/guides/components/buttons)
calls for clear action labels and a restrained hierarchy. Removing these prompts
is a product decision consistent with that guidance, not a claimed platform ban.
Keep Watch/Resume labels on actual buttons, accessible names/focused labels for
less-obvious icons, and targeted contextual help where an interaction genuinely
needs explanation. Do not replace good empty/error guidance with generic key tips.

| State | Presentation and next step |
|---|---|
| Empty Archive | “No recordings yet. Completed recordings appear here. Schedule shows upcoming recordings.” Archive retains focus and other modes remain reachable. |
| Empty nested folder | Name the folder and offer Back to its real parent. |
| Empty Schedule / Problems | Explain the absence of upcoming recordings / recording problems respectively; do not reuse the Archive message. |
| Loading | Stable layout and explicit loading status. Skeletons do not take focus. |
| Reconnecting with cached data | Keep valid content, show connection status, preserve focus. Distinguish temporarily unavailable mutations from usable playback. |
| Recoverable failure | Specific reason and Retry; retain navigation and Close. |
| Permission restriction / missing file | Explain that specific condition; do not offer a misleading retry or promise playback. |

Use US English app copy. Server-supplied names/tags are not translated. Large-text
German and RTL Arabic scenes are stress examples, not reviewed production strings.
Recovery states above have contracts but not yet matched concept renders.

## Implementation order and acceptance

Delivered baseline and remaining physical acceptance (before the current follow-up):

- [x] Channels headline, 396/360dp balance and content-measured 16:9 preview with
  embedded metadata, artwork/picon fallback and focused layout regressions.
- [x] Guide shared alignment, safe overflow fades and continuous Now-marker
  layering, preserving its time/focus owner and existing controls.
- [x] Recordings native growing rows, restrained surfaces and metadata hierarchy,
  preserving the working folder navigator and action flows.
- [x] Shell active/departing scrim ordering, including interrupted transitions.
- [x] Matched production captures, final verification and independent reviews.
- [ ] Physical-TV observation: video readability, focus/repeats, overscan and playback return.

Read-only preparation may run in parallel. Writable slices are serialized in this
worktree, including their focused Gradle gates. The final integrated gate runs once
after all slices. The original ten production captures are preserved under
`app/build/outputs/browse-cohesion/before-f3180df/`; implementation captures use
`app/build/outputs/browse-cohesion/implemented/`.

1. **Agree one visible baseline:** directly viewable PNGs of all three populated
   screens, the Channels headline tradeoff, large text and relevant focus states.
   Keep the source-matched current app captures as the before reference.
2. **Implement visual foundations together:** current-sheet scrim ordering,
   native rows, shared spacing/type/focus, Guide overflow fades and time-line
   layering, and the accepted Channels headline, ratio and artwork fallback.
   Preserve existing
   entry, folder navigation, DVR eligibility, activation and Back behavior in
   this slice; do not bundle new Search/View settings behavior into it.
3. **Capture the implemented baseline:** matched production-composable images,
   normal/large localized text, missing metadata, empty/reconnecting states,
   collapsed/expanded drawer and active-versus-departing sheet transitions.
   Add focused layering and navigation regressions. Run final verification,
   screenshot-first UX review and runtime review where behavior is affected.
4. **Physical TV:** readability over video, focus growth, repeats, Back, overscan
   and return from playback. Static concepts cannot close these checks.
5. **Current follow-up:** resolve shared-grid composition and Archive depth together
   under the direction above. Action-first details, guidance/recovery refinements
   and new screen actions remain separate proposals, outside baseline acceptance.

The shell correction retains the original gradient geometry and draws it before
active content. `BrowseContentLayering.kt` applies screen-aligned black
`SrcAtop` attenuation to the outermost departing contribution only, preserving
alpha, transparent gaps, existing transforms and overflow. Active destination,
category and depth wrappers draw above outgoing siblings. Rendered tab identity,
not pending interaction ownership, determines visual currentness. Pixel tests
cover cached ancestor motion, RTL, interruptions, alpha, overlap and overflow.
Guide now owns one continuous current-time marker above all rows and row gaps.

Native Guide rows need 80dp at normal text rather than the concept's 72dp lanes.
Enlarged text grows the lanes and reduces the measured visible time span without
changing SDK acquisition or availability policy. Missing/loading/error picon
glyphs inherit native row content colors; passive previews retain their previous
tint and successful images are not tinted.

Keep playback, SDK/domain ownership, channel ordering, shared scopes, recorded
folder hierarchy, permission policy and action confirmations intact. Do not add
recommendations, external artwork discovery, duplicate SDK state or a general
browse framework. Programme imagery must come from available metadata; the
no-artwork fallback is the complete supported path when none is supplied.

## Native implementation evidence

The accepted baseline is implemented in production composables. Native test
captures are under `app/build/outputs/browse-cohesion/channels/`, `guide/`,
`recordings/`, `shell/` and `implemented/`. Frozen review sets and their source,
PNG and metadata hashes are under `app/build/outputs/browse-cohesion/review/`;
the corrected Guide states are in its `closure/` directory. Generated evidence
remains ignored and can be recreated by the corresponding JVM tests.

The final integrated `./tools/verify` passed on 2026-10-04: **1,719 tests in
182 suites** (app 1,073; client 646), zero failures/errors/skips. Lint, Android-test
compilation, debug APK build, released SDK consumption and packaged native-library
integrity/alignment checks passed. Log:
`/tmp/opencode/browse-cohesion-reviewed-verify.64QkoI.log`.
The frozen source/test hashes match that final state. Debug APK SHA-256:
`a6975ccce5b7137ede3cace8d72a68b92228cc7a28dcad88d23111e22d2f07e0`.

Initial independent runtime reviews identified two Guide regressions: disappearing
history could reclaim focus from tabs/drawer, and overlong programmes could trap
horizontal navigation. Both have focused red/green regressions and corrections.
Additional tests verify enlarged-display acquisition and pending-window identity.
The screenshot review requested terminal focus breathing room; shared spatial
padding now preserves complete native focus, duration geometry and existing time
bounds, including RTL. The matched 1.3× horizon capture has 61px of trailing space,
with a one-hour card occupying exactly half of its two-hour ruler. Fourteen
regressions were added across these review corrections. Independent Astra Fast Max
and Opus high runtime closures returned **CLEAN**. Screenshot-first Astra Fast Max
review returned **DESIGN_READY**, closing the terminal-edge finding without a
directly affected visible regression. Full verification covers that final state.

These are production Compose captures with fake state, not HTML concepts or
physical-TV acceptance. Remaining observation gates are moving-video readability,
SurfaceView visibility, overscan, remote-repeat/focus feel, transition performance
and playback return. The non-blocking root-scan and idle-layer cost observations
also need measured evidence before any optimization work.

## Concept-stage evidence and checks

`BrowseCohesionEvidenceTest` captures the **actual production composables**
with the real theme/rail, synthetic SDK data, no artwork and an opaque no-video
background: 960×540 mdpi, UTC/en-US, normal and 1.3× text. Seven tests produce ten
PNG/metadata pairs under `app/build/outputs/browse-cohesion/implemented/` and assert
native focus and no unintended playback/navigation/SDK actions. Fixtures are
relative to the run clock, which the metadata records; they are not pixel-goldens.

```sh
./gradlew :app:testDebugUnitTest \
  --tests 'at.bernhardberger.tvhplayer.ui.screens.BrowseCohesionEvidenceTest' \
  --console=plain --no-scan
```

The proposal's 21 PNG/JSON pairs use local Chromium, a fixed illustrative clock,
and painted focus. They are not evidence for native focus, translations or behavior.
The earlier concept review cleared corrected timeline alignment, German channel
wrapping and clock-consistent progress bars. The current revision additionally
changes active-sheet stacking, the Guide marker and the Channels headline option,
and removes redundant always-visible OK reminders following product feedback.
The preceding 16-scene revision passed the text/geometry audit and JavaScript
syntax check. A bounded Astra Max concept review found that revision ready for
product feedback, including the heading's one-row tradeoff, singular focus,
enlarged German layouts and continuous Guide marker in RTL. Active/departing-sheet
stacking is not demonstrated by these single-sheet frames; the layered native
state remains an implementation acceptance check. A subsequent revision added the
Guide scroll-edge study, Channels artwork/picon cases, separate subtitle and
396/360dp hierarchy trial. A bounded Astra Max review of nine affected concept states found the
artwork/overflow delta ready for product feedback with no new visible blocker. It recommends
retaining the 396dp list, 360dp preview and 40dp gap: narrowing the preview further
would compromise long titles and subtitles. This remains concept feedback, not
native implementation approval.

The latest revision preserves 16:9 and embeds short metadata to recover useful
details. All 21 captures pass the existing geometry audit and match the current
HTML/artwork source hashes; JavaScript and SVG syntax checks also pass. A focused
DOM check of all ten Channels scenes confirms exactly 16:9 image slots, metadata
inside the image, no fallback/progress overlap and no clipped programme title.
The inspector ends above y508: normal populated y504.5, enlarged German y504.6,
long normal-size title y500. The normal synopsis has three lines and the enlarged
one two. A fresh bounded Astra Max review of five affected images found this delta
ready for product feedback with no new visible blocker. Native content measurement,
interaction and physical-TV readability over warm playback remain separate gates.
After product feedback, the navbar's original drawable paths, dimming and continuous
edge scrim were restored. The corrected 80×540 rail regions differ from the native
baseline captures by less than 0.12 mean RGB levels out of 255 across Channels,
Guide and Recordings in that restoration check. This verifies navbar appearance,
not the formerly incorrect ordering relative to current-sheet content or native
interaction behavior.
The initial, concept-stage test run exposed a capture readiness race: Compose idle did not
wait for off-main library preparation. Waiting for the actual Archive focus target
resolved it and that stage's `./tools/verify` passed, before production UI changes.

The documentation-authority check reports five pre-existing issues. Comparing its
result with `f3180df` confirmed that this proposal introduces no new issues.

## Shared-grid static studies — 2026-10-04

**Design evidence only:** [new scene viewer](../design/browse-grid-study.html).
These 19 HTML compositions show the accepted working direction for the follow-up,
not its native implementation. They do not prove native focus, navigation,
translations, playback or physical-TV readability. Earlier studies, captures and
accepted contracts above are unchanged. Present the PNGs directly for feedback.

Each scene is 960×540 at one CSS pixel per logical dp, using the existing local
Roboto fonts, en-US/1.0 or de-DE/1.3 text, an opaque no-video background and one
painted focus target. The coastal artwork, channel picons, folder tree, programme
copy and resume position are synthetic. Nothing was fetched. A matching grid
overlay shows twelve 52dp columns, 20dp gutters and 58dp margins.

The candidate uses active x130/340dp and passive x562/340dp panes, a 92dp gap,
28/36 headings, 16/24 rows, 14/20 support and a 22/28 passive title. Text really
grows at 1.3×; it is not shrunk to fit. Original sidebar SVG paths, brand and
dimming are retained. The 208dp, 0.95-to-transparent scrim stays below active
content. The expanded 280dp drawer translates the fixed composition by +200dp
and clips the preview; it never reflows it.

### PNG comparisons

All montage panels remain a full 960×540; they are stacked, not reduced side by
side. Every individual scene also has `<scene>.png` and `<scene>-grid.png` under
`app/build/outputs/browse-cohesion/grid-study/`.

| Comparison | Clean PNG | Grid-overlay PNG |
|---|---|---|
| Archive root → immediate child → recording metadata, then Settings | [Depth](../app/build/outputs/browse-cohesion/grid-study/montage-depth.png) | [Depth grid](../app/build/outputs/browse-cohesion/grid-study/montage-depth-grid.png) |
| Channels normal, German 1.3×, missing metadata | [Channels](../app/build/outputs/browse-cohesion/grid-study/montage-channels.png) | [Channels grid](../app/build/outputs/browse-cohesion/grid-study/montage-channels-grid.png) |
| Guide reference and refined 172dp gutter, normal and German 1.3× | [Guide](../app/build/outputs/browse-cohesion/grid-study/montage-guide.png) | [Guide grid](../app/build/outputs/browse-cohesion/grid-study/montage-guide-grid.png) |
| Archive/Settings German 1.3×, empty folder, expanded drawer | [Depth stress](../app/build/outputs/browse-cohesion/grid-study/montage-depth-stress.png) | [Depth stress grid](../app/build/outputs/browse-cohesion/grid-study/montage-depth-stress-grid.png) |
| Schedule remains flat | [Schedule](../app/build/outputs/browse-cohesion/grid-study/schedule-flat.png) | [Schedule grid](../app/build/outputs/browse-cohesion/grid-study/schedule-flat-grid.png) |

### Retained design tradeoffs

- **One active depth level:** all immediate-child preview rows use the Settings
  0.6-alpha precedent, without a focus surface. The same children promote into
  the next active scene, not a recent-descendant shortcut. A fixed 32dp path strip
  keeps rows aligned but leaves visible blank space at root. The working baseline
  retains that cost and the restrained preview hierarchy. The empty-folder
  scene is a separate post-removal state, not a contradictory live item count.
- **340dp panes:** Settings' proposed stride is 432dp rather than 460dp, with its
  heading at y56 and a 60dp heading slot; there are no fake mode tabs. Root labels
  follow `SettingsScreen.kt`; Player child copy/values are illustrative. Channels
  keeps a metadata-independent 340×191.25dp image and the reserved Next footer at
  y508. Enlarged long text omits its synopsis and truncates the supplied subtitle.
  Enlarged recording metadata bounds the title to three lines and synopsis to
  one. Those losses remain subject to native validation; no font shrinks.
- **Guide's capacity cliff:** current x116/176dp and candidate x130/162dp both
  leave origin x300 and width 660dp, mapping three hours at 660/180dp per minute.
  The corrected native reference uses titleSmall 14/20, a 44×30dp picon, 8dp gap
  and 16dp padding: **94dp** for names with 162dp, versus **108dp** with 176dp.
  The first study incorrectly used larger 16/24 type and a 12dp gap, overstating
  its crowding. Its two-hour mapping is 660/120. Keeping 176dp at x130 leaves 646dp
  and two normal-text hours:
  `floor((646 - 48) / (2 * 100)) = 2`. The alternative uses 646/120, not a changed
  capacity threshold. The refined 172dp candidate instead leaves 650dp and retains
  three normal-text hours with the unchanged formula. It places number and a
  44×20dp logo in a compact line above the name, giving the name 156dp without
  reducing the native 14/20 type. At 1.3×, type grows and the window remains two
  hours. This trades logo height for name readability, while retaining the shared
  x130 anchor and 8dp timeline gap. Normal lanes are 80dp, enlarged lanes 104dp.
  This refinement supersedes the earlier 162dp candidate in the working baseline.
  The evidence remains static, including its browser-specific text wrapping.

### Bounded checks

[Manifest and per-scene audits](../app/build/outputs/browse-cohesion/grid-study/manifest.json)
record source/SVG/font/PNG SHA-256 values, canvas, locale, font scale, painted focus,
deliberate clamps and static limitations. The local scratch renderer is
`/tmp/opencode/browse-grid-study/render.cjs`; its log is in the same directory.

```sh
node --check /tmp/opencode/browse-grid-study/render.cjs
node /tmp/opencode/browse-grid-study/render.cjs > /tmp/opencode/browse-grid-study/render.log 2>&1
git diff --check
```

Final renderer result: **PASS — 19 scenes / 38 PNGs + 8 full-width montages;
0 runtime errors; 0 network requests.** Checks cover inline JavaScript syntax,
pane axes, unshrunk type, text bounds, clipping ancestors/focus reserve, immediate
child promotion, fixed image/Next geometry, original navigation paths, scrim
ordering and shared Guide ruler/cell/Now mappings. Pixel sampling confirms the
continuous Now line above cards. All 46 PNG hashes and dimensions were checked.
Direct image inspection included the depth progression, all four Guide layouts,
large-text and missing/empty states, grid overlays, drawer and Schedule. The
Schedule focus edge was corrected after that inspection; the wider overflow
reserve does not alter its nominal x130 row axis. Native component integration,
key-cycle/restoration tests and physical-TV acceptance remain separate gates.
No Gradle or device operation was used for these static concepts.
