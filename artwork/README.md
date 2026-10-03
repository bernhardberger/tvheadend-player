# Tvheadend Player artwork

The mark is a cyan diamond aperture on a dark neutral field, layered outward
from the play symbol: orange play, transparent aperture, cyan diamond. Cyan is
the complete outer silhouette; there is no redundant dark keyline around it.

The rotated square is a deliberate nod to the diamond at the center of the
Tvheadend logo. The four chevrons that surround that diamond are not reproduced,
and no upstream path geometry is reused. The color roles are inverted: upstream
puts orange at the source and cyan on the distribution, while here cyan carries
the shape and orange marks playback. Tvheadend Player is not
affiliated with or endorsed by the Tvheadend project.

## Why the field is dark

The dark field matches the app and Android starting splash, avoids a bright
full-screen flash, and lets the cyan diamond remain the dominant silhouette at
television distance. Expanding cyan to the former keyline boundary preserves the
mark's launcher footprint without retaining an outline that served only to
separate cyan from a cyan field.

The orange play symbol never touches cyan directly; the transparent aperture
separates the accents and reveals the underlying surface. The mark has no interior
fill. Foreground/startup layers preserve this transparency; opaque banner, listing
and brand plates retain their separate dark background.

Orange is the accent, not a second primary. Measured as a share of the mark's own
ink it is 13.2%, against 18.9% for the upstream emblem.

## Palette

- Field: `#0F1014`
- Diamond: `#00BCFA`
- Aperture: transparent (no interior fill)
- Play symbol: `#FA7F00`
- Wordmark: Outfit 550, **Tvheadend** `#E3E3E8`, **Player** `#FA7F00`
- Separate contextual lockup: **for Android TV** in `#E3E3E8`

## Geometry

`RenderArtwork` normalizes the mark to the 66dp adaptive safe zone. Two nested
diamonds — each a rounded square turned through 45 degrees — take half-diagonals
of 33 and 27 units on that 66-unit square, with corner radii of 13 and 10.5. The
cyan diamond's vertices sit on the safe zone, so neither the circular nor the
rounded-square launcher mask clips it. The six-unit inset leaves a cyan band
about 9% of the diamond span.

The play symbol is a triangle unioned with its own round-joined outline. Its
horizontal center sits at 35.4 rather than 33 on the 66-unit square — quoted on
the 108dp adaptive grid, that is 56.4 rather than 54. A right-pointing triangle carries
its mass toward the flat back edge, so centering the bounding box leaves the area
centroid about two units left of where it reads as centered.

The monochrome layer combines the diamond ring and play symbol into one path
emitted from the same geometry as the rasters.

## Exports

`tools/RenderArtwork.java` is the reproducible source for the Android launcher
layers, density fallbacks, monochrome adaptive layer, 512x512 Play listing icon,
TV banner density set, family wordmark, symbol-only avatar, social preview, and
separate Android TV lockup. Family artwork has no platform suffix. The paired
launcher composition uses a 78-unit symbol and 36-unit stacked name on 320×180;
the horizontal family preserves the accepted 42:27 symbol/type proportions.

Run from the repository root with Java 21:

```bash
java tools/RenderArtwork.java
```

The same command creates the self-contained browser preview at
`artifacts/brand-preview/index.html` (ignored generated output).
It also writes `marquee-320x180.png` and `marquee-1280x720.png` in that ignored
directory for same-canvas comparison with the accepted horizontal reference.
These comparison intermediates regenerate on a clean clone; the portable,
committed horizontal family deliverable is `tvheadend-player-logo.svg` and its
960×300/1920×600 PNGs, covered by the artwork regeneration checks.

Everything is generated: never hand-edit the PNGs, SVGs,
`ic_launcher_monochrome.xml`, or `startup_brand_symbol.xml`. SVG text is outlined
from the pinned font with kerning enabled; no installed font or fallback is
needed to display it. PNGs render those shapes directly at each target size.
Byte-identical regeneration requires the same Java 21 rendering runtime.

The banner is exported at 320×180, 640×360 and 1280×720. Android resources use
160×90 mdpi, 240×135 hdpi, 320×180 xhdpi, 480×270 xxhdpi and 640×360 xxxhdpi,
all representing 160×90dp. Family/contextual lockups have 960×300 and 1920×600
PNGs; the symbol has 512×512 and 1024×1024 PNGs. Each also has a portable SVG.
The 1280×640 social preview is a prepared asset, not an upload.

## Startup artwork

The silent **1850ms hybrid-synced adaptation** uses native Canvas paths and a
single background image. The final ring/play reuse the canonical generated
Android artwork; traces retain unclipped overflow. Layout, lifecycle, loading,
recovery and readiness behavior are owned by
[`docs/DESIGN.md`](../docs/DESIGN.md#startup).

The timeline has a quiet opening, slower assembly and a 370ms settled hold
after the play finishes at 1480ms, but never delays readiness. Traces fade in
250–450ms and extend 250–880ms (cubic-out); bands fill 460–970ms (smoothstep),
gaps converge 700–1120ms (cubic-out), ring fills 970–1240ms (smoothstep), trace
stroke retires 1030–1270ms (smoothstep), play appears 1000–1330ms (smoothstep),
and play scale 1.1→1 / dy −6→0 settle 1000–1480ms (cubic-out). Wordmark appears
400–940ms (cubic-out), retaining the 2dp rise. No sound asset is packaged.
Assembly is admitted for a continuing startup wait after the real 400ms grace
at the first non-restored process-entry opportunity, regardless of connection
stage or retained disk cache. The pending opening frame already occupies the final
branded layout; an existing-player return stays spinner-only and never replays it.
Feedback has no 1200–1400ms brand-clock delay. At readiness the outgoing
visual frame freezes for dismissal, without completing unfinished assembly.
On 960×540dp the 80dp symbol starts at y=156dp, followed by a 14dp gap and unscaled
28sp/36sp Outfit 550 wordmark.
The theme-primary busy ring is 44dp/stroke3dp, 32dp below the wordmark at
(480,340); native TV status begins 16dp below the ring. Brand-free playback returns
keep their separate player-centred indicator without moving this composition.
Once return feedback is visible, it uses the same gently pulsing glow at the final
drift position, independent of the brand clock; it does not replay the sweep.
Ring placement does not depend on status text or font scale.

The original background-only plate supplies one light-only graded frame beneath
startup content, not a second video decoder. Source in the creative-assets project
(batch-relative path, original GPL provenance):
`batches/grok-broadcast-20260911T004615Z/startup-motion/production-a/plates/background-only.mp4`.
Source SHA256: `c131ab2730873fe3aa0fa625803a0e72abc8b0b8efe12af7ac36cc694792188b`.
Recipe: FFmpeg `-ss 0.1 -frames:v 1 -vf scale=960:540:flags=lanczos` extracts
the original frame. In RGB float, let `field=(15,16,20)` and
`light=max(source-field,0)` per channel. Let `d` be the minimum pixel distance
to the four edges, `t=clamp(d/(540*0.08),0,1)` and feather `t*t*(3-2*t)`.
Encode `round(field+light*feather)` as lossless Pillow WebP
(`lossless=True, method=6`). Every channel stays at or above the flat field,
removing the dark rim without brightening gain or inventing a new sweep. The density-independent
resource is `app/src/main/res/drawable-nodpi/startup_background_plate.webp`:
960×540 pixels, 79,650 compressed bytes and 2,073,600 decoded ARGB8888 bytes
(1.98MiB; below 2MiB).
Resource SHA256: `ddf49691ce5612c1e148a823623af0b666c35194b671d784a9c7356d940e81a3`.
One asynchronous decode prepares the image; draw reads use the same `brandMillis`
as the logo/wordmark, outside safe content padding. Opacity is zero through
120ms, smoothsteps to 0.5 at 950ms, then to a 0.20 baseline at 1850ms;
on animated entry a decoded image fades in over 200ms independently of the brand
clock, including when preparation completes after that clock has stopped. Static
entry displays the 0.20 base glow immediately when prepared, without an arrival fade. Center
crop uses 1.12× cover scale and diagonal travel of 4% screen width over
100–1850ms. No new dwell is introduced. Once motion settles, including static
entry, the glow stays at its final position. A composition-owned infinite reverse
tween raises opacity from 0.20 to 0.30 over 2000ms, then returns over 2000ms.
Each half uses `(1 - cos(π × fraction)) / 2` easing, producing a continuous
four-second breathing cycle with rounded turns and no hold at either end.
It starts only after the plate is prepared and assembly is settled, with its value
read in the draw phase. The pulse leaves with the startup layer on readiness or
cancellation; it neither replays assembly nor delays the handoff.
Recovery and reduced motion remain exactly `#0F1014`.

Source choreography/provenance in creative batch `grok-broadcast-20260911T004615Z`:
`startup-motion/hybrid-synced/{HANDOFF.md,timings.json,build_hybrid_synced.py}`;
segment geometry: `startup-motion/soft-tv-finalists/build_soft_tv_finalists.py`.
Physical-TV motion acceptance remains a separate gate.

## Font provenance

`fonts/Outfit-variable.ttf` is the unmodified Google Fonts Outfit variable font
from revision `8e44913e4ff26fc997e6856c1ec40ff4791c98c5`, path
`ofl/outfit/Outfit[wght].ttf`:
<https://github.com/google/fonts/tree/8e44913e4ff26fc997e6856c1ec40ff4791c98c5/ofl/outfit>.
SHA256: `fc7287273e66929776e2ba54f144fe699080bec29f61bf649d70d871468aeade`.
Copyright 2021 The Outfit Project Authors. The complete SIL OFL 1.1 is in
`fonts/OFL.txt`; it covers both the original and derived static instance.

`fonts/Outfit-550.ttf` is the static weight-550 instance, generated with
FontTools 4.59.1 (Python 3.13):

```bash
fonttools varLib.instancer artwork/fonts/Outfit-variable.ttf wght=550 --output artwork/fonts/Outfit-550.ttf
```

Its SHA256 is `727366fc010a90ad71c0f639ddc85336c175bb5074041311e2b863960d6ecf46`.
The generator loads it explicitly and fails if missing or invalid. It also copies
the same font to `app/src/main/res/font/outfit_550.ttf` and its license to
`app/src/main/assets/licenses/Outfit-OFL.txt` for distribution. Outfit is limited
to branding; general app typography is unchanged. The app remains an independent
GPLv3 client descended from [`Preclikos/tvhstream`](https://github.com/Preclikos/tvhstream);
see `../NOTICE.md`.

Review launcher masks and the banner on the physical TV after changing geometry,
fonts, or colors. Do not put server names, channel data, addresses, or household
screenshots in public artwork.
