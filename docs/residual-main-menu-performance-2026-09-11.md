# Residual main-menu work: retained mechanisms and limits

Product-specific P48 engineering checkpoint. The existing multi-channel Guide,
navigation-on-focus, spacing and playback path are preserved. This document does
not declare that all responsiveness budgets or final delivery gates have passed.

## What remains in the implementation

- **Visited destinations:** retain Channels and Guide only after each is visited
  during the current expanded Channels/Guide sidebar visit. A stable scene avoids
  reconstructing Channels on each reversal. Inactive content loses focus and
  semantics, stops measurement after the existing fade, and is released when the
  visit ends. Live observations remain active during retention.
- **Current visible extent:** the browse viewport supplies its current measured
  clipping width. Guide rows omit wholly clipped, non-target cells while retaining
  partial cells, the selected target and the complete navigation/coverage data.
  Cell positions, widths and text breakpoints still use the unchanged full grid
  width. This is separate from the rejected P46 shared-width experiment.
- **Non-UI coverage invocation:** enter the SDK coverage call on `Dispatchers.Default`.
  SDK 0.13.0 takes its metadata monitor before its first suspension. One captured
  Main-thread wait lasted 408.407 ms with only 0.061 ms scheduled CPU. Request
  generation, cancellation and completion remain with the existing UI owner.
- **Index keys and repeated preparation:** use the SDK snapshot's cached content
  hash through its equality contract instead of comparing raw event-list keys;
  share the current channel scope's numbering inputs; reuse timestamp formatting
  with timestamp, locale and zone keys. No frozen SDK model or duplicate repository
  index is introduced.
- **Entry correctness:** initialize an available restored viewport before the
  first lazy-list measurement; reset Guide state only on actual tag/category
  changes; fence stale Channels focus work when the drawer regains focus and use
  the current shared channel selection on re-entry.
- **Bounded packaged profile:** six app-owned method families supplement the
  dependency profiles. There are no plugin, dependency, R8 or native changes.
  APK-only and matching-DM installation conditions require separate evidence.

The background text-executor experiment was removed after failing to establish a
useful improvement. Paused cell-slot construction, prewarm-all and replacement
Guide presentations were not implemented. Full-AOT compilation was diagnostic
only; ordinary later installs restored their recorded compilation conditions.

## Measurement contract and code 28 checkpoint

The observed display period was 16.667 ms. Before candidate scoring, the primary
declared maximum app-owned blocking and event-to-focus/route-intent budgets of
100 ms, a repeated-work target of 50 ms, and 200 ms for an actual route/content
result. The 100 ms long-stall count includes the boundary itself.

The following are separate eight-second first-entry captures of code 28, with
native `cmd input`, requested 400 ms sleeps, foreground checks before every key,
and no Guide rehearsal. Live metadata, compilation and instrumentation overhead
remain conditions of the evidence; this is not a matched repeated acceptance
matrix or a percentage-improvement claim.

| Metric | APK-only `verify / install` | Matching DM `speed-profile / install-dm` |
|---|---:|---:|
| Maximum main frame wall time / scheduled CPU, ms | 1160.823 / 907.019 | 427.181 / 344.329 |
| Main frames at least 100 ms | 14 / 53 | 12 / 115 |
| Maximum owner-layout wall time / scheduled CPU, ms | 1017.680 / 799.523 | 348.266 / 283.287 |
| Owner layouts at least 100 ms | 2 / 29 | 2 / 60 |
| Maximum recomposer wall time / scheduled CPU, ms | 314.335 / 224.095 | 192.412 / 169.982 |
| Recomposer slices at least 100 ms | 12 / 41 | 9 / 87 |
| Down event to Guide intent, ms | 15.872 | 9.220 |
| Up event to Channels intent, ms | 677.886 | 61.803 |
| Down event to last required first-row draw recording, ms | 1748.962 | 773.682 |

Nested slice categories must not be added. Scheduled CPU is intersected with
thread scheduling; frame lifetime is not CPU. Callback and draw-recording times
are not key-to-photon. A root draw can precede lazy child construction and is not
content readiness. Both installation conditions miss declared limits here.

Actual positive physical feedback was subsequently supplied for up/down **main
navigation** on the installed code 28 / 0.2.22 profile-assisted artifact:

- APK: `68405d42f41b5706928342cc50547239cecf5bb25c3ad826eb8e8473b9256457`
- DM: `97d8b79f35f1d5d30e720dff5852e78c4975c6c33f0707a16877bd4e93893525`

That observation belongs to those bytes. It neither transfers to later builds
nor establishes first-entry, rapid-input, drawer-close or complete grid acceptance.
Subjective main-navigation lag is therefore not an unconditional remaining claim.

## Regression evidence and remaining ownership

Focused JVM checks cover coverage-request cancellation/generations, timeline
eligibility and existing channel-number policy. Production profile tests cover
visited lifetime and release, hidden live metadata, nonzero viewport/programme
restoration, initial and early Right entry, stale entry cancellation, and numbering
changes through a filtered scope. Plain UI tests cover LTR/RTL clipping, geometry
during drawer reversal, timestamp updates and the existing Guide rendering cases.

The minimal one-button fixture's early-Right assertion also failed with the
original layout. Its evidence is retained; the geometry test's explicit focus
changes are not presented as key-entry proof. The production Guide early-Right
test supplies that separate proof. Existing physical scope-tab trap restrictions
remain in force.

Independent review found a drawer re-entry viewport defect. A settled-only test
passed, but a frame-stepped control caught a 179 px transient jump before focus's
bring-into-view restored the position. Re-entry now preserves an already visible
row's position; explicit paging retains its existing scroll policy. The corrected
11-test production fixture and seven geometry/rendering tests pass. Review also
removed eager diagnostic-name construction from ordinary builds, shared the
formatting-zone lookup per row on the existing clock, and restored the nested
index-cache source guard. Those corrections are packaged as code 30 / 0.2.24;
their measurement and delivery identity require their own evidence.

The repeated code-30 cohorts exposed a separate cold-entry race: the drawer gained
Channels focus, then pending Channels restoration took it back 9.524 ms later.
Down and Up consequently moved channel rows instead of switching destinations.
That trace is a failed navigation scenario despite passing transport/trace health.
Code 31 / 0.2.25 reads the existing TV Material drawer state before scrolling and
again before the deferred focus request. This closes the interval before
composition's `drawerActive` feedback updates; the composition and generation
guards remain in place. It changes no explicit paging or Guide presentation rule.

A focused control reproduces the theft with a stale composition flag and live
open-drawer state. It passes with the correction, as do the 11 affected production
navigation tests. Five failures in the older `ChannelsScreenTest` suite also
reproduce when the P47 Channels focus logic is restored. That control establishes
neither their product cause nor an all-pass baseline. Their evidence is retained
for the existing central focus/paging triage; no scope-tab trap repair is included.
Code-30 review and verification do not certify code 31. Its exact-source checks,
bounded reviews, higher-version install and cold-first remeasurement are separate
evidence under the private `candidate31` and `live-drawer31` names.

All six code-31 cold-first runs preserved the ordered sidebar destinations, but
both compilation conditions still missed the declared budgets. APK-only maximum
owner layout was 1292.372 ms (maximum scheduled CPU 1004.329 ms); normal APK+DM
maximum owner layout was 388.978 ms (272.377 ms CPU). Fast route callbacks did not
establish complete content: the profile-assisted last-required-row draw lower
bounds were 441.919, 800.806 and 566.468 ms. One default Guide result was still
incomplete before reversal. The private `candidate31-matrix.md` retains all six
runs, inclusive 100 ms counts, ordered results and the remaining evidence limits.

The bounded review also found the same deferred-focus hazard in Channels' empty
recovery surface. A failing offline control demonstrated that its action button
could take drawer focus. Code 32 / 0.2.26 applies the same live-drawer check after
the frame wait. Its focused test verifies both preserving drawer focus and normal
action focus after the drawer closes. This does not alter the populated-content
measurement path or resolve the numeric budget misses.

Remaining cost includes initial Compose construction/text/layout and large
SDK-derived lookups. The SDK metadata lock itself remains a library concern.
Retention focus boundaries, current-measure clipping and default versus
profile-assisted delivery need continued regression coverage. Later architecture
or presentation decisions remain centrally owned.

Private evidence is preserved under `.local/backlog/p48-menu-evidence/`: named
APKs and DMs, source patches, trace directories, build/instrumentation logs,
`checkpoint-2026-09-11.md` and `keep-remove-2026-09-11.md`. These checkpoint results
do not substitute for the exact final source, repeated delivery cohorts, reviews,
owning gate, CI and installed-identity evidence required for delivery.
