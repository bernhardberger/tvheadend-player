# Croatian

## Register

Informal singular; imperative action labels. Croatian forms throughout (poslužitelj, postavke), not Serbian interface terms.

## Typography

- Android folder: `values-hr`.
- Clock example keeps `\u00A0` before PM; seek-back keeps U+2212; ellipses use U+2026; no bidi control characters added. The draft contains no quotation marks, so none are decided.

## Plurals

`one`, `few`, `other`. Bulk failures follow the total `%2$d`; the denominator after "od ukupno" is genitive.

## Term decisions

- `nav_channels` Kanali; `nav_epg` TV vodič; `nav_recordings` Snimke; `context_watch` Gledaj.
- Programme = *emisija*; recording file = *snimka*, process = *snimanje*; `details_retention` Čuvanje.
- `timeshift_setting` Vremenski pomak; `timeshift_setting_description` Pauziraj i premotavaj TV uživo; mux = multipleks; buffer = međuspremnik.
- Cancel otkaži, Stop zaustavi (existing part kept), Delete izbriši.
- `player_tuning` Prebacivanje, not scanning.
- Appliance = *Način TV prijamnika*.

## Review status

0.5 remote key strings (`record_unavailable*`, `player_scrub_*`) drafted against `d7198998` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

Reconciled to frozen source `6d80a8ff` for 0.3.0 by GPT-6.1 Sol (medium), retaining unchanged round-1 text from `daebdd86`. Full resources reviewed by Sonnet 5.5 (medium); occupied-tuner terminology made consistent. No human reviewer; remains labeled machine-drafted.

0.4.0 Home strings drafted against `d2d9a9ab` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (high); still machine-drafted, no human reviewer.

0.4.1 setup search strings drafted against `e59ff38a` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 connections strings (`server_*`) drafted against `996a93f9` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 "Edit connection" (`setup_connection`, formerly "Set up connection") drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page buttons "Cancel" and "Go to Home" (`cancel`, `server_go_home`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page texts "Back to Settings", "%1$s is still in use" and the unreachable explanation (`back_to_settings`, `server_still_in_use`, `server_switch_unreachable_body`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 confirmation and playback card strings (`server_switch_question`, `server_remove_*`, `*_confirm_body`, `delete_folder_*_confirm_question`, `recording_recorded_of`) drafted against `90078f81` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 automatic recording, timer, Keep until and expiry strings (`automatic_recording_*`, `autorec_editor_*`, `timer_editor_*`, `context_edit_*`, `context_skip_*`, `context_unskip_*`, `context_record_*`, `context_keep_until`, `recording_keep_until_*`, `recording_expir*`, `home_expiring_soon`, `recording_from_automatic`, `recording_state_and_origin`) drafted against `9e30bc99` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer. Reviewer corrections accepted for `autorec_editor_delete_episodes`, `autorec_editor_delete_programs`, `autorec_editor_keep_year`, `autorec_editor_no_matches`, `autorec_editor_words_empty_summary`, `automatic_recording_new_choose`, `timer_editor_weekdays`.

0.5 Schedule tab strings for timers and the list of automatic recordings (`schedule_*`) drafted against `b257da70` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer. Reviewer corrections accepted for `schedule_automatic_empty_help`, `schedule_automatic_new_help`, `schedule_new_timer_help`.

0.5 leave-card label `autorec_editor_discard` (English now "Discard changes") redrafted against `dcad2205` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

## Open questions

- Check TTS pronunciation of the × speed multiplier in `player_scrub_*`.

- The plan asks the Croatian contact to look over the language.
- Welcome uses masculine *Dobro došao*; check for a neutral greeting. `host` *Računalo* may need a clearer label.
