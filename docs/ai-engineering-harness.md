# AI engineering harness

OpenCode and OpenChamber use the same repository-local harness from the project
root:

```text
/root/projects/tvhstream
```

## Tracked architecture

| Path | Purpose |
|---|---|
| `AGENTS.md` | Sole automatic project instruction; concise safety, routing, and workflow floor |
| `docs/README.md` | Documentation authority and lifecycle index used for task-specific reads |
| `docs/archive/README.md` | Historical-document containment and successor map |
| `.opencode/opencode.json` | Default agent, disabled generic Build, model assignments, sharing policy, and one-level child allowlist |
| `.opencode/agents/android-tv.md` | Application implementation primary |
| `.opencode/agents/repo-maintainer.md` | Harness, tooling, CI, documentation, licensing, and release-policy primary |
| `.opencode/agents/android-reviewer.md` | Read-only Android runtime and cross-layer correctness reviewer |
| `.opencode/agents/tv-interaction-reviewer.md` | Read-only TV interaction code reviewer for focus, keys, Back, accessibility, and UI tests |
| `.opencode/agents/tv-ux-reviewer.md` | Screenshot-first, read-only product design and visual-quality reviewer |
| `.opencode/agents/quick-explore.md` | Exact low-consequence repository lookup child |
| `.opencode/agents/scout.md` | Bounded repository and external-documentation research child |
| `.agents/skills/` | Reviewed, pinned Kotlin and Compose implementation guidance |
| `.opencode/skills/` | TVHeadend product, playback, device, DVR, and upstream overlays |
| `.opencode/commands/` | Model-tier, verification, device, reviewer, UX, and upstream shortcuts |
| `skills-lock.json` / `NOTICE.md` | Imported skill source, hashes, license, and attribution |
| `tools/check-ai-harness` | Config, agent, skill, command, permission, safety, and live OpenCode validation |
| `tools/check-doc-authority` | Documentation classification, archive containment, and stale-context prevention |
| `tools/ai-model-tier` | Checked child-agent switch between matching standard and fast service tiers |
| `tools/verify` | Native/tool/JVM/lint/Android-test compilation, APK, identity, ABI, and 16 KB gates |
| `tools/check-native-libs` | Native AAR integrity, ABI/ELF, corresponding-source, and release-provenance gate |
| `tools/device` | Role-aware bounded ADB wrapper |

## Primary agents and model tier

`android-tv` is the default and only application implementation primary.
`repo-maintainer` owns repository infrastructure and must not implement Kotlin,
Compose, playback, EPG, DVR, or appliance behavior. The generic built-in `build`
agent is disabled so a retained primary selection cannot bypass those scopes.

Primary agents inherit the operator's OpenCode model. While subscription capacity
is available, child agents are temporarily pinned to matching OpenAI
`gpt-5.6-*-fast` service-tier models in `.opencode/opencode.json`: Luna/low for
exact lookup, Terra/medium for exploration and research, Sol/high for reviewers,
and approval-gated Sol/medium for General. The fast IDs select service priority,
not a less capable model.

OpenCode cannot hot-reload model assignments for later Task calls. Use the
checked repository tool or matching slash command, then restart OpenCode:

```bash
./tools/ai-model-tier status
./tools/ai-model-tier fast
./tools/ai-model-tier standard
```

## Delegation and evidence containment

Delegation is capability-based and one level deep. The project policy denies
every child first, then allows these read-only children:

- `quick-explore` for exact, low-consequence lookup;
- `explore` for architecture, multi-hop tracing, or completeness;
- `scout` for bounded repository or external-source research;
- `android-reviewer` for Android runtime and cross-layer code correctness;
- `tv-interaction-reviewer` for TV interaction code correctness; and
- `tv-ux-reviewer` for independent screenshot-first product design review.

`general` requires user approval plus explicit scope and exclusive file
ownership. Every child resolves to `task: deny`, preventing recursive spawning.
Read-only children may run in parallel; writers, Gradle builds, device operations,
Git mutations, signing, publishing, and release operations may not.

## Review lifecycle and autonomous continuation

Independent review supplements focused tests and primary-agent self-review; it
does not replace either and is not an automatic gate for every edit. Select it by
risk:

| Change | Independent review |
|---|---|
| Documentation, tests, or mechanical work with no production behavior change | Normally none |
| Runtime behavior, production wiring, concurrency, playback, security, native, or release invariant | `android-reviewer` |
| Compose for TV focus, keys, Back, accessibility, safe bounds, or UI-test behavior | `tv-interaction-reviewer` |
| A slice crossing runtime and TV interaction code | Both code reviewers once, in parallel, with non-overlapping scopes |
| Visual hierarchy, alignment, spacing, typography, density, focus appearance, consistency, or Material for TV design | `tv-ux-reviewer` against supplied current images |
| HTSP, Media3, concurrency, subscription ownership, or DVR lifecycle | One early architecture/race audit may replace the normal audit, followed by closure review |

Every code-review assignment declares an exact slice, acceptance criteria,
included paths, exclusions, an owning reviewer, and either `audit` or `closure`
mode. An `audit` is one broad defect-discovery pass over that stable scope. A
`closure` verifies named finding IDs, regressions introduced by their fixes, and
the delta since the audit; it is not another audit of unchanged code or adjacent
architecture. The two code reviewers may run together only when their assignments
name distinct concerns and paths or symbols. The primary deduplicates a genuine
cross-boundary finding and sends closure only to its owner.

The primary batches blocking code fixes before closure. Code reviewers classify
results as `PASS`, `REMEDIATE`, `ADVISORY`, or `HUMAN_DECISION_REQUIRED` and
separate blocking findings from optional improvements, pre-existing issues, and
physical gates. A blocking finding needs a stable ID, evidence, the violated
invariant or acceptance criterion, and a closure condition. A clean review with
zero findings is valid; reviewers never fill a quota. Android findings use
`AND-` IDs and interaction findings use `TVI-` IDs so closure ownership remains
unambiguous. Review economy never downgrades or waives a confirmed correctness,
security, accessibility, resource-ownership, release-safety, or
acceptance-criterion violation.

Routine remediation is autonomous. `REMEDIATE` creates a bounded remediation
sub-slice with a reproducing test where practical, focused checks, and targeted
closure. A new blocker found during closure does not start another broad audit or
ask the user whether to continue. If related findings recur, stop micro-patching,
state the subsystem invariants, perform one root-cause audit of that defect
family, batch the correction, and resume targeted closure. `ADVISORY` items are
recorded without blocking the current acceptance criteria. Scouts answer bounded
research questions and do not act as additional approval reviewers.

Visual design uses a separate evidence lifecycle. `tv-ux-reviewer` accepts
`brief`, `review`, and `closure` modes. A brief turns baseline images and accepted
requirements into one preferred visual direction before implementation. Review
judges one stable current evidence set after implementation. Closure compares
named `UX-` findings with matched updated captures and does not restart broad
redesign. `DESIGN_REMEDIATE` is fixed autonomously when it violates accepted
visual criteria; `DESIGN_READY` proceeds; advisory polish does not silently
expand scope. Routine spacing, typography, hierarchy, component, and composition
judgments belong to the reviewer, not to a non-designer user.

Continue automatically through planned slices, internal checkpoints, recoverable
test failures, reviewer findings, child-agent errors, and in-scope technical
remediation. A checkpoint records scope, acceptance status, tests, finding
dispositions, and the next action, then proceeds without user confirmation. Ask
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
prohibition is an explicit reviewer/command policy whose required wording—not
dynamic path behavior—the harness checks.

Prefer deterministic offline captures of production composables with fake
channels, EPG, tracks, timelines, recovery states, local images, and a
deterministic video backdrop. The primary, not the read-only design reviewer,
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
`tools/check-ai-harness` invokes that gate and validates the effective OpenCode
permissions and evidence-review boundary without requiring shared prose to be
copied across prompts.

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

```bash
./tools/check-doc-authority
./tools/check-ai-harness
./tools/verify
```

OpenCode loads config-time files only at startup. After changing config, an
agent, skill, command, or plugin, quit and restart before evaluating the result.
