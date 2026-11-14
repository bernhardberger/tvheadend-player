# Hebrew

Status: deferred to a later release; not shipped in 0.3.0. The reviewed resources
remain in `values-iw`, but the build filters them out. RTL layout and physical-TV
acceptance remain open.

## Register

Gender-neutral: nominal actions and impersonal guidance (יש, אפשר); no masculine reader imperatives.

## Typography

- Android folder: `values-iw`, the legacy code Android's own resources use. When shipping is enabled, the language tag is `he`; the build maps it to `iw`.
- Right-to-left; no bidi controls and no placeholder reversal. `select_channel_hint` keeps the source "left".
- Clock example keeps `\u00A0`; ellipses use U+2026.

## Plurals

`one`, `two`, `other`, the agreed trial baseline; the `two` patterns use plural nouns with the numeral. Android/ICU category selection (`two`, historical `many`) differs by version and is unverified.

## Term decisions

- `nav_channels` ערוצים; `nav_epg` מדריך; `nav_recordings` הקלטות; `context_watch` צפייה.
- Live = שידור חי; `timeshift_setting` השהיית שידור חי; `timeshift_setting_description` השהיה והרצה לאחור של שידור חי; `player_tuning` מעבר ערוץ, not scanning.
- Cancel ביטול ההקלטה המתוזמנת, Stop עצירת ההקלטה (content kept), Delete מחיקת הקלטה.
- Mux = מקבץ שידור, distinct from channel.

## Review status

0.5 remote key strings (`record_unavailable*`, `player_scrub_*`) drafted against `d7198998` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

Reconciled to frozen source `6d80a8ff` for 0.3.0 by GPT-6.1 Sol (medium), retaining unchanged round-1 text from `daebdd86`. Full resources reviewed by Sonnet 5.5 (medium); singular button/action labels, category number and audio-channel counts corrected. No human reviewer; remains labeled machine-drafted. RTL layout acceptance remains open; see the round report.

0.4.0 Home strings drafted against `d2d9a9ab` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (high); still machine-drafted, no human reviewer.

0.4.1 setup search strings drafted against `e59ff38a` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 connections strings (`server_*`) drafted against `996a93f9` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 "Edit connection" (`setup_connection`, formerly "Set up connection") drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page buttons "Cancel" and "Go to Home" (`cancel`, `server_go_home`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page texts "Back to Settings", "%1$s is still in use" and the unreachable explanation (`back_to_settings`, `server_still_in_use`, `server_switch_unreachable_body`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 confirmation and playback card strings (`server_switch_question`, `server_remove_*`, `*_confirm_body`, `delete_folder_*_confirm_question`, `recording_recorded_of`) drafted against `90078f81` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 automatic recording, timer, Keep until and expiry strings (`automatic_recording_*`, `autorec_editor_*`, `timer_editor_*`, `context_edit_*`, `context_skip_*`, `context_unskip_*`, `context_record_*`, `context_keep_until`, `recording_keep_until_*`, `recording_expir*`, `home_expiring_soon`, `recording_from_automatic`, `recording_state_and_origin`) drafted against `9e30bc99` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer. Reviewer corrections accepted for `autorec_editor_search_in`, `timer_editor_ends`, `home_expiring_soon`, `autorec_editor_raw_help`, `autorec_editor_no_matches`, `autorec_editor_words_empty_summary`, `timer_editor_deleting`, `autorec_editor_new_unknown`, `autorec_editor_off_after_save`, `context_skip_series_support`, `context_skip_automatic_support`, `context_skip_timer_support`, `autorec_editor_turned_on`, `timer_editor_delete_scheduled`, `autorec_editor_delete_episodes`, `autorec_editor_delete_programs`, `timer_editor_never`, `autorec_editor_deleting`, `automatic_recording_new_keyword_help`, `autorec_editor_no_day`.

0.5 Schedule tab strings for timers and the list of automatic recordings (`schedule_*`) drafted against `b257da70` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer. Reviewer corrections accepted for `schedule_automatic_empty_help`.

0.5 leave-card label `autorec_editor_discard` (English now "Discard changes") checked against `dcad2205` by GPT-6.1 Sol (medium) and Sonnet 5.5 (medium); the existing wording already says what is lost and stays.

0.5 details action `context_record_title` shortened to "Record all" (English) against `e00c95bb`, reusing this file's `autorec_editor_duplicate_all` wording where it fits, reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

## Open questions

- Check remote-key RTL speed placement and spoken numeral pronunciation in `player_scrub_*`.

- Verify the plural categories on supported Android versions before integration.
- Mux vocabulary still needs native review. The timeshift setting description explicitly includes rewind.
- Mixed-direction rendering of `server_confirmation_anonymous_detail` and the duration strings is unverified.
- `recording_expires*` use a bare *תפוג*; check that it reads naturally before a date or a time span.
- `schedule_automatic_keep` puts the Keep until heading before its value, which reads awkwardly with the Forever value (*שמירה עד: לתמיד*); check for a label that fits every value.
