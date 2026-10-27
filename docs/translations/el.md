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
- `timeshift_setting` Χρονική μετατόπιση; `timeshift_setting_description` Παύση και επανατύλιξη ζωντανής TV; `details_retention` Διατήρηση; tuner = δέκτης; mux = πολυπλεξία.
- Cancel ακύρωση, Stop διακοπή (recorded part kept), Delete διαγραφή.
- `player_tuning` Αλλαγή καναλιού, not scanning; `keep_recording` is συνέχιση εγγραφής.
- Appliance = *λειτουργία συσκευής TV*, an explanatory choice.

## Review status

Reconciled to frozen source `6d80a8ff` for 0.3.0 by GPT-6.1 Sol (medium), retaining unchanged round-1 text from `daebdd86`. Full resources reviewed by Sonnet 5.5 (medium); no definite linguistic defects reported. No human reviewer; remains labeled machine-drafted.

## Open questions

- Confirm Greek DVB use of *πολυπλεξία* for the multiplex (`error_203_reason`).
- Review *πίσω από τα ζωντανά* (`player_behind`) for natural TV wording.
