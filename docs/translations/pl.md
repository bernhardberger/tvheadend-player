# Polish

## Register

Informal singular imperatives; lowercase twoje/twój in explanations.

## Typography

- Android folder: `values-pl`.
- Clock example keeps `\u00A0` before PM; seek-back keeps U+2212; ellipses use U+2026; no bidi control characters added. The draft contains no quotation marks, so none are decided.

## Plurals

`one`, `few`, `many`, `other`. Current integer selectors never select the decimal `other`.

## Term decisions

- `nav_channels` Kanały; `nav_epg` Program TV; `nav_recordings` Nagrania; `context_watch` Oglądaj.
- Recording file = *nagranie*, process = *nagrywanie*; `details_retention` Przechowywanie.
- `timeshift_setting` Przesunięcie w czasie; `timeshift_setting_description` Wstrzymuj i przewijaj telewizję na żywo; mux = multipleks; `player_tuning` Przełączanie, not scanning.
- Cancel anuluj, Stop zatrzymaj (recorded part kept), Delete usuń.
- `recording_state_missed` Pominięte; the notice says *Nagrywanie nie odbyło się*.
- Bulk-failure denominator follows the total `%2$d`; "z … nagrania/nagrań" uses the genitive.

## Review status

0.5 remote key strings (`record_unavailable*`, `player_scrub_*`) drafted against `d7198998` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

Reconciled to frozen source `6d80a8ff` for 0.3.0 by GPT-6.1 Sol (medium), retaining unchanged round-1 text from `daebdd86`. Full resources reviewed by Sonnet 5.5 (medium); no definite linguistic defects reported. No human reviewer; remains labeled machine-drafted.

0.4.0 Home strings drafted against `d2d9a9ab` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (high); still machine-drafted, no human reviewer.

0.4.1 setup search strings drafted against `e59ff38a` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 connections strings (`server_*`) drafted against `996a93f9` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 "Edit connection" (`setup_connection`, formerly "Set up connection") drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page buttons "Cancel" and "Go to Home" (`cancel`, `server_go_home`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page texts "Back to Settings", "%1$s is still in use" and the unreachable explanation (`back_to_settings`, `server_still_in_use`, `server_switch_unreachable_body`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 confirmation and playback card strings (`server_switch_question`, `server_remove_*`, `*_confirm_body`, `delete_folder_*_confirm_question`, `recording_recorded_of`) drafted against `90078f81` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer. Reviewer corrections accepted for `server_remove_body_active`.

## Open questions

- Check TTS pronunciation of the × speed multiplier in `player_scrub_*`.

- Check whether Polish TV terminology prefers another label than *Pominięte* for missed.
- `tunneling_setting` exceeds the old 36-character guidance to keep the experimental status; `guide_time_prime_time` is *Pasmo główne*.
