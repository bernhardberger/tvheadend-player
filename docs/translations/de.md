# German

## Register

Informal **du**, already in use in the app. Action labels are infinitives or nouns (Aufnehmen, Ansehen, Löschen).

## Typography

- Android folder: `values-de` (existing resources).
- German keeps ß; the de-CH override replaces it with ss.
- Dates and numbers come from the system locale, not the strings.

## Plurals

`one` and `other`.

## Term decisions

- `nav_channels` Sender: a TV service, not a frequency.
- `nav_epg` Programmführer; `nav_recordings` Aufnahmen; `record` Aufnehmen; `context_watch` Ansehen.
- `details_retention` Aufbewahrung; `recording_state_missed` Verpasst; `recording_state_completed` Abgeschlossen; `timeshift_live` Live.
- `cancel_recording` Aufnahme abbrechen (future), `stop_recording` Aufnahme beenden (active), `context_delete` Löschen.
- `player_tuning` Sender wird eingestellt: switching to a channel, not scanning.

## Review status

Existing German strings predate this workflow; they are kept and only changed for source changes or accepted corrections.

## Open questions

- None recorded.
