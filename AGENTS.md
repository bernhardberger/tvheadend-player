# TVHeadend Player Engineering Guide

TVHeadend Player for TV is an independently developed, public GPLv3 Android TV
client for TVHeadend. It descends from
[`Preclikos/tvhstream`](https://github.com/Preclikos/tvhstream) and preserves
upstream history, attribution, and a clean path for generic contributions.

## Start here

Before non-trivial work:

1. Run `git status -sb` and inspect the recent log. Preserve every existing
   worktree change and stop if another writer is active in the same checkout.
2. Use `docs/README.md` to select only the documents relevant to the task. Do
   not read the whole documentation tree.
3. Use the built-in writable `build` primary for both application and repository
   work. The active package and repository rules define its scope; no second
   writable project agent exists.
4. State assumptions before ambiguous or architectural work and implement one
   small, independently verifiable slice.
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

Read and load only the matching row:

| Concern | Required authority and workflow |
|---|---|
| Compose UI, focus, remote keys, accessibility, TV surfaces | `docs/tv-design-spec.md`; `android-tv-compose-ux`; every focused Kotlin/Compose skill whose trigger matches |
| Channels, EPG, recordings, DVR | `live-tv-dvr-conventions`; the relevant appliance specification/plan sections only when appliance behavior is involved |
| Appliance launch, HOME, GUIDE, wake, Simple TV | `docs/appliance-mode-spec.md`, relevant sections of `docs/appliance-mode-plan.md` |
| Media3, HTSP, PlayerView, codecs, native AARs | `media3-htsp-playback-safety`; when the active task or package allows external SDK reads, its `sdk/decoder-ffmpeg-binary/native-dependencies.json`; dated assessments only when the task names that upgrade/finding |
| Physical TV, ADB, install, screenshots, remote keys | `docs/device-targets.md`; `android-tv-device-testing` |
| Product identity | `docs/product-identity-plan.md` |
| Signing, publication, rollback | `docs/release-process.md` |
| AI harness, agents, skills, commands, OpenCode config | `docs/ai-engineering-harness.md`; `customize-opencode` when that external skill is available |
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
- For behavior changes, write the failing test first. Keep pure policy outside
  Android UI where practical so JVM tests can cover it.
- Run focused checks while iterating and `./tools/verify` before considering a
  slice complete.
- External review is risk-based, not an automatic approval loop. Use one scoped
  audit and one closure limited to its findings and fix delta, then remediate
  new in-scope blockers autonomously. Never ask the user merely whether to
  continue; interrupt only for a genuine product choice or safety boundary.
  Review economy never waives an unresolved correctness or safety blocker.
- Review the final diff for secrets, unrelated churn, stale paths, GPLv3
  attribution, and generic/product/appliance boundaries.
- Do not commit, amend, push, publish, sign, install, or mutate a TV unless the
  user explicitly requests that operation and its safety requirements pass.
- Do not run parallel writers in one dirty worktree or concurrent Gradle builds,
  device operations, Git mutations, signing, publishing, or releases.
- Run at most one substantial visual or navigation slice per primary session;
  return a compact next-slice handoff instead of carrying accumulated context
  into another implementation.
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
  `android-tv-device-testing`, and use `./tools/device`. Confirm the selected
  role and all four live identity properties before mutation.
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

All children are read-only. The primary owns decomposition and delegation: use
children whenever they are likely to materially improve correctness, evidence
coverage, context isolation, turnaround time, or final quality. A roughly 20%
resource overhead is an acceptable soft target for a meaningful quality gain,
not a hard accounting threshold. There is no fixed child-count limit; avoid only
duplicated assignments and unnecessarily verbose returned evidence.

Use Luna/low `app-locator` for mechanical retrieval, Terra/medium `app-explore`
for bounded multi-file source maps and call traces, `app-planner` for an optional
design second opinion, `app-analyze` for one concrete post-plan contradiction,
and `app-research` for one authoritative external-source question after local
sources are insufficient. Use `android-reviewer` for a risk-based frozen-packet
review. Use `tv-evidence-curator` for mechanical screenshot-set validation,
`tv-ux-brief` for pre-implementation product direction, and `tv-ux-reviewer` for
final screenshot-first design review. Only `app-locator` children may be
delegated by read-only children, and depth 2 is terminal. Children cannot edit,
use shell, run builds or devices, mutate Git, or read project instructions,
ledgers, handoffs, archives, or broad plans. Their configured model variants and
step limits do not inherit the writable primary's `medium`, `high`, `xhigh`, or
`max` effort.

The Muse reviewer field test is complete; do not invoke `app-review-muse`.

For a substantial new or redesigned TV surface, obtain one fresh fixed
Opus/high `tv-ux-brief` before implementation and one final fixed Opus/medium
`tv-ux-reviewer` after the Sol primary captures current evidence and the
Terra/medium curator validates its state matrix. Run a closure review only when
blocking design findings require matched recaptures. Before each Opus UX call,
run `./review-provider-route.sh select ux`; launch the role only when it prints
`opus`. The UX route requires remaining Claude quota above 20% in the 5-hour
window and above 5% in the 7-day window. Missing, malformed, exhausted, or
below-threshold telemetry skips that optional call without weakening the
mandatory Sol Android review. Keep `claude-audit-lead` Opus/high for genuinely
critical or complex nonvisual architecture and dependency work. Use at most one
Opus specialization for a concern; routine, documentation, test-only, and
configuration-only changes are Sol-only.

Start each child as a fresh session by omitting `task_id`; never resume old child
history. Supply one self-contained question with accepted invariants, included
paths, exclusions, relevant evidence, and a stop condition. Do not redefine the
role's permissions, generic policy, output contract or verdict vocabulary in a
task packet. Supply the actual relevant diff or exact readable changed paths;
Git identity and gate status are caller-provided evidence for children that
cannot run commands. A child that reaches
its terminal budget reports inspected scope and the exact remaining evidence
gap instead of claiming completion. The writable primary adjudicates and fixes
in-scope review findings directly; a broad new concern becomes a separately
authorized package rather than an expanding review loop.

## Upstream and repository discipline

Classify work as **generic**, **product-specific**, **appliance-specific**, or
**mixed**. Keep generic foundations separable and use the upstream contribution
skill before proposing them to `Preclikos/tvhstream`. Never push product or
appliance work to the predecessor repository.

Direct local work on `main` is allowed, but use a branch for upstream work,
parallel development, or risky experiments. Never force-push or rewrite
published history.
