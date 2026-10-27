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
- `timeshift_setting` Пауза и перемотка прямого эфира; `details_retention` Срок хранения; mux = мультиплекс; tuner = тюнер.
- Cancel отменить запись, Stop остановить запись (recorded part kept), Delete удалить запись.
- `keep_recording` is продолжить запись; `player_tuning` Переключение, not scanning.
- `profile_direct_streaming` "Прямая трансляция потока" is kept distinct from *прямой эфир*.
- Audio-channel count uses "Каналов: %1$d" to avoid an inflected noun.

## Review status

Reconciled to frozen source `6d80a8ff` for 0.3.0 by GPT-6.1 Sol (medium), retaining unchanged round-1 text from `daebdd86`. Full resources reviewed by Sonnet 5.5 (medium); `keep_channel_setting` now explicitly means keeping the channel tuned during pause, rather than saving a channel. No human reviewer; remains labeled machine-drafted.

## Open questions

- Confirm *режим ТВ-приставки* and *Погружение* (immersive layout).
- Review specialist terms *тифлокомментарий* and *туннельное воспроизведение*. Drafted together with Ukrainian.
