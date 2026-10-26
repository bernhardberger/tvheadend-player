# Russian

## Register

Polite plural **вы** (lowercase when explicit); infinitive action labels.

## Typography

- Android folder: `values-ru`.
- Compact season/episode prefixes are С/Э; Ё is written normally.
- Clock example keeps `\u00A0` before PM; seek-back keeps U+2212; ellipses use U+2026; no bidi control characters added. The draft contains no quotation marks, so none are decided.

## Plurals

`one`, `few`, `many`, `other`. Bulk failures follow the total `%2$d`; forms after "из" are genitive. Credit headings carry no count placeholder.

## Term decisions

- `nav_channels` Каналы; `nav_epg` Телепрограмма; `nav_recordings` Записи; `context_watch` Смотреть.
- `timeshift_setting` Отложенный просмотр; `details_retention` Срок хранения; mux = мультиплекс; tuner = тюнер.
- Cancel отменить запись, Stop остановить запись (recorded part kept), Delete удалить запись.
- `keep_recording` is продолжить запись; `player_tuning` Переключение, not scanning.
- `profile_direct_streaming` "Прямая трансляция потока" is kept distinct from *прямой эфир*.
- Audio-channel count uses "Каналов: %1$d" to avoid an inflected noun.

## Review status

Machine-drafted by GPT-6.1 Sol (medium) in round 1 from source commit daebdd86; Sonnet 5.5 review pending; no human reviewer yet. Ships labeled machine-drafted.

## Open questions

- Confirm *режим ТВ-приставки* and *Погружение* (immersive layout).
- Review specialist terms *тифлокомментарий* and *туннельное воспроизведение*. Drafted together with Ukrainian.
