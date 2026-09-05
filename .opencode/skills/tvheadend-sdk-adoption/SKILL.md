---
name: tvheadend-sdk-adoption
description: Use when adopting a TVHeadend SDK version or API in Player, replacing app-owned protocol or playback logic with SDK behavior, or diagnosing a defect across the app/SDK boundary. Not a general Compose or SDK-internal implementation guide.
---

# Adopt SDK behavior without duplicating it

Use `docs/code-ownership.md` to locate the affected app boundary and `AGENTS.md`
for current engineering rules. Start from the requested behavior and the SDK
contract actually resolved by the app, not a remembered version or stale plan.

1. Identify the dependency coordinate/source and relevant public API change.
   Inspect release notes or authorized SDK source where necessary. Distinguish
   released dependencies from local substitution; never claim one proves the
   other. Read only the relevant ownership and lifecycle contracts.
2. Trace the affected app call, observation and teardown path. Keep Player,
   service, navigation, timing and presentation policy in the app; use SDK-owned
   protocol, repository and playback semantics rather than reconstructing them
   from UI labels, timers, low-level errors or mirrored state machines.
3. Replace the affected adapter/call sites and remove only the obsolete code
   made redundant by this change. Preserve profile isolation, command ordering,
   cancellation and stale-result fencing where affected. Do not combine a
   dependency adoption with an unrelated UI redesign or native-decoder upgrade.
4. Exercise the observable app behavior using SDK-boundary fakes. A compiling
   call site alone does not establish correct lifecycle or error handling.
   Add focused regressions for changed behavior, not tests of version strings.
5. Run relevant checks and the required final application gate. Use playback or
   device guidance only for affected runtime behavior; reuse unchanged passing
   evidence. Verify required dependency provenance without bypassing its checks.

If the necessary behavior is absent or defective in the SDK, report the exact
contract gap and evidence to the existing central owner. Do not edit a sibling
repository or launch another worker without authority. The central
`tvheadend-outcome-handoff` skill can route an authorized cross-repository outcome;
it is not a prerequisite for an already admitted in-scope migration.

Finish with the adopted behavior, resolved dependency evidence, verification and
any remaining runtime limitation. Credential, device, push and release operations
retain their separate authorization boundaries.
