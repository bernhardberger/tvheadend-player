# TVHeadend Player Engineering Guide

TVHeadend Player for TV is an independently developed, public GPLv3 Android TV
client for TVHeadend. It descends from
[`Preclikos/tvhstream`](https://github.com/Preclikos/tvhstream) and preserves
upstream history, attribution, and a clean path for generic contributions.

## Start here

Before non-trivial work:

1. Run `git status -sb` and inspect the recent log. Preserve every existing
   worktree change and its ownership. Coordinate actual conflicting edits or
   shared mutations; another writer's disjoint work is not itself a stop condition.
2. Use `docs/README.md` to select only the documents relevant to the task. Do
   not read the whole documentation tree.
3. Use the built-in writable `build` primary for both application and repository
   work. The operator's task, any admitted package and repository rules define its scope; no second
   writable project agent exists.
4. State assumptions before ambiguous or architectural work. Use small,
   independently verifiable changes to complete the authorized outcome end-to-end.
5. Fetch remotes before upstream synchronization, contribution preparation, or
   commit-range comparison. A local documentation or tooling edit does not need
   a fetch merely to begin.

Anything under `docs/archive/`, legacy screenshots, captures, review artifacts,
and model/session notes is historical by default. Read it only when the user or
assignment names its exact path. A recent timestamp or a filename containing
`current` does not establish authority.

## Product and safety boundaries

- The public product is a remote-first Android TV live-TV client. Appliance
  behavior is an optional profile and household integration layer.
- `docs/product-identity-plan.md` is the identity authority. Do not rename the
  package, product, or public repository as incidental cleanup.
- The accepted playback baseline is upstream TVHStream's Media3/HTSP path. Do
  not alter the extractor, stream readers, renderer/decoder selection, native
  extensions, or progressive/interlaced behavior as a side effect.
- Focusable TV UI uses `androidx.tv:tv-material`. Mobile Material is limited to
  primitives that the installed TV artifact does not provide.
- Distributed combined binaries and corresponding source remain GPLv3. Preserve
  predecessor attribution and do not imply this fork is wholly original.
- Native decoder provenance remains a signed-release gate. Never reinterpret a
  warning from `./tools/check-native-libs` as permission to publish.

## Task routing

Read the relevant sections and load skills only for the actual operation or
implementation question. A touched file or adjacent topic does not trigger an
entire skill family. Multiple rows apply only when their concerns are affected.

| Concern | Required authority and workflow |
|---|---|
| Compose UI, focus, remote keys, accessibility, TV surfaces | `docs/tv-design-spec.md`; `android-tv-compose-ux`, whose router selects focused mechanics |
| Channels, EPG, recordings, DVR | `live-tv-dvr-conventions`; the relevant appliance specification/plan sections only when appliance behavior is involved |
| Appliance launch, HOME, GUIDE, wake, Simple TV | `docs/appliance-mode-spec.md`, relevant sections of `docs/appliance-mode-plan.md` |
| Media3, HTSP, PlayerView, codecs, native AARs | `media3-htsp-playback-safety`; native provenance references only for dependency/native work; dated assessments only for a named upgrade/finding |
| SDK adoption or app/SDK contract diagnosis | `tvheadend-sdk-adoption` |
| Physical TV or emulator operations, ADB, install, device capture or key injection | `android-tv-device-testing`; `docs/device-targets.md` for physical targets |
| Gradle execution or build/test/lint diagnosis | `gradle-run` |
| Product identity | `docs/product-identity-plan.md` |
| Signing, publication, rollback | `docs/release-process.md` |
| AI harness, agents, skills, commands, OpenCode config | `docs/ai-engineering-harness.md`; `customize-opencode` when that external skill is available |
| Child dispatch or independent review | Relevant Delegation or Review lifecycle sections of `docs/ai-engineering-harness.md`; inline applicable hard requirements for restricted children |
| Upstream sync or contribution | `tvhstream-upstream-contribution` |

Focused imported skills are implementation guidance, not authorization for
opportunistic cleanup. Product and safety specifications take precedence, then
repository-local domain overlays, then the focused skill, then local style.

## Engineering workflow

- Make the smallest correct change. Do not introduce an abstraction for one
  use or combine unrelated cleanup with behavior work.
- Do not add compatibility façades, SDK model/result mirrors, protocol-shaped
  test translators, bespoke verification frameworks, or production test seams
  when released APIs and test fakes already own the behavior.
- During the 0.x development track, backward compatibility with older Player
  versions, SDK/HTSP library APIs, and stored formats is not required. Prefer one
  current path; retire obsolete adapters and migrations, update current consumers,
  and document any manual migration or fresh setup instead of adding compatibility
  layers. Supported Android/TVHeadend targets, current protocol optionality,
  recovery behavior and session/permission guards remain separate requirements.
  This policy does not authorize automatic data clearing or device resets;
  SDK and HTSP repository changes remain with their respective owners.
- For behavior changes, add a focused regression test. Keep pure policy outside
  Android UI where practical so JVM tests can cover it.
- Run focused checks while iterating and `./tools/verify` once for the final code
  state. For docs/config-only edits use relevant static checks instead. Reuse
  unchanged successful evidence; a review or administrative stage is not a reason
  to run it again. Do not add tests for model names or prompt wording.
- External review is risk-based, not an automatic approval loop. Non-trivial
  non-UX work needs independent Astra and Opus reviews of the same bounded
  evidence, subject to the local review routing below. Follow up only on unresolved
  findings or materially changed behavior, not an automatic third or broad repeat audit. Remediate
  new in-scope blockers autonomously. Never ask the user merely whether to
  continue; interrupt only for a genuine product choice or safety boundary.
  Review economy never waives an unresolved correctness or safety blocker.
- Review the final diff for secrets, unrelated churn, stale paths, GPLv3
  attribution, and generic/product/appliance boundaries.
- Commits, amendments, pushes, publication, signing, installation and TV mutation
  require coverage in the operator's task or applicable standing/package authority
  and all operation-specific gates. Existing authorization need not be requested
  again; it never grants unrelated operations or bypasses a human gate.
- Coordinate actual conflicting edits and serialize shared Gradle, device, Git,
  signing, publishing and release mutations. For centrally admitted work,
  repository/resource overlap alone is not a scheduler gate; preserve disjoint
  work without adopting it or inventing a lock protocol. The writable child's
  stricter non-concurrent-editing contract remains binding.
- One primary owns a coherent task end-to-end, including authorized release and
  verification. Split only for a real dependency, ownership boundary or context
  problem—not because implementation, review and release are different stages.
- On the shared LXC, keep Gradle state in disk-backed `$HOME/.gradle`, retain
  `--no-daemon`, and stop rather than increasing memory if the host becomes
  sluggish.

## TV interaction floor

TV UI is ten-foot, 16:9, D-pad-only first. Every changed surface needs a
deterministic initial focus, complete directional reachability, predictable Back,
visible focus, restoration, TV-safe spacing, readable long localized text,
accessibility semantics, and explicit loading/empty/error/recovery behavior. A
key that reveals, replaces, or moves focus to UI must be consumed so the same
event cannot activate the new target. Use deliberate scrims over video.

Automated tests and ADB screenshots do not prove SurfaceView visibility, focus
feel, readability over motion, overscan, remote-repeat behavior, deinterlacing,
or motion quality. Record those as physical-TV gates.

Prefer deterministic offline captures of production composables with fake state
for static visual review. Record canvas, locale, font scale, and focus state and
keep generated evidence ignored. This can prove only the captured composition;
it does not replace integrated or physical-TV gates.

## Device, credential, and release safety

- TVHeadend credentials and signing material belong only in ignored owner-only
  files and Android app-private storage. Never place values in arguments,
  environment variables, Git, Gradle properties, logs, screenshots, reports, or
  generated output.
- Before any physical-device operation, read `docs/device-targets.md`, load
  `android-tv-device-testing`, and follow `docs/android-tooling.md`: official CLI
  with explicit `--device` for ordinary install/capture, ADB only for missing CLI
  capabilities. Confirm the selected role and all four live identity properties
  before mutation; `tools/device doctor` is not a mandatory extra preflight.
- Production and unclassified devices are read-only except for an explicitly
  approved production-signed update. Never substitute one TV for another based
  only on a generic model string.
- Do not use broad `logcat`, `dumpsys`, `uiautomator dump`, app-data export, or
  credential-bearing UI automation. Credential provisioning is allowed only
  through `./tools/device provision-test-credentials` on an exact designated
  test target under `docs/test-device-credential-provisioning.md`.
- Do not add debug exported components for secret injection or modify TVHeadend
  accounts, tuners, OSCam, storage, stream profiles, TV packages, or network
  infrastructure without separate explicit approval.
- For a human-visible or time-window device check, ask one focused question and
  wait. Do not infer a human-observation pass from counters or screenshots.

## Delegation and evidence

Before dispatch, read the relevant **Delegation and context containment** and
**Review lifecycle and autonomous continuation** sections in
`docs/ai-engineering-harness.md`. They own role selection, packet construction,
review routing, fallback and recovery. Model/effort/permission/budget assignments
remain in OpenCode configuration and agent frontmatter; retired roles stay retired.

The primary owns decomposition and integration. Use children when they materially
improve correctness, evidence coverage, context isolation, turnaround or quality.
Review, analysis and retrieval children are read-only; only `app-locator` nesting
is allowed where configured, and depth 2 is terminal. The bounded implementer
and device-operator exceptions retain their own restrictions, not primary authority.
Restricted children cannot read project policy: callers must inline applicable
hard requirements, accepted invariants and exact evidence without redefining the
role's permissions, output or verdict contract.

Non-trivial non-UX work requires independent `android-reviewer` and
`claude-audit-lead` coverage under the harness routing. Substantial new/redesigned
TV surfaces require final screenshot-first `tv-ux-reviewer` coverage; use
`tv-ux-brief` when direction is unresolved. UX review does not replace distinct
runtime review or physical-TV gates. Low-impact work has no mandatory pair.

Before EVERY Opus dispatch, including UX and follow-ups, run
`./review-provider-route.sh select eligible`; only successful stdout `opus`
permits it. Follow the harness quota/fallback/abort procedure, never cached
eligibility or a waived non-substitutable gate. The primary adjudicates and fixes
supported in-scope findings and continues authorized work.

## Upstream and repository discipline

Classify work as **generic**, **product-specific**, **appliance-specific**, or
**mixed**. Keep generic foundations separable and use the upstream contribution
skill before proposing them to `Preclikos/tvhstream`. Never push product or
appliance work to the predecessor repository.

Direct local work on `main` is allowed, but use a branch for upstream work,
parallel development, or risky experiments. Never force-push or rewrite
published history.
