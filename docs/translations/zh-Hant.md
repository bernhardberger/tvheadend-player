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
- Timeshift as a concept 時移; `timeshift_setting` 時移; `timeshift_setting_description` 暫停及倒轉電視直播; live 直播; `player_tuning` 正在調諧; tuner 調諧器; mux 多工串流.
- Cancel 取消錄影, Stop 停止錄影 (recorded part kept), Delete 刪除錄影.
- `details_retention` 保留期限.

## Review status

0.5 remote key strings (`record_unavailable*`, `player_scrub_*`) drafted against `d7198998` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

Reconciled to frozen source `6d80a8ff` for 0.3.0 by GPT-6.1 Sol (medium), retaining unchanged round-1 text from `daebdd86`. Full resources reviewed by Sonnet 5.5 (medium); Behind live meaning and service-entry terminology corrected. No human reviewer; remains labeled machine-drafted. A representative Settings capture checks Traditional Chinese rendering, not whole-language visual acceptance.

0.4.0 Home strings drafted against `d2d9a9ab` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (high); still machine-drafted, no human reviewer.

0.4.1 setup search strings drafted against `e59ff38a` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 connections strings (`server_*`) drafted against `996a93f9` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 "Edit connection" (`setup_connection`, formerly "Set up connection") drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page buttons "Cancel" and "Go to Home" (`cancel`, `server_go_home`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page texts "Back to Settings", "%1$s is still in use" and the unreachable explanation (`back_to_settings`, `server_still_in_use`, `server_switch_unreachable_body`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 confirmation and playback card strings (`server_switch_question`, `server_remove_*`, `*_confirm_body`, `delete_folder_*_confirm_question`, `recording_recorded_of`) drafted against `90078f81` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 automatic recording, timer, Keep until and expiry strings (`automatic_recording_*`, `autorec_editor_*`, `timer_editor_*`, `context_edit_*`, `context_skip_*`, `context_unskip_*`, `context_record_*`, `context_keep_until`, `recording_keep_until_*`, `recording_expir*`, `home_expiring_soon`, `recording_from_automatic`, `recording_state_and_origin`) drafted against `9e30bc99` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer. Reviewer corrections accepted for `recording_keep_until_heading`, `autorec_editor_duplicate_episode_help`, `autorec_editor_duplicate_state`, `recording_expiry_hours_short`, `recording_expiry_minutes_short`.

## Open questions

- Check TTS pronunciation of 倍速 in `player_scrub_*`; `record_unavailable` now uses 錄影 consistently with `record`.

- Needs its own Traditional Chinese terminology review; no universal regional suitability is claimed.
- Check `recordings_archive` 典藏 vs 保存錄影, and 通道式播放 (`tunneling_setting`).
