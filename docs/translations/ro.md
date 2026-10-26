# Romanian

## Register

Informal singular where natural; imperative action buttons.

## Typography

- Android folder: `values-ro`.
- Clock example keeps `\u00A0` before PM; seek-back keeps U+2212; ellipses use U+2026; no bidi control characters added. The draft contains no quotation marks, so none are decided.

## Plurals

`one`, `few`, `other`. The `other` category uses "de" where required.

## Term decisions

- `nav_channels` Canale; `nav_epg` Ghid TV; `nav_recordings` Înregistrări; `context_watch` Vizionează.
- `timeshift_setting` Redare decalată; `details_retention` Păstrare; mux = multiplex.
- Cancel anulează, Stop oprește (recorded part kept), Delete șterge.
- `keep_recording` means continue the active recording, not keep a file.
- `player_tuning` Se schimbă canalul, not scanning.
- Appliance = *mod aparat TV*.

## Review status

Machine-drafted by GPT-6.1 Sol (medium) in round 1 from source commit daebdd86; Sonnet 5.5 review pending; no human reviewer yet. Ships labeled machine-drafted.

## Open questions

- Check *mod aparat TV*, *Imersiv* and *redare tunelizată* for conventional terms and length.
- `guide_time_prime_time` and long help strings may exceed soft limits.
