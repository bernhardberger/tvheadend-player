# Danish

## Register

Informal **du**/din; imperative action labels.

## Typography

- Android folder: `values-da`.
- Typographic apostrophe U+2019 in two strings.
- Clock example keeps `\u00A0` before PM; seek-back keeps U+2212; ellipses use U+2026; no bidi control characters added. The draft contains no quotation marks, so none are decided.

## Plurals

`one` and `other`. Bulk-failure nouns follow the total `%2$d`.

## Term decisions

- `nav_channels` Kanaler; `nav_epg` Tv-guide; `nav_recordings` Optagelser; `context_watch` Se.
- `timeshift_setting` Tidsforskydning; `details_retention` Opbevaringstid; mux = multiplex.
- Cancel annuller, Stop stop (recorded part kept), Delete slet.
- `player_tuning` Skifter kanal, not scanning.
- Missed = *Ikke optaget*, distinct from failed and canceled.
- Appliance = *Tv-tilstand*; service labels use *tv-starttjeneste*.

## Review status

Machine-drafted by GPT-6.1 Sol (medium) in round 1 from source commit daebdd86; Sonnet 5.5 review pending; no human reviewer yet. Ships labeled machine-drafted.

## Open questions

- Check *Tv-tilstand*, *Fordybende* (immersive layout) and *Lydgennemgang* (audio passthrough) with Danish AV terminology.
- `recording_state_missed` may need a shorter native idiom.
