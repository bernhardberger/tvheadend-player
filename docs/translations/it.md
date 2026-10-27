# Italian

## Register

Informal **tu** in directions; imperative action labels.

## Typography

- Android folder: `values-it`.
- Typographic apostrophe U+2019, avoiding Android ASCII apostrophe escaping.
- Clock example keeps `\u00A0` before PM; seek-back keeps U+2212; ellipses use U+2026; no bidi control characters added. The draft contains no quotation marks, so none are decided.

## Plurals

`one`, `many`, `other`. Integer 1 is singular; 0 and integers above 1 are plural.

## Term decisions

- `nav_channels` Canali; `nav_epg` Guida; `nav_recordings` Registrazioni; `context_watch` Guarda.
- `recording_state_missed` Mancata, distinct from Non riuscita and Annullata; `details_retention` Conservazione.
- Timeshift as a concept = *riproduzione differita*; `timeshift_setting` Riproduzione differita; `timeshift_setting_description` Pausa e riavvolgimento della TV in diretta; `timeshift_live` Diretta; `player_tuning` Cambio canale.
- Cancel annulla, Stop interrompi, Delete elimina.
- Appliance = *Modalità TV*. Passthrough and tunneling keep familiar loanwords.
- Recording states agree with feminine *registrazione*; programme playback lines use masculine *registrato*.

## Review status

Reconciled to frozen source `6d80a8ff` for 0.3.0 by GPT-6.1 Sol (medium), retaining unchanged round-1 text from `daebdd86`. Full resources reviewed by Sonnet 5.5 (medium); no definite linguistic defects reported. No human reviewer; remains labeled machine-drafted.

## Open questions

- Check *Modalità TV*, *riproduzione differita*, passthrough and tunneling for consistent product terms.
- Time and date articles (alle/dalle, il/al) may need adjustment after seeing formatter output.
