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
| `.opencode/opencode.json` | Project config; selects the Android TV agent, disables sharing and subagent spawning |
| `.opencode/agents/android-tv.md` | Default implementation agent |
| `.opencode/agents/android-reviewer.md` | Directly selectable read-only review agent |
| `.opencode/skills/android-tv-device-testing/` | Safe TCL/ADB/runtime verification workflow |
| `.opencode/skills/tvhstream-upstream-contribution/` | Upstream sync and contribution boundary workflow |
| `.opencode/commands/` | `/verify`, `/device-check`, and `/upstream-review` shortcuts |
| `tools/check-ai-harness` | Static harness/config validation plus OpenCode parser check |
| `tools/verify` | Native/tool/JVM/lint/Android-test compilation, debug assembly, APK identity/ABI, and 16 KB gate |
| `tools/check-native-libs` | Audited AAR hashes, ABI/ELF checks, and a strict release-provenance gate |
| `tools/device` | Role-aware bounded ADB wrapper that blocks production mutations and screenshots, safely provisions designated test devices, and avoids secret-bearing broad dumps |

The project intentionally does not pin an AI provider or model. It inherits the
operator's OpenCode provider configuration while keeping project behavior and
safety rules in Git.

Automatic/background subagents remain disabled for the tracked OpenCode agents.
Codex Ultra mode has a narrow exception in `AGENTS.md` and may use subagents for
bounded parallel work. In OpenCode, get an independent review by switching
directly to `android-reviewer` or running `/upstream-review`.

The harness treats Compose for TV as the focusable UI default, the accepted
Media3/HTSP path as a regression boundary, incomplete native provenance as a
signed-release blocker, and read-only GitHub CI as the only enabled automation
until signing and publication are separately approved.

Dedicated TV UX sections in `AGENTS.md`, `android-tv`, and
`android-reviewer` make Google TV and Android TV design guidance, Compose for TV,
and Material for TV guidance a standing implementation and review gate. Google
TV defines the target product experience; Android TV OS and Compose for TV remain
the platform and implementation APIs. `tools/check-ai-harness` requires those
sections so future harness edits cannot silently remove the focus, ten-foot
readability, safe-area, key-dispatch, accessibility, video-scrim, and physical-TV
validation expectations.

## Local device configuration

OpenCode loads both `AGENTS.md` and `docs/device-targets.md` as project
instructions. The tracked target document identifies device roles but contains
no private address; the ignored local file selects the reachable device.

Copy the tracked example to the ignored local file and set the current ADB
serial. Keep `role` set to `production` for the household TV. Only a separately
assigned development target may use `role: "test"`; test-device mutations also
require exact manufacturer, model, device, and product expectations:

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
