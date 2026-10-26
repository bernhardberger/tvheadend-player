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
- `timeshift_setting` Časový posun; mux = multiplex; `player_tuning` Přepínání, not scanning.
- Cancel zrušit, Stop zastavit, Delete smazat.
- `recording_state_missed` Nenahráno; the notice says *Nahrávání neproběhlo*.
- Bulk-failure sentences use the total `%2$d` for the inflected denominator.

## Review status

Machine-drafted by GPT-6.1 Sol (medium) in round 1 from source commit daebdd86; Sonnet 5.5 review pending; no human reviewer yet. Ships labeled machine-drafted.

## Open questions

- The plan asks the Czech contact to look over the language.
- `channels_layout_immersive` "Na celou obrazovku" exceeds the old 14-character hint; `guide_time_prime_time` "Hlavní čas" and appliance label "režim TV přijímače" need review.
