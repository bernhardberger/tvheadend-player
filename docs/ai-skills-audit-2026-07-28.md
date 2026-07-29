# AI skills audit

Date: 2026-07-28
Status: dated audit record. Current agents and local skills carry the durable
routing and caveats; this file is not mandatory implementation reading.

## Conclusion

Use the reviewed Chris Banes skills as the default implementation guidance for
their focused Kotlin and Compose concerns. Keep repository-local skills as thin
product overlays for Android TV interaction, TVHeadend DVR semantics, playback
safety, device safety, and upstream contribution boundaries.

Do not adopt the external suite as an unchecked workflow. It contains strong
technical guidance, but some instructions are too broad for this application or
have correctness caveats. Product specifications, repository safety rules, and
the exceptions below take precedence.

## Audit method

- Read all 21 candidate `SKILL.md` files initially selected from
  `chrisbanes/skills` tag `2026.7.21`, including the four removed below.
- Scanned the imported content for Git, network, release, device, worktree, and
  subagent actions.
- Compared its recommendations with `AGENTS.md`, the product and appliance
  specifications, the codebase audit, and representative current code patterns.
- Verified discovery with `OPENCODE_PURE=1 opencode debug skill --pure`, not only
  with the Skills CLI or OpenCode config parser.
- Kept vendor files unmodified and recorded project-specific limitations outside
  them so lock hashes remain meaningful.

## Workflow findings

### High: availability did not guarantee use

OpenCode discovers the imported project skills correctly, but the primary agent
only said to use applicable skills. It provided no mandatory trigger map. This
made the custom checklist more likely to drive work while the stronger focused
guidance remained optional.

Resolution: the primary and reviewer agents must load matching focused skills
before editing or reviewing. The local Android TV skill now defines deterministic
routing and only the product-specific delta.

### Medium: several external instructions require constraints

- `compose-modifier-and-layout-style` requires a modifier API on effectively
  every layout-emitting composable and encourages opportunistic sibling cleanup.
  That conflicts with the repository's minimal-change rule for private one-use
  composables. Apply it to reusable APIs and real caller-placement problems; do
  not create unrelated signature churn.
- `compose-stability-diagnostics` correctly requires compiler/runtime evidence,
  but its remembered callback example keys only on the item ID and can retain an
  old changing callback. Do not remember callbacks unless every changing capture
  participates in identity or current-value handling.
- `kotlin-flow-state-event-modeling` calls a buffered `Channel` an exactly-once
  handoff. It is not durable across process death and can still lose application
  intent across lifecycle boundaries. Use it only for one active consumer when
  lifecycle loss is acceptable; use explicit state or persisted requests for
  product-critical work.
- `compose-state-authoring` describes every bare local `var` in a composable as
  state. A local variable used only to calculate the current composition is
  ordinary Kotlin. State backing is required only when a value must survive or
  invalidate recomposition.
- `compose-focus-navigation` gives useful requester and key guidance, but a
  `LaunchedEffect` focus request is not a complete TV container-entry contract.
  TV surfaces also need `focusRestorer`, semantic identity, lateral-entry
  behavior, and same-event consumption where the product specification requires
  them.
- `kotlin-coroutines-structured-concurrency` is a strong default, but Media3
  `DataSource` methods are synchronous framework boundaries and app-scoped HTSP
  and player owners have explicit close/restart lifecycles. Review these against
  the skill's lifecycle-owner and synchronous-boundary exceptions rather than
  mechanically deleting their scopes or bridges.
- `kotlin-functions` recommends preserving or deprecating public entry points by
  default. This application does not add compatibility shims without a concrete
  external or persisted consumer.

### High: action-bearing workflow skills are not safe defaults

- `shepherd` can drive commits, pushes, rebases, merges, and repeated background
  review/fix loops. That conflicts with user-controlled Git operations and the
  bounded policy requiring approval, explicit scope, and exclusive file
  ownership for writing delegation.
- `implement-issue` assumes Git worktrees, unavailable Superpowers skills, and
  delegated implementation/review sessions. Adapting it would duplicate the
  repository workflow rather than provide focused technical guidance.
- `using-chrisbanes-skills` is a router into `shepherd`, so retaining it would
  reintroduce the excluded workflow indirectly.

All three remain removed. Direct skill routing and the bounded read-only child
allowlist replace their orchestration role without importing their Git or
uncontrolled writing behavior.

### Low: one technical skill is outside the product boundary

`kotlin-multiplatform-expect-actual` is technically sound but irrelevant to the
current Android-only app and could encourage speculative platform abstraction.
It is removed from the installed set. Related links in upstream skill text are
informational and do not change the product boundary.

## Skill decisions

| Skill | Decision | Project use |
|---|---|---|
| `compose-focus-navigation` | Default for focus/key work | Add TV entry/restoration and event-consumption rules from the local overlay |
| `compose-ui-testing-patterns` | Default for Compose tests | Use D-pad input for behavior and interaction-source injection only for visual interaction state |
| `compose-side-effects` | Default for Compose effects | Apply to every changed effect, focus request, listener, event collector, or composition coroutine |
| `compose-state-hoisting` | Default when state ownership changes | Prefer the lowest real owner and avoid a holder for trivial state |
| `compose-state-holder-ui-split` | Default for screen-boundary work | Migrate incrementally when it improves testability; no broad architecture rewrite |
| `kotlin-coroutines-structured-concurrency` | Default for coroutine changes | Preserve explicit process/service lifecycle owners and narrow synchronous framework bridges |
| `kotlin-flow-state-event-modeling` | Default for Flow API changes | Treat Channel delivery as non-durable and preserve product-critical requests as state |
| `compose-modifier-and-layout-style` | Targeted | Reusable components and demonstrated placement/layout problems, not blanket private API churn |
| `compose-slot-api-pattern` | Targeted | Reusable variable visual regions; keep constrained product primitives and one-use UI simple |
| `compose-animations` | Targeted | Add TV focus retention, clipping, readability, and physical motion evidence |
| `compose-state-authoring` | Targeted | Persistent/observable local UI state and read-only contracts, not temporary calculations |
| `compose-state-deferred-reads` | Targeted or diagnostic | Frame-rate state and proven cross-phase back-writing only |
| `compose-recomposition-performance` | Diagnostic router | Require measured recomposition evidence before optimizing |
| `compose-stability-diagnostics` | Diagnostic | Require compiler evidence; reject stale remembered callbacks and false stability promises |
| `kotlin-control-flow` | Targeted | Use for a real branching refactor, not style-only churn |
| `kotlin-functions` | Targeted | Use accurate ownership without speculative compatibility layers |
| `kotlin-types-value-class` | Targeted | Require domain value and contract review; do not wrap IDs merely for style |
| `kotlin-multiplatform-expect-actual` | Removed | Mobile and multiplatform UI are outside the current product scope |
| `implement-issue` | Removed | Assumes worktrees, unavailable workflow skills, and delegated agents |
| `shepherd` | Removed | Can commit, push, rebase, merge, and run automatic review loops |
| `using-chrisbanes-skills` | Removed | Routes tasks into the excluded `shepherd` workflow |

## Required operating model

1. Read the product specification and identify applicable product constraints.
2. Load every focused external skill whose concrete trigger is present before
   editing that concern.
3. Load the local domain overlay when the task involves TV UX, live TV/DVR,
   playback, devices, or upstream boundaries.
4. Resolve conflicts in this order: product/safety specification, audited caveat
   in this document, focused external skill, existing local style.
5. Change only the requested behavior. External style guidance is not permission
   for opportunistic cleanup.
6. Verify with focused tests and repository gates, then separate automated proof
   from physical-TV evidence.
