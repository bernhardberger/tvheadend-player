# French

## Register

Informal **tu** in directions; infinitives on action labels.

## Typography

- Android folder: `values-fr`.
- Typographic apostrophe U+2019, so no unescaped ASCII apostrophe in Android text.
- Ordinary spaces before colons and question marks; the typography still needs review during integration.
- Clock example keeps `\u00A0` before PM; seek-back keeps U+2212; ellipses use U+2026; no bidi control characters added. The draft contains no quotation marks, so none are decided.

## Plurals

`one`, `many`, `other`. `one` covers 0 and 1; `many` is only for million multiples and uses plural wording.

## Term decisions

- `nav_channels` Chaînes (TV); audio channels stay *canaux* (`track_channels_count`).
- `details_broadcast_new` Inédit, repeat Rediffusion.
- `details_retention` Conservation; `recording_state_missed` Manqué, distinct from Échec and Annulé.
- Timeshift as a concept = *lecture différée*; `timeshift_setting` Lecture différée; `timeshift_setting_description` Mettre en pause et rembobiner la TV en direct; `timeshift_live` Direct; `player_tuning` Changement de chaîne; mux = multiplex.
- Cancel annuler, Stop arrêter, Delete supprimer; each confirmation keeps its consequence.
- Appliance = *Mode téléviseur*, not a literal household-appliance word.
- `recording_length_recorded` "Durée enregistrée : %1$s" avoids agreement with a formatted duration.

## Review status

Reconciled to frozen source `6d80a8ff` for 0.3.0 by GPT-6.1 Sol (medium), retaining unchanged round-1 text from `daebdd86`. Full resources reviewed by Sonnet 5.5 (medium); video-buffer agreement corrected. The keycap-localization suggestion was rejected because literal remote key names are preserved. No human reviewer; remains labeled machine-drafted.

## Open questions

- *Mode téléviseur* and the retained English key labels in `player_channel_card_hint` need review.
- `guide_time_prime_time` (24 characters) exceeds the old soft limit; fit unchecked.
