# TVHeadend Player artwork

The mark represents parallel channel lanes crossing a visible tuning cursor. It
deliberately avoids the television-outline and play-triangle imagery used by the
upstream TVHStream project.

## Palette

- Background: `#080f1e`
- Mark surface: `#101d33`
- Primary text: `#f4f7fb`
- Secondary text: `#b5c1d4`
- Live channel cyan: `#59c3ff`
- Guide blue: `#657cff`
- Tuning cursor amber: `#ffb454`

## Exports

`tools/RenderArtwork.java` is the reproducible source for the Android launcher
layers, density fallbacks, 320x180 TV banner, README logo, social preview, and
editable SVG wordmark.

Run from the repository root with Java 21:

```bash
java tools/RenderArtwork.java
```

Review launcher masks and the banner on the physical TV after changing geometry,
fonts, or colors. Do not put server names, channel data, addresses, or household
screenshots in public artwork.
