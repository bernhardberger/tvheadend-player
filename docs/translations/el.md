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

0.4.0 Home strings drafted against `d2d9a9ab` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (high); still machine-drafted, no human reviewer.

0.4.1 setup search strings drafted against `e59ff38a` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 connections strings (`server_*`) drafted against `996a93f9` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 "Edit connection" (`setup_connection`, formerly "Set up connection") drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

## Open questions

- Confirm Greek DVB use of *πολυπλεξία* for the multiplex (`error_203_reason`).
- Review *πίσω από τα ζωντανά* (`player_behind`) for natural TV wording.
