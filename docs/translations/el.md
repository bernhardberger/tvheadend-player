# Greek

## Register

Informal singular instructions; neutral noun labels on buttons where idiomatic.

## Typography

- Android folder: `values-el`.
- Compact season/episode prefixes are Σ/Ε.
- Clock example keeps `\u00A0` before PM; seek-back keeps U+2212; ellipses use U+2026; no bidi control characters added. The draft contains no quotation marks, so none are decided.

## Plurals

`one` and `other`. Bulk-failure messages use noun-led wording so agreement does not depend on the failed count.

## Term decisions

- `nav_channels` Κανάλια; `nav_epg` Οδηγός; `nav_recordings` Εγγραφές; `context_watch` Προβολή.
- `timeshift_setting` Χρονική μετατόπιση; `details_retention` Διατήρηση; tuner = δέκτης; mux = πολυπλεξία.
- Cancel ακύρωση, Stop διακοπή (recorded part kept), Delete διαγραφή.
- `player_tuning` Αλλαγή καναλιού, not scanning; `keep_recording` is συνέχιση εγγραφής.
- Appliance = *λειτουργία συσκευής TV*, an explanatory choice.

## Review status

Machine-drafted by GPT-6.1 Sol (medium) in round 1 from source commit daebdd86; Sonnet 5.5 review pending; no human reviewer yet. Ships labeled machine-drafted.

## Open questions

- Confirm Greek DVB use of *πολυπλεξία* for the multiplex (`error_203_reason`).
- Review *πίσω από τα ζωντανά* (`player_behind`) for natural TV wording.
