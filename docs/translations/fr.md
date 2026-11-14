# French

## Register

Informal **tu** in directions; infinitives on action labels.

## Typography

- Android folder: `values-fr`.
- Typographic apostrophe U+2019, so no unescaped ASCII apostrophe in Android text.
- Ordinary spaces before colons and question marks; the typography still needs review during integration.
- Clock example keeps `\u00A0` before PM; seek-back keeps U+2212; ellipses use U+2026; no bidi control characters added. The draft contains no quotation marks, so none are decided.

## Plurals

`one`, `many`, `other`. `one` covers 0 and 1; `many` is only for million multiples and uses plural wording.

## Term decisions

- `nav_channels` Chaînes (TV); audio channels stay *canaux* (`track_channels_count`).
- `details_broadcast_new` Inédit, repeat Rediffusion.
- `details_retention` Conservation; `recording_state_missed` Manqué, distinct from Échec and Annulé.
- Timeshift as a concept = *lecture différée*; `timeshift_setting` Lecture différée; `timeshift_setting_description` Mettre en pause et rembobiner la TV en direct; `timeshift_live` Direct; `player_tuning` Changement de chaîne; mux = multiplex.
- Cancel annuler, Stop arrêter, Delete supprimer; each confirmation keeps its consequence.
- Appliance = *Mode téléviseur*, not a literal household-appliance word.
- `recording_length_recorded` "Durée enregistrée : %1$s" avoids agreement with a formatted duration.

## Review status

0.5 remote key strings (`record_unavailable*`, `player_scrub_*`) drafted against `d7198998` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

Reconciled to frozen source `6d80a8ff` for 0.3.0 by GPT-6.1 Sol (medium), retaining unchanged round-1 text from `daebdd86`. Full resources reviewed by Sonnet 5.5 (medium); video-buffer agreement corrected. The keycap-localization suggestion was rejected because literal remote key names are preserved. No human reviewer; remains labeled machine-drafted.

0.4.0 Home strings drafted against `d2d9a9ab` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (high); still machine-drafted, no human reviewer.

0.4.1 setup search strings drafted against `e59ff38a` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 connections strings (`server_*`) drafted against `996a93f9` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 "Edit connection" (`setup_connection`, formerly "Set up connection") drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page buttons "Cancel" and "Go to Home" (`cancel`, `server_go_home`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page texts "Back to Settings", "%1$s is still in use" and the unreachable explanation (`back_to_settings`, `server_still_in_use`, `server_switch_unreachable_body`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 confirmation and playback card strings (`server_switch_question`, `server_remove_*`, `*_confirm_body`, `delete_folder_*_confirm_question`, `recording_recorded_of`) drafted against `90078f81` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 automatic recording, timer, Keep until and expiry strings (`automatic_recording_*`, `autorec_editor_*`, `timer_editor_*`, `context_edit_*`, `context_skip_*`, `context_unskip_*`, `context_record_*`, `context_keep_until`, `recording_keep_until_*`, `recording_expir*`, `home_expiring_soon`, `recording_from_automatic`, `recording_state_and_origin`) drafted against `9e30bc99` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer. Reviewer corrections accepted for `autorec_editor_any_time`, `home_expiring_soon`, `autorec_editor_delete_programs`, `recording_keep_until_forever`, `autorec_editor_more_preview`.

0.5 Schedule tab strings for timers and the list of automatic recordings (`schedule_*`) drafted against `b257da70` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer. Reviewer corrections accepted for `schedule_new_timer_help`.

## Open questions

- Check TTS pronunciation of `player_scrub_*` speed multipliers.

- *Mode téléviseur* and the retained English key labels in `player_channel_card_hint` need review.
- `guide_time_prime_time` (24 characters) exceeds the old soft limit; fit unchecked.
- Skipping one episode or recording uses *Ignorer* (`context_skip_*`), playback skips use *Passer*; check that the split reads as intended.
- `schedule_automatic_keep` puts the Keep until heading before its value, which reads awkwardly with the Forever value (*Conserver jusqu’à : Pour toujours*); check for a label that fits every value.
