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
- Timeshift = *lecture différée*; `timeshift_live` Direct; `player_tuning` Changement de chaîne; mux = multiplex.
- Cancel annuler, Stop arrêter, Delete supprimer; each confirmation keeps its consequence.
- Appliance = *Mode téléviseur*, not a literal household-appliance word.
- `recording_length_recorded` "Durée enregistrée : %1$s" avoids agreement with a formatted duration.

## Review status

Machine-drafted by GPT-6.1 Sol (medium) in round 1 from source commit daebdd86; Sonnet 5.5 review pending; no human reviewer yet. Ships labeled machine-drafted.

## Open questions

- *Mode téléviseur* and the retained English key labels in `player_channel_card_hint` need review.
- `guide_time_prime_time` (24 characters) exceeds the old soft limit; fit unchecked.
