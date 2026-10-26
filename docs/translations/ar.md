# Arabic

## Register

Modern Standard Arabic. Gender-neutral: nominal labels and impersonal guidance (يمكن, يلزم); no masculine imperatives.

## Typography

- Android folder: `values-ar`. Right-to-left; the draft adds no bidi controls and does not reverse placeholder order.
- `select_channel_hint` keeps the source "left" instead of assuming a mirrored layout.
- Digit presentation and mixed-script time ranges need integrated checks.
- Clock example keeps `\u00A0`; ellipses use U+2026.

## Plurals

All six: `zero`, `one`, `two`, `few`, `many`, `other`, each keeping the numeral. Failed-folder strings select on the total `%2$d`.

## Term decisions

- `nav_channels` القنوات; `nav_epg` دليل البرامج; `nav_recordings` التسجيلات; `record` تسجيل.
- `timeshift_setting` الإزاحة الزمنية; live = مباشر; `player_tuning` جارٍ تبديل القناة, not scanning.
- Cancel إلغاء التسجيل المجدول, Stop إيقاف التسجيل (content kept), Delete حذف التسجيل.
- Mux = مُجمّع البث (`error_203_reason`), distinct from channel.
- Role headings are collective labels.

## Review status

Machine-drafted by GPT-6.1 Sol (medium) in round 1 from source commit daebdd86; Sonnet 5.5 review pending; no human reviewer yet. Ships labeled machine-drafted.

## Open questions

- Regional broadcast terminology for multiplex and timeshift needs reviewer attention.
- Actual RTL layout, focus mirroring and digit presentation need captures and a TV check.
