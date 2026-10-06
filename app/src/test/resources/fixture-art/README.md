# Fictional broadcast fixture art

Channel picons and programme key art for unit-test captures. Every channel,
programme and mark here is fictional: none depicts or imitates a real
broadcaster, and none may be replaced with real picons or EPG images, which
carry third-party trademarks and copyright.

Load these through `testutil/FixtureArt.kt`.

## Contents

- `picons/<channel>.png`: 400×240 transparent channel marks. Most are dark
  marks for dark plates. `foundry-docs` and `quiet-hours` are deliberately
  dark marks that need a light plate. `coastal-miles` is an extreme wide
  wordmark (5.5:1) and `roundtable` is a crest.
- `art/<programme>.jpg`: 16:9 key art at 1280×720 with the title designed
  into the image. Some carry a channel bug or a NEW/NEU/LIVE badge.
- Format edge cases: `glass-harbor-poster-2x3` (480×720, generated as a
  poster, not padded), `harbor-kickoff-4x3` (960×720) and
  `ion-wake-edge-title` (title ink close to the right edge).
- `art/still-*.jpg`: programme stills without readable text, usable as a stand-in video frame behind the player.

## When to use what

| Test kind | Images |
| --- | --- |
| Captures for human review: any test that writes a screen PNG under `captures/`, `artifacts/` or `build/outputs/` | This pack, through `FixtureArt` |
| Pixel, colour, ink, aspect or contrast assertions (`ChannelAccentsTest`, `PiconBoxTest`, `GuideVisualLayoutTest` ink checks, scrim measurements) | Synthetic bitmaps with known colours and geometry |
| Geometry, focus, key handling and state tests that draw no art | No images |
| Deliberate worst cases inside a capture: bright or white frame behind a scrim, missing logo, missing art, loading | Keep them synthetic or absent next to the pack art |

Rules:

- Keep a scene coherent: the picon, channel name, programme title, synopsis,
  key art and still in one row or card come from the same line in the
  line-up below. Football art under a documentary title hides real problems
  as well as a colour block does.
- Never use real broadcaster names or marks (ORF, ARD, ServusTV and so on),
  real picons or downloaded EPG images in committed tests. Long-name and
  German cases extend the fictional names instead.
- `still-*` images stand in for live video behind the player and for warm
  backgrounds; key art goes where the app shows programme artwork.
- Fixtures stay test-only. The APK keeps its generic
  `startup_background_plate.webp`; do not ship pack art.
- Serve images through `FixtureArt.imageLoader`, keyed by `ArtworkId`; an id
  without an image fails like a missing server image. Decoding needs
  `@GraphicsMode(NATIVE)`.
- Instrumented `androidTest` screenshots cannot read these JVM test resources
  and need their own copy.
- To add an image, export it from the creative batch, scale it to fit 1280×720
  as JPEG quality 82 (picons stay lossless PNG), and add a provenance row.

## Line-up

The channel each programme belongs to in captures. Channels without a
programme row are picon-only: use them for rows without art or for the mark
edge cases.

| Channel (picon) | Programme | Key art | Still | Synopsis |
| --- | --- | --- | --- | --- |
| Ridge Earth (`ridge-earth`) | Ridge Light | `ridge-light` | `still-ridge-light` | Seasonal mountain light, forests and weather moving over a ridge. |
| Ridge Earth | Tide Watch | — | `still-tide-watch` | Coastal wildlife along a tidal estuary, wading birds and seals. |
| Harbor Sport (`harbor-sport`) | Harbor Kickoff | `harbor-kickoff`, `harbor-kickoff-4x3` | `still-harbor-kickoff` | Match coverage from a floodlit stadium as Harbor FC meet Amber Town. |
| Harbor Sport | River Court | `river-court` | `still-river-court` | Indoor basketball, River Court against North Quay. |
| Northline News (`northline-news`) | Northline Tonight | `northline-tonight` | `still-northline-studio`, `still-northline-graphics` | Evening studio news from a regional desk with two anchors. |
| Northline News | Field Desk | `field-desk` (NEW badge) | `still-field-desk-street` | Field reporting from a flooded riverside road. |
| Northline News | Skyglass Weather | — | `still-skyglass-square` | Weather presentation. |
| Kite Kids (`kite-kids`) | Paper Kite Tales | `paper-kite-tales` | `still-paper-kite-tales` | 2D animation about a paper kite over painted hills. |
| Kite Kids | Little Orbit | `little-orbit` | `still-little-orbit` | A gentle science table about orbits using marbles and a bowl. |
| Ion Frame (`ion-frame`) | Ion Wake | `ion-wake`, `ion-wake-edge-title` | `still-ion-wake` | Sci-fi anime: a courier rides a magnetic rail through Ion Harbor. |
| Ion Frame | Lantern Road | `lantern-road` | `still-lantern-road` | Fantasy anime: a cartographer maps a valley from a lantern sky-bridge. |
| Amber Stage (`amber-stage`) | The Kettle Hour | `kettle-hour` | `still-kettle-hour` | A warm kitchen programme about broths, bread and weeknight pans. |
| Amber Stage | Glass Harbor | `glass-harbor`, `glass-harbor-poster-2x3` | `still-glass-harbor` | A quiet drama about night-shift workers in a harbour glass workshop. |
| Amber Stage | Coastal Miles | — | `still-coastal-miles` | A walking travel strand along a temperate coast. |
| Harbor Lights (`harbor-lights`) | Harbor Lights Live | `harbor-lights-live` | `still-harbor-lights-live` | A small-hall concert with a quartet. |

Picon-only channels: `lantern-hour`, `little-orbit`, `glass-drama`,
`northline-world`, `river-court`, `landfunk-1`, `reedfen-4`, `coastal-miles`
(5.5:1 wordmark), `roundtable` (crest), `foundry-docs` and `quiet-hours`
(dark marks on a light plate).

## Provenance

Exported from the operator's local creative-assets batch
`grok-broadcast-20260911T004615Z` (generator commit `599188b` on
`creative/grok-demo-media`). The picons are hand-written SVG rendered with
cairosvg using Liberation Sans (SIL OFL 1.1) and DejaVu Sans (Bitstream Vera
licence). Most key-art titles and pictures were generated with xAI
`grok-imagine-image-2.0`. Badges were added in Liberation Sans Bold, and the
edge-title fixture was drawn in Liberation Sans Bold on a generated plate.
No exclusive copyright is claimed for the generated media.

The key art and stills were scaled to fit 1280×720 and re-encoded as JPEG
(quality 82). The picons are the source PNGs, where available the refined
`-v2` versions, losslessly re-optimised.

Source path and SHA-256 of each source file, relative to the batch:

| Fixture | Source | SHA-256 |
| --- | --- | --- |
| `picons/harbor-sport.png` | `channels/ch-harbor-sport/picon/picon-400x240-v2.png` | `56b11ce4d61a4c26c6cbc5a053fbcd315204d7014c849a54b33fbcc05f583e98` |
| `picons/river-court.png` | `channels/ch-river-court/picon/picon-400x240.png` | `effcc000bc99615d1586bc043db0c1188be0f7f1a709cbe8d4b3e13e60915479` |
| `picons/northline-news.png` | `channels/ch-northline-news/picon/picon-400x240.png` | `4eec33b596d2765f1eff3e3ba4aab7bee7c94d753da92f63f0ad3b0e92753ca4` |
| `picons/northline-world.png` | `channels/ch-northline-world/picon/picon-400x240.png` | `d806e96e06287fb3e9d9c26ee0e8b972ceb4af303aeae7adc2f82aefe1679c5b` |
| `picons/amber-stage.png` | `channels/ch-amber-stage/picon/picon-400x240.png` | `ba8aabea63df949977a93acb1ee02a69a49c90bb26ba015487eee6d2b6813361` |
| `picons/glass-drama.png` | `channels/ch-glass-drama/picon/picon-400x240.png` | `f8cedc4f81a36fb1a83df3ffc59361dbdf77d9ecda2458c2067a5382ab92b0ee` |
| `picons/kite-kids.png` | `channels/ch-kite-kids/picon/picon-400x240.png` | `1deff0e09d962e7ac6d4172d25a440090bccd61835c8a2bf6a069ab5b597de9a` |
| `picons/little-orbit.png` | `channels/ch-little-orbit/picon/picon-400x240.png` | `6703450c657bc604cb344b758750833d042ed0ac0b82b8b232b44175e8df968f` |
| `picons/lantern-hour.png` | `channels/ch-lantern-hour/picon/picon-400x240.png` | `270efe11b52d16e55ffd0c3a17edc65833ecd0e5569bd56f7158fa448a8266db` |
| `picons/ion-frame.png` | `channels/ch-ion-frame/picon/picon-400x240.png` | `5ee06f02c434397b3a840393dddeec735bbd98657bc02cf4b4d8d7d236173455` |
| `picons/ridge-earth.png` | `channels/ch-ridge-earth/picon/picon-400x240.png` | `a1dcf1f7e08e6dad2438c57f0ad79a5904cbb3b799a694ff310c5e7db46e193f` |
| `picons/harbor-lights.png` | `channels/ch-harbor-lights/picon/picon-400x240.png` | `ffd5f278ecf0ac190864f65d88e34babe5c669324ccbabf0bdd96a0602174239` |
| `picons/landfunk-1.png` | `channels/ch-landfunk-1/picon/picon-400x240.png` | `541da782935af57830459f852a71fd62648422c9e8733b610e679556fe3f5490` |
| `picons/reedfen-4.png` | `channels/ch-reedfen-4/picon/picon-400x240.png` | `2732f92e218e217a33d685479592880967c99d6d03b037f4bc3425522a90d78c` |
| `picons/foundry-docs.png` | `channels/ch-foundry-docs/picon/picon-400x240.png` | `84ad95f0237497f1d43642b06301e4991914a00e98da05d04fbd63c200746e7b` |
| `picons/coastal-miles.png` | `channels/ch-coastal-miles/picon/picon-400x240-v2.png` | `1a18b237c78ef2d080d31dc0f746cf145faf42dd15e9dcb48d8f81fda2f6c032` |
| `picons/roundtable.png` | `channels/ch-roundtable/picon/picon-400x240-v2.png` | `12029ef7408dbd3eec226dd61d213472a3d04dfeda8c1855c7226bfc784191fc` |
| `picons/quiet-hours.png` | `channels/ch-quiet-hours/picon/picon-400x240.png` | `894a41ae1a2e26ee20ff275784703df0bb32dd96ac1d969e908321ac28c89d5b` |
| `art/harbor-kickoff.jpg` | `programmes/sport-harbor-kickoff/keyart/title-16x9-v2.png` | `a52d543d1ca8b9a34dcf2dbb79bb729c5b9ca3798319a0a611e4af4352b079e4` |
| `art/river-court.jpg` | `programmes/sport-river-court/keyart/title-16x9-v2.png` | `2cdfbb3d7dd1f422b949584df108aa4a351128eda764aa1f209e1c81449db43d` |
| `art/northline-tonight.jpg` | `programmes/news-northline-tonight/keyart/title-16x9-v2.png` | `523d8cd21b5d04184851ca0348640a152572af0ce0bfdb889394d94b0ca26fa8` |
| `art/field-desk.jpg` | `programmes/news-field-desk/keyart/title-16x9-v2.png` | `27b129695889037dcf675e7951cb8f5b2f3389b135c8443f04601d554697cf20` |
| `art/kettle-hour.jpg` | `programmes/ent-kettle-hour/keyart/title-16x9-v2.png` | `da7da5bfedf384049834b82176496b40c69a1bb6600fcee9c6ea2babed5db50c` |
| `art/glass-harbor.jpg` | `programmes/ent-glass-harbor/keyart/title-16x9-v2.png` | `2e2af6612e79d922e05653b913252771696a0c2848d90b9ca528694f11414b9c` |
| `art/paper-kite-tales.jpg` | `programmes/kids-paper-kite/keyart/title-16x9-v2.png` | `acef06e367e6b98153c8d7620ac59506321f4f8d8b3f86f3905475e3886a9599` |
| `art/little-orbit.jpg` | `programmes/kids-little-orbit/keyart/title-16x9-v2.png` | `40876c8b4435ebc4a26d9e768559cf916c931ca91835162b740d981cec09d484` |
| `art/lantern-road.jpg` | `programmes/anime-lantern-road/keyart/title-16x9-v2.png` | `e3b5fb6d65b2270b79811b973cc7b5cfb8e364640c08939df6b675b03fba0438` |
| `art/ion-wake.jpg` | `programmes/anime-ion-wake/keyart/title-16x9-v2.png` | `db5e79e1d64f4ea5d5653d5e71546511a205250eb29ab8b2eb80ac82991759b5` |
| `art/ridge-light.jpg` | `programmes/nat-ridge-light/keyart/title-16x9-v2.png` | `7a7e12d3a8f7c666358aece45f48482547c2c806ba73e7447ba2cde60e46e0d6` |
| `art/harbor-lights-live.jpg` | `programmes/live-harbor-lights/keyart/title-16x9-v2.png` | `42c76af464f8a3087dddff2097750f551fc5b1fe297c00240781eb707516df1b` |
| `art/glass-harbor-poster-2x3.jpg` | `programmes/ent-glass-harbor/keyart/poster-2x3-v2.png` | `9a1bf9147379933602ba2a09fa8d0b5e14d07b54d4a1d5b7a101c60407543801` |
| `art/harbor-kickoff-4x3.jpg` | `programmes/sport-harbor-kickoff/keyart/frame-4x3-v2.png` | `5bb8da2e2b94520c083ee33358d1fef3c966c528155f684fffef9d312cb4cf49` |
| `art/ion-wake-edge-title.jpg` | `programmes/anime-ion-wake/keyart/edge-16x9-v2.png` | `41cd43e1730e36f692797a1d07b0452a965961e72166579adf1a86cf1b59de8a` |
| `art/still-northline-studio.jpg` | `programmes/news-northline-tonight/stills/04.png` | `d38a374b4dbbc1647fa9c7b3f05b006654e7f13d78bb973234c445ebc0f5e358` |
| `art/still-northline-graphics.jpg` | `programmes/news-northline-tonight/stills/05.png` | `232bdc10e57e272d4140ad5be960f8a4dbf76fc22af27d2301c3d2f140110cd4` |
| `art/still-field-desk-street.jpg` | `programmes/news-field-desk/stills/04.png` | `5cb830aae46330fc25adfbff338694679bd91bdb4b3b221a9acbe4d1800d3101` |
| `art/still-skyglass-square.jpg` | `programmes/news-skyglass-weather/stills/04.png` | `918647322963ec557b462b0876cf57507d4f83553c2e1ba673922f762e67860d` |
| `art/still-harbor-kickoff.jpg` | `programmes/sport-harbor-kickoff/stills/01.png` | `db7910283813a10a9a09e318c298d94cda17fd5f0320046c07e842bd0605a63b` |
| `art/still-river-court.jpg` | `programmes/sport-river-court/stills/01.png` | `00ef0f0d115ecb945fdbfbe9facdf762899173c91f8912d172636472a5f6d1ce` |
| `art/still-kettle-hour.jpg` | `programmes/ent-kettle-hour/stills/01.png` | `40a8ec8293924ae1613125f830e898e1fa771425d7a2046ade16e538855f5874` |
| `art/still-glass-harbor.jpg` | `programmes/ent-glass-harbor/stills/01.png` | `4c35d363f9adde869915298db9cfaf448c07ab8ee05a81ce8cf31c56b6615e1c` |
| `art/still-paper-kite-tales.jpg` | `programmes/kids-paper-kite/stills/01.png` | `a6f14acca850641819f8fd75275dbaf238b09e7b7c9de6e4f803cf386aeae7e8` |
| `art/still-little-orbit.jpg` | `programmes/kids-little-orbit/stills/01.png` | `2c83c48ba869b4f960237762daa24913d19aa20e3a59198f7ca5dcb0d2a22a5f` |
| `art/still-lantern-road.jpg` | `programmes/anime-lantern-road/stills/01.png` | `74e4974579fec885f478a2e8dcb6864f890576d4a9e17188931c94f3f2cd5adf` |
| `art/still-ion-wake.jpg` | `programmes/anime-ion-wake/stills/01.png` | `1fe94653d3b2bfbe157a5484fc5dde7c436c88f9003c55e1d99a61c17db88be8` |
| `art/still-ridge-light.jpg` | `programmes/nat-ridge-light/stills/01.png` | `2b4a8071e65c2bdad4fa8179312e74d96d6897636cdfbd530a8adc410289db60` |
| `art/still-harbor-lights-live.jpg` | `programmes/live-harbor-lights/stills/01.png` | `4984f731d6abdf36dcfd94e6912454abce7c6e3cc27c6cddbb74f97452e73f41` |
| `art/still-tide-watch.jpg` | `programmes/nat-tide-watch/stills/01.png` | `fbecedef93e3acdf52252cf0369bcb56c21132d6300e65a1ba99905d97665a80` |
| `art/still-coastal-miles.jpg` | `programmes/ent-coastal-miles/stills/01.png` | `04a1fa31e9d3375d0bd0061dc51e6043ed86687eb83533c21b4cf569d488ead3` |
