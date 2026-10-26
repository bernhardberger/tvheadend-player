# Swedish

## Register

Informal **du**/din; neutral or imperative action labels.

## Typography

- Android folder: `values-sv`.
- Clock example keeps `\u00A0` before PM; seek-back keeps U+2212; ellipses use U+2026; no bidi control characters added. The draft contains no quotation marks, so none are decided.

## Plurals

`one` and `other`. Bulk failures use the total `%2$d` for noun agreement.

## Term decisions

- `nav_channels` Kanaler; `nav_epg` Tv-guide; `nav_recordings` Inspelningar; `context_watch` Titta.
- `timeshift_setting` Tidsförskjutning; `details_retention` Lagringstid; mux = multiplex.
- Cancel avboka, Stop stoppa, Delete radera; generic canceled server states use *avbruten*.
- `player_tuning` Byter kanal, not scanning.
- Appliance = *Tv-läge*; service labels use *tv-starttjänst*.
- `audio_passthrough_setting` ljudgenomströmning; `channels_layout_immersive` Uppslukande.

## Review status

Machine-drafted by GPT-6.1 Sol (medium) in round 1 from source commit daebdd86; Sonnet 5.5 review pending; no human reviewer yet. Ships labeled machine-drafted.

## Open questions

- Confirm *Tv-läge* / *tv-starttjänst* and whether TV users expect the English term for audio passthrough.
- `guide_time_prime_time` exceeds the soft length limit.
