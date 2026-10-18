# Documentation archive

Everything below `docs/archive/` is historical. These files preserve rationale,
measurements, review findings, and old implementation sequencing; they are not
current requirements or a work queue. Read one only when an assignment names its
exact path or a current document links it explicitly as historical rationale.

Historical instructions to read other handoffs, use a model, edit a branch, run
a device, or treat old screenshots as authoritative are part of the record and
must not be followed without revalidation against `AGENTS.md`, `docs/README.md`,
current specifications, and current source.

## Handoffs

| Archived document | Current successor |
|---|---|
| `handoffs/claude-opus-5-ux-review-implementation-handoff.md` | `docs/DESIGN.md`, current UI source and tests |
| `handoffs/ui-ux-overhaul-implementation-review-handoff.md` | `docs/DESIGN.md`, current UI source and tests |
| `handoffs/persistent-player-surface-handoff.md` | `docs/appliance-mode-spec.md`, current transition/source tests |
| `handoffs/player-overlay-consolidation-handoff.md` | `docs/DESIGN.md`, current player UI source and tests |
| `handoffs/tv-design-consolidation-handoff.md` | `docs/DESIGN.md` |

## Reports

| Archived document | Status |
|---|---|
| `reports/github-issue-responses.md` | Unposted draft tied to the commit named in the file; not current issue state. |
| `reports/issue-2-iptv-freeze-analysis.md` | Unverified blind hypothesis record; not a confirmed current root cause. |
| `tv-design-spec-2026-09.md` | Superseded long-form TV design specification; the successor is `docs/DESIGN.md`. |
| `persistent-cache-plan.md` | Completed plan for SDK-owned catalog, EPG and picon persistence (defect-ledger D16, D19, D23); archived 2026-10-18. |
| `player-ui-ux-overhaul-plan.md` | Completed implementation record of the fullscreen player overhaul through Slice 8; its interaction contracts are in `docs/DESIGN.md` §10. Archived 2026-10-18. |
| `current-player-ui-ux-2026-07-29.md` | Revision-bound fullscreen-player inventory and screenshot manifest for the overhaul; superseded by current source and `docs/DESIGN.md`. Archived 2026-10-18. |
| `product-architecture-remediation-plan.md` | Completed architecture remediation sequence; the open dependency and quality items moved to `docs/roadmap-1.0.md` (R3, R4, R7, U12). Archived 2026-10-18. |
| `recording-progress-sync-plan.md` | Completed plan for server-synchronized recording progress and resume, including growing recordings. Archived 2026-10-18. |
| `appliance-mode-plan.md` | Completed appliance implementation record; current behavior is in `docs/appliance-mode-spec.md` and dated TCL findings are in `docs/appliance-tcl-findings-2026-07-27.md`. Archived 2026-10-18. |
| `browse-cohesion-proposal.md` | Browse-cohesion design history and evidence; the accepted baseline is in `docs/DESIGN.md` §9. Archived 2026-10-18. |
