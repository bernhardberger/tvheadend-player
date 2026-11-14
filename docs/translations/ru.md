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
- `timeshift_setting` Отложенный просмотр; `timeshift_setting_description` Пауза и перемотка прямого эфира; `details_retention` Срок хранения; mux = мультиплекс; tuner = тюнер.
- Cancel отменить запись, Stop остановить запись (recorded part kept), Delete удалить запись.
- `keep_recording` is продолжить запись; `player_tuning` Переключение, not scanning.
- `profile_direct_streaming` "Прямая трансляция потока" is kept distinct from *прямой эфир*.
- Audio-channel count uses "Каналов: %1$d" to avoid an inflected noun.

## Review status

0.5 remote key strings (`record_unavailable*`, `player_scrub_*`) drafted against `d7198998` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

Reconciled to frozen source `6d80a8ff` for 0.3.0 by GPT-6.1 Sol (medium), retaining unchanged round-1 text from `daebdd86`. Full resources reviewed by Sonnet 5.5 (medium); `keep_channel_setting` now explicitly means keeping the channel tuned during pause, rather than saving a channel. No human reviewer; remains labeled machine-drafted.

0.4.0 Home strings drafted against `d2d9a9ab` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (high); still machine-drafted, no human reviewer.

0.4.1 setup search strings drafted against `e59ff38a` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 connections strings (`server_*`) drafted against `996a93f9` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 "Edit connection" (`setup_connection`, formerly "Set up connection") drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page buttons "Cancel" and "Go to Home" (`cancel`, `server_go_home`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page texts "Back to Settings", "%1$s is still in use" and the unreachable explanation (`back_to_settings`, `server_still_in_use`, `server_switch_unreachable_body`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 confirmation and playback card strings (`server_switch_question`, `server_remove_*`, `*_confirm_body`, `delete_folder_*_confirm_question`, `recording_recorded_of`) drafted against `90078f81` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 automatic recording, timer, Keep until and expiry strings (`automatic_recording_*`, `autorec_editor_*`, `timer_editor_*`, `context_edit_*`, `context_skip_*`, `context_unskip_*`, `context_record_*`, `context_keep_until`, `recording_keep_until_*`, `recording_expir*`, `home_expiring_soon`, `recording_from_automatic`, `recording_state_and_origin`) drafted against `9e30bc99` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer. Reviewer corrections accepted for `autorec_editor_titles_only`, `autorec_editor_full_text`, `autorec_editor_left_out`, `autorec_editor_off_help`, `autorec_editor_discard`, `autorec_editor_duplicate`.

0.5 Schedule tab strings for timers and the list of automatic recordings (`schedule_*`) drafted against `b257da70` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 leave-card label `autorec_editor_discard` (English now "Discard changes") checked against `dcad2205` by GPT-6.1 Sol (medium) and Sonnet 5.5 (medium); the existing wording already says what is lost and stays.

## Open questions

- Check TTS pronunciation of the × speed multiplier in `player_scrub_*`.

- Confirm *режим ТВ-приставки* and *Погружение* (immersive layout).
- Review specialist terms *тифлокомментарий* and *туннельное воспроизведение*. Drafted together with Ukrainian.
