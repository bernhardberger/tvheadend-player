# Japanese

## Register

Neutral action labels with polite explanatory and recovery sentences (approved 2026-10-26).

## Typography

- Android folder: `values-ja`.
- Full-width colon in 18 strings; 「」 used once.
- Clock example keeps `\u00A0` before PM; seek-back keeps U+2212; ellipses use U+2026; no bidi control characters added. The draft contains no quotation marks, so none are decided.

## Plurals

`other` only; numeric placeholders are kept.

## Term decisions

- `nav_channels` チャンネル; `nav_epg` 番組表; `nav_recordings` 録画; `context_watch` 視聴.
- `timeshift_setting` タイムシフト; `timeshift_setting_description` ライブ放送の一時停止と巻き戻し; live = ライブ; `player_tuning` チャンネル切り替え中, not scanning.
- Cancel 録画予約を取り消す (future), Stop 録画を停止 (recorded part kept), Delete 削除.
- Mux = マルチプレックス; `details_retention` 保存期間.

## Review status

Reconciled to frozen source `6d80a8ff` for 0.3.0 by GPT-6.1 Sol (medium), retaining unchanged round-1 text from `daebdd86`. Full resources reviewed by Sonnet 5.5 (medium); spoken seek direction and legal-notice meaning corrected. No human reviewer; remains labeled machine-drafted.

## Open questions

- Review 専用機モード (`settings_appliance`), シーク terminology and dense accessibility disclosures.
- Long CJK text needs font-scale and TV layout checks.
