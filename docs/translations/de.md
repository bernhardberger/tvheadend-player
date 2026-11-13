# German

## Register

Informal **du**, already in use in the app. Action labels are infinitives or nouns (Aufnehmen, Ansehen, Löschen).

## Typography

- Android folder: `values-de` (existing resources).
- German keeps ß; the de-CH override replaces it with ss.
- Dates and numbers come from the system locale, not the strings.

## Plurals

`one` and `other`.

## Term decisions

- `nav_channels` Sender: a TV service, not a frequency.
- `nav_epg` TV-Programm; `nav_recordings` Aufnahmen; `record` Aufnehmen; `context_watch` Ansehen.
- `details_retention` Aufbewahrung; `recording_state_missed` Verpasst; `recording_state_completed` Abgeschlossen; `timeshift_live` Live.
- `cancel_recording` Aufnahme abbrechen (future), `stop_recording` Aufnahme beenden (active), `context_delete` Löschen.
- `player_tuning` Sender wird eingestellt: switching to a channel, not scanning.

## Review status

0.5 remote key strings (`record_unavailable*`, `player_scrub_*`) drafted against `d7198998` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

Existing German strings predate this workflow; they are kept and only changed for source changes or accepted corrections.

0.4.0 Home strings drafted against `d2d9a9ab` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (high); still machine-drafted, no human reviewer.

0.4.1 setup search strings drafted against `e59ff38a` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 connections strings (`server_*`) drafted against `996a93f9` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 "Edit connection" (`setup_connection`, formerly "Set up connection") drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page buttons "Cancel" and "Go to Home" (`cancel`, `server_go_home`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page texts "Back to Settings", "%1$s is still in use" and the unreachable explanation (`back_to_settings`, `server_still_in_use`, `server_switch_unreachable_body`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 confirmation and playback card strings (`server_switch_question`, `server_remove_*`, `*_confirm_body`, `delete_folder_*_confirm_question`, `recording_recorded_of`) drafted against `90078f81` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer. Reviewer corrections accepted for `server_remove_body`, `server_remove_body_active`.

## Open questions

- Check TTS pronunciation of the hyphenated speed multipliers in `player_scrub_*`.
