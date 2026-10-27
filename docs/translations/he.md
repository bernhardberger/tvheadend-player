# Hebrew

## Register

Gender-neutral: nominal actions and impersonal guidance (יש, אפשר); no masculine reader imperatives.

## Typography

- Android folder: `values-iw`, the legacy code Android's own resources use. The shipped-language list names it `he`; the build maps it to `iw`.
- Right-to-left; no bidi controls and no placeholder reversal. `select_channel_hint` keeps the source "left".
- Clock example keeps `\u00A0`; ellipses use U+2026.

## Plurals

`one`, `two`, `other`, the agreed trial baseline; the `two` patterns use plural nouns with the numeral. Android/ICU category selection (`two`, historical `many`) differs by version and is unverified.

## Term decisions

- `nav_channels` ערוצים; `nav_epg` מדריך; `nav_recordings` הקלטות; `context_watch` צפייה.
- Live = שידור חי; `timeshift_setting` השהיה והרצה לאחור של שידור חי; `player_tuning` מעבר ערוץ, not scanning.
- Cancel ביטול ההקלטה המתוזמנת, Stop עצירת ההקלטה (content kept), Delete מחיקת הקלטה.
- Mux = מקבץ שידור, distinct from channel.

## Review status

Reconciled to frozen source `6d80a8ff` for 0.3.0 by GPT-6.1 Sol (medium), retaining unchanged round-1 text from `daebdd86`. Full resources reviewed by Sonnet 5.5 (medium); singular button/action labels, category number and audio-channel counts corrected. No human reviewer; remains labeled machine-drafted. RTL layout acceptance remains open; see the round report.

## Open questions

- Verify the plural categories on supported Android versions before integration.
- Mux vocabulary still needs native review. The current timeshift setting explicitly includes rewind, matching the frozen English source.
