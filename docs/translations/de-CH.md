# Swiss Standard German

## Register

Inherits German informal **du**. Swiss Standard German spelling, not a dialect translation.

## Typography

- Android folder: `values-de-rCH`, partial by design; other strings fall back to `values-de`.
- Spelling only: ß becomes ss in the affected keys.

## Plurals

Not overridden; inherits German (`one`, `other`).

## Term decisions

- Spelling override keys: `appliance_accessibility_service_description`, `close`, `details_size`, `action_close`. The `appliance_accessibility_service_description` key lives in the Appliance companion (`appliance/src/main/res`); its screen-only disclosure now stays en-US. The Player also explicitly includes the reviewed `appliance_setup` translation, identical to German.
- Each override equals the existing German text with only ß replaced by ss; no terminology or register change.

## Review status

0.5 remote key strings (`record_unavailable*`, `player_scrub_*`) drafted against `d7198998` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer. All six keys inherit German unchanged; no Swiss overrides.

Reconciled to frozen source `6d80a8ff` for 0.3.0. GPT-6.1 Sol (medium) supplied the regional assessment; the five applicable German `ß` → `ss` overrides were checked against every frozen German resource and reviewed by Sonnet 5.5 (medium). Other strings inherit reviewed `de`. No human reviewer; the override locale is labeled machine-drafted.

## Open questions

- Check TTS pronunciation of the inherited `player_scrub_*` speed multipliers.

- Re-derive the key list if German strings with ß change.
