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
- `timeshift_setting` Ajansiirto; `details_retention` Säilytysaika; mux = kanavanippu; channel tag = kanavatunniste.
- Cancel peru, Stop pysäytä (recorded part kept), Delete poista.
- Spoken duration ratios use explicit elapsed/total phrases instead of slashes.
- Placeholder names are not inflected, to avoid suffixes on arbitrary names.

## Review status

Machine-drafted by GPT-6.1 Sol (medium) in round 1 from source commit daebdd86; Sonnet 5.5 review pending; no human reviewer yet. Ships labeled machine-drafted.

## Open questions

- Confirm *Uppoutuva* (immersive layout) and the TV-mode service wording with native reviewers.
- Screen-reader phrasing of duration ratios is unverified.
