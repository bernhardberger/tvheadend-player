# TVHeadend Player artwork

The mark is a cyan television with Tvheadend’s orange center diamond and a play
triangle cutout. Official Tvheadend cyan (`#00BCFA`) and orange (`#FA7F00`) are
sampled from upstream `logobig.png`. The silhouette reads as a live-TV player
for Tvheadend servers at launcher and banner scale.

## Palette

- Background: `#080f1e`
- Gradient highlight: `#112240`
- Mark tile: `#101d33`
- Screen inset: `#0A1A2E`
- Primary text: `#f4f7fb`
- Secondary text: `#b5c1d4`
- Tvheadend cyan: `#00BCFA`
- Tvheadend orange: `#FA7F00`

## Exports

`tools/RenderArtwork.java` is the reproducible source for the Android launcher
layers, density fallbacks, monochrome adaptive layer, 320x180 TV banner, README
logo, social preview, and editable SVG wordmark.

Run from the repository root with Java 21:

```bash
java tools/RenderArtwork.java
```

Review launcher masks and the banner on the physical TV after changing geometry,
fonts, or colors. Do not put server names, channel data, addresses, or household
screenshots in public artwork.
