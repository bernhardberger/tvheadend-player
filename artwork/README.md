# TVHeadend Player artwork

The mark is an original segmented cyan widescreen with clipped corners and an
orange play symbol. Four filled corner blocks are separated by narrow horizontal
and vertical gaps. A centered circular knockout removes cyan behind the orange
symbol, leaving a balanced dark whitespace moat around it. It reads directly
as a live-TV player while the cyan (`#00BCFA`) and orange (`#FA7F00`) palette
recalls compatibility with Tvheadend servers.

The mark does not reuse the Tvheadend rosette, center diamond, or upstream logo
geometry. TVHeadend Player is not affiliated with or endorsed by the Tvheadend
project.

## Palette

- Background: `#080f1e`
- Gradient highlight: `#112240`
- Center glow: `#1b395c`
- Mark tile: `#101d33`
- Tile stroke: `#324a6b`
- Primary text: `#f4f7fb`
- Secondary text: `#b5c1d4`
- Tvheadend cyan: `#00BCFA`
- Tvheadend orange: `#FA7F00`

## Geometry

`RenderArtwork` holds one filled angular corner block, mirrors that original
geometry into four rectangular corners, subtracts the circular play clearance,
and places a smaller rounded play triangle inside the knockout. Every surface
scales from that definition. The complete silhouette stays inside the 66dp
adaptive safe zone so circular launcher masks cannot clip it.

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
artwork cannot drift apart.

Review launcher masks and the banner on the physical TV after changing geometry,
fonts, or colors. Do not put server names, channel data, addresses, or household
screenshots in public artwork.
