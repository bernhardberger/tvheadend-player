# Arabic

Status: deferred to a later release; not shipped in 0.3.0. The reviewed resources
remain in `values-ar`, but the build filters them out. RTL layout and physical-TV
acceptance remain open.

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
- `timeshift_setting` الإزاحة الزمنية; `timeshift_setting_description` إيقاف البث المباشر مؤقتًا وإرجاعه; live = مباشر; `player_tuning` جارٍ تبديل القناة, not scanning.
- Cancel إلغاء التسجيل المجدول, Stop إيقاف التسجيل (content kept), Delete حذف التسجيل.
- Mux = مُجمّع البث (`error_203_reason`), distinct from channel.
- Role headings are collective labels.

## Review status

0.5 remote key strings (`record_unavailable*`, `player_scrub_*`) drafted against `d7198998` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

Reconciled to frozen source `6d80a8ff` for 0.3.0 by GPT-6.1 Sol (medium), retaining unchanged round-1 text from `daebdd86`. Full resources reviewed by Sonnet 5.5 (medium); accepted findings applied. No human reviewer; remains labeled machine-drafted.

0.4.0 Home strings drafted against `d2d9a9ab` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (high); still machine-drafted, no human reviewer.

Review corrections: past-tense recorded dates, singular TV/GUIDE button, channel/category number, explicit audio-channel counts and compact seconds. RTL layout acceptance remains open; see the round report.

0.4.1 setup search strings drafted against `e59ff38a` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 connections strings (`server_*`) drafted against `996a93f9` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 "Edit connection" (`setup_connection`, formerly "Set up connection") drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page buttons "Cancel" and "Go to Home" (`cancel`, `server_go_home`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page texts "Back to Settings", "%1$s is still in use" and the unreachable explanation (`back_to_settings`, `server_still_in_use`, `server_switch_unreachable_body`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 confirmation and playback card strings (`server_switch_question`, `server_remove_*`, `*_confirm_body`, `delete_folder_*_confirm_question`, `recording_recorded_of`) drafted against `90078f81` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 automatic recording, timer, Keep until and expiry strings (`automatic_recording_*`, `autorec_editor_*`, `timer_editor_*`, `context_edit_*`, `context_skip_*`, `context_unskip_*`, `context_record_*`, `context_keep_until`, `recording_keep_until_*`, `recording_expir*`, `home_expiring_soon`, `recording_from_automatic`, `recording_state_and_origin`) drafted against `9e30bc99` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer. Reviewer corrections accepted for `recording_expiry_minutes`, `recording_expiry_hours`, `recording_expiry_days`, `autorec_editor_minutes`, `autorec_editor_maximum_choice`, `autorec_editor_reset_options`, `context_edit_title`, `autorec_editor_new_title`, `automatic_recording_kind_title`, `autorec_editor_delete_title`, `context_edit_keyword`, `autorec_editor_new_keyword`, `automatic_recording_kind_keyword`, `autorec_editor_delete_keyword`, `home_expiring_soon`, `autorec_editor_delete_unsaved`, `autorec_editor_maximum_help`, `recording_keep_until_forever`, `autorec_editor_programs_all`, `autorec_editor_programs_selected`, `autorec_editor_off_after_save`, `automatic_recording_episodes_not_applied_change`, `autorec_editor_days_after_recording`.

0.5 Schedule tab strings for timers and the list of automatic recordings (`schedule_*`) drafted against `b257da70` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer. Reviewer corrections accepted for `schedule_automatic_keep`, `schedule_automatic_new_help`, `schedule_automatic_upcoming`.

0.5 leave-card label `autorec_editor_discard` (English now "Discard changes") checked against `dcad2205` by GPT-6.1 Sol (medium) and Sonnet 5.5 (medium); the existing wording already says what is lost and stays.

## Open questions

- Review `player_scrub_backward` إرجاع as the media rewind term, RTL speed placement and TTS pronunciation of × in `player_scrub_*`.

- Regional broadcast terminology for multiplex and timeshift needs reviewer attention.
- Actual RTL layout, focus mirroring and digit presentation need captures and a TV check.
- Mixed-direction rendering of `server_confirmation_anonymous_detail` and the duration strings is unverified.
- `recording_expires*` use *ينتهي*, which also reads as "ends"; check for a term that says the recording is removed.
