# Dutch

## Register

Informal **je**; infinitive action labels.

## Typography

- Android folder: `values-nl`.
- Clock example keeps `\u00A0` before PM; seek-back keeps U+2212; ellipses use U+2026; no bidi control characters added. The draft contains no quotation marks, so none are decided.

## Plurals

`one` and `other`. Bulk-failure nouns follow the total `%2$d`.

## Term decisions

- `nav_channels` Zenders; `nav_epg` Tv-gids; `nav_recordings` Opnamen; `context_watch` Kijken.
- Cancel annuleren (future), Stop stoppen (active, file kept), Delete verwijderen.
- Mux = multiplex, never *zender*. Timeshift stays the technical feature name; `timeshift_setting` Timeshift; `timeshift_setting_description` Live-tv pauzeren en terugspoelen; behind live = *achter live*.
- `player_tuning` Zender afstemmen: switching to a channel, not scanning.
- Appliance = *Tv-modus*; entry service = *tv-startservice*.
- `appliance_back_for_menu` keeps the literal Back label.

## Review status

Reconciled to frozen source `6d80a8ff` for 0.3.0 by GPT-6.1 Sol (medium), retaining unchanged round-1 text from `daebdd86`. Full resources reviewed by Sonnet 5.5 (medium); genre negation corrected from absence *Geen* to predicate *Niet*. No human reviewer; remains labeled machine-drafted.

## Open questions

- Confirm *Tv-modus* and *tv-startservice*; `channels_layout_immersive` is *Beeldvullend*.
- Long accessibility and audio strings keep meaning over length; TV fit unchecked.
