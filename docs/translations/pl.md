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
- `timeshift_setting` Wstrzymuj i przewijaj telewizję na żywo; mux = multipleks; `player_tuning` Przełączanie, not scanning.
- Cancel anuluj, Stop zatrzymaj (recorded part kept), Delete usuń.
- `recording_state_missed` Pominięte; the notice says *Nagrywanie nie odbyło się*.
- Bulk-failure denominator follows the total `%2$d`; "z … nagrania/nagrań" uses the genitive.

## Review status

Reconciled to frozen source `6d80a8ff` for 0.3.0 by GPT-6.1 Sol (medium), retaining unchanged round-1 text from `daebdd86`. Full resources reviewed by Sonnet 5.5 (medium); no definite linguistic defects reported. No human reviewer; remains labeled machine-drafted.

## Open questions

- Check whether Polish TV terminology prefers another label than *Pominięte* for missed.
- `tunneling_setting` exceeds the old 36-character guidance to keep the experimental status; `guide_time_prime_time` is *Pasmo główne*.
