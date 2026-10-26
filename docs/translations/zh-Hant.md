# Traditional Chinese

## Register

Neutral, Taiwan-oriented software terminology, no reader pronouns; polite 請 in recovery text. A separate terminology draft, not character conversion of zh-Hans.

## Typography

- Android folder: `values-b+zh+Hant`.
- Full-width sentence punctuation (17 full-width colons in the draft).
- Clock example keeps `\u00A0` before PM; seek-back keeps U+2212; ellipses use U+2026; no bidi control characters added. The draft contains no quotation marks, so none are decided.

## Plurals

`other` only.

## Term decisions

- `nav_epg` 節目表 (not the converted 節目指南); `nav_recordings` 錄影; `context_watch` 觀看.
- Server 伺服器; audio 音訊; cache 快取; profile 設定檔 (`profile` 串流設定檔); folder 資料夾; frame rate 影格率.
- Timeshift 時移; live 直播; `player_tuning` 正在調諧; tuner 調諧器; mux 多工串流.
- Cancel 取消錄影, Stop 停止錄影 (recorded part kept), Delete 刪除錄影.
- `details_retention` 保留期限.

## Review status

Machine-drafted by GPT-6.1 Sol (medium) in round 1 from source commit daebdd86; Sonnet 5.5 review pending; no human reviewer yet. Ships labeled machine-drafted.

## Open questions

- Needs its own Traditional Chinese terminology review; no universal regional suitability is claimed.
- Check `recordings_archive` 典藏 vs 保存錄影, and 通道式播放 (`tunneling_setting`).
