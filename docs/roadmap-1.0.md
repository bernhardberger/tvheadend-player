# Roadmap and plan to 1.0

**Status:** Draft for owner review, 2026-10-11. Base `main` 72530a9f (Player
0.2.93, SDK 0.24.0 / HTSP 1.0 beta). Nothing in this plan is started.
**Scope:** Everything between the current Player and a signed 1.0 release of the
remote-first Android TV client. Detailed designs stay in the plans each
workstream links to; this document owns scope, order, decisions and exit
criteria. Archive it once 1.0 ships.

## Scope decisions (owner, 2026-10-11)

In 1.0:

- **Store readiness:** a Google Play TV listing that passes the TV app quality
  review and Play policy (workstream S).
- Home-screen integration (scoped by the H1 spike below).
- Series recording.
- The open UI/UX decisions.
- Remaining open work: defects, appliance, release, documentation.
- Benchmarks and performance optimization.

Not in 1.0:

- Multiple servers (at least not simultaneously).
- App-owned favourites beyond TVHeadend channel tags.
- Picture-in-picture. Android TV allows PiP only from Android 14 and only for
  communication, smart-home, health or ticker content; media playback in PiP is
  explicitly disallowed
  (<https://developer.android.com/training/tv/get-started/multitasking>).

Stretch goal: sleep timer (countdown or "end of programme" that stops playback
after an on-screen warning).

Owner, 2026-10-11:

- D5 (edit scheduled entries) and D6 (timer rules) are in.
- No fixed startup/zap targets: E1 records the best achieved values as
  regression baselines.

## Baseline (verified 2026-10-11)

Present:

- Live TV with timeshift and the programme window.
- Channels with tags; Guide with EPG search.
- Recordings (Archive, Schedule, Problems) with single-event
  schedule/stop/cancel/delete.
- Resume from the server position, cutpoint markers.
- Audio, subtitle and teletext tracks; stream profiles.
- Appliance profile, stats overlay.
- German translation complete (507/507 translatable entries).
- Native decoder provenance pinned: `decoder-ffmpeg/provenance.json` and
  `tools/check-native-libs`.

Available in SDK 0.24.0 but unused:

- autorec (series) rules;
- timerec rules;
- editing scheduled entries (`updateEntry`);
- DVR disk space;
- server discovery.

Release builds are not minified (`app/build.gradle.kts`, release
`isMinifyEnabled = false`), so the shipped binary differs from the optimized
`profileServer` build used for measurement.

## Workstreams

Each item has an ID for tracking. **[TV]** marks a physical-TV (G10) gate, which
needs owner observation when the item is human-visible.

### P — Playback reliability

Source: `defect-ledger.md`, `media3-upgrade-assessment-2026-07-28.md`.

| ID | Item | Exit |
|---|---|---|
| P1 | D14: Media3 rendered/dropped/skipped frame counters and A/V offset in diagnostics and the stats overlay | Counters visible; unit coverage of the model |
| P2 | D24: retain a privacy-safe exception classification (cause class, top frame) with `Failed` | Detail line shows it; unit coverage |
| P3 | G10 reliability session: zap stress (D12/D17), ORF1 matrix (D24), timeshift (D15), header fallback (D20), stutter (D14) **[TV]** | Each row attributed, fixed or closed with evidence |
| P4 | Fixes arising from P3 | Focused regression test per fix |
| P5 | Media3 1.11.1 device matrix and interlaced-motion comparison (assessment steps 5–6, doc still names 1.10.1) **[TV]** | Assessment updated to the current stack and gate passed |
| P6 | G08 intermittent bad render mode (`appliance-mode-plan.md`): decide observe-only or investigate | Owner decision recorded |

### D — DVR completeness

Source: SDK `DvrRepository`; `recording-progress-sync-plan.md`;
`live-tv-dvr-conventions`.

| ID | Item | Exit |
|---|---|---|
| D1 | Series-recording UX brief: entry from programme details (Guide, player, search), rule options exposed (channel, any-time vs slot, duplicates), how rules appear in Recordings | Approved brief and captures |
| D2 | Create series rule from programme details | Shared DVR actions extended; tests; notices via `NoticeCenter` |
| D3 | Rules list in Recordings: view, edit, delete autorec rules | Depth navigation, focus/Back contract, empty/error states |
| D4 | Disk space in Recordings (`dvrDiskSpaceState`) | Truthful unknown/unsupported state |
| D5 | Edit a scheduled entry (`updateEntry`, e.g. padding/profile) | Edit from Schedule and programme details; tests |
| D6 | Timer (timerec) rules: create, list, edit, delete | Shares the D3 rules list; tests |
| D7 | Recording-progress Slice 6 (growing recordings): run the disposable spike, then decide | Spike result and decision recorded |
| D8 | Real-server DVR mutation pass on the test server **[TV]** | Schedule, series, cancel and delete observed end to end |

### H — Home-screen integration

Research, 2026-10-11, developer.android.com:

- **Watch Next** (`androidx.tvprovider`): open API. On Google TV it shows only
  after Google certifies the integration for quality
  (<https://developer.android.com/training/tv/get-started/google-tv>). Live-TV
  guidance allows programme-level items, such as recordings and time-shifted
  programmes, never channels.
- **Preview channels**: documented for the classic Android TV launcher. Whether
  Google TV renders third-party app channels is unverified.
- **Engage SDK / media actions feed**: partner-only (Play, 100k DAU or Media
  Experience Program); "TV guides" are listed as unsupported. Not reachable.
- **TV Input Framework / Live tab**: OEM-only for channel publishing; third
  parties are pointed to the home-screen APIs. Not reachable.
- **In-app search provider** (`SearchManager`, `searchable.xml`): documented for
  Android TV; Google TV behavior unverified.

| ID | Item | Exit |
|---|---|---|
| H1 | Spike on the G10 and TCL: debug build that publishes a Watch Next recording, a preview channel and a search provider; record what each launcher renders **[TV]** | Matrix of what is visible per device; owner picks 1.0 scope |
| H2 | Watch Next: in-progress recordings ("Continue watching"), deep link into recording playback with resume; removal on completion/delete | Only if H1 shows it renders or certification is pursued |
| H3 | Preview channel ("Recently recorded" / "On now") with deep links | Only if H1 shows it renders on targets that matter |
| H4 | Global/voice search into Guide/Recordings | Only if H1 shows it is honoured |
| H5 | Deep-link contract: one documented intent shape for channel, programme and recording, guarded by the Appliance profile | Tests for each link kind and profile |

H5 is useful regardless and underlies H2–H4. If H1 shows nothing renders on
Google TV without certification, 1.0 home-screen scope reduces to H5 plus
whatever the classic launcher shows. Decide then.

### U — UI/UX decisions and convergence

Sources: `DESIGN.md` §2 and §12, `browse-cohesion-proposal.md`,
`programme-window-prototype.md`, `player-ui-ux-overhaul-plan.md` Slice 9, owner
notes. Decisions first; each accepted decision becomes a bounded implementation
package with captures and screenshot-first UX review.

| ID | Decision | State |
|---|---|---|
| U1 | Channel-rows screen: placement (player Down, programme room, channel room) or drop | Kept; off `main` since 7ad43902, survives on `trial/channel-rows` |
| U2 | Shipped two-row rail as the final player channel browser; retire `trial/rail-below`, `ui/new-chrome-trial`, `ui/zap-*` | Rail on `main`; in-place rail trial and Up-recents experiment open |
| U3 | Programme window B: keep as default, make it a setting, or drop | Reversible experiment, default on |
| U4 | Browse density: run the typography/hierarchy trial or accept | Open concern in browse-cohesion |
| U5 | Channels header actions (Search, View settings) and first-entry policy | Illustrative only, not authorized |
| U6 | Spacing consolidation: Guide channel column (124 vs 172 dp), remaining batches, dialog-to-kit mapping | Baseline in `TvLayout`; batches pending |
| U7 | `DESIGN.md` §12 open items: opacity tiers vs Material roles, action-row idle colours, card grid widths/ratios, single-line headline rule, connection editor presentation, Recordings folder container | Each undecided |
| U8 | Destination-loading placeholders | Deferred until the Channels layout settles (U4/U5) |
| U9 | Player Slice 9: pick at most one (previous channel, next recording, expanded Now/Next, elapsed/remaining, recording speed, thumbnail trick play) or none | Not started |
| U10 | Series recording and disk-space surfaces | From D1 |
| U11 | Physical acceptance: browse baseline, D21 rail, programme window, player overhaul human gates **[TV]** | Observation only |
| U12 | Screenshot goldens for production composables once U1–U9 settle | Deferred by plan until the UX is accepted |
| U13 | **Done 2026-10-11.** 12/24-hour clock (owner: required for 1.0): an app setting "Clock format" with System (default, follows `DateFormat.is24HourFormat`), 12-hour and 24-hour, applied through one shared time formatter. The setting goes in Settings → General (`ui/screens/settings/SettingsGeneral.kt`), and a change is applied immediately without a restart. Today the format is fixed at `HH:mm` in `ui/common/TimeConverter.kt:9`, `ui/player/ProgrammeWindow.kt:146`, `ui/notifications/NoticeFormatter.kt:93`, `ui/screens/EpgGridScreen.kt:2408` and `ui/screens/recordings/RecordingsContent.kt:873`. The D5/D6 time entry follows the same setting. Re-check widths of the Guide time ruler, rail, Banner and cards for "12:45 PM" | By default all times follow the system setting, and the override wins when set; JVM tests for all three options in both locales; setting row meets the TV floor (focus, Back, TalkBack); captures in 12-hour mode |

### E — Performance and benchmarks

Sources: `profiling.md`, `responsive-ui-foundations-plan.md` (HEAD copy),
`navigation-startup-profiling-2026-09-10.md`,
`residual-main-menu-performance-2026-09-11.md`.

Current state:

- Profiling is manual ADB/Perfetto (`tools/profiling/`).
- The baseline profile (`app/src/main/baseline-prof.txt`) is hand-written and
  covers the sidebar/Guide pair and the live player, not startup, Recordings or
  Settings.
- There is no Macrobenchmark module, no machine-readable budgets and no
  regression detection.

Measured on the G10, 2026-09:

- about 8 s from process bind to first admitted tune;
- zap to first frame 0.5–3.4 s;
- Guide page return 286–353 ms;
- rail reopen 209–265 ms;
- cold main menu misses the declared 100/200 ms budgets.

| ID | Item | Exit |
|---|---|---|
| E1 | Budgets as data: p95 focus response ≤50 ms, 100 ms max blocking, 200 ms route result; startup and zap have no fixed target, so record the best achieved values as regression baselines | Committed budget file referenced by benchmarks |
| E2 | Macrobenchmark module with the fixture (`profile`) data path: cold/warm startup, Channels scroll, Guide navigation, rail open, Recordings, Settings | Runs on the emulator lane; results stored per run |
| E3 | Generated baseline and startup profiles (AGP baseline-profile plugin) replacing the hand-written file; `profileinstaller` declared | Generated profile committed; APK-only vs `.dm` comparison recorded |
| E4 | Fix the optimized instrumentation runner (`androidx.tracing` stripped by R8) | Optimized fixture tests pass |
| E5 | Startup decomposition and work: split SDK cache restore, metadata publication and autoplay readiness; target the 8 s path (responsive plan Phase 4) | Measured against E1 on the G10 **[TV]** |
| E6 | Zap decomposition: first-media, codec-init and render markers; then optimize the dominant segment | Zap target from E1 met or attributed to server/tuner |
| E7 | Guide and rail: cold Guide construction, SDK metadata lock contention, rail reopen and tag-cycle layout (responsive plan Phases 3 and 5) | Budgets met on the G10 **[TV]** |
| E8 | Regression gate: benchmark comparison against stored baselines in an explicit pre-release step | Documented threshold; release checklist entry |
| E9 | Operator fluidity acceptance on the optimized release build **[TV]** | Recorded owner judgment |

E3 and E8 depend on R2 (minified release), so what is measured is what ships.

### A — Appliance profile

Source: `appliance-mode-spec.md`, `appliance-mode-plan.md`.

| ID | Item | Exit |
|---|---|---|
| A1 | HOME role on the TCL is ignored by firmware: record the accepted GUIDE accessibility workaround as the 1.0 behavior, or pursue further | Owner decision recorded in the spec |
| A2 | Residual gates: GUIDE/wake retest, process death, held repeat, TalkBack **[TV]** | Observed on the current build |
| A3 | Deep links from the home screen (H5) respect Appliance containment | Covered by H5 tests |

### R — Release hardening

Source: `release-process.md`, `product-identity-plan.md`,
`product-architecture-remediation-plan.md` §13–15.

| ID | Item | Exit |
|---|---|---|
| R1 | Native gate: run `./tools/check-native-libs --release` on the release candidate; correct stale "blocked" claims (appliance plan, codebase audit AUD-008) | Gate passes; docs current |
| R2 | R8 for release: keep rules for Koin, serialization restoration, native decoders; licenses and corresponding source unaffected | Minified release passes `./tools/verify` and a G10 smoke **[TV]** |
| R3 | Dependency hygiene §13: Material Components re-parent, Coil OkHttp artifact, extended icons, inert dependencies — each its own package | APK size and visual smoke per package |
| R4 | Accessibility: TalkBack pass across Channels, Guide, Recordings, Settings, player **[TV]** | Findings fixed or recorded |
| R5 | Release notes, privacy policy and `NOTICE.md` checked against the artifact (identity plan item 5) | Checked |
| R6 | Signed-release acceptance: prepare on LXC 106, sign on LXC 117, verify, G10 install and same-key update, playback/HOME/GUIDE/standby/reboot matrix **[TV]** | `release-process.md` end to end |
| R7 | Dependency-update and wrapper-validation automation (separately authorized repository package) | Optional for 1.0 |

### S — Store readiness and TV quality gates

Sources:

- TV app quality guidelines:
  <https://developer.android.com/docs/quality-guidelines/tv-app-quality>
- TV publishing checklist:
  <https://developer.android.com/training/tv/publishing/checklist>
- 16 KB page sizes: <https://developer.android.com/guide/practices/page-sizes>
- Play policy pages cited per item.

Research and repository audit are dated 2026-10-11.

Already met:

- Leanback launcher intent; touchscreen not required.
- 320×180 xhdpi banner and launcher icon.
- minSdk 28 (≤31) and targetSdk 36 (≥34 for TV).
- 16 KB LOAD alignment on every shipped `.so` (`tools/check-native-libs`
  enforces it for 64-bit ABIs).
- Activity-scoped `MediaSession`; app-owned audio focus.
- Keep-screen-on only while playing.
- No analytics, crash-reporting or ads SDKs.
- Backup disabled.
- `LICENSE`, `NOTICE.md` and `privacy-policy.md` present.

Gaps:

- The HOME intent filter and the GUIDE `AccessibilityService` are declared
  unconditionally in the main manifest (`app/src/main/AndroidManifest.xml`), and
  no build flavor separates them.
- The release pipeline builds APKs only, never an AAB.
- There are no store assets and no current screenshots.
- A reviewer cannot use the app without a TVHeadend server.

| ID | Item | Exit |
|---|---|---|
| S1 | Distribution model (owner has no preference; proposed): the Play build keeps the current application ID; the Appliance sideload build gets its own ID (for example an `.appliance` suffix) so a Play update can never replace an Appliance install with the build that lacks GUIDE/HOME. Both can be installed side by side. Existing Appliance TVs are set up once more (0.x needs no migration). Needs a `product-identity-plan.md` entry | Decision recorded in `release-process.md` and `product-identity-plan.md` |
| S2 | Play account: personal accounts created after 2023-11-13 need a closed test with ≥12 testers opted in for 14 consecutive days before production; organization accounts are exempt (<https://support.google.com/googleplay/android-developer/answer/14151465>) | Account type chosen; tester pool identified early because this sets the calendar |
| S3 | Appliance boundary for Play: the GUIDE accessibility service is a high policy risk (launcher/automation is not an accessibility tool; it would need a prominent disclosure, consent and a video declaration: <https://support.google.com/googleplay/android-developer/answer/10964491>). The HOME filter is an unverified risk. Decided (owner): a `play` build without both, and an Appliance sideload build with them; product profile logic unchanged. The Appliance build is a hard accessibility requirement for the household (autostart, GUIDE/HOME never leave the player) and stays a fully supported, release-gated build, not a leftover | Flavor or manifest split; tests prove the Play manifest lacks both, the Appliance manifest keeps both, and Standard behavior is unchanged; each release checks the Appliance build on the G10 |
| S4 | Reviewer access. Proposed: a small hosted demo TVHeadend (own VM/container, no tuners or OSCam, 2–3 IPTV channels looping CC-licensed films, a scripted XMLTV guide, a few recordings, a review-only account) with the credentials in Play Console "App access". An in-app demo mode was rejected: it would need a fake server, guide, DVR and stream path that the real app does not use and would have to be kept in step with them. The demo server is internet-exposed infrastructure and needs separate owner approval | Server running and approved; reviewer can reach live playback, Guide and Recordings |
| S5 | TV quality audit against every checklist ID. Likely review risks: TV-DB (Back from the top level returns to the TV home screen), TV-PC/TV-PP (D-pad center and media play/pause toggle playback; Center already toggles pause where live pause or a recording allows it, and otherwise shows the reason on the Banner, which needs only confirming), TV-OV overscan, TV-ME low-RAM devices, TV-BU/TV-BY ambient mode. Validate `MediaSession` with the Media Session Validator | Each ID passes, or is a documented deviation with rationale |
| S6 | AAB in the release pipeline (`tools/prepare-release`, `release-process.md`): build, align and verify the bundle; keep the APK for sideload; prove 16 KB alignment with `bundletool` | Signed AAB and APK from the same commit |
| S7 | Store listing: TV screenshots from real devices, banner, English and German descriptions naming Android TV, hosted privacy-policy URL (mention the accessibility service in the `full` build) | Listing complete in Play Console |
| S8 | App content declarations: Data safety (credentials stored on device and sent only to the user's server; unencrypted HTSP disclosed), content rating, target audience, ads, any foreground-service types | Declarations submitted |
| S9 | GPLv3 on Play: confirm corresponding-source offer and compatibility of Play distribution terms (licensing check, not a Play-docs question) | Owner-accepted conclusion recorded in `product-identity-plan.md` |
| S10 | Developer verification for sideloaded installs (global rollout 2027): register the package and key | Registered before 1.0 |
| S11 | Closed test track: run the S2 window on the 0.9 RC | 14 days completed; feedback triaged |
| S12 | Watch-ahead: targetSdk 37 makes local-network access a runtime permission. Not for 1.0 while targeting 36 | Noted in the release checklist |

### X — Housekeeping

| ID | Item |
|---|---|
| X1 | `responsive-ui-foundations-plan.md` is emptied in the worktree (uncommitted): restore, archive, or fold its remaining phases into E5/E7 |
| X2 | Close or archive completed plans (player overhaul, persistent cache once its physical gates pass, product-architecture remediation once §13–15 move here) |
| X3 | Prune merged and abandoned trial branches and worktrees after U1/U2 |
| X4 | Defect ledger: close D22 (superseded), archive the ledger once P3/P4 clear it |

## Milestones

| Version | Content | Exit |
|---|---|---|
| 0.3 | P1–P4, H1, D1, decisions U1–U3 and S1–S4 | Reliability session done; home-screen, series and distribution scope fixed |
| 0.4 | U13 (before D5/D6 time entry), D2–D6, D7 decision, H5, plus H2–H4 as scoped; S3 split | Recording rules and deep links on the G10; Play build has no Appliance entry points |
| 0.5 | E1–E4, R2, S4 review server, S5 audit, S6 AAB | Benchmarks against a minified build; TV checklist gaps known |
| 0.6 | U4–U10 implementation, E5–E7, S5 fixes | UX decisions implemented; budgets met or attributed |
| 0.9 RC | U11, U12, P5, A1–A2, R1, R3–R5, E8–E9, S7–S10, X1–X4; S11 closed test | All [TV] gates observed; goldens in place; closed test running |
| 1.0 | R6, Play production release after S11 | Signed release on GitHub and Play |

The S2 closed-test window (14 days) and Play review (about 7 days) put at least
three weeks between the 0.9 RC and a Play 1.0. If the account is personal, the
tester pool needs recruiting before 0.9.

Order rationale:

- Reliability and the two scope-fixing spikes come first because they change
  what later work builds on.
- R2 precedes performance work so benchmarks measure the shipped binary.
- UX implementation follows the decisions and precedes goldens, which would
  otherwise churn.
- Physical-TV gates are batched per milestone so the owner observes once, with
  the G10 sessions clustered at 0.3 (reliability) and 0.9 (acceptance).

## Owner decisions needed

1. S1–S4 (distribution model, account type, Appliance split, reviewer access)
   at 0.3.
2. S9 GPLv3-on-Play conclusion.
3. U1–U9, at the milestones above.
4. H2–H4 after the H1 matrix.
5. A1 and P6.
