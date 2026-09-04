# AI engineering harness

OpenCode and OpenChamber use the same repository-local harness from the
repository root, identified by this `AGENTS.md` and `.opencode/opencode.json`.

## Tracked architecture

| Path | Purpose |
|---|---|
| `AGENTS.md` | Sole automatic project instruction; concise safety, routing, and workflow floor |
| `docs/README.md` | Documentation authority and lifecycle index used for task-specific reads |
| `docs/archive/README.md` | Historical-document containment and successor map |
| `.opencode/opencode.json` | Built-in Build default, read-only child assignments, sharing policy, permissions, and depth-2 child allowlist |
| `.opencode/commands/continue-app.md` | Concise task execution contract for the built-in Build primary |
| `.opencode/agents/app-locator.md` | Mechanical repository locator |
| `.opencode/agents/app-explore.md` | Bounded multi-file source and behavior mapper |
| `.opencode/agents/app-planner.md` | Optional bounded planning second opinion |
| `.opencode/agents/app-analyze.md` | Concrete implementation diagnostician |
| `.opencode/agents/app-research.md` | Authoritative external-source researcher |
| `.opencode/agents/android-reviewer.md` | Risk-based Android runtime and TV interaction reviewer |
| `.opencode/agents/tv-evidence-curator.md` | Optional validator for exact screenshot evidence sets |
| `.opencode/agents/tv-ux-brief.md` | TV product design specialist when direction is unresolved |
| `.opencode/agents/tv-ux-reviewer.md` | Final screenshot-first visual-quality reviewer |
| `.agents/skills/` | Reviewed, pinned Kotlin and Compose implementation guidance |
| `.opencode/skills/` | TVHeadend product, playback, device, DVR, and upstream overlays |
| `.opencode/commands/` | Verification, device, reviewer, UX, and upstream shortcuts |
| `skills-lock.json` / `NOTICE.md` | Imported skill source, hashes, license, and attribution |
| `tools/check-doc-authority` | Documentation classification, archive containment, and stale-context prevention |
| `tools/verify` | Native/tool/JVM/lint/Android-test compilation, APK, identity, ABI, and 16 KB gates |
| `tools/check-native-libs` | Native AAR integrity, ABI/ELF, corresponding-source, and release-provenance gate |
| `tools/device` | Role-aware bounded ADB wrapper |

## Primary agents and model assignments

The built-in `build` is the sole writable primary for application and repository
work. `AGENTS.md` and the operator's task supply authority; an already admitted
package retains its explicit contract. New direct work needs no package. No
custom writable primary or repository-configured primary step ceiling exists.

Like the SDK workspace, the trusted writable primary receives edit, Bash, web
fetch, and external-directory capabilities directly from server-side project
configuration. Operator authority, any admitted package, and `AGENTS.md` define what it may
do; there is no per-command approval relay or duplicated command deny list.
Read-only children retain their own explicit restrictions and the exact Task
allowlist remains deny-by-default.

Model, effort and step assignments live only in `.opencode/opencode.json` and
agent frontmatter. Edit the relevant assignment directly and restart OpenCode;
do not duplicate its value in policy or tests. Child settings do not inherit the
primary's effort. The model-tier helper and its parallel registry were removed:
mixed supported models are valid, and changing one is not a product gate.

## Delegation and context containment

Delegation is read-only and may nest through one additional locator level.
The primary owns decomposition and delegation and may use as many children as it
judges useful for correctness, evidence coverage, context isolation, turnaround,
or final quality. A roughly 20% resource overhead is an acceptable soft target
for a meaningful quality gain, not a hard budget. Avoid duplicated work and
verbose returns because cheap child output can still enlarge the primary's
expensive context.

The project Task policy denies every child first, then permits the configured
roles above. `app-locator` performs mechanical retrieval; `app-explore` maps
bounded multi-file flows without diagnosis or design; the remaining roles retain
their specialized contracts. Only `app-locator` children may be delegated by
read-only children, and depth 2 is terminal. Reviewers may use that capability
only for exact in-packet retrieval, never to reconstruct missing evidence.

Children cannot edit, use shell, run builds or devices, mutate Git, or read
project instructions, ledgers, handoffs, archives, or broad plans. Start each as
a fresh session without `task_id` and supply one self-contained question with
exact evidence and a stop condition. The writable primary has no
repository-configured step ceiling; deterministic wall-clock and stalled-session
watchdogs bound orchestration. Child step limits are terminal evidence budgets,
not a reason for generic continuation.

Only the built-in writable primary writes repository files. Read-only review
begins after its delta is stable. Writers, Gradle builds, device operations, Git
mutations, signing, publishing, and release operations may not overlap.

## Review lifecycle and autonomous continuation

Independent review supplements focused tests and primary-agent self-review; it
does not replace either and is not an automatic gate for every edit. Select it by
risk:

| Change | Independent review |
|---|---|
| Documentation, tests, or mechanical work with no production behavior change | Normally none |
| Security-sensitive or substantial runtime/lifecycle/concurrency change | One suitable reviewer, normally `android-reviewer` |
| Ordinary release using the existing release path | No additional model approval; retain release artifact and authorization checks |
| Unresolved visual direction for a substantial new or redesigned TV surface | Optional `tv-ux-brief` against supplied requirements and baseline images |
| Screenshot-set coverage, metadata, duplication, staleness, or privacy | Primary checks it; optional `tv-evidence-curator` for useful independent work |
| Final visual hierarchy, alignment, spacing, typography, density, focus appearance, consistency, or Material for TV design | `tv-ux-reviewer` against the curated current manifest |
| HTSP, Media3, concurrency, subscription ownership, or DVR lifecycle | One architecture/race audit can replace runtime review; closure only for a fix that remains uncertain |

Every code-review assignment supplies one frozen tested packet: exact acceptance
criteria, changed files or diff, relevant source/tests, invariants, exclusions,
and verification evidence. The reviewer does not reconstruct scope through Git
or broad repository archaeology.

The primary adjudicates and fixes supported in-scope findings directly. The
reviewer ends with `BLOCKING`, `NON_BLOCKING`, `CLEAN`, or
`INSUFFICIENT_EVIDENCE` and separates blockers from optional or out-of-scope
observations. A clean review with zero findings is valid. Review economy never
downgrades or waives a confirmed correctness, security, accessibility,
resource-ownership, release-safety, or acceptance-criterion violation.

Routine in-scope remediation is autonomous. A broad new concern becomes a
separate task only if the operator chooses it, rather than another broad audit, blind continuation,
or request merely to continue. Related recurring findings stop micro-patching and
trigger a bounded root-cause re-scope. Research children answer bounded questions
and do not act as additional approval reviewers.

For substantial visual work, use `tv-ux-brief` only when direction is unresolved.
The primary captures production composables, checks coverage and supplies exact
paths to `tv-ux-reviewer`; a separate curator is optional. The reviewer accepts
`review` or a targeted `closure` over current evidence.
After `DESIGN_READY`, the primary keeps accepted geometry fixed while wiring
behavior. At most one closure compares named `UX-` findings with matched
captures and does not restart broad redesign. `DESIGN_REMEDIATE` is fixed
autonomously when it violates accepted visual criteria; advisory polish does
not silently expand scope.

Continue automatically through the current slice, internal checkpoints,
recoverable test failures, reviewer findings, child-agent errors, and one batched
technical remediation. Complete a coherent task end-to-end rather than creating
handoffs for administrative stages. Split for a real dependency or context
problem. Ask
one substantive question only when progress requires a product choice, conflicts
with current authority, changes accepted scope or capability, cannot preserve
unrelated worktree changes, crosses an explicit credential/device/signing/release
boundary, or requires a human physical-TV observation. Never ask merely whether
to continue.

TV design evidence is assignment-allowlisted. The assignment must name every
exact current and historical path, capture state, and canvas or device. The
reviewer denies Glob and Bash and may not replace them with directory reads or
searches. Without current images it returns `EVIDENCE_REQUIRED`; it never turns
that gap into a source-only code review. Read remains available only for an exact
supplied implementation path after the image-first critique, so the evidence
prohibition is an explicit reviewer/command policy rather than a dynamic path
control.

Prefer deterministic offline captures of production composables with fake
channels, EPG, tracks, timelines, recovery states, local images, and a
deterministic video backdrop. The writable primary, not the design reviewer,
generates them without a live TVHeadend connection and records scenario, canvas,
density, font scale, locale, and focus state. Generated PNGs remain under ignored
evidence paths and are passed by exact path. They can prove only the captured
static composition; integrated navigation, SurfaceView/video, focus and remote
feel, overscan, HDR, deinterlacing, and motion retain their emulator/device or
human gates.

## Skills and routing

The 17 retained Chris Banes skills are pinned to `chrisbanes/skills` tag
`2026.7.21`; `skills-lock.json` records their paths and content hashes. They are
default mechanics only when their concrete trigger matches. Repository-local
skills provide the TV interaction, TVHeadend DVR, playback, device, and upstream
constraints plus durable caveats for this product.

`implement-issue`, `shepherd`, and `using-chrisbanes-skills` remain excluded
because they can introduce uncontrolled worktree/Git orchestration.
`kotlin-multiplatform-expect-actual` remains excluded because this product is
Android-only. Review upstream changes before updating the imported set, use the
Skills CLI only with `--agent opencode`, and review the full output. The dated AI
skills audit records the original selection; agents do not load it as standing
implementation context.

Precedence is: product and safety specification, repository-local domain overlay,
matching focused imported skill, then existing local style. Focused guidance is
never permission for broad cleanup. OpenCode discovery, not only the lock file,
is the runtime proof that a skill is available.

## Documentation context

OpenCode automatically loads only `AGENTS.md`. Agents use `docs/README.md` to
select domain authority after understanding the task. Device targets, appliance
plans, dated audits, evidence inventories, and archived handoffs are not startup
context.

`tools/check-doc-authority` requires every active root document to be classified,
every archived document to be inventoried, lifecycle status on plans and dated
references, and no handoff/session prompt in active `docs/`. It also rejects
any exact dated or archived document hard-coded into an agent, command, or skill;
this prohibition includes conditional static examples. Assignment arguments,
which are not hard-coded harness context, remain the only way to introduce an
exact historical path.
Use `tools/check-doc-authority` only when changing documentation classification
or archive-containment rules. Routine product verification does not invoke it.

## Device, native, and release boundaries

Physical-device work must load `android-tv-device-testing`, read
`docs/device-targets.md`, and use `./tools/device`. Reachable serials and any
credential-file reference live only in ignored owner configuration. Production
and unclassified targets remain read-only except for an explicitly approved
production-signed update. Credential provisioning is limited to the designated
test-device workflow in `docs/test-device-credential-provisioning.md`.

The accepted Media3/HTSP path and source-matched native extension remain a
regression boundary. Native integrity passing does not clear an unresolved
corresponding-source or provenance warning; signed publication remains blocked
until the release policy's gate passes. The dated Media3 assessment is read only
for that named upgrade decision and remaining physical playback matrix.

## Validation

OpenCode loads and validates its own project configuration when a session
starts. Tool changes use the relevant tests under `tools/tests`; changes to the
verification entry point run the complete tool test suite. The final product
maintenance gate remains:

```bash
./tools/verify
```

OpenCode loads config-time files only at startup. After changing config, an
agent, skill, command, or plugin, quit and restart before evaluating the result.
