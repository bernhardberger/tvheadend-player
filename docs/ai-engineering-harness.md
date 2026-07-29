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
| `.opencode/agents/android-reviewer.md` | Read-only Android correctness reviewer |
| `.opencode/agents/tv-ux-reviewer.md` | Evidence-scoped, read-only product UX reviewer |
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
- `android-reviewer` and `tv-ux-reviewer` for independent review.

`general` requires user approval plus explicit scope and exclusive file
ownership. Every child resolves to `task: deny`, preventing recursive spawning.
Read-only children may run in parallel; writers, Gradle builds, device operations,
Git mutations, signing, publishing, and release operations may not.

TV UX evidence is assignment-allowlisted. The assignment must name every exact
path it treats as current or declare a source-only UI-change scope. The UX
reviewer denies Glob and may not use directory reads or searches to replace it.
Without supplied current visual evidence it skips the visual pass and does not
infer appearance or physical-TV behavior from source. Glob and Bash discovery
are capability-denied. Read and Grep remain available for scoped source review,
so their prohibition for evidence discovery is an explicit reviewer/command
policy whose required wording—not dynamic path behavior—the harness checks.

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
