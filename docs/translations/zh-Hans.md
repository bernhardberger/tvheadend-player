# Simplified Chinese

## Register

Concise and neutral, no reader pronouns; polite 请 in recovery guidance.

## Typography

- Android folder: `values-b+zh+Hans`.
- Full-width sentence punctuation (the draft has 17 full-width colons); placeholders and separator symbols are kept.
- Clock example keeps `\u00A0` before PM; seek-back keeps U+2212; ellipses use U+2026; no bidi control characters added. The draft contains no quotation marks, so none are decided.

## Plurals

`other` only.

## Term decisions

- `nav_channels` 频道; `nav_epg` 节目指南; `nav_recordings` 录制; `context_watch` 观看.
- `timeshift_setting` 暂停和回退电视直播; live = 直播; behind live = 落后直播.
- Cancel 取消录制 (future), Stop 停止录制 (recorded part kept), Delete 删除录制内容.
- `player_tuning` 正在调谐; tuner = 调谐器; mux = 复用流, distinct from 频道.
- `details_retention` 保留期限; server = 服务器; profile = 配置.

## Review status

Reconciled to frozen source `6d80a8ff` for 0.3.0 by GPT-6.1 Sol (medium), retaining unchanged round-1 text from `daebdd86`. Full resources reviewed by Sonnet 5.5 (medium); mainland audio-description terminology and Behind live meaning corrected. No human reviewer; remains labeled machine-drafted.

## Open questions

- Review `profile` 流配置 against the more descriptive 串流配置.
- Confirm 隧道播放 (`tunneling_setting`) and 电视模式 as product terms.
