# Finnish

## Register

Informal singular where direct address is natural; mostly imperative labels without pronouns, and impersonal status text.

## Typography

- Android folder: `values-fi`.
- Clock example keeps `\u00A0` before PM; seek-back keeps U+2212; ellipses use U+2026; no bidi control characters added. The draft contains no quotation marks, so none are decided.

## Plurals

`one` and `other`. Bulk-failure text uses a genitive counted phrase for failed `%1$d` and a separate total `%2$d` phrase.

## Term decisions

- `nav_channels` Kanavat; `nav_epg` Ohjelmaopas; `nav_recordings` Tallenteet; `context_watch` Katso.
- Completed recording = *tallenne*; recording process or schedule = *tallennus*.
- `timeshift_setting` Suoran TV-lähetyksen tauotus ja takaisinkelaus; `details_retention` Säilytysaika; mux = kanavanippu; channel tag = kanavatunniste.
- Cancel peru, Stop pysäytä (recorded part kept), Delete poista.
- Spoken duration ratios use explicit elapsed/total phrases instead of slashes.
- Placeholder names are not inflected, to avoid suffixes on arbitrary names.

## Review status

Reconciled to frozen source `6d80a8ff` for 0.3.0 by GPT-6.1 Sol (medium), retaining unchanged round-1 text from `daebdd86`. Full resources reviewed by Sonnet 5.5 (medium); no definite linguistic defects reported. No human reviewer; remains labeled machine-drafted.

## Open questions

- Confirm *Uppoutuva* (immersive layout) and the TV-mode service wording with native reviewers.
- Screen-reader phrasing of duration ratios is unverified.
