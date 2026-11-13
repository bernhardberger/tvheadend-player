# Hungarian

## Register

Informal singular in instructions; conventional action nouns on buttons. No formal address.

## Typography

- Android folder: `values-hu`.
- Clock example keeps `\u00A0` before PM; seek-back keeps U+2212; ellipses use U+2026; no bidi control characters added. The draft contains no quotation marks, so none are decided.

## Plurals

`one` and `other`. Counted nouns stay singular after numerals.

## Term decisions

- `nav_channels` Csatornák; `nav_epg` Műsorújság; `nav_recordings` Felvételek; `context_watch` Megnézés.
- `timeshift_setting` Időeltolás; `timeshift_setting_description` Élő TV-adás szüneteltetése és visszatekerése; `details_retention` Megőrzési idő; mux = multiplex; buffer = puffer.
- Cancel lemondás, Stop leállítás (recorded part kept), Delete törlés.
- `player_tuning` Csatornaváltás, not scanning.
- Appliance = *TV-készülék mód*.
- `stop_recording_confirm_message` uses "A(z)" before an unknown title to avoid choosing the wrong article.

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

- Check TTS pronunciation of the × speed multiplier in `player_scrub_*`.

- Check *Alagutas lejátszás* (`tunneling_setting`) against preferred Android terminology.
- `channels_layout_immersive` "Teljes képernyős" is just above the old 14-character hint.
