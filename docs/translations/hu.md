# Hungarian

## Register

Informal singular in instructions; conventional action nouns on buttons. No formal address.

## Typography

- Android folder: `values-hu`.
- Clock example keeps `\u00A0` before PM; seek-back keeps U+2212; ellipses use U+2026; no bidi control characters added. The draft contains no quotation marks, so none are decided.

## Plurals

`one` and `other`. Counted nouns stay singular after numerals.

## Term decisions

- `nav_channels` Csatornák; `nav_epg` Műsorújság; `nav_recordings` Felvételek; `context_watch` Megnézés.
- `timeshift_setting` Időeltolás; `details_retention` Megőrzési idő; mux = multiplex; buffer = puffer.
- Cancel lemondás, Stop leállítás (recorded part kept), Delete törlés.
- `player_tuning` Csatornaváltás, not scanning.
- Appliance = *TV-készülék mód*.
- `stop_recording_confirm_message` uses "A(z)" before an unknown title to avoid choosing the wrong article.

## Review status

Machine-drafted by GPT-6.1 Sol (medium) in round 1 from source commit daebdd86; Sonnet 5.5 review pending; no human reviewer yet. Ships labeled machine-drafted.

## Open questions

- Check *Alagutas lejátszás* (`tunneling_setting`) against preferred Android terminology.
- `channels_layout_immersive` "Teljes képernyős" is just above the old 14-character hint.
