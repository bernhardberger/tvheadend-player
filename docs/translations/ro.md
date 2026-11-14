# Romanian

## Register

Informal singular where natural; imperative action buttons.

## Typography

- Android folder: `values-ro`.
- Clock example keeps `\u00A0` before PM; seek-back keeps U+2212; ellipses use U+2026; no bidi control characters added. The draft contains no quotation marks, so none are decided.

## Plurals

`one`, `few`, `other`. The `other` category uses "de" where required.

## Term decisions

- `nav_channels` Canale; `nav_epg` Ghid TV; `nav_recordings` Înregistrări; `context_watch` Vizionează.
- `timeshift_setting` Redare decalată; `timeshift_setting_description` Pauză și derulare înapoi pentru TV în direct; `details_retention` Păstrare; mux = multiplex.
- Cancel anulează, Stop oprește (recorded part kept), Delete șterge.
- `keep_recording` means continue the active recording, not keep a file.
- `player_tuning` Se schimbă canalul, not scanning.
- Appliance = *mod aparat TV*.

## Review status

0.5 remote key strings (`record_unavailable*`, `player_scrub_*`) drafted against `d7198998` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

Reconciled to frozen source `6d80a8ff` for 0.3.0 by GPT-6.1 Sol (medium), retaining unchanged round-1 text from `daebdd86`. Full resources reviewed by Sonnet 5.5 (medium); no definite linguistic defects reported. No human reviewer; remains labeled machine-drafted.

0.4.0 Home strings drafted against `d2d9a9ab` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (high); still machine-drafted, no human reviewer.

0.4.1 setup search strings drafted against `e59ff38a` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 connections strings (`server_*`) drafted against `996a93f9` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 "Edit connection" (`setup_connection`, formerly "Set up connection") drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page buttons "Cancel" and "Go to Home" (`cancel`, `server_go_home`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page texts "Back to Settings", "%1$s is still in use" and the unreachable explanation (`back_to_settings`, `server_still_in_use`, `server_switch_unreachable_body`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 confirmation and playback card strings (`server_switch_question`, `server_remove_*`, `*_confirm_body`, `delete_folder_*_confirm_question`, `recording_recorded_of`) drafted against `90078f81` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 automatic recording, timer, Keep until and expiry strings (`automatic_recording_*`, `autorec_editor_*`, `timer_editor_*`, `context_edit_*`, `context_skip_*`, `context_unskip_*`, `context_record_*`, `context_keep_until`, `recording_keep_until_*`, `recording_expir*`, `home_expiring_soon`, `recording_from_automatic`, `recording_state_and_origin`) drafted against `9e30bc99` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer. Reviewer corrections accepted for `autorec_editor_new_unknown`, `automatic_recording_kind_series`, `context_edit_series`, `autorec_editor_new_series`, `autorec_editor_delete_series`, `context_skip_series_support`, `automatic_recording_action_not_confirmed`.

0.5 Schedule tab strings for timers and the list of automatic recordings (`schedule_*`) drafted against `b257da70` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer. Reviewer corrections accepted for `schedule_automatic_upcoming`, `schedule_automatic_on_count`, `schedule_automatic_off_count`.

0.5 leave-card label `autorec_editor_discard` (English now "Discard changes") redrafted against `dcad2205` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

## Open questions

- Check TTS pronunciation of the × speed multiplier in `player_scrub_*`.

- Check *mod aparat TV*, *Imersiv* and *redare tunelizată* for conventional terms and length.
- `guide_time_prime_time` and long help strings may exceed soft limits.
- The series kind is *Înregistrare de serie* (`automatic_recording_kind_series`) while `recording_from_series_rule` says *Face parte din înregistrarea unei serii*; align the two.
- `schedule_automatic_keep` puts the Keep until heading before its value, which reads awkwardly with the Forever value (*Păstrează până: Pentru totdeauna*); check for a label that fits every value.
