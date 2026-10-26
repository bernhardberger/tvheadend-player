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
- Timeshift = *riproduzione differita*; `timeshift_live` Diretta; `player_tuning` Cambio canale.
- Cancel annulla, Stop interrompi, Delete elimina.
- Appliance = *Modalità TV*. Passthrough and tunneling keep familiar loanwords.
- Recording states agree with feminine *registrazione*; programme playback lines use masculine *registrato*.

## Review status

Machine-drafted by GPT-6.1 Sol (medium) in round 1 from source commit daebdd86; Sonnet 5.5 review pending; no human reviewer yet. Ships labeled machine-drafted.

## Open questions

- Check *Modalità TV*, *riproduzione differita*, passthrough and tunneling for consistent product terms.
- Time and date articles (alle/dalle, il/al) may need adjustment after seeing formatter output.
