# Swiss Standard German

## Register

Inherits German informal **du**. Swiss Standard German spelling, not a dialect translation.

## Typography

- Android folder: `values-de-rCH`, partial by design; other strings fall back to `values-de`.
- Spelling only: ß becomes ss in the six affected keys.

## Plurals

Not overridden; inherits German (`one`, `other`).

## Term decisions

- Override keys: `player_info_close`, `appliance_accessibility_disclosure`, `appliance_accessibility_service_description`, `close`, `details_size`, `action_close`.
- Each override equals the existing German text with only ß replaced by ss; no terminology or register change.

## Review status

Machine-drafted by GPT-6.1 Sol (medium) in round 1 from source commit daebdd86; Sonnet 5.5 review pending; no human reviewer yet. The override file holds only the differing strings.

## Open questions

- Re-derive the key list if German strings with ß change.
