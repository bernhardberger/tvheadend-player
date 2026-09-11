# Tvheadend Player artwork

The mark is a cyan diamond aperture on a dark neutral field, layered outward
from the play symbol: orange play, neutral charcoal core, cyan diamond. Cyan is
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

The orange play symbol never touches cyan directly; the neutral charcoal core
separates the accents without introducing a blue or navy cast.

Orange is the accent, not a second primary. Measured as a share of the mark's own
ink it is 13.2%, against 18.9% for the upstream emblem.

## Palette

- Field: `#0F1014`
- Diamond: `#00BCFA`
- Core: `#171717`
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
separate Android TV lockup. Family artwork has no platform suffix.

Run from the repository root with Java 21:

```bash
java tools/RenderArtwork.java
```

The same command creates the self-contained browser preview at
`artifacts/brand-preview/index.html` (ignored generated output).

Everything is generated: never hand-edit the PNGs, `ic_launcher_monochrome.xml`,
or SVG exports. SVG text is outlined from the pinned font; no installed font,
network resource, or fallback is needed to display it. The PNGs render those
same shapes directly at each target size, rather than upscaling a raster.
Byte-identical PNG regeneration requires the same Java 21 rendering runtime.

The banner is exported at 320×180, 640×360 and 1280×720. Android resources use
160×90 mdpi, 240×135 hdpi, 320×180 xhdpi, 480×270 xxhdpi and 640×360 xxxhdpi,
all representing 160×90dp. The family and contextual lockups have 960×300 and
1920×600 PNGs; the symbol has 512×512 and 1024×1024 PNGs. Each also has a
portable SVG. The 1280×640 social preview is a prepared asset, not an upload.

## Font provenance

`fonts/Outfit-variable.ttf` is the unmodified Google Fonts Outfit variable font
from revision `8e44913e4ff26fc997e6856c1ec40ff4791c98c5`, path
`ofl/outfit/Outfit[wght].ttf`:
<https://github.com/google/fonts/tree/8e44913e4ff26fc997e6856c1ec40ff4791c98c5/ofl/outfit>.
SHA256: `fc7287273e66929776e2ba54f144fe699080bec29f61bf649d70d871468aeade`.
Copyright 2021 The Outfit Project Authors. The complete SIL OFL 1.1 is in
`fonts/OFL.txt`; it covers both the original and the derived static instance.

`fonts/Outfit-550.ttf` is the static weight-550 instance, generated with
FontTools 4.59.1 (Python 3.13):

```bash
fonttools varLib.instancer artwork/fonts/Outfit-variable.ttf wght=550 --output artwork/fonts/Outfit-550.ttf
```

Its SHA256 is `727366fc010a90ad71c0f639ddc85336c175bb5074041311e2b863960d6ecf46`.
The Java generator loads that file explicitly and fails if it is missing or
invalid. It does not approximate variable weight with synthetic bold. Fonts
are artwork build inputs, not a change to the application's general typography.
The app remains an independent GPLv3 client descended from
[`Preclikos/tvhstream`](https://github.com/Preclikos/tvhstream); see `../NOTICE.md`.

Review launcher masks and the banner on the physical TV after changing geometry,
fonts, or colors. Do not put server names, channel data, addresses, or household
screenshots in public artwork.
