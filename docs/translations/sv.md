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
- `timeshift_setting` Tidsförskjutning; `timeshift_setting_description` Pausa och spola tillbaka direktsänd tv; `details_retention` Lagringstid; mux = multiplex.
- Cancel avboka, Stop stoppa, Delete radera; generic canceled server states use *avbruten*.
- `player_tuning` Byter kanal, not scanning.
- Appliance = *Tv-läge*; service labels use *tv-starttjänst*.
- `audio_passthrough_setting` ljudgenomströmning; `channels_layout_immersive` Uppslukande.

## Review status

Reconciled to frozen source `6d80a8ff` for 0.3.0 by GPT-6.1 Sol (medium), retaining unchanged round-1 text from `daebdd86`. Full resources reviewed by Sonnet 5.5 (medium); no definite linguistic defects reported. No human reviewer; remains labeled machine-drafted.

0.4.0 Home strings drafted against `d2d9a9ab` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (high); still machine-drafted, no human reviewer.

0.4.1 setup search strings drafted against `e59ff38a` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 connections strings (`server_*`) drafted against `996a93f9` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 "Edit connection" (`setup_connection`, formerly "Set up connection") drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

## Open questions

- Confirm *Tv-läge* / *tv-starttjänst* and whether TV users expect the English term for audio passthrough.
- `guide_time_prime_time` exceeds the soft length limit.
