# Mechanical Player feedback verification

This product-specific slice retains the P35 programme timeline. Its mechanical
corrections were delivered as 0.2.5 (code 11) with SDK 0.10.1 / HTSP 0.9.0.
Version 0.2.6 (code 12) retains them and adopts public SDK 0.11.0 / HTSP 0.10.0.
These are debug acceptance builds, not public releases or physical-TV acceptance.

## Demonstrated corrections

- G10-17: outgoing numeric content retains its digits and background together
  through fade-out. Empty input no longer shrinks the outgoing surface. Confirm
  and both timeout paths still use the existing numeric admission and tune code.
- G10-20: position sampling rejects a suspended result that crosses a seek
  dispatch/settlement, Go Live or source-generation boundary. Samples are not requested
  during a position-changing command. A fresh lower position remains admissible; direction alone does
  not establish staleness. Neither command acceptance, reader acknowledgement,
  preview expiry nor timeout is promoted to confirmed displayed position.
- G10-19: normal D-pad seeking sends the same signed step as reduced seeking to
  the existing queue. That queue alone clamps against its selected history,
  rather than normal mode pre-clamping before queue admission. Hidden normal
  controls reject these keys. Reduced preview now exposes command feedback,
  including uncertain outcomes, visually and through accessibility semantics.
  If a command rejects stacked input, only the actually dispatched request stays
  as an outcome preview for the existing 950 ms interval; discarded input is not
  presented as completed playback, and a dismissed preview is not restored.
  A new command clears the previous outcome label. Initial no-op input at either
  history boundary does not create a preview; held-key acceleration retains focus.

The normal preview previously switched from its target to the latest sampled
position after a fixed feedback interval. That permits an apparent UI rollback
without proving playback reversed. The sample fence fixes a provable app ordering
race, not all possible causes of the field observation. The last admitted sample
remains an estimate. The SDK provides no sample-to-command sequence or rendered
frame acknowledgement with which to classify a sample requested after settlement
as cached pre-seek content versus genuinely newer backward playback evidence.
No monotonic-position filter, new coordinate model or SDK change was introduced.

## G10-18 disposition

**Unreproduced; deferred without a speculative production correction.** The
bounded investigation began at 2026-09-08 16:33 UTC with a 45-minute ceiling.
The deterministic offline matrix completed successfully within that budget:

- Production `VideoPlayerScreen`, `VideoPlayerViewModel`, `ChannelsViewModel`,
  tag settings, numeric key handling and real app/SDK target admission.
- A-B-A using centre confirmation, the 1500 ms one/two-digit timeout and the
  250 ms three-digit timeout, each with all channels and an active filtered tag.
- A third untagged channel verifies that the tagged case is actually filtered.
- Published fake session and the existing controlled player; no live server,
  TV credentials, decoder or rendered-video claim.

All six cases passed. Source tracing confirms numeric lookup uses the current
tag-scoped channel list, with server numbers preferred and ordinal fallback only
when every visible channel lacks a number. A channel outside that scope is not a
valid numeric target. The observed intermittent physical failure remains
unattributed: these fixtures do not reproduce remote timing, live catalog changes
or real playback failure. No numeric lookup, lifecycle, audio or autoplay behavior
was changed to guess at those causes.

## Published dependency adoption

The same-outcome consumer correction adopts SDK 0.11.0 from source
`80a1433302e3624b41eb57d52781b93a9812e5c9` and transitive HTSP 0.10.0 from
`a32af6157a3c29fe6e54fa732eb32927664dbea9`. The public SDK release manifest has
SHA-256 `efe88f3ccb5d514f61bf0843022f2a2a208d8345987e24340898c77cdf842570`.
The native checker pins its exact source classifiers. SDK Media3 AAR, decoder
payloads and FFmpeg corresponding-source bytes are unchanged from 0.10.1.

The app is recompiled for the queue constructor/copy ABI changes. It does not
consume `errorCount` or introduce a diagnostics UI; absent counts are not
converted to zero or conflated with frame/client drops. Producer wire, nullable
unsigned-count, signature and publication evidence is reused, not reimplemented.
Actual app dependency-graph, build and affected offline checks remain required.
The installed 0.2.5 artifact is retained rather than relabelled or overwritten.

The recompiled app passed its exact public dependency-graph check and focused
timeline JVM tests. Offline consumer checks passed: diagnostics 4/4, numeric
A-B-A 6/6 and command consistency 3/3. The diagnostics test's existing ambiguous
single-node assertions were corrected to require both displayed Source labels
and both equal signal measurements; no production diagnostics UI changed.

## Evidence

- Focused JVM timeline tests cover pre-seek suspended samples, source replacement,
  same-segment history advancement, fresh backward estimates, uncertain outcomes,
  command ownership and repeated commands at both observed buffer boundaries.
- Offline `ChannelNumberOverlayTest`: 2 passed; visible, exiting and absent
  captures show coherent numeric/background exit without size collapse.
- Offline `TimeshiftCommandConsistencyTest`: 3 passed across focused runs; normal
  signed steps, hidden input rejection and accessible reduced uncertain-result
  feedback, including a suspended seek followed by stacked input and timeout.
- Offline `NumericChannelReturnTest`: 6 passed.
- Existing offline `ProgrammeWindowInputTest`: 10 passed, retaining P35 input,
  target expiry, delayed result and font-scaled behavior.
- Isolated LXC119 identity: `emulator-5556`, `gf2_google_tv_api36_ps16k`, API 36,
  x86_64, 16384-byte pages, 1920x1080 at 320 dpi. Production composables rendered
  over synthetic offline imagery; numeric captures use English, 1x font scale.
- Generated screenshots and delivery evidence remain ignored under
  `captures/p36-a1/`; final artifact provenance is recorded with the debug bundle.

No physical G10 tests or human acceptance are inferred from these checks.
Video visibility, motion, overscan and physical remote feel remain unclaimed.
