# TVHeadend Player Engineering Guide

TVHeadend Player for TV is an independently developed, public GPLv3 Android TV
client for TVHeadend servers. It descends from
[`Preclikos/tvhstream`](https://github.com/Preclikos/tvhstream) and preserves the
upstream history, copyright, and a clean path for contributing generic fixes
back to that project.

## Start here

Before non-trivial work:

1. Read `docs/appliance-mode-spec.md`.
2. Read `docs/device-targets.md` before any build, install, ADB, or physical-TV
   work.
3. Read `docs/appliance-mode-plan.md` and identify the current task.
4. Read `docs/codebase-audit-2026-07-23.md` for hardening work and
   `docs/product-identity-plan.md` for identity work.
5. Run `git status -sb` and inspect the recent log.
6. Fetch configured remotes before changing code: `git fetch --all --prune`.
7. Run `./tools/check-ai-harness` when changing agents, skills, commands, or
   OpenCode configuration.

The standalone product repository is `bernhardberger/tvheadend-player`. A
separately configured `upstream` remote may point at `Preclikos/tvhstream` for
comparison and generic contributions; never push product or appliance work to
that predecessor repository.

## Current product boundary

The public product is a remote-first Android TV live-TV client for TVHeadend.
Appliance behavior is an optional profile and household integration layer, not
the identity of every core type or workflow. Distribution is GitHub-first while
remaining compatible with a later Google Play path.

The accepted playback baseline is upstream TVHStream's Media3/HTSP path.

Current product identity:

- Application ID: `at.bernhardberger.tvhplayer`
- Public name: `TVHeadend Player for TV`
- Launcher label: `TVHeadend Player`
- Source namespace: `at.bernhardberger.tvhplayer`
- Minimum SDK: 28
- Target/compile SDK: 36
- Java toolchain: 21
- Household device ABI: `armeabi-v7a`
- Current packaged ABIs: `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`

Do not alter the HTSP extractor, Media3 playback path, or decoder behavior as a
side effect of appliance-shell work. Progressive and interlaced playback are
regression gates.

Focusable TV UI uses `androidx.tv:tv-material`. Mobile Material remains only
for primitives that TV Material 1.1.0 does not provide here, such as text
fields, progress/dividers, and selected dialog primitives. Do not introduce a
second competing theme or move focusable controls back to mobile Material.

## TV UX and UI requirements

- Google TV is the target product experience, while Android TV OS remains the
  platform and API layer. Treat current official Google TV and Android TV design
  guidance, Compose for TV guidance, and Material for TV component behavior as
  the baseline for every UI change. Check current documentation and official
  samples before inventing an interaction pattern or working around an
  unfamiliar component.
- Prefer official Material for TV components and semantics. Do not recreate a
  component that the installed TV Material version provides, and do not use a
  focusable mobile Material control when a TV equivalent exists.
- Design for a ten-foot, 16:9, remote-only experience first. Every screen must
  have deterministic initial focus, complete D-pad reachability, predictable
  Back behavior, visible focus at viewing distance, and focus restoration after
  navigation or recomposition. Avoid dead focus containers and scale effects
  that clip inside lists or safe areas.
- Treat key dispatch as stateful navigation. Consume an event when it reveals,
  replaces, or moves focus to new UI so that the same press cannot activate the
  newly focused control. Cover these transitions with policy or UI tests.
- Respect TV-safe content margins, large readable type, concise labels, strong
  focused/unfocused contrast, and stable layouts for long localized text.
  Touch-only affordances, hover assumptions, dense mobile forms, and subtle
  color-only state are not acceptable TV interactions.
- For video-backed UI, preserve the live picture with deliberate scrims and
  layered surfaces rather than uncontrolled transparency. Keep dense guides and
  settings more opaque than browsing chrome, keep focused controls strongest,
  and avoid blur or effects that add GPU cost without improving comprehension.
- Preserve accessibility semantics, meaningful content descriptions, and clear
  selected/focused state. Validate loading, empty, error, reconnecting, and
  long-content states rather than only the happy path.
- Compile focused Compose UI coverage where practical, then validate focus,
  clipping, readability, and motion on the physical TV. ADB screenshots cannot
  prove SurfaceView video visibility or human-perceived motion quality.

## Engineering workflow

- State assumptions before ambiguous or architectural work.
- Keep `docs/appliance-mode-spec.md` and `docs/appliance-mode-plan.md` current
  when requirements or architecture change.
- Implement one small slice at a time.
- For behavior changes, write a failing test first, then the minimum code to
  make it pass.
- Run the focused test while iterating and `./tools/verify` before committing.
- Treat warnings from `./tools/check-native-libs` as signed-release blockers,
  not as permission to invent missing provenance. The stricter
  `./tools/check-native-libs --release` must pass before distributing a signed
  build from this fork.
- Review every diff for secrets, unrelated churn, upstreamability, and GPLv3
  attribution before pushing.
- Do not spawn background or automatic subagents. The project agents are for
  direct selection and explicit review sessions.

Use the project-local skills when relevant:

- `android-tv-device-testing` for ADB, TCL runtime checks, remote keys, playback,
  HOME, standby/wake, and reboot validation.
- `tvhstream-upstream-contribution` when syncing upstream or preparing a generic
  change for an upstream pull request.

## Commands

```bash
# Full local verification
./tools/verify

# AI harness/config validation
./tools/check-ai-harness

# Device wrapper help
./tools/device --help

# Native AAR integrity and 16 KB ELF alignment
./tools/check-native-libs

# Configure a local device without committing its address
cp .tvhplayer-device.example.json .tvhplayer-device.json
./tools/device doctor
```

The Gradle portion of verification is:

```bash
./gradlew testDebugUnitTest lintDebug compileDebugAndroidTestKotlin assembleDebug --no-daemon
```

## Code layout

- `app/src/main/java/at/bernhardberger/tvhplayer/` — application source
- `app/src/test/` — JVM unit tests for pure policy and navigation logic
- `app/src/androidTest/` — device/instrumentation tests
- `docs/` — appliance specification, plan, and engineering/operator notes
- `tools/` — repeatable local build and device workflows
- `.opencode/` — project agents, skills, commands, and OpenCode configuration
- `app/libs/native-dependencies.json` — audited hashes/layout and explicit
  release-provenance status for bundled native AARs

Keep policy code independent from Android UI where practical so it can be
covered by fast JVM tests. Match the existing Kotlin and Compose style; do not
introduce abstractions for a single use.

## Device and credential safety

- TVHeadend credentials belong only in an ignored owner-only local secret file
  while provisioning and in Android app-private storage afterward. Never put
  their values in command arguments, Git, committed config, Gradle properties,
  scripts, command history, issue text, console output, logs, screenshots, or
  generated reports.
- Never use `uiautomator dump`, broad `dumpsys`, unrestricted `logcat`, or app
  data exports while a credential field or secret-bearing screen may be
  present. Prefer bounded commands in `./tools/device`.
- Do not add debug-only exported receivers, activities, services, or content
  providers for credential injection.
- Automated credential provisioning is permitted only through
  `./tools/device provision-test-credentials`, for a local device configured
  with `role: "test"` whose live manufacturer, model, device, and product exactly
  match the local expectations. Production and unclassified devices remain
  prohibited.
- Provisioning must use the debug-only app-private staging mechanism and local
  secret file described in `docs/test-device-credential-provisioning.md`. Do
  not replace it with synthetic keyboard input, raw ADB arguments, UI automation,
  or an exported Android component.
- Never commit signing keys, keystores, key passwords, service-account JSON, or
  Firebase configuration.
- Runtime device addresses belong in ignored `.tvhplayer-device.json` or the
  `TVHPLAYER_ADB_SERIAL` environment variable.
- The dining-room G10 is the current development target and may use local role
  `test`. The bedroom G08 is handed-over production and must use role
  `production`. The exact identities and lifecycle rules are in
  `docs/device-targets.md`.
- `tools/device` rejects install, launch, force-stop, smoke, synthetic key, and
  credential-provisioning actions unless the local role is `test` and all four
  expected identity properties match.
- Do not modify TVHeadend server accounts, tuners, OSCam, recording storage,
  stream profiles, TCL/Google packages, or network infrastructure from this
  repository unless the user explicitly approves that separate operation.
- Keep Google Basic TV, Headent, and the upstream-package diagnostic client as
  rollback paths until appliance validation is complete.

For physical tests that require the user to press a remote button, observe the
TV, or act within a time window, ask one focused question and wait for the
result. Do not infer a human-visible motion-quality pass from ADB counters.

## Appliance behavior invariants

- Persist only a channel that was actually sent to the player; UI focus alone
  must not change the last-played channel.
- `CH+` and `CH-` wrap through the current ordered channel list.
- Appliance launches are one-shot requests consumed only after channel data is
  available.
- Back from playback returns to the normal UI and must not trigger an autoplay
  loop.
- The accessibility service, when implemented, consumes only GUIDE and must not
  inspect window content or become general UI automation.
- Google/TCL packages remain installed and reversible throughout validation.

## Upstreamability and licensing

Classify each change before implementation:

- **Generic:** useful to the predecessor TVHStream project without TVHeadend
  Player assumptions. Keep it separable and suitable for an upstream pull
  request.
- **Product-specific:** TVHeadend Player identity, public UX, release policy, or
  repository metadata. Keep it in this repository.
- **Appliance-specific:** HOME behavior, TCL GUIDE handling, household defaults,
  device deployment, or signing. Keep it in this repository.
- **Mixed:** split the generic foundation from the appliance integration before
  committing.

Do not rewrite upstream attribution or imply that this fork is wholly original.
Distributed combined binaries and corresponding source remain GPLv3.
Bundled Media3 decoder AARs currently have incomplete provenance and notices;
do not publish a signed binary until the native release gate and license/source
obligations are satisfied.

## Git discipline

- Direct local development on `master` is allowed during rapid iteration. Use a
  branch for upstream contributions, parallel work, or risky experiments.
- Keep commits small, buildable, and independently reviewable.
- Do not force-push, rewrite published history, or amend commits unless the user
  explicitly asks.
- Before committing, inspect `git status`, `git diff`, and recent history.
- Before an upstream pull request, compare the proposed range with the configured
  `Preclikos/tvhstream` remote and remove product/appliance assumptions.
