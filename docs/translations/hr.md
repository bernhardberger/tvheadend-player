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
- `timeshift_setting` Vremenski pomak; mux = multipleks; buffer = međuspremnik.
- Cancel otkaži, Stop zaustavi (existing part kept), Delete izbriši.
- `player_tuning` Prebacivanje, not scanning.
- Appliance = *Način TV prijamnika*.

## Review status

Machine-drafted by GPT-6.1 Sol (medium) in round 1 from source commit daebdd86; Sonnet 5.5 review pending; no human reviewer yet. Ships labeled machine-drafted.

## Open questions

- The plan asks the Croatian contact to look over the language.
- Welcome uses masculine *Dobro došao*; check for a neutral greeting. `host` *Računalo* may need a clearer label.
