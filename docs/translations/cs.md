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
- `timeshift_setting` Pozastavit a přetáčet živé vysílání; mux = multiplex; `player_tuning` Přepínání, not scanning.
- Cancel zrušit, Stop zastavit, Delete smazat.
- `recording_state_missed` Nenahráno; the notice says *Nahrávání neproběhlo*.
- Bulk-failure sentences use the total `%2$d` for the inflected denominator.

## Review status

Reconciled to frozen source `6d80a8ff` for 0.3.0 by GPT-6.1 Sol (medium), retaining unchanged round-1 text from `daebdd86`. Full resources reviewed by Sonnet 5.5 (medium); empty-search grammar and gender-neutral resume wording corrected. No human reviewer; remains labeled machine-drafted.

## Open questions

- The plan asks the Czech contact to look over the language.
- `channels_layout_immersive` "Na celou obrazovku" exceeds the old 14-character hint; `guide_time_prime_time` "Hlavní čas" and appliance label "režim TV přijímače" need review.
