# TVHeadend Player artwork

The mark is a cyan diamond aperture on a dark neutral field, layered outward
from the play symbol: orange play, neutral charcoal core, cyan diamond. Cyan is
the complete outer silhouette; there is no redundant dark keyline around it.

The rotated square is a deliberate nod to the diamond at the center of the
Tvheadend logo. The four chevrons that surround that diamond are not reproduced,
and no upstream path geometry is reused. The color roles are inverted: upstream
puts orange at the source and cyan on the distribution, while here cyan carries
the shape and orange marks playback. TVHeadend Player is not
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
- Wordmark: `#E3E3E8`, subtitle at 75% opacity

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
320x180 TV banner, README logo, social preview, and editable SVG wordmark.

Run from the repository root with Java 21:

```bash
java tools/RenderArtwork.java
```

Everything is generated: never hand-edit the PNGs, `ic_launcher_monochrome.xml`,
or `tvheadend-player-logo.svg`. The monochrome layer and the SVG paths are
emitted from the same shapes as the rasters, so themed, colored, and marketing
artwork cannot drift apart. The committed text-bearing PNGs use DejaVu Sans;
font resolution and text antialiasing can vary between Java rendering runtimes,
so byte-identical raster regeneration requires the same font and Java runtime.

Review launcher masks and the banner on the physical TV after changing geometry,
fonts, or colors. Do not put server names, channel data, addresses, or household
screenshots in public artwork.
