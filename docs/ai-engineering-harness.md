# AI engineering harness

The current LXC 106 checkout is:

```text
/root/projects/tvhstream
```

Add that directory as the project root in OpenCode or OpenChamber. OpenChamber
uses the same OpenCode backend, so both interfaces see the same Git worktree,
project instructions, agents, skills, and commands.

## What is tracked

| Path | Purpose |
|---|---|
| `AGENTS.md` | Project-wide engineering, safety, testing, Git, and upstream rules |
| `docs/device-targets.md` | Current development-versus-production TV boundary, without private addresses |
| `.opencode/opencode.json` | Project config; selects the Android TV agent, disables generic Build and sharing, and limits delegation to one capability-gated child level |
| `.opencode/agents/android-tv.md` | Default implementation agent |
| `.opencode/agents/repo-maintainer.md` | Repository harness, tooling, CI, documentation, licensing, and release-policy maintainer |
| `.opencode/agents/android-reviewer.md` | Directly selectable read-only review agent |
| `.opencode/agents/tv-ux-reviewer.md` | Screenshot-first product UX and Material for TV reviewer |
| `.opencode/agents/quick-explore.md` | Read-only exact lookup child that escalates ambiguous or consequential investigation |
| `.opencode/agents/scout.md` | Read-only repository and external-documentation research child |
| `.agents/skills/` | Reviewed, project-local Kotlin and Compose skills imported from `chrisbanes/skills` |
| `skills-lock.json` | Imported skill source, `2026.7.21` ref, paths, and content hashes |
| `.opencode/skills/android-tv-compose-ux/` | Product TV UI, D-pad, focus, Back, accessibility, and evidence workflow |
| `.opencode/skills/live-tv-dvr-conventions/` | Channel, EPG, recordings, DVR action, and context-restoration workflow |
| `.opencode/skills/media3-htsp-playback-safety/` | Playback baseline, native coupling, and device regression workflow |
| `.opencode/skills/android-tv-device-testing/` | Safe TCL/ADB/runtime verification workflow |
| `.opencode/skills/tvhstream-upstream-contribution/` | Upstream sync and contribution boundary workflow |
| `.opencode/commands/` | Verification, device, direct reviewer, TV UX, and upstream-review shortcuts |
| `docs/ai-skills-audit-2026-07-28.md` | Per-skill decisions, external-guidance caveats, and mandatory routing model |
| `docs/media3-upgrade-assessment-2026-07-28.md` | Evidence and execution gate for the deferred Media3 1.10.1 candidate |
| `tools/check-ai-harness` | Static harness/config validation plus live OpenCode parser and skill-discovery checks |
| `tools/verify` | Native/tool/JVM/lint/Android-test compilation, debug assembly, APK identity/ABI, and 16 KB gate |
| `tools/check-native-libs` | Audited AAR hashes, ABI/ELF checks, and a strict release-provenance gate |
| `tools/device` | Role-aware bounded ADB wrapper that blocks production mutations and screenshots, safely provisions designated test devices, and avoids secret-bearing broad dumps |

The project intentionally does not pin an AI provider or model. It inherits the
operator's OpenCode provider configuration while keeping project behavior and
safety rules in Git.

`android-tv` is the default and the only application implementation primary for
this repository. `repo-maintainer` is the separate full-tool primary for the AI
harness, repository tools, CI, documentation infrastructure, licensing, and
release-policy mechanics; it must hand application Kotlin, Compose, playback,
EPG, DVR, and appliance behavior to `android-tv`.

The generic built-in `build` agent is disabled because it receives project
instructions but neither primary agent's mandatory scope and routing prompt.
Setting a default alone is insufficient: existing sessions can retain an
explicitly selected primary agent across restarts.

Delegation is capability-based and one level deep. The top-level Task policy
denies every child first, then allows automatic spawning only for read-only
children. `quick-explore` handles exact low-consequence lookups and must escalate
ambiguous, multi-hop, or completeness-sensitive work to `explore`. `scout`
handles bounded source research; `android-reviewer` and `tv-ux-reviewer` provide
independent review. `general` requires user approval; after approval, every
writing assignment needs an explicit scope and exclusive file ownership. Every
permitted child has an effective `task: deny`, so recursive spawning remains
prohibited.

Parallel read-only exploration and research are allowed. Parallel writers must
not modify the same dirty worktree, and agents must not run concurrent Gradle
builds, device operations, Git mutations, signing, publishing, or release
operations. `/android-review`, `/tv-ux-review`, and `/upstream-review` create
isolated child sessions. Direct agent selection remains available.

The imported Chris Banes suite is intentionally selective. Its 17 retained
skills are the default implementation guidance for matching Kotlin, coroutine,
Flow, Compose state, layout, focus, performance, animation, API-design, and
UI-testing concerns. Agents must route to concrete matching skills before edits;
repository-local skills add TVHeadend product constraints instead of replacing
that guidance. `docs/ai-skills-audit-2026-07-28.md` records every decision and
the few instructions that require a project-specific caveat.

`implement-issue` and `shepherd` are excluded because they assume
subagent/worktree execution or can drive commits, pushes, rebases, and merges.
`using-chrisbanes-skills` is excluded because its router points to the excluded
shepherd workflow. `kotlin-multiplatform-expect-actual` is excluded because this
is an Android-only product and speculative platform abstraction conflicts with
the current product boundary.

The imported source is pinned to the `2026.7.21` tag in `skills-lock.json`.
Review upstream changes before updating it; do not install `*` over the reviewed
set without repeating the action-bearing-instruction audit. Run Skills CLI
updates only with `--agent opencode`, then review its output and complete diff;
the generic `skills check` command can create integrations for unrelated agents.

The imported files are unmodified Apache-2.0 material. `NOTICE.md` records their
source and points to the retained license text.

The operating precedence is product and safety specifications, audited external
skill caveats, the matching focused Chris Banes skill, then existing local style.
The audit does not permit broad cleanup: apply external guidance only to the
requested concern. OpenCode discovery is validated with `opencode debug skill`,
not inferred from the Skills CLI lock file.

The harness treats Compose for TV as the focusable UI default, the accepted
Media3/HTSP path as a regression boundary, incomplete native provenance as a
signed-release blocker, and read-only GitHub CI as the only enabled automation
until signing and publication are separately approved.

The 2026-07-28 Media3 assessment keeps `1.9.2` as the accepted baseline and
identifies stable `1.10.1` as a future dedicated compatibility candidate. A
temporary forced compile passed, but no catalog or native artifact was changed;
source-matched native rebuilding and the full physical playback matrix remain
mandatory before adoption.

Dedicated TV UX sections in `AGENTS.md`, `android-tv`, `android-reviewer`, and
`tv-ux-reviewer` make Google TV and Android TV design guidance, Compose for TV,
and Material for TV guidance a standing implementation and review gate. Google
TV defines the target product experience; Android TV OS and Compose for TV remain
the platform and implementation APIs. `tools/check-ai-harness` requires those
sections so future harness edits cannot silently remove the focus, ten-foot
readability, safe-area, key-dispatch, accessibility, video-scrim, evidence, and
physical-TV validation expectations.

## Local device configuration

OpenCode loads both `AGENTS.md` and `docs/device-targets.md` as project
instructions. The tracked target document identifies device roles but contains
no private address; the ignored local file selects the reachable device.

Copy the tracked example to the ignored local file and set the current ADB
serial. The example deliberately defaults to `role: "production"`. Change it to
`role: "test"` only for the assigned dining-room G10 development target and set
its exact manufacturer, model, device, and product expectations; keep the
bedroom G08 at `role: "production"`:

```bash
cp .tvhplayer-device.example.json .tvhplayer-device.json
```

The same value can be supplied without a file:

```bash
export TVHPLAYER_ADB_SERIAL='<adb-serial>'
```

An environment or command-line serial does not override the role policy from
the local file. `doctor`, `current`, and `package-info` are available for
production or unclassified targets. Debug install, launch, force-stop, smoke,
and synthetic-key operations are rejected unless the configured role is
`test` and all four live identity properties match the local expectations.

The device file contains no TVHeadend credential values. For a designated test
device only, it may name an ignored owner-only `credential_file`; the bounded
`provision-test-credentials` command streams that file over stdin to a debug-only
app-private importer after role and identity validation. Production and
unclassified devices are always rejected. See
`docs/test-device-credential-provisioning.md` for setup and cleanup.

Test-device screenshots use
`./tools/device screenshot --confirm-safe-screen`, default to an owner-only PNG
under `/tmp`, and reject repository output paths. Never run that command while a
credential or other secret-bearing screen is visible.

## Validation

```bash
./tools/check-ai-harness
./tools/verify
./tools/device doctor
```

OpenCode loads project configuration only at startup. After changing
`opencode.json`, an agent, a skill, or a command, quit and restart the OpenCode
session before evaluating the new harness.
