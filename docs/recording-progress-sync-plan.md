# Implementation plan: server-synchronised recording progress and resume

**Status:** Slices 0–5 implemented 2026-07-29; Slice 6 remains gated pending
the disposable growing-recording spike and physical-TV evidence described below.
**Classification:** Generic DVR/playback foundation. Any product presentation is
separate from appliance behavior.
**Scope boundary:** This plan does not add a Home **Continue watching** rail or
otherwise change the current Home work. It covers TVHeadend-backed recording
resume, watched state, and the minimum recordings/player UI needed to make that
behavior understandable.

This plan extends the recording architecture in
`docs/appliance-mode-plan.md` without changing the accepted Media3/HTSP
extractor, renderer, decoder, or live-TV playback path.

## Outcome

- A recording resumes from the latest usable position stored by TVHeadend, even
  when that position was written by another compatible client.
- TVHeadend receives bounded progress checkpoints while this app plays a
  recording.
- Reaching the completion boundary clears the resume point and marks the
  recording watched without inflating `playcount` on every Media3 reopen or
  seek.
- Playback remains usable when progress synchronization is unsupported, denied,
  disconnected, or temporarily failing.
- The server remains the durable authority. The app does not create a competing
  persisted progress database or replay stale writes after a later session.

## Explicit non-goals

- Home recommendations, a **Continue watching** Home section, or Home card
  redesign.
- Cross-server or cloud synchronization.
- Exact distributed conflict resolution; HTSP provides no revision, compare-and-
  set, or client ownership token for DVR progress.
- Treating `playcount` as analytics or guaranteeing a precise count of every
  replay.
- Changing recording transport from authenticated HTSP file access to HTTP.
- Altering the custom extractor, stream readers, renderer selection, native
  decoder AAR, or live/timeshift behavior.

## Current baseline and protocol facts

The app already parses and retains `DvrEntry.playPosition` and `playCount` in
`DvrRepository`, including partial `dvrEntryUpdate` messages. Only `playCount`
is currently shown in recording metadata. Neither value controls playback, and
the app has no explicit `updateDvrEntry` progress request.

Recording playback uses `HtspRecordingDataSource` and
`fileOpen`/`fileRead`/`fileSeek`/`fileClose`. Media3 may close and reopen the
data source while probing or seeking, so an HTSP file close is not equivalent to
the user completing a recording.

TVHeadend's relevant semantics are:

- `playposition` is an unsigned whole-second recording position.
- `updateDvrEntry` accepts `id`, `playposition`, and `playcount` and returns
  `success=1` or an error response.
- Actual changes are broadcast to authorized async-metadata clients as
  `dvrEntryUpdate` messages.
- `playcount=0` resets watched state, `1` sets watched only when the current
  value is zero, `INT32_MAX-1` keeps the current value, and `INT32_MAX`
  increments it on servers implementing the HTSP 27 behavior.
- With negotiated HTSP 27 or later, a bare DVR `fileClose` increments
  `playcount`; sending a non-increment command suppresses that side effect and
  `fileClose` can also carry `playposition`.
- Stable TVHeadend 4.2.2 through 4.2.8 negotiate HTSP 27 or later. TVHeadend
  4.2.1 negotiates HTSP 26 and increments a DVR play count at file open; that
  older behavior cannot be safely suppressed by a close field.

The current app always sends a bare `fileClose`. On HTSP 27+ this can therefore
increment the server count for extractor-driven closes and seeks. Correcting
that existing side effect is the first slice and must remain separate from the
resume feature.

## Proposed product policy

### Server authority and units

- Treat TVHeadend's position as seconds at the repository/protocol boundary and
  Media3's position as milliseconds inside playback.
- Convert by flooring non-negative Media3 milliseconds to whole seconds so a
  checkpoint never resumes ahead of the last rendered position.
- Reject negative, overflowing, or duration-inconsistent positions. Do not infer
  a usable media duration from file size.
- Do not optimistically mutate `DvrRepository`. A successful request means the
  server accepted the command; shared DVR state still changes through the
  authoritative async update or the next full snapshot.

### Resume decision

Recommended first defaults:

- Ignore resume positions below **180 seconds**. Brief previews start from the
  beginning and do not create a persistent resume point.
- Auto-resume only when the recording is playable and the stored position is
  below the completion boundary.
- When an exact Media3 duration is known, reject a position at or beyond that
  duration. Schedule duration and file size are sanity evidence, not substitutes
  for the media timeline.
- A nonzero `playCount` does not by itself disable resume. A previously watched
  recording can acquire a new resume point during a partial rewatch.
- Direct playback entry uses the eligible server resume point by default.
  Recording details expose **Resume from …** as the primary action and **Play
  from beginning** as the secondary action when a resume point is eligible.
- **Play from beginning** is an explicit reset epoch. It may clear the shared
  resume point after playback has actually started; merely focusing or opening
  details never writes server state.

Completed recordings with a known Media3 timeline are the first supported
slice. Resume for growing recordings and recordings whose duration remains
unknown is enabled only after a focused spike proves that the initial seek is
stable and does not mistake temporary growing-file EOF for completion.

### Completion and watched state

Recommended conservative completion rule:

- A completed DVR entry is complete when Media3 reaches `STATE_ENDED`, or when a
  final session checkpoint is being written and both of these are true:
  - at least **95%** of the known Media3 duration has been reached; and
  - no more than **5 minutes** remain.
- A DVR entry still in `RECORDING` state is never marked complete merely because
  Media3 reaches the current end of a growing file.
- A terminal playback/read error is never completion, even inside the threshold.
  Its final flush preserves the last stable nonzero position and does not set
  watched, so a failed playback cannot activate removal-after-playback policy.
- Ordinary periodic, seek, and pause checkpoints do not clear position or mark
  watched merely because playback crosses the threshold; playback continues
  normally. The near-end position is ignored as a resume point if the process
  dies before a final checkpoint.
- At completion, write `playposition=0` and the idempotent TVHeadend
  **set-watched** command (`playcount=1`). This preserves an existing count above
  zero and avoids duplicate increments after ambiguous timeout/retry paths.
- The first implementation treats `playcount` as watched state, not an exact
  replay counter. Deliberately counting every completed rewatch would require a
  separate policy because increment is non-idempotent and HTSP has no
  compare-and-set operation.
- Existing server counts are never normalized or reduced. They may already
  include closes performed by this or other legacy clients.

The conservative threshold matters because a nonzero play count can activate a
TVHeadend DVR profile's removal-after-playback policy. Validation must include a
disposable recording under a profile whose removal behavior is known; household
recordings must not be used without explicit approval.

### Checkpoint timing

Progress ownership belongs to the active recording session, not a composable's
250 ms display loop.

- Start no periodic write until the initial resume/start-over decision has been
  applied and playback has reached a stable state.
- Suppress every nonzero periodic, seek, pause, and final checkpoint below the
  180-second resume floor. The only below-floor write is an explicit
  start-over/completion clear to zero.
- While actively playing, evaluate once per second and write at most once every
  **30 seconds**.
- Skip a periodic write unless the whole-second checkpoint changed by at least
  **10 seconds** from the last successfully accepted checkpoint.
- After a user seek, debounce for **2 seconds**, then checkpoint the settled
  position. Rapid seek steps coalesce into one write just as they coalesce into
  one Media3 seek.
- Flush promptly when playback pauses.
- Perform one bounded final flush before serialized teardown on explicit Stop,
  replacement by live TV or another recording, activity background stop, player
  release, or a terminal recording error.
- Natural completion writes the completion state immediately and at most once
  per playback generation.
- Closing only the recording route with Back is not a final boundary while the
  warm recording session continues behind browse UI.
- A final progress request gets a short local-LAN timeout budget (target **2
  seconds**) and must use `disconnectOnTimeout=false`. Progress persistence must
  never tear down otherwise healthy playback or the shared HTSP connection.

Thirty-second checkpointing bounds ordinary process-death loss without turning
the HTSP control socket into a high-frequency telemetry channel.

Natural end and any final checkpoint classified as complete use one serialized
end transaction: latch the generation as ending, discard queued non-completion
checkpoints, attempt the bounded `playposition=0`/set-watched write, and suppress
every later final/periodic position write for that generation. On natural end,
publish `PlaybackSessionState.Finished` only after that attempt so automatic Stop
cannot restore the near-end resume position. Crossing the threshold during
ongoing playback does not itself finish or navigate away from the player.

## Multi-client reconciliation

HTSP progress is shared last-write-wins state. The app must be deterministic and
honest about that limitation:

1. At playback start, use the latest published server snapshot.
2. Once playback starts, remote updates never jump or seek the active player.
3. Track the last server value observed, last value submitted, last accepted
   value, and whether local playback has advanced or explicitly sought since the
   latest remote update.
4. If a remote update arrives and the local session has not changed since its
   previous checkpoint, adopt the remote value as the new baseline and do not
   overwrite it merely because a timer fired.
5. If the user is actively watching or has explicitly sought, the next local
   checkpoint may win. Concurrent active clients can still replace each other's
   positions; the client must not claim conflict-free merge semantics.
6. Never replay a pending write after the playback generation ends or after a
   process restart. This prevents an old client session from overwriting newer
   server progress minutes or hours later.
7. Coalesce to one in-flight request and one latest pending checkpoint per active
   recording. Older queued positions are discarded.
8. Completion uses idempotent set-watched rather than increment, eliminating the
   most harmful play-count retry race.

Backward seeking is valid local playback intent. The first slice may persist the
settled lower position from the active session; it must not invent a monotonic
"furthest watched" model unless that becomes an explicit product decision.

## HTSP compatibility and capability states

Gate behavior on the negotiated HTSP number already published by
`ConnectionState.Connected`, never on a server version string.

| Condition | Resume read | Progress write | File-close behavior | User outcome |
|---|---|---|---|---|
| HTSP 27+ and DVR write access | Yes | Full | Send explicit `KEEP` on every DVR close | Resume and server sync enabled |
| HTSP 27+ with read but no DVR write access | Yes | No | Send `KEEP` where accepted | Playback can use the existing server point; explain once that new progress will not be saved |
| HTSP 19-26 | Compatibility spike only | Not claimed | Legacy server may increment on open/close and cannot honor safe close suppression | Preserve recording playback, default to start-over until legacy behavior is explicitly validated; do not claim synchronized progress |
| Disconnected or transient request failure | Current session continues | Latest in-memory checkpoint may retry while the same generation remains active | Teardown still proceeds | Playback continues; server retains last accepted checkpoint |
| Permission denied or unsupported method | Existing playback remains available | Disable at the narrowest proven scope | No retry storm | Stable read-only/unsupported state, not a playback failure |

Do not raise the whole application's current minimum HTSP version merely for
this feature. Full progress synchronization is a per-connection capability.

## Failure behavior

- Progress RPC failures are never mapped to `PlaybackSessionState.Failed` and do
  not trigger live-playback recovery.
- Transient failures retain only the latest checkpoint in process memory and use
  **30/60/120/300-second** bounded backoff while the same recording generation
  remains active. Success clears the degraded state.
- Authenticated `dvr=false`, an unsupported method, or an unsupported negotiated
  version disables progress writes for the connection. An ownership or
  entry-specific denial disables progress only for that recording generation;
  it must not change the existing connection-wide recording-action capability
  or hide valid record/delete actions for other entries.
- Caller or generation cancellation is always rethrown. Only the operation's own
  narrowly scoped timeout becomes a typed progress result; a broad
  `CancellationException` must never be converted into rejection or connection
  failure.
- An ambiguous timeout must not retry a non-idempotent play-count increment. The
  proposed set-watched operation is safe to retry. Repeating the same position
  is idempotent only when no other client wrote an intervening value, so a retry
  must first pass the active-generation reconciliation policy.
- If final persistence fails, teardown and navigation still complete. A later
  launch resumes from the last server-accepted checkpoint, not from a locally
  fabricated value.
- Show at most one concise, non-blocking **Playback progress couldn't be saved**
  state when controls are visible. Do not place raw errors, server details,
  paths, identifiers, or credentials in UI, diagnostics, or logs.
- No durable offline queue is created. Offline replay would be stale by design
  in a multi-client system.

## Target ownership and data flow

### Pure policy

Add a JVM-testable recording progress policy beside the existing recording
playback policies. It decides:

- server-seconds/Media3-milliseconds conversion;
- resume eligibility and start-over;
- completion classification;
- checkpoint coalescing and minimum delta;
- generation-safe completion-once behavior; and
- remote-baseline versus locally dirty reconciliation.

The policy must not depend on Android, Compose, ExoPlayer, or sockets.

### HTSP and repository

- Extend the recording-specific close path so HTSP 27+ closes send
  `playcount=KEEP`. Do not add these DVR fields to picon or unrelated file
  closes.
- Add a typed `updateDvrEntry` progress operation through `DvrRepository` with a
  result that distinguishes accepted, permission denied, unsupported, timeout,
  disconnected, and rejected outcomes.
- Send position and watched state in one request where both change.
- Keep async `dvrEntryUpdate` as shared-state authority; do not manufacture an
  updated `DvrEntry` after request success.

### Player session

- Replace the recording ID-only session marker with a generation-scoped active
  recording context containing the initial server fields and selected start
  mode.
- Apply the initial resume point through an explicit paused preparation phase:
  prepare with autoplay held, await a usable timeline/duration, validate and
  apply the server position, fall back deterministically to zero when it remains
  unknown or invalid, then enable autoplay. Replacing the playback generation or
  failing preparation while waiting cancels this phase without a stale seek.
- Own the sampler, seek/pause signals, latest pending checkpoint, in-flight
  write, and final flush inside `PlayerSession` or a small plain coordinator it
  owns. The coordinator has one authoritative serialized execution context.
  Media3 position reads occur on the player thread and enter that owner as
  immutable events; filtered `dvrEntryUpdate` values for the active recording
  enter through the same event boundary. Do not share mutable coordinator state
  between the repository IO scope and player callbacks.
- Cancel and discard all pending work before a newer playback generation can
  write under the old recording ID.
- Keep player command serialization and data-source teardown order intact.

### UI boundary

- `RecordingPlayerScreen` renders sync state and sends user intent; it does not
  perform HTSP writes or own checkpoint timing.
- Define stable playback intents rather than overloading the current recording
  ID callback: `Resume(position)`, `FromBeginning`, and `DefaultPolicy` (names
  illustrative). **Resume from …** sends Resume, **Play from beginning** sends
  FromBeginning, and **Play** sends DefaultPolicy. Starting from the beginning
  clears server progress only after playback actually starts.
- Recording details use **Resume from …**, **Play from beginning**, or **Play**
  according to pure policy. Direct recording launches use DefaultPolicy.
- No Home rail, Home progress card, recommendation, or new Home focus path is
  part of this plan.

## Test-driven implementation slices

### Slice 0: Stop implicit play-count inflation

Write failing protocol/data-source tests proving that extractor reopen, seek,
and ordinary close do not request a play-count increment on HTSP 27+.

Acceptance:

- Every recording `fileClose` on HTSP 27+ carries `playcount=KEEP` unless a
  separately tested completion command is being sent.
- Picon and unrelated file closes remain unchanged.
- HTSP 26 behavior is explicitly covered and not falsely described as fixed.
- Repeated/concurrent `close()` calls and factory data-source replacement produce
  exactly one close request per handle.
- No resume behavior or UI is added in this commit.

This is a generic upstream candidate and should remain separable.

### Slice 1: Pure resume/completion policy

Write the policy tests first for:

- 0, negative, 179-second, 180-second, valid, oversized, and near-end positions;
- unknown versus known duration;
- completed versus growing DVR entries;
- 95% plus five-minute completion boundaries;
- watched entries with a later partial-rewatch position;
- explicit start-over;
- whole-second flooring and overflow; and
- completion emitted only once per generation.

Each checkpoint trigger also covers the 179/180-second boundary so periodic,
seek, pause, and final paths cannot persist a position the resume policy ignores.

Acceptance: all decisions are deterministic JVM code with no player or Compose
dependency.

### Slice 2: Typed server update path

Add fake-HTSP request tests before the repository operation.

Acceptance:

- Exact `updateDvrEntry` fields and sentinel values are asserted.
- Success and async echo remain separate concepts.
- Permission, error, timeout, cancellation, disconnect, and unsupported-version
  results are classified without disconnecting the session; caller cancellation
  propagates rather than becoming a result.
- Position and watched retries pass generation and remote-update reconciliation
  before they are treated as idempotent.

### Slice 3: Completed-recording resume

Add a player-session/coordinator test seam with a fake clock, fake position
source, and fake progress writer. Do not put timing tests in Compose.

Acceptance:

- Eligible completed recordings start at the server point before autoplay.
- Delayed timeline publication, unknown/invalid duration, replacement while
  waiting, and preparation failure have deterministic start behavior.
- Start-over starts at zero and does not write merely because details opened.
- A new recording/live request cancels old-generation progress work.
- The extractor and live playback code are unchanged.

### Slice 4: Checkpoint lifecycle and reconciliation

Acceptance:

- 30-second periodic, settled-seek, pause, final, error, and natural-end paths
  have focused tests.
- One request is in flight; newer checkpoints replace older queued values.
- Remote updates do not seek the active player and idle timers do not overwrite
  a newer remote value.
- No retry survives the active generation.
- Back to warm browse does not prematurely finish the recording session.
- `final checkpoint at the 95% boundary` and `STATE_ENDED -> completion ->
  automatic Stop` emit no later nonzero checkpoint.
- `terminal error at or beyond 95%` preserves nonzero progress and sends neither
  set-watched nor a completion clear.

### Slice 5: Minimal TV presentation

Before this slice, sketch and test the details-panel focus graph:

- Initial focus: **Resume from …** when eligible, otherwise **Play**. Delete is
  never the default because a conditional playback action changed.
- Stable order when resumable: **Resume from …**, **Play from beginning**,
  Delete, Close. Otherwise: **Play**, Delete, Close. Keep playback actions
  together and destructive/dismissal actions separate in two deterministic rows
  rather than opportunistic wrapping.
- D-pad exits: details is a modal focus scope. Boundary presses remain inside its
  actions and the browser behind it is absent from focus and accessibility
  traversal.
- Back has one owner per key cycle. Back from a confirmation restores its
  invoking Cancel/Delete action; Back from details restores the invoking row.
- Selecting playback closes details before navigation. Returning from playback
  restores mode, folder, scroll, and row rather than reopening retained details.
- Preserve player Back order: auxiliary surface, visible controls, then warm
  return to the library. Back does not stop playback or clear progress.

Acceptance:

- TV Material buttons remain the focusable controls.
- Build actions with stable semantic IDs. Request initial focus only on panel
  entry; changing a timestamp/message never steals focus. If the focused action
  disappears, fall back deterministically to Play or Play from beginning while
  preserving Delete/Close focus across unrelated metadata updates.
- Do not ellipsize an action into ambiguity. Use a compact visible timestamp
  such as `1:02:03` with a localized accessible label. Validate the 960 x 540 dp
  canvas, English and German, long titles, and supported font scales before
  accepting the two-row geometry.
- Read-only state says that existing resume is available but new progress will
  not be saved. Unsupported legacy state avoids protocol terminology and offers
  start-over. A transient failure is announced politely once per degradation
  episode, clears after an accepted checkpoint, and is not repeated on retries.
  These messages remain non-focusable/non-blocking; fatal recording failures
  take priority. Static details copy stays in reading order and dynamic player
  status uses appropriate live-region semantics.
- Existing player controls, Back dispatch, and focus restoration regressions are
  covered.
- Home is unchanged.

### Slice 6: Growing recordings

Implement only after a focused protocol/player spike.

Acceptance:

- A server position can be applied to a growing file without using file size as
  media time.
- Temporary EOF does not set watched or clear progress while DVR state is still
  `RECORDING`.
- Transition to `COMPLETED` reconciles the final duration and completion policy.
- Reconnect/read failure behavior remains explicit and does not overwrite a
  newer server position.

## Verification matrix

Automated for every slice:

```bash
./tools/check-native-libs
./tools/verify
```

The native check should remain unchanged and green; this work must not touch the
native AAR or Media3 coordinates.

Required protocol/integration coverage:

- fake server request/response and async-update ordering;
- HTSP 26 legacy behavior documented as unsupported for full sync;
- HTSP 27 behavior with explicit close `KEEP`;
- current supported HTSP behavior;
- DVR write allowed, denied, and connection loss during checkpoint;
- two simulated clients writing the same recording;
- timeout after server application but before client response; and
- reconnect/full snapshot after accepted progress.

Real-server tests must use a disposable recording and require explicit approval
because they modify server-side DVR fields. Do not change accounts, DVR profiles,
retention/removal settings, storage, or household recordings for this work.

Physical G10 validation, after local and disposable-server tests pass:

- completed recording start, resume, start-over, seek, pause, Back/warm return,
  explicit Stop, natural end, app background, and process restart;
- progress written on one client and consumed on another;
- growing recording behavior if Slice 6 is enabled;
- progressive and interlaced live-TV regression, recording audio/subtitles,
  focus restoration, and playback teardown; and
- confirmation that no progress failure blocks playback; and
- physical-TV checks for modal focus feel, safe-area/overscan, English/German
  action readability, focus clipping, readability over moving video, and real
  SurfaceView visibility. Automated tests and screenshots do not prove these.

The G08 remains production-only and is not a routine validation target.

### 2026-07-29 automated implementation record

- Focused JVM protocol, policy, preparation, checkpoint, retry, reconciliation,
  completion, and data-source lifecycle tests pass.
- `./tools/check-native-libs` passes without changing the Media3 coordinates or
  native AAR baseline.
- `./tools/verify` passes, including all JVM tests, lint, Android-test Kotlin
  compilation, and debug APK assembly.
- Completed-recording resume and synchronization are enabled only for negotiated
  HTSP 27+ capability. Growing recordings retain start-over playback and do not
  emit progress writes from this implementation.
- Real-server mutation tests and physical G10 validation remain intentionally
  outstanding. They require an explicitly approved disposable recording and
  cannot be replaced by compilation, screenshots, or ADB counters.

## Rollout and success criteria

1. Merge the close-side-effect fix independently before enabling resume.
2. Keep progress synchronization behind negotiated capability until the HTSP 27
   and current-server matrix passes.
3. Enable completed-recording resume first; growing recordings remain start-over
   until their separate gate passes.
4. Do not add a durable local fallback or Home surfacing to make an incomplete
   server path appear finished.

The agenda item is complete when:

- a compatible server point survives app process death and is consumed by a
  second client/device;
- seeks and Media3 reopens no longer inflate play count on HTSP 27+;
- checkpoint, completion, permission, disconnect, timeout, and concurrent-client
  policies pass focused tests;
- unsupported/read-only sessions remain playable and clearly bounded;
- `./tools/verify` and native integrity pass;
- the G10 recording/live regression matrix passes; and
- any server-side mutation used for testing had explicit approval and touched
  only disposable test content.

## Source references

- TVHeadend current HTSP server implementation:
  <https://github.com/tvheadend/tvheadend/blob/master/src/htsp_server.c>
- TVHeadend DVR play-position units:
  <https://github.com/tvheadend/tvheadend/blob/master/src/dvr/dvr.h>
- HTSP play-count command constants:
  <https://github.com/tvheadend/tvheadend/blob/master/src/htsp_server.h>
- HTSP 27 play-status change:
  <https://github.com/tvheadend/tvheadend/commit/729d7b66f2e025676f9eabb9400ceba04beccef3>
- HTSP protocol-version bump:
  <https://github.com/tvheadend/tvheadend/commit/df2a06411bbbdbe4e469a14910240ffbbf886c5f>
- TVHeadend HTSP client-to-server RPC documentation:
  <https://docs.tvheadend.org/documentation/development/htsp/client-to-server-rpc-methods.md>
