# README presentation captures

This workflow renders the README screenshots from production composables with
fictional fixtures. It does not pick the README lead, publish anything, or
stand in for playback or physical-TV checks.

## Reproduce the screenshots

Use the normal Java and Android SDK setup from
[CONTRIBUTING.md](../CONTRIBUTING.md). From the repository root:

```bash
./gradlew :app:testPlayDebugUnitTest \
  --tests '*.ChannelsLayoutCaptureTest.presentation*' \
  --tests '*.GuideVisualLayoutTest.presentation' \
  --tests '*.ProgrammeDetailsCaptureTest.presentation' \
  --tests '*.RecordingsVisualLayoutTest.presentation*' \
  --tests '*.PlayerChromeCaptureTest.presentation*' \
  --tests '*.ChannelScheduleCaptureTest.presentation' \
  --tests '*.SettingsVisualEvidenceTest.presentation' \
  --console=plain --no-scan
python3 tools/prepare-presentation.py
```

The tests write `app/build/outputs/presentation/`. The packaging script needs
Python 3 and Pillow. It copies the thirteen PNGs into ignored `captures/presentation/`
without cropping or resizing, refuses any frame that is not 1920×1080, and writes
`manifest.json` (commit, tracked-diff hash, source paths and SHA-256 values) and
`review.html`, which shows one image at a time at 830 CSS pixels, about the
README's desktop width. It does not run the tests or check that their outputs are
fresh, so run both steps together.

Pixel-exact reproduction also depends on the same JDK, Robolectric native graphics
and fonts. Copy only owner-selected images into a tracked artwork directory and
review the diff; never force-add the generated directory.

The README uses Channels as the lead image, then the other three Channels views
in a row: list, Grid and without program pictures. A gallery in pairs follows:
Guide and details, the player controls and its channel list, the channel schedule
and the side menu over warm playback, then Recordings folders and a recording
inside a folder. The Settings capture is not used. The tracked copies in
[`artwork/screenshots/`](../artwork/screenshots/) are the same pixels saved
without the fully opaque alpha channel.

## Scene contract

Every scene uses English, UTC, font scale 1.0, a 960×540dp landscape canvas at
xhdpi (1920×1080 pixels) and the fictional
[fixture-art pack](../app/src/test/resources/fixture-art/README.md). The live
scenes share channel **1 · Ridge Earth**, **Ridge Light** on
**2026-10-04 20:00–21:00 UTC** and a **20:15** clock, and agree on what the other
channels have on air. Ridge Light carries the XMLTV genre Documentary and no DVB
content type, so every screen names its genre the same way. Harbor Kickoff on
**2 · Harbor Sport** is recording and the 21:00 Tide Watch on Ridge Earth is
scheduled, so the screens that mark recordings agree. Recordings shows the
previous evening's airing with a saved position of 00:20:23.

| Capture | Time and position | Focus |
|---|---|---|
| `channels`: Channels, Immersive | 20:15; Ridge Light 20:00–21:00, then Tide Watch | First card, Ridge Earth |
| `channels-grid`: Channels, Grid | As Immersive; program art on | First card, Ridge Earth |
| `channels-list`: Channels, List | As Immersive | First row, Ridge Earth |
| `channels-logos`: Immersive without program pictures | As Immersive; program art off | First card, Ridge Earth |
| `guide`: Guide | 20:15; seven channels, Ridge Light 20:00–21:00, then Tide Watch | Ridge Light |
| `details`: Programme details | 20:15; Ridge Light 20:00–21:00 | Watch |
| `recordings`: Recordings | Clock 20:15; Ridge Light of 2026-10-03 20:00–21:00, 00:20:23 watched | Ridge Light inside Documentaries |
| `recordings-folders`: Recordings, top level | As Recordings; Recording now first, then Sport with tonight's match; seven channels with their logos | Documentaries folder, two Down from Recording now |
| `player`: Player controls | 20:15; ten minutes buffered, at live | Pause |
| `player-rail`: Quick zap rail | 20:15; the Channels line-up | Ridge Earth card |
| `player-schedule`: Channel schedule | 20:15; one full day on Ridge Earth, repeated for a week | Ridge Light row |
| `drawer`: Side menu over warm playback | 20:15; back from the player, Northline News live at the live edge behind Channels | Channels in the menu: from Northline News, Left past Ridge Earth's card |
| `settings`: Settings | Playback page over the playing picture | First row |

Channels, Guide and Recordings set the Robolectric wall clock; programme details,
the player and the channel schedule pass the time as arguments. The player, schedule and Settings
backdrop is the `still-ridge-light` fixture and the side menu's is `still-northline-studio`, not decoded video: it is fine for a screenshot of
fictional content, not evidence of SurfaceView capture, decoding or response.

## Play Store candidate review

Round 3 retains the first four round-2 PNGs byte-for-byte and adds programme
details and the player channel schedule. Keep `captures/play-store/round2/`
with its manifest, first four plain PNGs, feature C, icon and banner. To recreate
that baseline, use this document and the packager at commit `adbc9f2a` in a
separate checkout; its instructions include the round-1 comparison prerequisite.
No baseline images are tracked.

Run both whole classes owning the new scenes, then package in the same checkout:

```bash
ANDROID_HOME=/opt/android-sdk timeout --kill-after=5s 20m ./gradlew \
  :app:testPlayDebugUnitTest -Ptvhplayer.captureTests=all \
  --tests '*.ProgrammeDetailsCaptureTest' \
  --tests '*.ChannelScheduleCaptureTest' --console=plain --no-scan
python3 tools/prepare-play-assets.py
```

Use `all`, not `only`: the details method is not tagged as a visual capture.
Run the tests immediately before packaging; the script checks results but
cannot establish source freshness from XML. Both whole classes must pass
without skips. Retained frames carry their round-2 test evidence; they are
not regenerated or described as newly tested.

The order is Channels (Harbor Kickoff, recording), Guide (Northline Tonight),
Player (Harbor Lights Live), Recordings (Glass Harbor, saved progress), then:

- `ProgrammeDetailsCaptureTest.storeDetails`: Ion Frame's Ion Wake,
  19:20–20:25; Watch focused. A title-free still leaves the native page text clear.
- `ChannelScheduleCaptureTest.storeSchedule`: Harbor Lights Live, 19:00–22:00;
  the on-air row focused over the same concert still as Player.

All six use English (US), font scale 1.0 and the fictional evening clock
2026-10-04 20:15 UTC. Recording now is omitted from this set.

The script needs Python 3, Pillow and CairoSVG and uses the pinned Outfit font.
It writes ignored `captures/play-store/round3/`: six plain 1920×1080 RGB PNGs,
three initial 1024×500 C variations plus the C4 owner blend and C5 primary blend, the
retained icon and banner, 960px-wide previews,
320px thumbnails and an image-embedded `review.html`. The variations use the
canonical symbol's paths and colours with its background omitted; the mark
itself is unchanged. Their television screens contain complete app captures.
Plain screenshots preserve every RGB pixel, without added frames or copy.

The review leads with C4 and C5 side by side, followed by C1 and C3 as references.
Each feature is also compared with round-2 C. C5 retains C3's left column and
frame exactly and replaces only the screen pixels with the complete Channels
capture, keeping the card row above the bottom bezel. The packager verifies
retained files against the round-2 manifest before copying. The new manifest
records the source commit, tracked diff hash, new tests, inherited evidence,
input hashes and every output file's SHA-256 except itself. Add the local report
and review evidence before the final packaging pass so they are hashed too.

These are candidates for owner selection, not uploaded assets. Keep at least one
plain screenshot in a proposed listing set. TV-G4 requires an unaltered current
TV-app screenshot; acceptance of offline fixture renders remains a submission
question. The review includes Google rule citations; round 2 retains the listing
comparisons that informed these directions.
Only copy selected assets to `artwork/play/` after owner approval.

## Editorial use

Lead with what the app is and that it connects straight to Tvheadend. Pick one
lead image; the others can follow as smaller or linked images.

- **Channels, Immersive:** says "TV app" first. Title and focused card read at
  README width; the smaller metadata does not.
- **Guide:** makes the Tvheadend programme data tangible. Times are small at
  README width, and as the lead it can make the whole app look like a guide.
- **Player controls:** the closest to watching TV. Titles and controls stay
  readable, but a still cannot show remote response or motion.
