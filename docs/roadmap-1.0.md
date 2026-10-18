# Working plan to 1.0

**Status:** Internal, preliminary working plan of the maintainer, not a
published project roadmap: nothing here is a promise or a release date, and
items change or drop without notice. First draft 2026-10-11. The milestone table shows what
is done; the SDK version is pinned in `gradle/libs.versions.toml`.
**Scope:** Everything between the current Player and a signed 1.0 release of the
remote-first Android TV client. Detailed designs stay in the plans each
workstream links to; this document owns scope, order, decisions and exit
criteria. Archive it once 1.0 ships.

## Scope decisions (owner, 2026-10-11)

In 1.0:

- **Store readiness:** a Google Play TV listing that passes the TV app quality
  review and Play policy (workstream S).
- ~~Home-screen integration~~: not in 1.0. The H1 spike showed Google TV renders
  nothing from an uncertified app (owner 2026-10-12: H2–H5 out).
- Disk space in Recordings (owner's personal requirement).
- UX rework of every screen, including a new home screen (workstream U).
- Remaining open work: defects, appliance, release, documentation.
- Benchmarks and performance optimization.

Not in 1.0:

- Multiple servers (at least not simultaneously).
- App-owned favourites beyond Tvheadend channel tags.
- Picture-in-picture. Android TV allows PiP only from Android 14 and only for
  communication, smart-home, health or ticker content; media playback in PiP is
  explicitly disallowed
  (<https://developer.android.com/training/tv/get-started/multitasking>).

Stretch goal: sleep timer (countdown or "end of programme" that stops playback
after an on-screen warning).

Owner, 2026-10-11:

- Series rules stay in the Tvheadend web UI: the app adds no series or rule
  logic, which gets complicated quickly with EIT-only or weak EPG. This
  replaces the earlier "series recording" scope item and drops D1–D3, D5 and
  D6 (later the same day; supersedes "D5 and D6 are in"). Rule-made
  recordings already appear in Recordings.
- Manual one-off recordings (D9) are in, after D4 free disk space, and may be
  dropped if time runs short (owner 2026-10-12).
- No numeric performance targets at all (startup, zap or UI response): the
  benchmarks record numbers, and the owner's judgment on the G10 decides what
  is slow. No budget file or automatic gate.

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

Available in the SDK but unused (checked 2026-10-18):

- autorec (series) rules;
- timerec rules;
- editing scheduled entries (`updateEntry`);
- server discovery.

DVR disk space was unused on 2026-10-11 and is now shown in Settings and
Recordings (D4; `ui/screens/SettingsScreen.kt:43`,
`ui/screens/RecordingsScreen.kt:461`).

Release builds are R8-minified since R2 (2026-10-15); the optimized
`profileServer` build used for measurement needs `-Ptvhplayer.profileMinify=true`
to match them.

## Workstreams

Each item has an ID for tracking. **[TV]** marks a physical-TV (G10) gate, which
needs owner observation when the item is human-visible.

### P — Playback reliability

Source: `defect-ledger.md`, `media3-upgrade-assessment-2026-07-28.md`.

| ID | Item | Exit |
|---|---|---|
| P1 | D14: Media3 rendered/dropped/skipped frame counters and A/V offset in diagnostics and the stats overlay | Counters visible; unit coverage of the model **Done 2026-10-12** |
| P2 | D24: retain a privacy-safe exception classification (cause class, top frame) with `Failed` | Detail line shows it; unit coverage **Done 2026-10-12** |
| P3 | **Deferred (owner 2026-10-12): the G10 has run fine all day; reopen only when a defect shows up in use.** G10 reliability session: zap stress (D12/D17), ORF1 matrix (D24), timeshift (D15), header fallback (D20), stutter (D14) **[TV]** | Each row attributed, fixed or closed with evidence |
| P4 | Deferred with P3. Fixes arising from P3 | Focused regression test per fix |
| P5 | Media3 1.11.1 device matrix and interlaced-motion comparison (assessment steps 5–6, doc still names 1.10.1) **[TV]** | Assessment updated to the current stack and gate passed |
| P6 | G08 intermittent bad render mode (`appliance-tcl-findings-2026-07-27.md`): decide observe-only or investigate | Owner decision recorded |

### D — DVR completeness

Source: SDK `DvrRepository`; `archive/recording-progress-sync-plan.md`;
`live-tv-dvr-conventions`.

| ID | Item | Exit |
|---|---|---|
| D1–D3 | **Dropped 2026-10-11** (owner): series rules stay in the Tvheadend web UI | — |
| D4 | Disk space in Recordings (`dvrDiskSpaceState`); owner requirement for 1.0 | Truthful unknown/unsupported state |
| D5, D6 | **Dropped 2026-10-11** (owner): no entry editing, no timer rules | — |
| D7 | **Shipped:** growing recordings play with a moving timeline (0e4e0fbf) and resume where the viewer stopped (0c4d14c5). The plan's status line was corrected and the plan archived on 2026-10-18 | Done |
| D8 | Real-server DVR mutation pass on the test server **[TV]** | Schedule, cancel and delete (and D9 if in) observed end to end |
| D9 | **In (owner 2026-10-12), after D4; droppable if time runs short.** Manual one-off recording: channel, day, start and end, sent as the SDK's `DvrSchedule.ExplicitTime`. No rule and no EPG dependency; the server records the window. The cost is a D-pad time-entry surface that follows U13 | Owner decision; then TV floor, tests, notices via `NoticeCenter` |

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
| H1 | **Done 2026-10-12 (result below).** Spike on the G10 and TCL: debug build that publishes a Watch Next recording, a preview channel and a search provider; record what each launcher renders **[TV]** | Matrix of what is visible per device; owner picks 1.0 scope |
| H2 | **Out of 1.0 (owner 2026-10-12).** Watch Next: in-progress recordings ("Continue watching"), deep link into recording playback with resume; removal on completion/delete | Only if H1 shows it renders or certification is pursued |
| H3 | **Out of 1.0 (owner 2026-10-12).** Preview channel ("Recently recorded" / "On now") with deep links | Only if H1 shows it renders on targets that matter |
| H4 | Global/voice search into Guide/Recordings. Google TV global search does not query app search providers (H1) | Stretch after 1.0 (U10) |
| H5 | **Out of 1.0 (owner 2026-10-12): no caller without H2–H3; the Appliance build enters the player through its own path.** Deep-link contract: one documented intent shape for channel, programme and recording, guarded by the Appliance profile | Tests for each link kind and profile |

Before the spike: H5 underlay H2–H4, and if nothing rendered on Google TV
without certification, scope would shrink to H5 plus the classic launcher.

H1 result on the G10 (Google TV launcher, 2026-10-12; spike branch
`spike/h1-home-screen`, commit `acdb82af`): the spike published 2 Watch Next
entries and a 3-entry preview channel, and the request to show the channel
went to the launcher without a consent dialog. Nothing appears on the Home tab:
there is no Continue watching row and no app channel row; every row below "Your
apps" is Google's own feed. A global search for a published title returns only
YouTube videos. The spike's search provider is not queried. On Google TV, H2 and
H3 render nothing without partner certification, and H4 has no path.
Decision (owner 2026-10-12): H1 is done and H2–H5 are out of 1.0. The Basic TV
mode of the production G08 (no Google account) uses the same Google TV launcher
with apps only, so no older launcher needs a test.

### U — UX rework (the main body of 1.0)

Owner, 2026-10-11: the UI/UX is far from settled. A home screen is missing,
and every screen needs rework, is unfinished or has nits. The earlier U list
(browse-density trial, spacing consolidation, `DESIGN.md` §12 items, "at most
one" player feature) was compiled from stale documents and is withdrawn:
spacing is largely done (`e6eaa110`, `24cbaf4c`, `71c55a19`, `eb39731c`),
several §12 items are settled or superseded, and the player has larger gaps.

The UX inventory (U2) is the source for the screen packages below. It is
drafted by the agent from code and captures and corrected by the owner; if the
draft is too far off, the owner lists the screens first and the agent fills in.
Each screen package is a bounded unit: brief and captures where direction is
open, the TV interaction floor, its own loading/empty/error states, and
screenshot-first UX review.

| ID | Item | Exit |
|---|---|---|
| U1 | Open trials, decided inside the rework of the screen they belong to: channel-rows screen (kept on `trial/channel-rows`), the two-row rail vs the in-place rail trial and Up-recents experiment, programme window B (default on) | Each kept, made a setting or dropped; branches pruned (X3) |
| U2 | UX inventory (`docs/ux-inventory-1.0.md`): per screen its current state, unfinished parts, nits and gaps; also re-checks `DESIGN.md` §12 against what is settled. First owner pass 2026-10-12 ("not far off"; owner direction recorded per screen) | Owner-corrected inventory; package order agreed |
| U3 | Home screen: new top-level destination, missing today. Autostart keeps opening the player, but its default is off (owner 2026-10-12; done in code, `settings/UiSettings.kt:15`). First package, because it changes navigation for every other screen; uses the H1 results and the S5 requirements (for example Back from the top level) | Brief accepted, implemented, reviewed |
| U4 | Channels rework: one or two more view modes (wall/grid, maybe an immersive list; channel-rows is a candidate); group tabs vs a top-right filter vs a setting. Introduces layout-matched skeleton loading as one shared, non-focusable component (owner 2026-10-16): the shell and layout show at once, placeholders sit where rows and cards will be, content crossfades in; every later screen rework reuses it instead of holding the launch frame or showing a spinner | From U2 |
| U5 | Guide rework, probably a complete rewrite for looks and performance (Guide performance numbers come from E7); OK opens details first with Watch focused on a live programme (owner, 2026-10-12; a direct-tune setting may follow); the Search entry is hidden for 1.0. Genre presentation in the grid (none today) and a genre filter (logic exists, no UI) are decided in this rework (owner, 2026-10-12). Both must work from category text as well as genre codes: Tvheadend's XMLTV importer only assigns a genre code when a category's text exactly matches one of its genre names, so Schedules Direct data mostly arrives without one, and category text only arrives when the grabber's "Scrape credits and extra information" option is on (off by default; checked in the Tvheadend source 2026-10-14). Past programmes after a cold start are wanted if the cost is proportionate; Tvheadend deletes ended programmes, so this needs the SDK to persist its guide history (SDK owner estimate, 2026-10-12: medium, about 2–4 days, no public API change with a fixed 6 h horizon, also fixes history lost on every reconnect; scheduled after SDK 0.27 once the Player commits to a guide-history UI; not needed for 1.0) | From U2 |
| U6 | Recordings rework: folder browse kept; in-progress recordings, watch status, D4 free space, list and details content; one or two more views (genre, channel); Back restores the previous folder; tabs overhauled or removed; D9 entry if in; recording length shows what was actually recorded, not the scheduled length (G10: "51m" for a short recording, 46m for a 9:06 file stopped early; "61m · Completed" for a 7 MB / 5 s file Tvheadend calls Completed OK), from the SDK's `DvrRecordingFile` start/stop, with a hint when far shorter than planned (threshold decided in U6); running recordings show the real stop including padding (`stopExtraMinutes`, effective value from the DVR profile) in the list, Schedule and recording player ("Recording until 16:30" while it records until 18:30 with 2 h post-padding), as the details page already does; watched-progress bars orange per DESIGN.md §2 progress-colour rule (details page included) | From U2 |
| U7 | Settings rework: sliding-depth browse kept, rows restructured; server connection editor. Onboarding (`ui/screens/OnboardingScreen.kt`) and the connection editor (`ui/screens/settings/SettingsConnection.kt`) need a redo (owner, 2026-10-18). Idea to decide here (owner, 2026-10-14): a setting to opt out of warm playback while browsing, and/or a finer choice to prefer programme art or the warm video on details pages | From U2 |
| U8 | Player polish and finishing: the gaps from U2, not a single extra feature. The player's programme-details screen (`ui/player/ProgramDetails.kt`, buttons `ProgrammeDetailsButton` in `ui/components/ProgrammeDetailsLayout.kt`) needs a redesign (owner, 2026-10-18, handed over from U4) | From U2 |
| U9 | Splash and startup: never looks hung. Revised 2026-10-16 after SDK 0.32 (saved rows draw in about 0.5 s): with saved data no in-app splash and no extra launch-frame hold (Android TV shows no splash icon by design; screens get skeletons in their reworks, see U4); without saved data an autoplay launch shows the animated splash and stage messages until channels arrive, never flashing, and a launch without autoplay opens on the screens' own loading states (`core/MainStartupPresentation.kt`); unreachable server offers Try again and Edit connection; no counts, guide wait or Skip action. Done 2026-10-17 with D26; the autoplay startup screen dissolves into the first video frames over 800 ms, and any key ends that early | Done 2026-10-17 |
| U10 | Search: hidden for 1.0 (owner, 2026-10-12). A global search, and what it covers, is a stretch feature after 1.0 | Guide search entry hidden |
| U11 | Physical acceptance of the reworked screens **[TV]** | Observation only |
| U12 | Screenshot goldens for production composables once screens settle | After U3–U10 |
| U13 | **Done 2026-10-11.** 12/24-hour clock (owner: required for 1.0): an app setting "Clock format" with System (default, follows `DateFormat.is24HourFormat`), 12-hour and 24-hour, applied through one shared time formatter. The setting goes in Settings → General (`ui/screens/settings/SettingsGeneral.kt`), and a change is applied immediately without a restart. Today the format is fixed at `HH:mm` in `ui/common/TimeConverter.kt:9`, `ui/player/ProgrammeWindow.kt:146`, `ui/notifications/NoticeFormatter.kt:93`, `ui/screens/EpgGridScreen.kt:2408` and `ui/screens/recordings/RecordingsContent.kt:873`. Any later time entry (D9) follows the same setting. Re-check widths of the Guide time ruler, rail, Banner and cards for "12:45 PM" | By default all times follow the system setting, and the override wins when set; JVM tests for all three options in both locales; setting row meets the TV floor (focus, Back, TalkBack); captures in 12-hour mode |
| U14 | Remote keys in the open app, both builds, no accessibility service: GUIDE opens our Guide, the TV key goes to the live player, LIST keeps opening the channel shelf. Owner check on the G10 (2026-10-11, service off): the keys reach the open app, which routes them to the wrong screens; the mapping is settled with the UX rework, and more keys than GUIDE and TV are likely (owner). Today the foreground app has no GUIDE→Guide mapping (`KEYCODE_GUIDE` appears only in `ui/startup/MainStartupKeyCycleOwner.kt` and the accessibility service), and TCL's TV key `4001` (`core/ApplianceEntryPolicy.kt`) is handled only by the service. Step 1: record the exact key codes the foreground app receives for GUIDE and TV **[TV]** | Each key consumed and routed from every screen; key policy JVM tests; observed on the G10 |
| U15 | Shared patterns used by every screen, decided before the screen packages and applied in each: Menu/long-press context actions, one details surface family, and an overhaul of error delivery and recovery (one vocabulary, recovery actions, translated reasons instead of raw codes). Owner agreed these are gaps (2026-10-12) | Patterns accepted in a brief; each screen package applies them |
| U16 | Entry focus and Back across screens: focus lands consistently when a screen is entered from the side rail, and Back meets TV-DB. Owner: deferred until the screens are feature complete | One rule, applied everywhere; key-policy tests |
| U17 | Locale-aware dates and numbers everywhere (owner 2026-10-14), the same way U13 did the clock: one shared formatter set, used by every screen, following the app's selected language, so new translations need no formatting work. Today dates use fixed patterns: "EEE d MMM" in the Guide and Recordings (`ui/screens/EpgGridScreen.kt:2286`, `ui/screens/recordings/RecordingsContent.kt:863`), "EEEE d MMMM" for Recordings headers (`RecordingsContent.kt:842`), and a German/English switch in `ui/common/TimeConverter.kt:90`; only "First aired" uses the locale's medium date. Numbers: file sizes are hand-built "%.1f GB" (`RecordingsContent.kt:893-908`), cache size in `core/CacheStatisticsFormatting.kt`; the player statistics overlay uses `Locale.ROOT` on purpose (diagnostics) and timecodes stay "h:mm:ss". Formats come from Android for the locale (`DateFormat.getBestDateTimePattern` skeletons, `NumberFormat`, `Formatter.formatShortFileSize`), named by use (short day label, day header, short numeric date, file size). Do it before the screen packages so they use it | No fixed date pattern or hand-built number format left in UI code outside the shared set; JVM tests for each format in en-US and de; captures re-checked for width |

### E — Performance and benchmarks

Sources: `profiling.md`, `responsive-ui-implementation-record.md`,
`navigation-startup-profiling-2026-09-10.md`,
`residual-main-menu-performance-2026-09-11.md`.

Current state:

- Profiling is manual ADB/Perfetto (`tools/profiling/`).
- The `:baselineprofile` module generates the baseline and startup profiles
  (`baselineprofile/build.gradle.kts`, journeys in
  `baselineprofile/src/main/java/at/bernhardberger/tvhplayer/baselineprofile/PlayerBaselineProfile.kt`)
  into `app/src/main/generated/baselineProfiles/`. It uses the Macrobenchmark
  library for generation only; E3 covers the minimal journeys.
- There is no benchmark module that records timings, no machine-readable
  budgets and no regression detection (E2).

Measured on the G10, 2026-09:

- about 8 s from process bind to first admitted tune;
- zap to first frame 0.5–3.4 s;
- Guide page return 286–353 ms;
- rail reopen 209–265 ms;
- cold main menu misses the declared 100/200 ms budgets.

| ID | Item | Exit |
|---|---|---|
| E1 | **Dropped 2026-10-11** (owner): no numeric targets or budget file. The 50/100/200 ms figures came from the retired responsive-UI plan and were never validated on the G10. Benchmarks record numbers; the owner's judgment on the G10 decides what is slow | — |
| E2 | Macrobenchmark module with the fixture (`profile`) data path: cold/warm startup and live playback first; Channels scroll, Guide navigation, rail open, Recordings and Settings once those screens are reworked | Runs on the emulator lane; results stored per run |
| E3 | Generated baseline and startup profiles (AGP baseline-profile plugin) replacing the hand-written file; `profileinstaller` declared explicitly (it is already present through Compose). Needs only the generator module and a few journeys, not R2 or the full E2 suite; regenerate after UI changes | Generated profile committed; APK-only vs `.dm` comparison recorded; G10 install tooling applies the profile and compiles it (measured 2026-10-11 with the hand-written profile: about 9 s per install); re-profiling reminder, not a gate: a `release-process.md` checklist line plus an agent rule to remind the owner after a UI batch that changes screens or navigation |
| E4 | Fix the optimized instrumentation runner (`androidx.tracing` stripped by R8) | Optimized fixture tests pass |
| E5 | Startup decomposition and work: split SDK cache restore, metadata publication and autoplay readiness; target the 8 s path; the visible side is U9. SDK owner answer (2026-10-12): initial-sync progress (stage, guide channel N of M, approximate) was proposed for SDK 0.27 but is on hold: the SDK owner measured the initial sync against the real server from the build machine at under 0.2 s, and the owner judged the splash fast on the G10 with SDK 0.26 (not timed). It returns only if a G10 measurement of Connecting → Synchronizing → Ready shows a long sync. Now/next first is possible but not small (needs a 2–3 h first window, a two-phase enable and cache/coverage guards), so the owner skipped it. Without autostart the app already opens on cached data; with autostart the splash waits for the full sync even when a cache exists (`core/MainStartupPresentation.kt`: SyncingChannels stays on the splash), so warm-start autostart can start from the cache | Before/after numbers on the G10 **[TV]** |
| E6 | Zap decomposition: first-media, codec-init and render markers; then optimize the dominant segment | Dominant segment improved or attributed to server/tuner |
| E7 | Guide and rail: cold Guide construction, SDK metadata lock contention, rail reopen and tag-cycle layout, on the reworked screens | Before/after numbers on the G10; owner judges it fluid **[TV]** |
| E8 | Pre-release look at the benchmark numbers against the previous release; large regressions are investigated. No automatic threshold gate | Release checklist entry |
| E9 | Operator fluidity acceptance on the optimized release build **[TV]** | Recorded owner judgment |

E8 depends on R2 (minified release), so what is measured is what ships. Keep E2–E8 small: Jetpack Macrobenchmark in one module and a table of numbers; no home-grown budget, threshold or gating code. E3 generation is a separate, manually run task (not part of every build or deploy); builds only package the committed profile.

### A — Appliance profile

Source: `appliance-mode-spec.md`, `appliance-tcl-findings-2026-07-27.md`.

| ID | Item | Exit |
|---|---|---|
| A1 | HOME role on the TCL is ignored by firmware: record the accepted GUIDE accessibility workaround as the 1.0 behavior, or pursue further | Owner decision recorded in the spec |
| A2 | Residual gates: GUIDE/wake retest, process death, held repeat, TalkBack **[TV]** | Observed on the current build |
| A3 | ~~Deep links from the home screen (H5) respect Appliance containment~~ Dropped with H5 (2026-10-12) | — |

### R — Release hardening

Source: `release-process.md`, `product-identity-plan.md`; the open dependency
and quality items of the archived product-architecture remediation plan
(§13–15) are R3, R4, R7 and U12; the Compose compiler metrics stay a
diagnostic for an identified recomposition question, not a deliverable.

| ID | Item | Exit |
|---|---|---|
| R1 | Native gate: run `./tools/check-native-libs --release` on the release candidate; correct stale "blocked" claims (appliance plan, codebase audit AUD-008) | Gate passes; docs current |
| R2 | R8 for release: keep rules for Koin, serialization restoration, native decoders; licenses and corresponding source unaffected. Done 2026-10-15: no extra keep rules needed, mappings kept with each release; Play release smoked on the G10, Appliance release smoke is a release-time step | Minified release passes `./tools/verify` and a G10 smoke **[TV]** |
| R3 | Dependency hygiene §13: Material Components re-parent (visual smoke for splash, system bars and the mobile-Material primitives still in use), Coil OkHttp artifact (only after proving no valid artwork request reaches the generic HTTP fetcher, then artwork check on the target TV), extended icons (committed vectors or the smallest supported source, with the APK change measured), inert dependencies (only with source and resolved-graph proof) — each its own package, never bundled into player or navigation work | APK size and visual smoke per package |
| R4 | Accessibility: TalkBack pass across Channels, Guide, Recordings, Settings, player **[TV]** | Findings fixed or recorded |
| R5 | Release notes, privacy policy and `NOTICE.md` checked against the artifact (identity plan item 5) | Checked |
| R6 | Signed-release acceptance: prepare on LXC 106, sign on LXC 117, verify, G10 install and same-key update, playback/HOME/GUIDE/standby/reboot matrix **[TV]** | `release-process.md` end to end |
| R7 | Dependency-update and wrapper-validation automation (separately authorized repository package); playback-critical pinned coordinates stay manually reviewed | Optional for 1.0 |

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

Done since the audit: the `play` and `appliance` flavors
(`app/build.gradle.kts:67-77`; the Appliance build adds `.appliance` to the
application ID and `APPLIANCE_BUILD`) and the Play bundle in the release pipeline
(`tools/prepare-release:46-50` builds `:app:bundlePlayRelease` and checks
`app/build/outputs/bundle/playRelease/app-play-release.aab`).

Gaps:

- There are no store assets and no current screenshots.
- A reviewer cannot use the app without a Tvheadend server.

| ID | Item | Exit |
|---|---|---|
| S1 | Distribution model. **Decided (owner, 2026-10-11):** two builds, a Play build and an Appliance build, and both APKs are attached to every GitHub release so users can install newer builds before Play review finishes. Keep it minimal: one build dimension and one release pipeline that produces both, with no second code path. For a GitHub APK to update a Play install (and the other way round), Play App Signing must use our existing release key ("use your own key" at enrollment), not a Google-generated key. Originally proposed: the Play build keeps the current application ID; the Appliance sideload build gets its own ID (for example an `.appliance` suffix) so a Play update can never replace an Appliance install with the build that lacks GUIDE/HOME. Both can be installed side by side. Existing Appliance TVs are set up once more (0.x needs no migration). Needs a `product-identity-plan.md` entry | Decision recorded in `release-process.md` and `product-identity-plan.md` |
| S2 | Play account: personal accounts created after 2023-11-13 need a closed test with ≥12 testers opted in for 14 consecutive days before production; organization accounts are exempt (<https://support.google.com/googleplay/android-developer/answer/14151465>) | Decided (owner, 2026-10-11): personal account, created 2026-10-11, so the closed test applies. Exit: ≥12 testers recruited before the 0.9 RC |
| S3 | Appliance boundary for Play: the GUIDE accessibility service is a high policy risk (launcher/automation is not an accessibility tool; it would need a prominent disclosure, consent and a video declaration: <https://support.google.com/googleplay/android-developer/answer/10964491>). The HOME filter is an unverified risk. Decided (owner): a `play` build without both, and an Appliance sideload build with them; product profile logic unchanged. In-app GUIDE/TV handling (U14) is in both builds; the Appliance build adds HOME, boot/wake entry and capturing GUIDE/TV while the app is closed. That is the minimum (owner, 2026-10-11): the Appliance build may later add more, including player behavior such as exit gating from the dropped Simple TV mode, decided case by case. The Appliance build is a hard accessibility requirement for the household (autostart, GUIDE/HOME never leave the player) and stays a fully supported, release-gated build, not a leftover | Flavor or manifest split; tests prove the Play manifest lacks both, the Appliance manifest keeps both, and Play behavior is unchanged; each release checks the Appliance build on the G10 |
| S4 | Reviewer access. **Owner (2026-10-11): no permanently hosted server.** Research (2026-10-11): Google requires that reviewers can reach all functionality "at all times" (<https://support.google.com/googleplay/android-developer/answer/15748846>), and lists demo accounts, QR codes and instructions as ways to give access. No verified precedent shows a server-dependent client passing on instructions or a video alone, and TVHClient and dream Player do not say how they pass review. Plan: submit the closed-test release (reviewed like production) with precise instructions and a demo video, and fall back only if Google rejects it. Fallbacks: a server run only during review windows (risk: a later re-review finds it offline), or the options below. Any hosted fallback is weak: it puts an unhardened Tvheadend and plaintext HTSP on the internet, and it is reached over an intercontinental link of unknown quality, so a slow or failing session can itself be judged broken functionality. Earlier proposal: a small hosted demo Tvheadend (own VM/container, no tuners or OSCam, 2–3 IPTV channels looping CC-licensed films, a scripted XMLTV guide, a few recordings, a review-only account) with the credentials in Play Console "App access". An in-app demo mode was rejected: it would need a fake server, guide, DVR and stream path that the real app does not use and would have to be kept in step with them. The demo server is internet-exposed infrastructure and needs separate owner approval | Closed-test release accepted with the chosen access route; if a fallback is needed, the owner approves it |
| S5 | TV quality audit against every checklist ID. Since May 2026 the guidelines have three tiers: Tier 3 "TV Ready" (the required checklist), Tier 2 "TV Optimized" and Tier 1 "TV Differentiated". The owner wants as much of tiers 2 and 1 as possible (2026-10-12); in-app search is no longer required (TV-SB removed). Tier 2/1 items and where they land: TV-BP baseline profiles (E3), TV-4K high-resolution icon, banner and cards (store assets), TV-LC stored credentials (done), TV-LI mobile/Google login (no fit for server credentials; documented deviation), TV-VS voice input in search (not applicable while search is hidden, U10), TV-VC voice commands through `MediaSession`, TV-EC/TV-ER Engage (partner-only, H1), TV-FR frame-rate requests (frame-rate matching exists; confirm), TV-AO audio output switching, TV-PI picture profiles, TV-TO pointer and touch, TV-SA spatial audio, TV-AX enhanced accessibility (audio description preference exists), TV-AI (no plan); each item is covered, planned or a documented deviation. Likely review risks: TV-DB (Back from the top level returns to the TV home screen), TV-PC/TV-PP (D-pad center and media play/pause toggle playback; Center already toggles pause where live pause or a recording allows it, and otherwise shows the reason on the Banner, which needs only confirming), TV-OV overscan, TV-ME low-RAM devices, TV-BU/TV-BY ambient mode. Validate `MediaSession` with the Media Session Validator | Each ID passes, or is a documented deviation with rationale |
| S6 | AAB in the release pipeline (`tools/prepare-release`, `release-process.md`): build, align and verify the bundle; keep the APK for sideload; prove 16 KB alignment with `bundletool` | Signed AAB and APK from the same commit |
| S7 | Store listing: TV screenshots from real devices, banner, English and German descriptions naming Android TV, hosted privacy-policy URL (mention the accessibility service in the Appliance build) | Listing complete in Play Console |
| S8 | App content declarations: Data safety (credentials stored on device and sent only to the user's server; unencrypted HTSP disclosed), content rating, target audience, ads, any foreground-service types | Declarations submitted |
| S9 | GPLv3 on Play: confirm corresponding-source offer and compatibility of Play distribution terms (licensing check, not a Play-docs question) | Owner-accepted conclusion recorded in `product-identity-plan.md` |
| S10 | Developer verification for sideloaded installs (global rollout 2027): register the package and key | Registered before 1.0 |
| S11 | Closed test track: run the S2 window on the 0.9 RC | 14 days completed; feedback triaged |
| S12 | Watch-ahead: targetSdk 37 makes local-network access a runtime permission. Not for 1.0 while targeting 36 | Noted in the release checklist |
| S13 | README screenshots. **Decided (owner, 2026-10-18):** three images (Channels, Guide, the player with its banner) from offline captures of the production screens with the fictional fixture pack, never device captures (video comes out black while playing, and real channels would show). The player image fakes the playing picture with one of the pack's Grok `still-*` frames behind the banner, from the same line-up row as the banner's channel and programme, as `PlayerChromeCaptureTest` already does. Pick the still from the captures; not Harbor Kickoff (owner), proposed Lantern Road (Ion Frame, `still-lantern-road`), then Ridge Light. Added with the 0.3.0 release, after U4 is merged; refreshed when a pictured screen changes and at 1.0. The README holds a placeholder comment for them | Three current images in the README at each release |
| S14 | README features section for people deciding whether to switch from another client. **Decided (owner, 2026-10-18):** facts about this app only, no comparison with other apps. Headline: the TV app is built for TV and follows Material Design for TV, not a phone layout adapted to a big screen. Name artwork support (logos kept offline, programme images, tinted details page, cast and crew; more only once the Tvheadend change is merged). Limits that will change read as "not yet": series rules in the Tvheadend web interface for now; English and German so far, with right-to-left layouts ready and translations welcome; a phone and tablet version with its own touch interface is planned after the TV 1.0 release. No line about HTSP encryption (a protocol limit, not ours). Written with S13 after U4 is merged, matching what 0.3.0 ships; rechecked against the out-of-scope list at 1.0 | Section in the README at 0.3.0 |

### X — Housekeeping

| ID | Item |
|---|---|
| X1 | ~~Empty `responsive-ui-foundations-plan.md`~~ Deleted 2026-10-18; E5/E7 use `profiling.md` and `responsive-ui-implementation-record.md` |
| X2 | ~~Close or archive completed plans~~ Done 2026-10-18: the player overhaul, persistent cache, product-architecture remediation, recording-progress, appliance and browse-cohesion plans and the player UI inventory are in `archive/`; the open items of the remediation plan are R3 and R7 |
| X3 | Prune merged and abandoned trial branches and worktrees after U1 |
| X4 | Defect ledger: resolve D22 (reopened 2026-10-18: the Channels-down cue the ledger records as removed is still drawn, `ui/player/VideoPlayerScreen.kt:1775`), archive the ledger once P3/P4 clear it |

## Milestones

| Version | Content | Exit |
|---|---|---|
| 0.3 | Done so far: P1–P2, H1, U2 inventory, U15 shared patterns, U17 locale formats, E3 (minimal journeys), R2, S1/S3 split, S6 AAB, D4, U9 startup, player entry and picture cover, display frame-rate matching from the stream's declared frame rate (SDK 0.34.0), artwork context (SDK 0.33.0); repository, docs and release material checked for stale content and naming first | Signed Play and Appliance release on GitHub; release smoke on the G10; README screenshots and features section (S13, S14) |
| 0.4 | U4 Channels; error catalog wired in three slices (live player, recording player, browse screens) | Channels accepted on the G10; no raw codes outside the stats overlay |
| 0.5 | U6 Recordings (D9 inside, real recorded length and padded stop) | Recordings accepted on the G10 |
| 0.6–0.7 | Remaining screens in the U2 order, U3 Home where it fits, E4, U14; U1 trials decided in place; E5/E6 alongside | Each screen accepted as it lands |
| 0.8 | U16 entry focus and Back; E2 full journeys, E7, E8 on the settled screens; S5 audit and fixes; U12 goldens | Numbers recorded; TV checklist passes or deviations documented |
| 0.9 RC | U11, P5, A1–A2, R1, R3–R5, E9, S7–S10, X1–X4; S4 reviewer instructions and demo video, recorded on the RC before the closed-test submission; S11 closed test | All [TV] gates observed; closed test running |
| 1.0 | R6, Play production release after S11 | Signed release on GitHub and Play |

The S2 closed-test window (14 days) and Play review (about 7 days) put at least
three weeks between the 0.9 RC and a Play 1.0. The account is personal, so the
tester pool needs recruiting before 0.9.

Order rationale:

- Reliability, the UX inventory and the home-screen integration spike come
  first because they change what later work builds on.
- The UX rework is the main body. Work that does not depend on the screens
  (playback, build split, minified release, AAB, startup and zap internals,
  profile generation) runs alongside it and stays valid whatever the screens
  become.
- Work that does depend on the screens (benchmark journeys beyond startup and
  playback, Guide and rail performance, audit fixes, goldens, store screenshots,
  demo video) waits until the screens settle, so it is not built twice.
- TV quality requirements and H1 results feed the UX work as inputs rather than
  arriving as late fixes.
- Physical-TV gates are batched per milestone so the owner observes once, with
  the G10 sessions clustered at 0.3 (reliability) and 0.9 (acceptance).

## Owner decisions needed

1. Remaining U2 questions, each settled inside its screen's rework with
   prototypes and trials rather than up front (owner 2026-10-12): Channels view
   modes and group tabs (U4), Recordings views and tabs (U6), continue watching
   on Home or in Recordings (U3/U6).
2. U1 trials, each inside its screen's rework.
3. S9 GPLv3-on-Play conclusion.
4. A1 and P6.
