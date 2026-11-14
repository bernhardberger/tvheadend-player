# Turkish

## Register

Use polite **siz** in instructions, with neutral labels where direct address is
unnecessary. Approved by the Player primary on 2026-10-26 under the owner's
delegated plan approval. Do not switch to informal “sen”.

## Review status

0.5 remote key strings (`record_unavailable*`, `player_scrub_*`) drafted against `d7198998` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

Reconciled to frozen source `6d80a8ff` for 0.3.0 by GPT-6.1 Sol (medium), retaining
unchanged round-1 text from `daebdd86`. Full resources reviewed by Sonnet 5.5
(medium); no definite linguistic defects reported. The approved polite register
is retained. No human reviewer; remains labeled machine-drafted.

0.4.0 Home strings drafted against `d2d9a9ab` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (high); still machine-drafted, no human reviewer.

0.4.1 setup search strings drafted against `e59ff38a` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 connections strings (`server_*`) drafted against `996a93f9` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 "Edit connection" (`setup_connection`, formerly "Set up connection") drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page buttons "Cancel" and "Go to Home" (`cancel`, `server_go_home`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page texts "Back to Settings", "%1$s is still in use" and the unreachable explanation (`back_to_settings`, `server_still_in_use`, `server_switch_unreachable_body`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 confirmation and playback card strings (`server_switch_question`, `server_remove_*`, `*_confirm_body`, `delete_folder_*_confirm_question`, `recording_recorded_of`) drafted against `90078f81` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

## Typography

- Android folder: `values-tr`.
- Placeholder indices may be reordered for natural syntax; no suffixes are attached directly to unknown names.
- Product and protocol names and the Back, TV/GUIDE and OK labels are preserved.
- Clock example keeps `\u00A0` before PM; seek-back keeps U+2212; ellipses use U+2026; no bidi control characters added. The draft contains no quotation marks, so none are decided.

## Plurals

`one` and `other`. Turkish nouns stay singular after numerals, so numeric strings match across categories. Bulk failures use the total `%2$d` as the containing set and failed `%1$d` as the affected subset.

## Term decisions

- `nav_channels` Kanallar; `nav_epg` Rehber (compact; long form *program rehberi*); `nav_recordings` Kayıtlar; `context_watch` İzle.
- *Kayıt* names both a file and an operation; the verbs keep the distinction.
- `timeshift_setting` Zaman kaydırma; `timeshift_setting_description` Canlı TV yayınına ara ver ve geri sar; `details_retention` Saklama süresi; channel tag = kanal etiketi.
- Tuner = *ayarlayıcı*; mux = *çoklayıcı*.
- Cancel iptal et, Stop durdur (recorded part kept), Delete sil.
- `player_tuning` Kanal değiştiriliyor, not scanning.
- Register: neutral labels (Kaydet, Sil, Kapat) and polite plural imperatives (seçin, deneyin) in instructions.

0.5 automatic recording, timer, Keep until and expiry strings (`automatic_recording_*`, `autorec_editor_*`, `timer_editor_*`, `context_edit_*`, `context_skip_*`, `context_unskip_*`, `context_record_*`, `context_keep_until`, `recording_keep_until_*`, `recording_expir*`, `home_expiring_soon`, `recording_from_automatic`, `recording_state_and_origin`) drafted against `9e30bc99` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer. Reviewer corrections accepted for `autorec_editor_duplicate`, `autorec_editor_duplicate_default_help`, `autorec_editor_duplicate_state`, `autorec_editor_no_matches`, `autorec_editor_words_empty_summary`, `autorec_editor_discard`, `autorec_editor_off_after_save`, `autorec_editor_after_time`, `autorec_editor_before_time`.

0.5 Schedule tab strings for timers and the list of automatic recordings (`schedule_*`) drafted against `b257da70` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer. Wording also changed after the review for `schedule_automatic_turn_off`, `schedule_automatic_turn_on`.

0.5 leave-card label `autorec_editor_discard` (English now "Discard changes") checked against `dcad2205` by GPT-6.1 Sol (medium) and Sonnet 5.5 (medium); the existing wording already says what is lost and stays.

## Open questions

- Check TTS pronunciation of `player_scrub_*` speed multipliers.

- A reviewer may prefer keeping *mux* and *tuner* over *çoklayıcı* and *ayarlayıcı*.
- *Sürükleyici* (immersive layout) and *Ses geçişi* (audio passthrough) need UI and AV review.
- Very long connection-field labels may need a shorter term than the current one for port.
- The one-line `recording_recorded_of` status needs a capture check at 400 dp.
