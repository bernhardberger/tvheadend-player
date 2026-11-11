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
- `timeshift_setting` Redare decalată; `timeshift_setting_description` Pauză și derulare înapoi pentru TV în direct; `details_retention` Păstrare; mux = multiplex.
- Cancel anulează, Stop oprește (recorded part kept), Delete șterge.
- `keep_recording` means continue the active recording, not keep a file.
- `player_tuning` Se schimbă canalul, not scanning.
- Appliance = *mod aparat TV*.

## Review status

Reconciled to frozen source `6d80a8ff` for 0.3.0 by GPT-6.1 Sol (medium), retaining unchanged round-1 text from `daebdd86`. Full resources reviewed by Sonnet 5.5 (medium); no definite linguistic defects reported. No human reviewer; remains labeled machine-drafted.

0.4.0 Home strings drafted against `d2d9a9ab` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (high); still machine-drafted, no human reviewer.

0.4.1 setup search strings drafted against `e59ff38a` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 connections strings (`server_*`) drafted against `996a93f9` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 "Edit connection" (`setup_connection`, formerly "Set up connection") drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

## Open questions

- Check *mod aparat TV*, *Imersiv* and *redare tunelizată* for conventional terms and length.
- `guide_time_prime_time` and long help strings may exceed soft limits.
