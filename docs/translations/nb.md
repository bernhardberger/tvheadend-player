# Norwegian Bokmål

## Register

Informal **du**/din where natural; short imperative labels.

## Typography

- Android folder: `values-nb`.
- Clock example keeps `\u00A0` before PM; seek-back keeps U+2212; ellipses use U+2026; no bidi control characters added. The draft contains no quotation marks, so none are decided.

## Plurals

`one` and `other`. *Opptak* is unchanged in the plural.

## Term decisions

- `nav_channels` Kanaler; `nav_epg` TV-guide; `nav_recordings` Opptak; `context_watch` Se.
- `timeshift_setting` Tidsforskyvning; `timeshift_setting_description` Pause og spol tilbake direktesendt TV; `details_retention` Lagringstid; mux = multipleks; channel tag = kanaletikett.
- Cancel avlys, Stop stopp (captured part kept), Delete slett.
- Appliance = *TV-modus* / *TV-starttjeneste*.
- `settings_channel_tags_last_enabled` uses the setting-state word *på*.

## Review status

Reconciled to frozen source `6d80a8ff` for 0.3.0 by GPT-6.1 Sol (medium), retaining unchanged round-1 text from `daebdd86`. Full resources reviewed by Sonnet 5.5 (medium); no definite linguistic defects reported. No human reviewer; remains labeled machine-drafted.

## Open questions

- Confirm *Oppslukende* (immersive) and *Lydgjennomgang* (passthrough) with native AV and UI terminology.
- UI fit is unverified.
