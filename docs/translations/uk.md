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
- `timeshift_setting` Відкладений перегляд; `timeshift_setting_description` Пауза й перемотування прямого ефіру; `details_retention` Термін зберігання; mux = мультиплекс; tuner = тюнер.
- Cancel скасувати запис, Stop зупинити запис (recorded part kept), Delete видалити запис.
- `player_tuning` Перемикання, not scanning.
- `profile_direct_streaming` "Пряма трансляція потоку" is kept distinct from *прямий ефір*.

## Review status

0.5 remote key strings (`record_unavailable*`, `player_scrub_*`) drafted against `d7198998` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

Reconciled to frozen source `6d80a8ff` for 0.3.0 by GPT-6.1 Sol (medium), retaining unchanged round-1 text from `daebdd86`. Full resources reviewed by Sonnet 5.5 (medium), including the recovered unchanged About draft; `keep_channel_setting` now explicitly means keeping the channel tuned during pause, rather than saving a channel. No human reviewer; remains labeled machine-drafted.

0.4.0 Home strings drafted against `d2d9a9ab` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (high); still machine-drafted, no human reviewer.

0.4.1 setup search strings drafted against `e59ff38a` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 connections strings (`server_*`) drafted against `996a93f9` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 "Edit connection" (`setup_connection`, formerly "Set up connection") drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page buttons "Cancel" and "Go to Home" (`cancel`, `server_go_home`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page texts "Back to Settings", "%1$s is still in use" and the unreachable explanation (`back_to_settings`, `server_still_in_use`, `server_switch_unreachable_body`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 confirmation and playback card strings (`server_switch_question`, `server_remove_*`, `*_confirm_body`, `delete_folder_*_confirm_question`, `recording_recorded_of`) drafted against `90078f81` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

## Open questions

- Check TTS pronunciation of the × speed multiplier in `player_scrub_*`.

- Confirm *режим ТВ-приставки* and *Занурення* (immersive layout).
- Confirm *пряме передавання звуку* (passthrough) and *тунельне відтворення*. Drafted together with Russian.
