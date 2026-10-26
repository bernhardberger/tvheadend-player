# Ukrainian

## Register

Polite plural **ви** (lowercase when explicit); infinitive action labels.

## Typography

- Android folder: `values-uk`.
- Apostrophe is U+2019, avoiding unescaped ASCII apostrophes. Compact season/episode prefixes are С/Е.
- Clock example keeps `\u00A0` before PM; seek-back keeps U+2212; ellipses use U+2026; no bidi control characters added. The draft contains no quotation marks, so none are decided.

## Plurals

`one`, `few`, `many`, `other`. Bulk failures follow the total `%2$d`; forms after "із" are genitive.

## Term decisions

- `nav_channels` Канали; `nav_epg` Телепрограма; `nav_recordings` Записи; `context_watch` Дивитися.
- App = *застосунок*; folder = *тека*.
- `timeshift_setting` Відкладений перегляд; `details_retention` Термін зберігання; mux = мультиплекс; tuner = тюнер.
- Cancel скасувати запис, Stop зупинити запис (recorded part kept), Delete видалити запис.
- `player_tuning` Перемикання, not scanning.
- `profile_direct_streaming` "Пряма трансляція потоку" is kept distinct from *прямий ефір*.

## Review status

Machine-drafted by GPT-6.1 Sol (medium) in round 1 from source commit daebdd86; Sonnet 5.5 review pending; no human reviewer yet. Ships labeled machine-drafted.

## Open questions

- Confirm *режим ТВ-приставки* and *Занурення* (immersive layout).
- Confirm *пряме передавання звуку* (passthrough) and *тунельне відтворення*. Drafted together with Russian.
