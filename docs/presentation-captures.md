# README presentation captures

This workflow renders the README screenshots from production composables with
fictional fixtures. It does not pick the README lead, publish anything, or
stand in for playback or physical-TV checks.

## Reproduce the screenshots

Use the normal Java and Android SDK setup from
[CONTRIBUTING.md](../CONTRIBUTING.md). From the repository root:

```bash
./gradlew :app:testPlayDebugUnitTest \
  --tests '*.ChannelsLayoutCaptureTest.presentation' \
  --tests '*.GuideVisualLayoutTest.presentation' \
  --tests '*.ProgrammeDetailsCaptureTest.presentation' \
  --tests '*.RecordingsVisualLayoutTest.presentation' \
  --tests '*.PlayerChromeCaptureTest.presentation' \
  --tests '*.SettingsVisualEvidenceTest.presentation' \
  --console=plain --no-scan
python3 tools/prepare-presentation.py
```

The tests write `app/build/outputs/presentation/`. The packaging script needs
Python 3 and Pillow. It copies the six PNGs into ignored `captures/presentation/`
without cropping or resizing, refuses any frame that is not 1920×1080, and writes
`manifest.json` (commit, tracked-diff hash, source paths and SHA-256 values) and
`review.html`, which shows one image at a time at 830 CSS pixels, about the
README's desktop width. It does not run the tests or check that their outputs are
fresh, so run both steps together.

Pixel-exact reproduction also depends on the same JDK, Robolectric native graphics
and fonts. Copy only owner-selected images into a tracked artwork directory and
review the diff; never force-add the generated directory.

## Scene contract

Every scene uses English, UTC, font scale 1.0, a 960×540dp landscape canvas at
xhdpi (1920×1080 pixels) and the fictional
[fixture-art pack](../app/src/test/resources/fixture-art/README.md). The live
scenes share channel **1 · Ridge Earth**, **Ridge Light** on
**2026-10-04 20:00–21:00 UTC** and a **20:15** clock, and agree on what the other
channels have on air. Recordings shows the previous evening's airing with a saved
position of 00:20:23.

| Capture | Time and position | Focus |
|---|---|---|
| Channels, Immersive | 20:15; Ridge Light 20:00–21:00, then Tide Watch | First card, Ridge Earth |
| Guide | 20:15; seven channels, Ridge Light 20:00–21:00, then Tide Watch | Ridge Light |
| Programme details | 20:15; Ridge Light 20:00–21:00 | Watch |
| Recordings | Clock 20:15; Ridge Light of 2026-10-03 20:00–21:00, 00:20:23 watched | Ridge Light inside Documentaries |
| Player controls | 20:15; ten minutes buffered, at live | Pause |
| Settings | Playback page over the playing picture | First row |

Channels, Guide and Recordings set the Robolectric wall clock; programme details
and the player pass the time as arguments. The player and Settings backdrop is
the `still-ridge-light` fixture, not decoded video: it is fine for a screenshot of
fictional content, not evidence of SurfaceView capture, decoding or response.

## Editorial use

Lead with what the app is and that it connects straight to Tvheadend. Pick one
lead image; the others can follow as smaller or linked images.

- **Channels, Immersive:** says "TV app" first. Title and focused card read at
  README width; the smaller metadata does not.
- **Guide:** makes the Tvheadend programme data tangible. Times are small at
  README width, and as the lead it can make the whole app look like a guide.
- **Player controls:** the closest to watching TV. Titles and controls stay
  readable, but a still cannot show remote response or motion.
