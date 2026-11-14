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
- `timeshift_setting` 时移; `timeshift_setting_description` 暂停和回退电视直播; live = 直播; behind live = 落后直播.
- Cancel 取消录制 (future), Stop 停止录制 (recorded part kept), Delete 删除录制内容.
- `player_tuning` 正在调谐; tuner = 调谐器; mux = 复用流, distinct from 频道.
- `details_retention` 保留期限; server = 服务器; profile = 配置.

## Review status

0.5 remote key strings (`record_unavailable*`, `player_scrub_*`) drafted against `d7198998` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

Reconciled to frozen source `6d80a8ff` for 0.3.0 by GPT-6.1 Sol (medium), retaining unchanged round-1 text from `daebdd86`. Full resources reviewed by Sonnet 5.5 (medium); mainland audio-description terminology and Behind live meaning corrected. No human reviewer; remains labeled machine-drafted.

0.4.0 Home strings drafted against `d2d9a9ab` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (high); still machine-drafted, no human reviewer.

0.4.1 setup search strings drafted against `e59ff38a` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 connections strings (`server_*`) drafted against `996a93f9` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 "Edit connection" (`setup_connection`, formerly "Set up connection") drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page buttons "Cancel" and "Go to Home" (`cancel`, `server_go_home`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page texts "Back to Settings", "%1$s is still in use" and the unreachable explanation (`back_to_settings`, `server_still_in_use`, `server_switch_unreachable_body`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 confirmation and playback card strings (`server_switch_question`, `server_remove_*`, `*_confirm_body`, `delete_folder_*_confirm_question`, `recording_recorded_of`) drafted against `90078f81` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 automatic recording, timer, Keep until and expiry strings (`automatic_recording_*`, `autorec_editor_*`, `timer_editor_*`, `context_edit_*`, `context_skip_*`, `context_unskip_*`, `context_record_*`, `context_keep_until`, `recording_keep_until_*`, `recording_expir*`, `home_expiring_soon`, `recording_from_automatic`, `recording_state_and_origin`) drafted against `9e30bc99` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer. Reviewer corrections accepted for `recording_keep_until_heading`, `autorec_editor_upcoming`, `autorec_editor_duplicate_episode_help`, `automatic_recording_episodes_not_applied_create`, `automatic_recording_episodes_not_applied_change`, `autorec_editor_duplicate_state`, `autorec_editor_words`, `autorec_editor_enter_words`, `recording_expiry_hours_short`, `recording_expiry_minutes_short`.

0.5 Schedule tab strings for timers and the list of automatic recordings (`schedule_*`) drafted against `b257da70` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

## Open questions

- Check TTS pronunciation of 倍速 in `player_scrub_*`.

- Review `profile` 流配置 against the more descriptive 串流配置.
- Confirm 隧道播放 (`tunneling_setting`) and 电视模式 as product terms.
