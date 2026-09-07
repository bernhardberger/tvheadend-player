# Player performance defect ledger

Status: active. Scope: the live-player performance and interaction defects
reported during the post-SDK-refactor stabilisation (app base `7ccda8c`, SDK
base `8f30f58`, SDK fixes staged as 0.7.0). Every user-reported or discovered
defect is listed once; close an entry only with the evidence named in its row.
Archive this file when every row is closed or moved into a dedicated plan.

Evidence classes: `unit` (JVM test), `device` (G10 test target, see
`device-targets.md`), `emulator` (offline production-composable instrumentation),
`published` (released SDK and artifact provenance), `user` (human observation on
the TV). Emulator evidence does not establish physical-TV acceptance.

## Open

| # | Defect | Source | Notes / next step |
|---|---|---|---|
| D12 | Channel becomes dead after quick zapping away and back (grey screen, black rectangle); stop + retune does not recover. Both dead channels so far were being recorded. | user | Failure reason is discarded before it can be shown; retain `SubscriptionIssue` / player error in `AppPlaybackState.Failed`, then diagnose. |
| D13 | "Playback stopped." failure box text is unreadable (dark text on dark scrim), `VideoPlayerScreen.kt` `player-channel-unavailable`. | device | Fix contrast with the D12 diagnostics change. |
| D14 | Intermittent ~10 fps stutter on a playing channel (also SD); clears on retune; compositor showed ~41 fps and no client CPU pressure during one occurrence. | user | Deferred by user until reproducible. Add Media3 rendered/dropped frame counters and A/V offset to the stats overlay so the next occurrence has evidence. |
| D15 | Timeshift playback choppy with artifacts (user report); one occurrence coincided with a TVHeadend restart under memory pressure. | user | Resume after 20 s pause measured 41-44 fps on device; needs user re-check on the current build. |
| D17 | Quick zapping sometimes ends in "Playback stopped." | user | Not reproduced after the EPG CPU fix (5- and 10-key bursts). Re-check with D12 diagnostics. |
| D18 | Quick zapping fires one subscription per key press: the tune effect (`VideoPlayerScreen.kt` `LaunchedEffect(... liveRequestToken)`) calls `playChannel` immediately with no settle delay, so a CH+/- burst is a subscribe/unsubscribe storm on the server (likely feeds D12/D17). | found | Debounce the subscription (~300-400 ms after the last key) while keeping the instant header/pill feedback; only the settled channel tunes. |
| D20 | While timeshifted, the header shows "Programme timing unavailable" instead of the programme at the watched position (or at least now/next). | user | Product decision: show the programme covering the timeshifted position; fall back to now/next. Replaces the current suppression in `OverlayControlsTv.kt`. |
| D21 | Channel rail cards: no now/next on the cards; "Playing" / "Recording now" text is verbose where icons exist elsewhere in the app; whitespace beside the picon. | user | Low priority. Redesign the rail card with now/next and status icons. |
| D22 | Player lacks the "down: Channels" hint from the concept. | user | Low priority; do with D21 when the player UI is touched next. |

## Closed

| # | Defect | Fix | Evidence |
|---|---|---|---|
| D16 | Picons are not persisted across restarts. | Published SDK 0.8.0 cache policy rooted at `context.cacheDir`, 7-day metadata / 30-day artwork retention, 64 MiB artwork cap; Coil disk cache disabled and its legacy directory removed on IO. | unit: `GuideRenderingCostContractTest`; published: plain `./tools/verify`, external SDK and native digest gates pass (2026-09-07). Physical-TV cache reuse remains a human gate below. |
| D19 | Warm cold start waits on "Loading channel information…". | Non-empty retained catalogs enter Channels during synchronization and automatic reconnect backoff; pending startup autoplay is cancelled, actionable failures stay visible, tuning still requires Ready. | unit: `CurrentChannelReadinessTest`, `MainStartupPresentationTest`, `CachedStartupTransitionTest`; plain `./tools/verify` passes (2026-09-07). No measured physical-TV startup-time claim. |
| D23 | No operator Clear cache control. | Settings > General > Storage shows SDK cache statistics, a repeat-safe Clear action, progress, confirmation and retry; connection settings and focus are preserved. | unit: `SettingsStorageViewModelTest`; emulator: 16/16 `StorageScreenshotTest` cases (EN/DE, font scales 1.0/1.3, four states), plus existing Settings/startup tests; final captures and review evidence in `docs/persistent-cache-plan.md`. |
| D1 | Re-tune loop: SDK recovery watchdog escalated during normal tuning. | SDK `PlaybackRecoveryPolicy.preparationDurationMillis` (20 s) and audio-latch; app `LiveRecoveryBackoff` with cap. | unit, device |
| D2 | Choppy playback ending in a retune: no LoadControl on a push-only source. | `PlaybackLoadControl` (2 s start, 1.5 s rebuffer, 15/30 s). | unit, device |
| D3 | Jittery "behind live" (6..9 s chatter). | Sample coherence (`TimeshiftPlaybackPosition.Estimate.timeline`, `describesSameSubscription`). | device |
| D4 | Seekbar disappearing 2-3x per second. | Sampling effect keyed on channel, not on the churning observation. | device |
| D5 | EPG hidden in the player header whenever timeshift is available. | `programmeTimingDescribesPlayback`. | unit, device |
| D6 | Picons slow / vanish after scrolling: unbounded HTSP file fetches. | SDK three-handle artwork bound; loading vs failed placeholder distinguished. | unit, device |
| D7 | "Behind live" shown right after a tune instead of "Live". | Live edge follows the server reader shift. | unit, device |
| D8 | Tuning slow, no zap feedback for seconds, main thread stalls, choppy playback: full EPG snapshot rebuilt on every HTSP EPG message and re-scanned by every observer. | SDK snapshot cache, deferred EPG publication with burst drain, content-hash equality. Zap feedback ~1 s, picture 3-4.5 s (was 8.5-12 s). | unit, device |
| D9 | "Tuning..." pill shown again a few seconds after a tune (rebuffer treated as a new tune). | `AppPlaybackState.Buffering`; start buffer 2 s. | unit, device |
| D10 | Options sheet: Back only cleared focus; close did not return focus to the triggering button. | Root key handler dispatches BACK when a target has focus; existing focus restoration now runs. | device |
| D11 | Lower player area bloated: fixed 100 dp column, "span" and "Estimated" labels, behind-live shown while live, initial focus on the seekbar, no pause button. | Slim lower area, one-argument history label, live edge hides distance, Play/Pause button is the initial focus. | device |

## Open human gate

D16/D19/D23 implementation closure does not close physical-TV acceptance. On a
separately authorized TV session, confirm warm-cache cold-start timing, retained
artwork reuse, readability/overscan and real remote focus/Back feel in Storage.
No physical TV, server-backed session or credential access was used for P26-A1.
