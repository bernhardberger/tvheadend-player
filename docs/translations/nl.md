# Dutch

## Register

Informal **je**; infinitive action labels.

## Typography

- Android folder: `values-nl`.
- Clock example keeps `\u00A0` before PM; seek-back keeps U+2212; ellipses use U+2026; no bidi control characters added. The draft contains no quotation marks, so none are decided.

## Plurals

`one` and `other`. Bulk-failure nouns follow the total `%2$d`.

## Term decisions

- `nav_channels` Zenders; `nav_epg` Tv-gids; `nav_recordings` Opnamen; `context_watch` Kijken.
- Cancel annuleren (future), Stop stoppen (active, file kept), Delete verwijderen.
- Mux = multiplex, never *zender*. Timeshift stays the technical feature name; `timeshift_setting` Timeshift; `timeshift_setting_description` Live-tv pauzeren en terugspoelen; behind live = *achter live*.
- `player_tuning` Zender afstemmen: switching to a channel, not scanning.
- Appliance = *Tv-modus*; entry service = *tv-startservice*.
- `appliance_back_for_menu` keeps the literal Back label.

## Review status

0.5 remote key strings (`record_unavailable*`, `player_scrub_*`) drafted against `d7198998` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

Reconciled to frozen source `6d80a8ff` for 0.3.0 by GPT-6.1 Sol (medium), retaining unchanged round-1 text from `daebdd86`. Full resources reviewed by Sonnet 5.5 (medium); genre negation corrected from absence *Geen* to predicate *Niet*. No human reviewer; remains labeled machine-drafted.

0.4.0 Home strings drafted against `d2d9a9ab` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (high); still machine-drafted, no human reviewer.

0.4.1 setup search strings drafted against `e59ff38a` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 connections strings (`server_*`) drafted against `996a93f9` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 "Edit connection" (`setup_connection`, formerly "Set up connection") drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page buttons "Cancel" and "Go to Home" (`cancel`, `server_go_home`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page texts "Back to Settings", "%1$s is still in use" and the unreachable explanation (`back_to_settings`, `server_still_in_use`, `server_switch_unreachable_body`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 confirmation and playback card strings (`server_switch_question`, `server_remove_*`, `*_confirm_body`, `delete_folder_*_confirm_question`, `recording_recorded_of`) drafted against `90078f81` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 automatic recording, timer, Keep until and expiry strings (`automatic_recording_*`, `autorec_editor_*`, `timer_editor_*`, `context_edit_*`, `context_skip_*`, `context_unskip_*`, `context_record_*`, `context_keep_until`, `recording_keep_until_*`, `recording_expir*`, `home_expiring_soon`, `recording_from_automatic`, `recording_state_and_origin`) drafted against `9e30bc99` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer. Reviewer corrections accepted for `timer_editor_repeat`, `automatic_recording_action_failed_create`, `automatic_recording_action_failed_delete`, `automatic_recording_action_failed_change`, `autorec_editor_search_failed`, `timer_editor_title`, `timer_editor_weekends`, `automatic_recording_new_choose`.

## Open questions

- Check TTS pronunciation of `player_scrub_*` speed multipliers.

- Confirm *Tv-modus* and *tv-startservice*; `channels_layout_immersive` is *Beeldvullend*.
- Long accessibility and audio strings keep meaning over length; TV fit unchecked.
- `recording_keep_until_*` rows read *Tot over 1 week* under the heading; check the idiom.
