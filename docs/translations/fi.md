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
- `timeshift_setting` Ajansiirto; `timeshift_setting_description` Suoran TV-lähetyksen tauotus ja takaisinkelaus; `details_retention` Säilytysaika; mux = kanavanippu; channel tag = kanavatunniste.
- Cancel peru, Stop pysäytä (recorded part kept), Delete poista.
- Spoken duration ratios use explicit elapsed/total phrases instead of slashes.
- Placeholder names are not inflected, to avoid suffixes on arbitrary names.

## Review status

0.5 remote key strings (`record_unavailable*`, `player_scrub_*`) drafted against `d7198998` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

Reconciled to frozen source `6d80a8ff` for 0.3.0 by GPT-6.1 Sol (medium), retaining unchanged round-1 text from `daebdd86`. Full resources reviewed by Sonnet 5.5 (medium); no definite linguistic defects reported. No human reviewer; remains labeled machine-drafted.

0.4.0 Home strings drafted against `d2d9a9ab` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (high); still machine-drafted, no human reviewer.

0.4.1 setup search strings drafted against `e59ff38a` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 connections strings (`server_*`) drafted against `996a93f9` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 "Edit connection" (`setup_connection`, formerly "Set up connection") drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page buttons "Cancel" and "Go to Home" (`cancel`, `server_go_home`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page texts "Back to Settings", "%1$s is still in use" and the unreachable explanation (`back_to_settings`, `server_still_in_use`, `server_switch_unreachable_body`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

## Open questions

- Check TTS pronunciation of the hyphenated speed multipliers in `player_scrub_*`.

- Confirm *Uppoutuva* (immersive layout) and the TV-mode service wording with native reviewers.
- Screen-reader phrasing of duration ratios is unverified.
