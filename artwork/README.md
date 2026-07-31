# TVHeadend Player artwork

The mark is a diamond aperture on a cyan field, layered outward from the play
symbol: orange play, navy core, cyan band, navy keyline. The cyan lives inside
the mark rather than behind it, so the whole thing is self-contained and holds
on any ground — white, black, a screenshot, or the launcher's own cyan.

The rotated square is a deliberate nod to the diamond at the center of the
Tvheadend logo. The four chevrons that surround that diamond are not reproduced,
and no upstream path geometry is reused. The color roles are inverted: upstream
puts orange at the source and cyan on the distribution, while here the navy
carries the shape and the orange marks playback. TVHeadend Player is not
affiliated with or endorsed by the Tvheadend project.

## Why the field is cyan

The previous mark was dark navy on a dark navy field, which collapsed into an
unreadable smudge on a television at viewing distance. The cyan is now the
ground, so the icon keeps a bright silhouette from across a room.

Orange on cyan measures 1.18:1 and is effectively invisible, so the orange play
symbol never touches the field directly — the navy core always separates them.
Navy on cyan is 7.91:1 and orange on navy is 6.69:1.

Orange is the accent, not a second primary. Measured as a share of the mark's own
ink it is 13.2%, against 18.9% for the upstream emblem.

## Palette

- Field: `#00BCFA`
- Mark ink: `#0B1B2E`
- Play symbol: `#FA7F00`
- Wordmark: `#0B1B2E`, subtitle at 75% opacity

## Geometry

`RenderArtwork` normalizes the mark to the 66dp adaptive safe zone. Three nested
diamonds — each a rounded square turned through 45 degrees — take half-diagonals
of 33, 29.5, and 24 units on that 66-unit square, with corner radii of 13, 11.5,
and 9.5. The outer diamond's vertices sit on the safe zone, so neither the
circular nor the rounded-square launcher mask clips the mark.

The play symbol is a triangle unioned with its own round-joined outline. Its
horizontal center sits at 35.4 rather than 33 on the 66-unit square — quoted on
the 108dp adaptive grid, that is 56.4 rather than 54. A right-pointing triangle carries
its mass toward the flat back edge, so centering the bounding box leaves the area
centroid about two units left of where it reads as centered.

The monochrome layer is the outer diamond with the cyan band and the play symbol
subtracted, emitted as one even-odd path from the same geometry as the rasters.

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
