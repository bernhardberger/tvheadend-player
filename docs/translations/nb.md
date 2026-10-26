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
- `timeshift_setting` Tidsforskyvning; `details_retention` Lagringstid; mux = multipleks; channel tag = kanaletikett.
- Cancel avlys, Stop stopp (captured part kept), Delete slett.
- Appliance = *TV-modus* / *TV-starttjeneste*.
- `settings_channel_tags_last_enabled` uses the setting-state word *på*.

## Review status

Machine-drafted by GPT-6.1 Sol (medium) in round 1 from source commit daebdd86; Sonnet 5.5 review pending; no human reviewer yet. Ships labeled machine-drafted.

## Open questions

- Confirm *Oppslukende* (immersive) and *Lydgjennomgang* (passthrough) with native AV and UI terminology.
- UI fit is unverified.
