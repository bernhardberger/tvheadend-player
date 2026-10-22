# Documentation authority

Use this index to select the smallest document set for a task. Do **not** read
the whole `docs/` tree. Repository presence, a recent date, or words such as
`current` and `handoff` do not make a document authoritative.

## Authority classes

- **Normative** — current requirements or operating policy for the stated
  domain. Keep these aligned with accepted behavior.
- **Active plan** — approved or pending work within a bounded scope. It may
  contain history; the matching normative specification and current source win
  if they conflict.
- **Dated reference** — point-in-time evidence or a decision record. Read it
  only when the task names that evidence or decision.
- **Historical archive** — preserved context under `docs/archive/`. Never use it
  as current instructions unless the assignment names its exact path.
- **Local evidence/session material** — ignored owner-only artifacts. Their
  presence never establishes currency or authority.

## Normative and operational documents

| Document | Authority and when to read it |
|---|---|
| `ai-engineering-harness.md` | Current AI-harness architecture and operation; harness work only. |
| `code-ownership.md` | Compact source and tooling ownership map; read before locating application code or delegating broad repository mapping. |
| `client-module.md` | UI-free `:client` library ownership, dependency rules and placement rule; read before adding runtime, persistence or domain code. |
| `appliance-mode-spec.md` | Autoplay, HOME/Guide/wake integration, warm playback, TCL firmware behavior and Simple TV retirement. |
| `device-targets.md` | Device roles and mutation boundary; physical-device, install, ADB, signing, or deployment work only. |
| `android-tooling.md` | Build/install/capture conventions and lane selection: Notebook032 interactive preview, LXC119 automated capture, physical TV acceptance; Android CLI and R8 limits. |
| `notebook-tv-preview.md` | Operational Notebook032 Android TV preview runbook: reuse/start, fullscreen keyboard control, verified APK updates, captures and ownership. Read for interactive UI iteration. |
| `profiling.md` | Baseline profile generation, offline/configured-server profiling, optional R8 arm, guarded sustained traces, timestamped video and physical-TV limits. |
| `product-identity-plan.md` | Implemented product identity specification; identity, packaging, or public-copy work. |
| `release-process.md` | Release, signing, publication, and rollback policy. |
| `test-device-credential-provisioning.md` | Test-device credential workflow; provisioning work only. |
| `DESIGN.md` | Current TV visual and interaction rules; Compose UI or UX work. |

## Active plans

| Document | Scope and lifecycle |
|---|---|
| `roadmap-1.0.md` | Internal, preliminary working plan (not a promise): milestone order from the current Player to a signed 1.0; links the plans that own each step. Archive at 1.0. |
| `ux-inventory-1.0.md` | Per-screen UX inventory (current state, unfinished parts, nits, gaps, questions) feeding the roadmap's UX rework packages. Owner-corrected 2026-10-12; per-screen questions remain open. |
| `programme-window-prototype.md` | Programme-window Player prototype behavior, interaction checks and current experiment boundaries. |
| `defect-ledger.md` | Open and closed defects from the post-SDK-refactor player stabilisation. Read when working on live-player performance or interaction defects; close rows only with the named evidence. |

## Dated references

| Document | Evidence boundary |
|---|---|
| `responsive-ui-implementation-record.md` | Planning context, experiments, rejected evidence and deployments through 0.2.53. Historical next steps are not current assignments; use the active plan. |
| `responsive-ui-phase1-results.md` | Initial qualified G10 R8 comparison, compiler workaround and recorded-highlight evidence. Later outcomes are in the implementation record. |
| `profiling-results-2026-09-10.md` | Bounded G10 and emulator journey timings, source attribution, rejected captures and measurement limits; not motion acceptance or an optimization mandate. |
| `navigation-startup-profiling-2026-09-10.md` | G10 main-sidebar subtree attribution, repeated channel scrolling, cold/warm startup and operator-identified broadcast tuning; measurements and next experiments, not fixes. |
| `sidebar-guide-performance-2026-09-10.md` | P46 Guide reconstruction attribution, bounded sidebar retention correction, matched G10 measurements and regression evidence; not cold-entry or universal motion acceptance. |
| `mechanical-feedback-verification.md` | Dated debug verification for the 0.2.5/0.2.6 mechanical-feedback slice; not a current task or physical acceptance result. |
| `g10-field-feedback-2026-09-08.md` | Bounded G10-05/09/15 and configured-state corrections, G10-08/02 triage, and static evidence limits. Original operator observations are preserved separately. |
| `ai-skills-audit-2026-07-28.md` | Audit record for the imported skill set. Current agents and local skills carry the durable routing and caveats; this audit is not mandatory startup reading. |
| `codebase-audit-2026-07-23.md` | Point-in-time hardening audit with later updates. Use only for a specifically identified finding, then verify current source. |
| `media3-upgrade-assessment-2026-07-28.md` | Evidence and remaining gate for the named Media3 upgrade only; the coupled-path list is outdated (see its note). |
| `residual-main-menu-performance-2026-09-11.md` | P48 checkpoint: mechanisms retained for the main-menu sidebar and their limits; not an acceptance result. |
| `appliance-tcl-findings-2026-07-27.md` | July 2026 TCL bring-up observations: accessibility service and wake, HOME priority, initial channel list, and the G08 intermittent render mode (roadmap P6). |
| `android-cli-qualification-2026-09-09.md` | Bounded 2026-09-09 qualification of the official Android CLI and the executed emulator evidence; the lasting limits are in `android-tooling.md`. |
| `profiling-p38-static-rail-2026-09-09.md` | Earlier static-rail profiling fixture, its tool qualification and first measurements; not current journey acceptance. |
| `guide-performance-baseline-2026-10-22.md` | G10 frame timings of the Guide before the U5 rework (E7 baseline); measurements only, no budgets. |
| `tv-design-audit-2026-07-27.md` | Historical rationale for `DESIGN.md`; the specification is authoritative. |
| `archive/tv-design-spec-2026-09.md` | Superseded long-form specification; historical only. |

## Historical and local material

- `docs/archive/README.md` inventories archived handoffs, reports, completed
  plans and their current successors. Do not browse the archive to discover
  instructions.
- `captures/` and `artifacts/` are ignored local evidence areas. Only exact paths
  explicitly supplied by an assignment may be treated as current evidence.
- `artifacts/ai-session-notes/` is the ignored location for retained model
  prompts, session handoffs, and completion reports. Do not create those files
  in active `docs/`.

## Writing documentation

Documentation stays in this repository as Markdown; there is no separate
documentation site or toolchain.

- [`README.md`](../README.md) is the entry point for people who use the app: what
  it is, requirements, features, which APK to install, privacy, and license.
- [`CONTRIBUTING.md`](../CONTRIBUTING.md) is for people who build or contribute:
  the technical overview, the Play and Appliance builds, building from source,
  release integrity, the engineering harness, and upstream contributions.
- [`CHANGELOG.md`](../CHANGELOG.md) records user-visible changes per release;
  GitHub release notes are taken from its section for that version.
- [`privacy-policy.md`](../privacy-policy.md) describes what the app stores and
  sends; change it in the same commit as any change to stored or sent data.
- Specifications and guides live in `docs/` and are listed in this index.

Check every claim against the code on the same branch; documentation never
promises behavior the app does not have. The `grounded-writing` skill in
`.agents/skills/` supports README, changelog, release-note and guide prose. Code
comments and KDoc follow the surrounding code instead.

## Document lifecycle

1. Put durable accepted behavior in a normative specification, not a handoff.
2. Give every active plan an explicit status, scope, evidence/base revision, and
   closure or revalidation condition.
3. On completion, update the normative document and current tests first, then
   archive the plan or handoff. Do not leave completion narratives as standing
   implementation instructions.
4. Keep transient session context in the final session response or the ignored
   local session-notes area.
5. Never make an archive, screenshot inventory, dated audit, or local session
   note an unconditional agent/skill read.
