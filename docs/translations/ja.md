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

0.5 remote key strings (`record_unavailable*`, `player_scrub_*`) drafted against `d7198998` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

Reconciled to frozen source `6d80a8ff` for 0.3.0 by GPT-6.1 Sol (medium), retaining unchanged round-1 text from `daebdd86`. Full resources reviewed by Sonnet 5.5 (medium); spoken seek direction and legal-notice meaning corrected. No human reviewer; remains labeled machine-drafted.

0.4.0 Home strings drafted against `d2d9a9ab` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (high); still machine-drafted, no human reviewer.

0.4.1 setup search strings drafted against `e59ff38a` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 connections strings (`server_*`) drafted against `996a93f9` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 "Edit connection" (`setup_connection`, formerly "Set up connection") drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page buttons "Cancel" and "Go to Home" (`cancel`, `server_go_home`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page texts "Back to Settings", "%1$s is still in use" and the unreachable explanation (`back_to_settings`, `server_still_in_use`, `server_switch_unreachable_body`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 confirmation and playback card strings (`server_switch_question`, `server_remove_*`, `*_confirm_body`, `delete_folder_*_confirm_question`, `recording_recorded_of`) drafted against `90078f81` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 automatic recording, timer, Keep until and expiry strings (`automatic_recording_*`, `autorec_editor_*`, `timer_editor_*`, `context_edit_*`, `context_skip_*`, `context_unskip_*`, `context_record_*`, `context_keep_until`, `recording_keep_until_*`, `recording_expir*`, `home_expiring_soon`, `recording_from_automatic`, `recording_state_and_origin`) drafted against `9e30bc99` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer. Reviewer corrections accepted for `home_expiring_soon`, `automatic_recording_new_choose`, `autorec_editor_padding_before`, `autorec_editor_padding_after`, `timer_editor_delete_scheduled`, `autorec_editor_no_matches`, `autorec_editor_words_empty_summary`, `autorec_editor_more_preview`, `autorec_editor_duplicate_episode_help`, `autorec_editor_unchanged_options`. Wording also changed after the review for `timer_editor_no_padding`.

## Open questions

- Check the spoken banner and TTS pronunciation of 倍速 in `player_scrub_*`.

- Review 専用機モード (`settings_appliance`), シーク terminology and dense accessibility disclosures.
- Long CJK text needs font-scale and TV layout checks.
