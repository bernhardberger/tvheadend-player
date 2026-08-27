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
| `.opencode/opencode.json` | Default agent, disabled generic Build, model assignments, step budgets, sharing policy, and one-level child allowlist |
| `.opencode/agents/android-tv.md` | Writable Sol/high application primary |
| `.opencode/agents/repo-maintainer.md` | Harness, tooling, CI, documentation, licensing, and release-policy primary |
| `.opencode/agents/android-reviewer.md` | Read-only combined Android runtime and TV interaction reviewer |
| `.opencode/agents/tv-ux-reviewer.md` | Screenshot-first, read-only product design and visual-quality reviewer |
| `.opencode/agents/scout.md` | Bounded repository and external-documentation research child |
| `.agents/skills/` | Reviewed, pinned Kotlin and Compose implementation guidance |
| `.opencode/skills/` | TVHeadend product, playback, device, DVR, and upstream overlays |
| `.opencode/commands/` | Model-tier, verification, device, reviewer, UX, and upstream shortcuts |
| `skills-lock.json` / `NOTICE.md` | Imported skill source, hashes, license, and attribution |
| `tools/check-ai-harness` | Config, agent, skill, command, permission, safety, and live OpenCode validation |
| `tools/check-doc-authority` | Documentation classification, archive containment, and stale-context prevention |
| `tools/ai-model-tier` | Checked switch of the four managed roles between standard and fast service tiers |
| `tools/verify` | AI-harness, native/tool/JVM/lint/Android-test compilation, APK, identity, ABI, and 16 KB gates |
| `tools/check-native-libs` | Native AAR integrity, ABI/ELF, corresponding-source, and release-provenance gate |
| `tools/device` | Role-aware bounded ADB wrapper |

## Primary agents and model tier

`android-tv` is the default writable application primary. It owns planning,
authority, implementation, verification, risk-based review, device gates, and
acceptance for one bounded slice. It does routine source exploration itself and
does not delegate application writing. `repo-maintainer` owns repository
infrastructure. The generic built-in `build` agent is disabled.

The four managed roles are deliberately small: application work uses Sol/high,
Scout research uses Sol/medium, combined Android code review uses Sol/high, and
screenshot-first design review uses Sol/medium. They use standard service by
default and may be switched together to matching `-fast` IDs. Fast IDs select
service priority, not a different model. `repo-maintainer` inherits the
operator-selected model.

OpenCode cannot hot-reload model assignments for later Task calls. Use the
checked repository tool or matching slash command, then restart OpenCode:

```bash
./tools/ai-model-tier status
./tools/ai-model-tier fast
./tools/ai-model-tier standard
```

## Delegation and context containment

Delegation is one level deep and read-only. The project Task policy denies every
child first, then permits only:

- `scout` for a bounded multi-hop repository or external-source question when
  isolating research context is materially useful;
- `android-reviewer` for combined runtime, cross-layer, focus, key, Back,
  accessibility, safe-bounds, and test-truth review; and
- `tv-ux-reviewer` for independent screenshot-first design review.

Routine lookup remains in the primary session. Children cannot edit, run a
device, mutate Git, or spawn another child. Start every research, audit, or
closure as a fresh session without `task_id`; never resume old child history.
Supply only the exact contract, accepted invariants, included paths, exclusions,
relevant evidence, and stop condition. The worktree carries implementation
state; transcripts do not belong in handoffs.

Step budgets bound a single runaway call: the application primary uses 128,
Scout 48, the combined code reviewer 64, and the UX reviewer 40. These are
ceilings, not targets. A role that cannot truthfully finish within its budget
returns `HANDOFF_REQUIRED` with established state and the smallest fresh-session
contract instead of claiming complete or pass.

Only the application primary writes application files. Read-only review begins
after its delta is stable. Writers, Gradle builds, device operations, Git
mutations, signing, publishing, and release operations may not overlap.

## Review lifecycle and autonomous continuation

Independent review supplements focused tests and primary-agent self-review; it
does not replace either and is not an automatic gate for every edit. Select it by
risk:

| Change | Independent review |
|---|---|
| Documentation, tests, or mechanical work with no production behavior change | Normally none |
| Runtime behavior, production wiring, concurrency, playback, security, native, release, focus, keys, Back, accessibility, safe bounds, or UI-test behavior | `android-reviewer` |
| Visual hierarchy, alignment, spacing, typography, density, focus appearance, consistency, or Material for TV design | `tv-ux-reviewer` against supplied current images |
| HTSP, Media3, concurrency, subscription ownership, or DVR lifecycle | One early architecture/race audit may replace the normal audit, followed by closure review |

Every code-review assignment declares an exact slice, acceptance criteria,
included paths, exclusions, an owning reviewer, and either `audit` or `closure`
mode. An `audit` is one broad defect-discovery pass over that stable scope. A
`closure` verifies named finding IDs, regressions introduced by their fixes, and
the delta since the audit; it is not another audit of unchanged code or adjacent
architecture. Audit and closure are separate fresh reviewer sessions. Closure
receives only named finding IDs, regressions introduced by their fixes, and the
fix delta.

The primary batches and fixes blocking code findings directly before closure.
The reviewer classifies results as `PASS`, `REMEDIATE`, `ADVISORY`,
`HUMAN_DECISION_REQUIRED`, or `HANDOFF_REQUIRED` and separates blocking findings
from optional improvements, pre-existing issues, and physical gates. A blocking
finding needs a stable `AND-` ID, evidence, the violated invariant or acceptance
criterion, and a closure condition. A clean review with zero findings is valid;
the reviewer never fills a quota. Review economy never downgrades or waives a
confirmed correctness, security, accessibility, resource-ownership,
release-safety, or acceptance-criterion violation.

Routine remediation is autonomous. `REMEDIATE` creates a bounded remediation
sub-slice with a reproducing test where practical, focused checks, and targeted
closure. A new blocker found during closure does not start another broad audit or
ask the user whether to continue. If related findings recur, stop micro-patching,
state the subsystem invariants, perform one root-cause audit of that defect
family, batch the correction, and request one fresh targeted closure. `ADVISORY` items are
recorded without blocking the current acceptance criteria. Scouts answer bounded
research questions and do not act as additional approval reviewers.

Visual design uses a separate evidence lifecycle. `tv-ux-reviewer` accepts
`brief`, `review`, and `closure` modes. A fresh brief turns named baseline images
and accepted requirements into one preferred visual direction. For a substantial
visual or navigation slice, the primary then renders the production composable
through deterministic fake-state evidence before broad player/state wiring. One
fresh review judges that stable composition boundary. After `DESIGN_READY`, the
primary keeps accepted geometry fixed while wiring behavior. At most one fresh
closure compares named `UX-` findings with matched captures and does not restart
broad redesign. `DESIGN_REMEDIATE` is fixed autonomously when it violates
accepted visual criteria; advisory polish does not silently expand scope.

Continue automatically through the current slice, internal checkpoints,
recoverable test failures, reviewer findings, child-agent errors, and one batched
technical remediation. Run at most one substantial visual or navigation slice
per primary session. Once it reaches acceptance or a named external gate, return
a compact next-slice handoff rather than carrying accumulated context onward. Ask
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

For a prompt-only harness change, use `./tools/check-ai-harness` as the focused
iteration gate. For a config-time change, also validate the effective config in
a fresh isolated OpenCode process. Tool changes use the relevant tests under
`tools/tests`; changes to the verification entry point run the complete tool
test suite. The final maintenance gate remains:

```bash
./tools/verify
```

`tools/verify` runs the documentation authority gate through
`tools/check-ai-harness` and invokes that harness checker exactly once.

OpenCode loads config-time files only at startup. After changing config, an
agent, skill, command, or plugin, quit and restart before evaluating the result.
