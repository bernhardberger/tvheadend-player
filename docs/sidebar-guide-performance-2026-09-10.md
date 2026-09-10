# Sidebar Guide reconstruction: measured correction

Dated P46 evidence, based on `e360616f82b33067540853642e9c6ec2ec711bbd`.
Scope: product-specific Channels/Guide sidebar destination lifetime. This is a
repeated-navigation improvement, not cold-start or universal motion acceptance.

## Cause and correction

`SideRail.requestRoute` commits on focus. `AppNavigation.navigateTopLevel`
retains/reorders navigation keys, but the default Navigation 3 single-pane scene
disposes the old destination's composition after its transition. Saved entries
and ViewModels do not retain the Guide layout tree.

The profiling-only `ProfileCompositionLifetime("guide")` marker and
`tools/profiling/guide-lifetime.sql` distinguish this from retained-tree
invalidation: each of twelve measured Guide returns had a new committed entry,
one expensive Guide measure, then disposal before the next return. Guide index
construction accompanies entry; its duration alone does not explain the stall.
The dominant repeated work is reconstruction of the Guide subtree, including
lazy-row subcomposition and text measurement.

An initial shared-width experiment removed repeated row `BoxWithConstraints`
subcomposition. It did not consistently improve measured work and was reverted.
Its private evidence remains available.

`SidebarGuideScene` now retains only an already-visited Guide during the current
expanded Channels/Guide sidebar visit. Channels continues its normal lifetime.
Closing on Channels or leaving the pair releases the Guide; closing on Guide
keeps its current composition. Hidden Guide content cannot receive focus, has
cleared semantics, and is not measured or placed after the existing 150 ms fade.
Live observations continue through the normal screen owners. No frozen index,
focus debounce, OK-to-navigate change, spacing change or new animation masks work.

## Matched G10 comparison

Three runs per build; eight app Down/Up dispatches per run, alternating
Channels/Guide four times while the expanded sidebar owns focus. Same launch and
Channels setup, then Left/Down/Up rehearsal before recording. Same guarded
eight-second Perfetto configuration, 400 ms requested sleeps plus foreground
query/injection overhead, 64 MiB service buffer, 8 MiB per CPU, 50 ms drain.
Every key required actual Player foreground; all six comparison traces passed
health, PID continuity and input-count checks. No concurrent owner builds or
captures. Capture cleanup stopped only Player.

G10: TCL G10/G10_4K_GB, Android 12, ARMv7. Display override 1920×1080 on a
3840×2160 panel, density 320, `en-GB`; font-scale setting returned `null`
(no explicit override established). Nondebuggable, shell-profileable
`profileServer`, production UID, existing debug certificate. Default installed
ART/JIT state, no forced compilation or profile reset. Sequential warm-up trends
and live metadata changes remain confounders; this is a small repeated cohort,
not a stationary benchmark or statistical significance claim.

| Run | Before max owner layout ms | After max owner layout ms | Before max scheduled CPU ms | After max scheduled CPU ms | Before total owner CPU ms | After total owner CPU ms |
|---|---:|---:|---:|---:|---:|---:|
| 1 | 649.817 | 430.779 | 591.702 | 357.885 | 2412.568 | 1537.964 |
| 2 | 616.555 | 280.546 | 507.089 | 258.316 | 2676.805 | 1321.439 |
| 3 | 463.839 | 238.966 | 418.764 | 226.461 | 2465.980 | 1175.735 |

The layout maximum falls 34–55% in these run-position comparisons; total scheduled
CPU inside non-nested main-owner layout sections falls 36–52%. Before: four Guide
entries and four Guide measures in each run. After rehearsal: zero new Guide
entries and zero Guide measures in each timed run; Guide placement still occurs.
Remaining maxima include Channels reconstruction. Initial Guide construction is
outside both timed cohorts, so this does not claim to accelerate first entry.
CPU columns intersect the relevant slices with scheduled thread time. Neither
frame lifetime nor callback latency is used as CPU or key-to-photon latency.

The earlier 692/613/491 ms owner maxima and separate 784/602/542 ms Guide-marker
maxima are historical attribution evidence, not this before/after denominator.
Rejected shared-width owner maxima were 715/541/515 ms.

| Artifact | Version | SHA-256 |
|---|---|---|
| Matched lifetime baseline | 0.2.14 / 20 | `d24de96e32e0ce715090f837e00414080d7ef89798902119256b6af9f2949a2e` |
| Rejected shared width | 0.2.15 / 21 | `ce0bf2b3326021c9a4903f6cff64ad7e1c958783c7a4ebc6252a5a68f8ff1de4` |
| Measured retained Guide | 0.2.16 / 22 | `f04b473e7f57a78ddc3c397e43a0c51c3a963d301f5457f0a65934bb5ee8bd1d` |
| Final corrected retention | 0.2.17 / 23 | `3def96e1e6c490e11c0880dfd8c5797c7becc6013e5c5acf7287b7bae70e2f69` |

Each named G10 artifact matched installed bytes and the existing certificate;
first-install time stayed `2026-09-05 17:15:35`. Configured playback and browsing
remained available without credential provisioning or data clearing. The first
retention compile failed on Navigation 3's private entry key; an accidental
reinstall of the old code-21 APK was identified by its hash, excluded from
retention measurements, and followed by the successful code-22 build/install.

The final code-23 confirmation used the same three-run input/capture protocol and
default compilation state. All three traces passed health, PID and eight-input
checks. Owner-layout maxima were **280.421 / 271.471 / 244.807 ms**, maximum
scheduled CPU **260.597 / 234.884 / 224.783 ms**, and total owner-layout CPU
**1339.438 / 1199.657 / 1125.282 ms**. Again there were zero new Guide entries or
Guide measures after rehearsal. This later confirmation crossed into a new
programme hour with updated live metadata; it is not substituted for the matched
code-20/code-22 denominator above. Code 23 changes the leave-pair eligibility
reset and handles an empty gated entry; the alternating-pair lifetime is unchanged.
Its in-place G10 update retained the certificate, original install date and
configured playback/browsing. A setup return to the launcher was corrected by
explicit Player relaunch before rehearsal; no keys were sent to the launcher.

## Regression evidence and limits

The isolated LXC119 fixture now uses the production sidebar, Navigation 3
decorators and scene strategy with public SDK fake observations. Five final tests
passed with explicit runner status, `OK (5 tests)` and final code `-1`:

- Initial Right entry reaches the selected Guide scope tab.
- Repeated sidebar focus navigation; EPG title publication while Guide is hidden
  without focus theft; latest programme content on return; later time-window and
  channel navigation; Back/sidebar alternation and restoration of the same
  programme focus; closing on Channels restores the shared selected channel;
  a later Guide visit increments the composition-entry count, proving release.
- Guide → Recordings → Back to Channels does not reconstruct an invisible Guide;
  an explicit Guide visit does construct it again.
- Right during the Guide exit fade enters the selected Channels row.
- Existing fixture recreation/catalog-session binding and exactly-once shutdown.

An attempted cold programme-entry/post-release focus assertion reproduced the
existing scope-tab trap with the scene strategy disabled as well. That control
failure is preserved. Programme focus after cold reconstruction is **not** claimed
as fixed; the passing restoration assertion covers the retained visit after a
metadata update. No tag-trap repair is included. This does not waive that separate
correctness issue or establish physical remote feel.

Final relevant `tools/verify` passed (133 tool tests, static rules, JVM tests,
debug lint/assembly, Android-test compilation, SDK/native integrity). An initial
attempt failed in an existing process-cleanup test because `/proc/.../stat`
disappeared between existence/read; the unchanged-state retry passed. Profile
assembly, profile instrumentation assembly and profile lint passed separately;
profileServer assembly/lint passed for the measured app source.

Independent initial review found stale retention eligibility after leaving the
pair; it is reset on that boundary and covered by the composition-count test.
The initial Opus packet became stale when the primary began that correction
before its review finished. Its frozen-state objection is accepted: final tests,
verification, measurement and a fresh frozen review checkpoint supersede that
packet. The empty-entry measure guard and stronger release/focus assertions also
address its bounded findings. Earlier test expectations incorrectly assumed
channel 1 after Guide browsing; the final assertion follows the shared channel
identity, rather than weakening it to “some focus”.

## Repeatable private evidence

Ignored owner-only root: `.local/backlog/p46-sidebar-evidence/`.
`lifetime-baseline/`, `shared-width/`, `retained-guide/`, `final-retained/` preserve traces,
capture metadata, analyzer health/input tables and `run-*-lifetime.csv`.
`lifetime-source.patch`, `shared-width-source.patch`, the named APKs, build logs,
`instrumentation-control.log`, `instrumentation-accepted.log`, and
`verify-final-retry.log` preserve attribution and rejected work. Final evidence is
`identity-execution.log`, `instrumentation-identity.log`, `verify-delivery.log`,
`final-correction-build.log`, `final-server-23.apk` and the final cohort. Prior
`.local/backlog/followup-profiling-evidence/` is untouched.

Repeat with the authorized explicit device and starting focus using
`tools/profiling/capture causal <serial> at.bernhardberger.tvhplayer <private-dir>
3 20 19 20 19 20 19 20 19`, then the existing analyzer and
`trace_processor_shell <trace> -q tools/profiling/guide-lifetime.sql`.
No raw traces, device addresses, credentials or screenshots are published.
