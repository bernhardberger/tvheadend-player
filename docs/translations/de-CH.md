# Swiss Standard German

## Register

Inherits German informal **du**. Swiss Standard German spelling, not a dialect translation.

## Typography

- Android folder: `values-de-rCH`, partial by design; other strings fall back to `values-de`.
- Spelling only: ß becomes ss in the five affected keys.

## Plurals

Not overridden; inherits German (`one`, `other`).

## Term decisions

- Override keys: `appliance_accessibility_disclosure`, `appliance_accessibility_service_description`, `close`, `details_size`, `action_close`.
- Each override equals the existing German text with only ß replaced by ss; no terminology or register change.

## Review status

Reconciled to frozen source `6d80a8ff` for 0.3.0. GPT-6.1 Sol (medium) supplied the regional assessment; the five applicable German `ß` → `ss` overrides were checked against every frozen German resource and reviewed by Sonnet 5.5 (medium). Other strings inherit reviewed `de`. No human reviewer; the override locale is labeled machine-drafted.

## Open questions

- Re-derive the key list if German strings with ß change.
