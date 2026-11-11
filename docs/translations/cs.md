# Czech

## Register

Informal singular in explanations; infinitive action labels. Plan: informal unless the Czech contact objects.

## Typography

- Android folder: `values-cs`.
- Clock example keeps `\u00A0` before PM; seek-back keeps U+2212; ellipses use U+2026; no bidi control characters added. The draft contains no quotation marks, so none are decided.

## Plurals

`one`, `few`, `many`, `other`. `many` is the decimal category, unreachable through current integer selectors.

## Term decisions

- `nav_channels` Kanály; `nav_epg` Program; `nav_recordings` Nahrávky; `context_watch` Sledovat.
- Recording file = *nahrávka*, process = *nahrávání*; `details_retention` Doba uchování.
- `timeshift_setting` Časový posun; `timeshift_setting_description` Pozastavit a přetáčet živé vysílání; mux = multiplex; `player_tuning` Přepínání, not scanning.
- Cancel zrušit, Stop zastavit, Delete smazat.
- `recording_state_missed` Nenahráno; the notice says *Nahrávání neproběhlo*.
- Bulk-failure sentences use the total `%2$d` for the inflected denominator.

## Review status

Reconciled to frozen source `6d80a8ff` for 0.3.0 by GPT-6.1 Sol (medium), retaining unchanged round-1 text from `daebdd86`. Full resources reviewed by Sonnet 5.5 (medium); empty-search grammar and gender-neutral resume wording corrected. No human reviewer; remains labeled machine-drafted.

0.4.0 Home strings drafted against `d2d9a9ab` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (high); still machine-drafted, no human reviewer.

0.4.1 setup search strings drafted against `e59ff38a` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 connections strings (`server_*`) drafted against `996a93f9` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 "Edit connection" (`setup_connection`, formerly "Set up connection") drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page buttons "Cancel" and "Go to Home" (`cancel`, `server_go_home`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page texts "Back to Settings", "%1$s is still in use" and the unreachable explanation (`back_to_settings`, `server_still_in_use`, `server_switch_unreachable_body`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

## Open questions

- The plan asks the Czech contact to look over the language.
- `channels_layout_immersive` "Na celou obrazovku" exceeds the old 14-character hint; `guide_time_prime_time` "Hlavní čas" and appliance label "režim TV přijímače" need review.
