# 3. Graphics

Requirements from <https://support.google.com/googleplay/android-developer/answer/9866151>:
app icon 512×512, 32-bit PNG with alpha, at most 1024 KB; feature graphic
1024×500, JPEG or 24-bit PNG without alpha; screenshots JPEG or 24-bit PNG
without alpha, 320 to 3840 px, the long side at most twice the short side, up to
eight per device type, and at least one Android TV screenshot; TV banner
1280×720, JPEG or 24-bit PNG without alpha.

## 3.0 Uploaded set (2026-10-29, screenshots replaced 2026-11-01 for 0.4.0)

Saved as a draft on the default (en-US) listing. The 0.4.0 set now in Play Console
was made with an earlier version of these captures. Regenerate the feature
graphic and screenshots with `tools/prepare-play-assets.py`, following
[Play Store set](../presentation-captures.md#play-store-set). Its outputs are in
ignored `app/src/play/play/listings/en-US/graphics/`. Upload them through the
[release process](../release-process.md#screenshots), not manually in Console.
Uploading replaces a whole image type's set when it differs. The icon source is
`app/src/main/ic_launcher-playstore.png`; the banner source is
`artwork/tvheadend-player-banner@4x.png`. Update the date above after an
owner-approved replacement is uploaded.

| Slot | File |
|---|---|
| App icon | `icon/01.png`, fully opaque RGBA from `app/src/main/ic_launcher-playstore.png` |
| Feature graphic | `feature-graphic/01.png`: mark and name, "Built around the remote.", the Channels screen in a slim frame |
| Android TV screenshots, in order | `tv-screenshots/01.png` through `07.png`: Home, Channels, Guide (Dense rows), Player, Recordings, details, schedule |
| TV banner | `tv-banner/01.png`, byte copy of `artwork/tvheadend-player-banner@4x.png` |
| Phone screenshots | none |
| 10-inch tablet screenshots | `large-tablet-screenshots/01.png` (Channels), `02.png` (Guide). A full save needs two phone or tablet screenshots, and the Android TV opt-in needs a fully saved listing, so these break the loop. After the opt-in (2026-10-29) Console still refuses to save without them ("Upload at least 2 phone or tablet screenshots"). Try again once TV is approved. Android XR has no opt-out in Console; the leanback requirement keeps phones and headsets from installing |
| AI asset declaration | "Don't label assets" (owner, 2026-10-29): fictional sample art inside app screens, not depicting anything real |
| Video | none yet, see [5.2](review.md#52-demo-video-shot-list) |

The AI declaration stays as chosen in Console; GPP 4.1.1 does not send
`aiGeneratedState` and does not set it.

The Android TV section of the listing only appears after adding Android TV
under **Test and release → Advanced settings → Form factors**.

For manual uploads only, Console's asset library adds several selected files in
a scrambled order. Add the screenshots one at a time, in order, and wait for each
upload to land in the slot before starting the next; overlapping uploads get lost.

Android XR shows as an active form factor that can't be removed. That's
harmless: the manifest requires `android.software.leanback`, so XR headsets
never get the app.

The sections below are the earlier candidates and plan, kept as history.

## 3.1 Candidates (not committed)

Composed from the generated brand PNGs in `artwork/` (themselves rendered by
`tools/RenderArtwork.java` from the SVG geometry) with
`artifacts/play-candidates/make_candidates.py`; `artifacts/play-candidates/` is
ignored. After your pick, the chosen files are committed under `artwork/play/`
with the recipe.

| File | What it is |
|---|---|
| `icon-a-canonical.png` | The listing icon `RenderArtwork` already writes (`app/src/main/ic_launcher-playstore.png`), mark at 78% |
| `icon-b-margin.png` | The same mark at 64%, more space around it |
| `icon-c-glow.png` | Mark at 74% over a faint cyan glow |
| `feature-a-lockup.png` | The stacked banner lockup, centered |
| `feature-b-android-tv.png` | The existing "for Android TV" lockup, centered |
| `feature-c-screen.png` | Stacked lockup on the left, the Channels screenshot on the right |

The icons are saved as 32-bit RGBA, fully opaque (Play applies its own corner
mask); the feature graphics as 24-bit RGB.

## 3.2 TV banner

`artwork/tvheadend-player-banner@4x.png`, 1280×720, 24-bit RGB, no alpha. It is
the same composition as the app's launcher banner.

## 3.3 TV screenshots

All are 1920×1080 offline renders of the production screens with the fictional
fixture pack, so they show no real channels or private data (see
[`presentation-captures.md`](../presentation-captures.md)). Upload eight, in this
order:

1. `artwork/screenshots/channels.png`, Channels as picture cards
2. `artwork/screenshots/guide.png`, the program guide
3. `artwork/screenshots/player.png`, player controls over the picture
4. `artwork/screenshots/details.png`, program details
5. `artwork/screenshots/recordings-folders.png`, recording folders
6. `artwork/screenshots/player-rail.png`, the channel list in the player
7. `artwork/screenshots/channels-list.png`, Channels as a list
8. `artwork/screenshots/player-schedule.png`, the channel schedule beside the picture

Four or more 16:9 screenshots of at least 1080 px also meet the bar Google
names for recommendation eligibility (same page).

## 3.4 Phone screenshots

Google's page says "You must provide a minimum of two screenshots across
different device types to publish your store listing" and asks for at least one
Android TV screenshot. It doesn't say that an app supporting only Android TV
needs phone screenshots. The eight TV screenshots cover the count. If Console
still marks the phone screenshots as required, don't put TV screenshots there
without deciding it ([owner decisions](decisions.md#8-owner-decisions)): the TV quality
guidelines ask for screenshots that accurately depict the TV app, and phone
slots imply a phone app.
