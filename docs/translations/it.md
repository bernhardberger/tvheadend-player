# Italian

## Register

Informal **tu** in directions; imperative action labels.

## Typography

- Android folder: `values-it`.
- Typographic apostrophe U+2019, avoiding Android ASCII apostrophe escaping.
- Clock example keeps `\u00A0` before PM; seek-back keeps U+2212; ellipses use U+2026; no bidi control characters added. The draft contains no quotation marks, so none are decided.

## Plurals

`one`, `many`, `other`. Integer 1 is singular; 0 and integers above 1 are plural.

## Term decisions

- `nav_channels` Canali; `nav_epg` Guida; `nav_recordings` Registrazioni; `context_watch` Guarda.
- `recording_state_missed` Mancata, distinct from Non riuscita and Annullata; `details_retention` Conservazione.
- Timeshift as a concept = *riproduzione differita*; `timeshift_setting` Riproduzione differita; `timeshift_setting_description` Pausa e riavvolgimento della TV in diretta; `timeshift_live` Diretta; `player_tuning` Cambio canale.
- Cancel annulla, Stop interrompi, Delete elimina.
- Appliance = *Modalità TV*. Passthrough and tunneling keep familiar loanwords.
- Recording states agree with feminine *registrazione*; programme playback lines use masculine *registrato*.

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

## Open questions

- Check TTS pronunciation of `player_scrub_*` speed multipliers.

- Check *Modalità TV*, *riproduzione differita*, passthrough and tunneling for consistent product terms.
- Time and date articles (alle/dalle, il/al) may need adjustment after seeing formatter output.
