# Google Play closed test: preparation pack

**Status:** Active plan. The owner decided on 2026-10-28 to start the Google
Play closed test with 0.3.0 instead of the 0.9 RC (roadmap S2/S11). Prepared on
2026-10-28 from `main` 0a3465ba; the upload artifact is the signed 0.3.0 Play
bundle (versionCode 162, source commit 1870e331).
**Scope:** Everything the owner needs to click through Play Console for the
first closed-test release: the Console checklist, store listing text, graphics,
App content answers, reviewer instructions, the demo video plan, tester texts
and open risks. Agents never touch Play Console, the owner's Google account, the
signing host or any key.
**Closure:** Once production access is granted, move the lasting facts (signing
enrollment, listing ownership, the review route that worked) into
`release-process.md` and archive this plan.

Every Play statement below cites a Google page fetched on 2026-10-28. Where a
page did not answer a question, this document says so instead of guessing.
Google changes Console labels often; when a label differs, follow the meaning.

## Contents

Section numbers stay the same, so references such as "1.9" or "3.0" still work.

- [1. Click-through checklist](play-store/checklist.md)
- [2. Store listing](play-store/listing.md)
- [3. Graphics](play-store/graphics.md)
- [4. App content answers](play-store/app-content.md)
- [5. Reviewer instructions and demo video](play-store/review.md)
- [6. Testers](play-store/testers.md)
- [7. Open risks](play-store/risks.md)
- [8. Owner decisions](play-store/decisions.md)

## What gets uploaded

| Item | Value |
|---|---|
| Bundle | `build/release/signed/0.3.0/tvheadend-player-0.3.0-play.aab` in the main checkout (`/root/projects/tvheadend-player`) |
| Application ID | `at.bernhardberger.tvhplayer` (never the `.appliance` build) |
| Version | 0.3.0, versionCode 162, source commit 1870e331 |
| Signing | Project release key, alias `tvhplayer-release`, certificate SHA-256 in [`release-process.md`](release-process.md#release-key) |
| R8 mapping | `tvheadend-player-0.3.0-play-mapping.txt` in the same directory |
| Corresponding source | GitHub release [`v0.3.0`](https://github.com/bernhardberger/tvheadend-player/releases/tag/v0.3.0) (source archives, SDK and FFmpeg source attached) |

Read from the signed artifacts on 2026-10-28 (`aapt2 dump badging` on the Play
APK, `bundletool dump manifest` on the AAB): minSdk 28, targetSdk 36;
`android.software.leanback` required, touchscreen not required; permissions
`INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK` and the app's own
signature-level `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (added by AndroidX);
`allowBackup="false"`; no foreground service, no accessibility service, no
`AD_ID` permission. Native code: arm64-v8a, armeabi-v7a, x86, x86_64.
