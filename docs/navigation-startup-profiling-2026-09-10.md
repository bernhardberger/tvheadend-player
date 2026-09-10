# Main navigation, scrolling, startup and tuning: G10 follow-up

Status: completed bounded measurement follow-up. This does not reopen the
completed P44 report or claim a fix.

## Source and method

Base: `31edcd8dc727416193bd8b23863a2fe2a0e76202`, including the delivered P43
chrome. Additional profiling-only subtree and playback markers distinguish this
work from that baseline. Production navigation, playback policy, SDK dependencies
and decoder selection were not changed. The marker additions are disabled in
ordinary builds.

G10 used the production package, retained configuration, nondebuggable
shell-profileable build and its existing debug signer. Version stayed 0.2.13/code
19. First installation time was unchanged after both in-place diagnostic updates.
No uninstall, data clear, credential export or server change occurred.

| Diagnostic APK | SHA-256 |
|---|---|
| Subtree ownership | `143c414c8cc9f8afef73d3045afcd2ddcd9460caf7744b16cade2a81eb8cce89` |
| Playback/startup markers | `62b6348fe5e3c6d4be0ee2c91c12c781a71fb973d018e2075f77206db320710c` |

Each accepted batch has three runs. Recordings were serial, without builds or
other captures. Private traces retain PID, source, setup and trace-health evidence.
Default ART/JIT was retained; these are not controlled AOT-compilation baselines.
Live catalogue/programme data was not frozen. The 1920×1080 screenshots establish
the captured composition, not physical motion or display latency.

## 1. Main sidebar: expensive destination work

The measured journey alternates Channels and Guide eight times while focus stays
in the main sidebar. This is separate from the player's channel drawer.

On the original profiling build, maximum main-thread measure/layout elapsed time
was **692, 613 and 491 ms** across the three runs. Corresponding scheduled CPU
maxima were **597, 521 and 465 ms**. Thus the large stalls are not just waiting.

A separate diagnostic build attributed synchronous subtree work:

| Owner | Per-run maximum measurement elapsed time |
|---|---|
| Guide content | **784 / 602 / 542 ms** |
| Sidebar itself | **1.12 / 0.90 / 0.64 ms** |
| Channels content | No wrapper invocation / 267 / 182 ms |

These are inclusive intervals and maxima from potentially different calls; do
not add them. The wrapper does not capture independently scheduled descendant
measurements outside its invocation. The extra marker build and changing live
data are separate diagnostic conditions, not an improvement/regression test.

**Attribution:** `SideRail.requestRoute` navigates on focus. `AppRoot` changes the
navigation stack and renders destination content even while the drawer retains
focus. `EpgGridScreen` performs its layout and initial positioning; disabling
initial focus does not disable that work. Four Guide measurement/index visits
were observed per run. Guide indexing alone, at roughly 20–40 ms maximum in the
first batch, does not account for the full stall. Subcomposition and text layout
appear inside the long main layout intervals.

**Next experiment:** avoid repeating expensive Guide construction/layout on
sidebar route changes while preserving the accepted navigation/focus semantics.
First distinguish retained versus newly constructed Guide content. Do not assume
that changing the sidebar highlight animation fixes this cost.

Confidence: high for Guide subtree ownership; incomplete for the best remedy.
The timed route changes start in the expanded sidebar. Opening latency from every
destination and the physical highlight's presentation time were not established.

## 2. Repeated channel scrolling

After a separate rehearsal, each run moved six rows down and six back up, crossing
the visible viewport. The capture helper requested 100 ms sleeps between inputs;
foreground checks and input injection add time, so this is not a physical held-key
repeat-rate measurement. All **36 expected channel-focus callbacks** were observed.

| Metric | Run 1 | Run 2 | Run 3 |
|---|---:|---:|---:|
| Median dispatch-to-focus callback | 4.04 ms | 4.15 ms | 5.39 ms |
| Maximum dispatch-to-focus callback | 16.82 ms | 6.70 ms | 14.39 ms |
| Maximum main measure/layout elapsed | 66.18 ms | 34.74 ms | 32.20 ms |
| Maximum main measure/layout CPU | 40.34 ms | 33.44 ms | 31.92 ms |

Programme lookup markers remained small: maximum 1.01 / 0.09 / 0.05 ms. This
sample does not support blaming those lookups for the main rendering cost. It also
does not certify scrolling smoothness: focus callbacks precede rendering and
physical presentation. Separate list-row versus preview-panel attribution would
be the next useful experiment. Fresh picon loading and large catalogue updates
were not isolated here.

An earlier twelve-key, 400 ms-delay batch missed the recording tail and was
rejected. None of its partial results are included above. Dedicated channel-list
page-key behavior was not established; hardware channel keys were not repurposed
as a guessed paging action.

## 3. Process-cold startup

Three force-stop/relaunch runs retained app data and configuration. The standard
20-second startup diagnostic includes process lifecycle events and uses a separate
128 MiB trace buffer. Each trace passed loss/overwrite checks and its observed PID
matched the installed app process.

| Milestone | Run 1 | Run 2 | Run 3 |
|---|---:|---:|---:|
| Android activity launch (`am start -W` TotalTime) | 825 ms | 843 ms | 870 ms |
| Application binding → first admitted tune | 7.892 s | 8.340 s | 8.056 s |
| Application binding → first-frame callback | 10.377 s | 10.676 s | 9.541 s |
| Bound target → first-frame callback | 1.998 s | 1.773 s | 1.222 s |

Activity launch completion is not usable playback. The callback is not a
key-to-photon measurement and can occur before Media3 `STATE_READY`. Two admission
markers preceded one bound target in every cold run; they must not be silently
treated as two successful tunes or paired as an unambiguous single request.

Before the first admission, main-thread scheduled CPU was **2.80–2.95 seconds**.
Several dispatcher threads were also busy; the busiest used **3.16–4.46 seconds**
of CPU in that interval. JIT activity remained substantial. This was not simply
eight seconds of idle waiting.

A separate sampled-CPU startup diagnostic recorded 3,868 samples, with 3,793
successful unwinds and 75 unwind errors. Pre-admission stacks included HTSP reader
and decoding work, SDK metadata-cache restoration (`loadEpg`), metadata draining
and EPG snapshot construction. Inclusive sample counts overlap and are not
additive CPU durations. This identifies useful investigation paths, not a precise
critical-path split or proof that one cache method causes the delay.

Source gates include profile connection/bootstrap, current catalogue authority
and stream-profile readiness. No explicit eight-second timer was found in Player.
The activity launch metric does not establish when navigation first becomes usable.
**Next experiment:** separately time cache restoration, initial metadata
publication and the readiness gate that releases autoplay. SDK internals remain
owned by the SDK; no optimization was made in this profiling pass.

## Warm return

Three one-second HOME/explicit-Player-return runs retained the same PID. HOME was
sent only from verified Player foreground; no navigation keys were sent to the
Google TV launcher. All three traces passed loss/overwrite and PID checks.
Android reported task return rather than a uniform cold/HOT launch classification,
so command wait times must not be presented as a consistently defined launch or
display-latency metric. Playback rebound in the retained process.
The measured `activityResume` entry to first-frame callback intervals were
**1.567 / 0.919 / 1.011 seconds**. The private foreground-resume path does not emit
the public `tune:admitted` marker; binding and first-frame events still identify
the resumed presentation epoch.

The new markers expose admitted command processing, binding, readiness and the
first-video callback. First-frame timing is emitted before diagnostic SPS parsing.
The observed startup stream was AVC 1280×720 with frame-only SPS coding; reported
frame rate was unknown. Frame-only versus field-capable coding is not certification
of progressive/interlaced programme motion. Source classification remained
`unknown`; it is based only on display labels, not a canonical transport type.

## 4. Operator-identified broadcast tuning matrix

The operator identified channels 1 ORF1 HD, 2 ORF2W HD, 3 ServusTV HD,
4 ATV HD, 7 RTL Austria and 8 VOX Austria as the test set, excluding IPTV.
Broadcast-source identification comes from that operator confirmation, not the
`unknown` source-label marker. Stream formats were measured rather than inferred
from channel names:

| Channels | Observed format at first-frame callback |
|---|---|
| 1, 2 | AVC 1280×720, frame-only SPS coding |
| 3, 4 | AVC 1920×1080, field-capable SPS coding |
| 7, 8 | AVC 720×576, field-capable SPS coding |

Frame rate was unspecified (`-1`) in these Media3 formats. Field-capable SPS coding
supports the operator's interlaced designation; it does not establish picture-by-
picture field use or certify deinterlacing/motion quality.

Each direction has three validated measurements. Each accepted trace contains
the expected input count, one admitted request, one bound target, one matching
first-frame epoch and a corresponding Media3 READY event. PID continuity and
error/loss/overwrite checks passed. No builds or other captures ran concurrently.

Times below start **after admission into serialized tune processing**. They do
not include waiting for that serialization, numeric entry or physical display.

| Direction | Admission → binding | Admission → first-frame callback | Admission → READY |
|---|---:|---:|---:|
| ORF1 → ORF2W (1→2) | 16–28 ms | **0.89–1.22 s** | 2.26–2.43 s |
| ORF2W → ORF1 (2→1) | 22–45 ms | **1.16–1.46 s** | 2.59–4.07 s |
| ORF1 → ATV (1→4) | 25–145 ms | **2.19–3.40 s** | 3.10–4.14 s |
| ATV → ORF1 (4→1) | 21–53 ms | **0.97–1.07 s** | 3.29–3.69 s |
| RTL → VOX (7→8) | 34–210 ms | **0.52–1.66 s** | 2.86–4.62 s |
| VOX → RTL (8→7) | 24–28 ms | **0.45–0.83 s** | 2.30–2.79 s |
| ORF2W → ServusTV (2→3) | 24–49 ms | **1.39–2.60 s** | 2.54–2.76 s |
| ServusTV → ORF2W (3→2) | 16–25 ms | **1.00–1.25 s** | 2.27–4.03 s |

The 1↔2 cohort used one numeric digit with normal timeout completion and one second
of recorder lead-in. Input-to-admission was approximately 1.63–1.65 seconds,
including the intentional numeric timeout. The other accepted cohorts used a
digit followed by explicit OK, three seconds of recorder lead-in, and a separately
rehearsed A→B→A setup. Confirmation-to-admission was roughly 109–188 ms.
These are separate cohorts, not a controlled comparison of entry methods.

Two earlier 1↔4 timeout cohorts were incomplete and excluded from the table: one
recording missed its input marker; another recorded a digit but no tune markers.
The latter does not establish whether numeric admission failed or trace visibility
was incomplete. Those attempts remain preserved, not counted as zero-latency
successes. The explicit-confirmation cohort is reported separately.

**Attribution and next experiment:** target binding was generally a small portion
of first-frame delay. The larger interval followed binding, especially toward ATV.
The current evidence cannot split waiting for suitable compressed media, decoder
initialization, decoding and renderer scheduling. Codec-initialization sections
were not available in the captured trace categories. Add correlated first-media
and decoder-initialized evidence before blaming interlacing or changing buffering.
Channel/transponder/GOP differences and the sample size prevent a format-only
causal claim. READY can follow the first-frame callback by seconds; neither event
alone certifies sustained, correctly rendered physical video.

Confidence: high for the recorded milestones and formats; limited for the cause
of the post-binding delay. The main-sidebar Guide cost remains the clearest
actionable performance finding in this follow-up.

## Repeatability and checks

Use the identity, installation and foreground rules in `profiling.md`. For the
scrolling cadence, after verified entry and a separate rehearsal:

```bash
TVHPLAYER_PROFILE_KEY_DELAY_SECONDS=0.1 bash tools/profiling/capture causal "$DEVICE" at.bernhardberger.tvhplayer "$NEW_PRIVATE_DIR" 3 20 20 20 20 20 20 19 19 19 19 19 19
python3 tools/profiling/analyze.py "$NEW_PRIVATE_DIR" --trace-processor "$TRACE_PROCESSOR" --focus-kind channel --focus-count 12
```

For startup, begin standard Perfetto with `tools/profiling/startup.pbtxt` before
`adb -s "$DEVICE" shell am start -W -n at.bernhardberger.tvhplayer/.ui.MainActivity`.
Record pre/post PID, process-cold versus retained-process setup, launch output,
foreground identity and host load. Run `playback-events.sql`, verify its PID and
reject nonzero error/loss/overwrite statistics before using timestamps. A missing
milestone is missing evidence, not zero latency.

For numeric tuning, use the same startup trace configuration on an already
verified, ready Player, with three seconds of recorder lead-in. Send one digit
for the operator-approved target, then explicit
`KEYCODE_DPAD_CENTER`, guarding Player foreground before each input. Retain the
full 20-second trace. Validate both inputs and one epoch-consistent admission,
binding and first-frame sequence using `playback-events.sql`; do not repurpose
the generic scrolling capture helper's restricted key allowlist. Keep rehearsal
outside recordings and the selected channel pair in the private run metadata.

The capture helper always stops Player afterwards and announces that the
underlying app may become visible. All input is foreground-guarded. No hardware
GUIDE or foreign-app navigation belongs in these journeys.

Focused real-parser tests cover both SPS coding modes and prefix forms, plus
malformed/non-SPS/oversized data. Capture tests reject unsafe delay input before
ADB. `tools/verify`, profileServer assembly and lint passed; independent Astra and
Opus reviews identified no supported blocker after the focused evidence closure.
Enabled trace emission has device evidence, not a dedicated automated stale-frame
or duplicate-callback test. Private raw traces and screenshots are not published.

### Evidence references

These are private batch names, not publicly downloadable traces. Each numbered
batch retains its capture metadata and analysis alongside the recording.

| Report section | Private evidence |
|---|---|
| Main sidebar baseline | `sidebar-channels-guide/run-{1,2,3}.perfetto-trace` |
| Diagnostic subtree ownership | `sidebar-owned-layout/run-{1,2,3}-ownership.csv` and corresponding traces; `owned-layout.sql` |
| Repeated channel scrolling | `channels-fast-scroll/run-{1,2,3}-latency.csv`, `-work.csv` and traces |
| Process-cold startup | `cold-baseline-{1,2,3}.perfetto-trace`, event/CPU CSVs, launch records; `startup-cpu.perfetto-trace` is a separate diagnostic |
| Warm return | `warm-{1,2,3}.perfetto-trace` and same-PID launch records |
| Complete tuning cohorts | `tuning-1-2`, `tuning-1-4-confirm`, `tuning-7-8-confirm`, `tuning-2-3-confirm`; three traces per direction and their validated milestone JSON |
| Excluded numeric-entry attempts | `tuning-1-4`, `tuning-1-4-lead3`; incomplete cohorts, not part of the reported ranges |
