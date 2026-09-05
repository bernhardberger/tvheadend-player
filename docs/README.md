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
| `appliance-mode-spec.md` | Autoplay, HOME/Guide/wake integration, warm playback and Simple TV retirement. |
| `device-targets.md` | Device roles and mutation boundary; physical-device, install, ADB, signing, or deployment work only. |
| `product-identity-plan.md` | Implemented product identity specification; identity, packaging, or public-copy work. |
| `release-process.md` | Release, signing, publication, and rollback policy. |
| `test-device-credential-provisioning.md` | Test-device credential workflow; provisioning work only. |
| `tv-design-spec.md` | Current TV visual and interaction rules; Compose UI or UX work. |

## Active plans

| Document | Scope and lifecycle |
|---|---|
| `appliance-mode-plan.md` | Appliance architecture and task history. Read only the sections relevant to an appliance, channel/EPG/DVR, or playback decision. Split completed history from current work in a dedicated later cleanup. |
| `player-ui-ux-overhaul-plan.md` | Completed implementation record for fullscreen Live TV, timeshift, and recording playback through Slice 8. Slice 9 remains optional and unstarted. |
| `product-architecture-remediation-plan.md` | Active sequence for profile isolation, dead-path removal, orchestration decomposition, and shared product flows. Read only the current package and its invariants. |
| `recording-progress-sync-plan.md` | Recording progress work; slices 0–5 are complete and the remaining slice is explicitly gated. |

## Dated references

| Document | Evidence boundary |
|---|---|
| `ai-skills-audit-2026-07-28.md` | Audit record for the imported skill set. Current agents and local skills carry the durable routing and caveats; this audit is not mandatory startup reading. |
| `codebase-audit-2026-07-23.md` | Point-in-time hardening audit with later updates. Use only for a specifically identified finding, then verify current source. |
| `current-player-ui-ux-2026-07-29.md` | Revision-bound fullscreen-player baseline and exact replacement G10 screenshot manifest used by the completed overhaul. Revalidate every claim against current source. |
| `media3-upgrade-assessment-2026-07-28.md` | Evidence and remaining gate for the named Media3 upgrade only. |
| `tv-design-audit-2026-07-27.md` | Historical rationale for `tv-design-spec.md`; the specification is authoritative. |

## Historical and local material

- `docs/archive/README.md` inventories archived handoffs, reports, and their
  current successors. Do not browse the archive to discover instructions.
- `screenshots/` contains legacy images of unknown revision. Its README defines
  the evidence boundary.
- `captures/` and `artifacts/` are ignored local evidence areas. Only exact paths
  explicitly supplied by an assignment may be treated as current evidence.
- `artifacts/ai-session-notes/` is the ignored location for retained model
  prompts, session handoffs, and completion reports. Do not create those files
  in active `docs/`.

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
