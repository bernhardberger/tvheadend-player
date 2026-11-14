# British English

## Register

No change from en-US; spelling only.

## Typography

- Android folder: `values-en-rGB`, partial by design; other strings fall back to the English source.
- British spellings: programme, colour, cancelled. The draft overrides 29 keys including one plural.

## Plurals

`one` and `other`; unindexed `%d` preserved.

## Term decisions

- Programme for broadcast content, colour, cancelled, e.g. `recording_state_cancelled`, `recording_notice_canceled`, `recordings_cancelled`.
- `tunneling_setting`: the English source now says "Tunneled"; the override needs "Tunnelled playback (experimental)".

## Review status

0.5 automatic recording, timer, Keep until and expiry strings (`automatic_recording_*`, `autorec_editor_*`, `timer_editor_*`, `context_edit_*`, `context_skip_*`, `context_unskip_*`, `context_record_*`, `context_keep_until`, `recording_keep_until_*`, `recording_expir*`, `home_expiring_soon`, `recording_from_automatic`, `recording_state_and_origin`): the lines that say "program" get a "programme" override, generated from the English source at `9e30bc99`; every other key inherits English.

0.5 remote key strings (`record_unavailable*`, `player_scrub_*`) drafted against `d7198998` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer. Only the two programme-spelling overrides are supplied; the other four keys inherit English.

Reconciled to frozen source `6d80a8ff` for 0.3.0 by GPT-6.1 Sol (medium), retaining applicable round-1 overrides from `daebdd86`. Sonnet 5.5 (medium) reviewed the six differing strings and regional fallback. No human reviewer; the override locale is labeled machine-drafted.

## Open questions

- The plan changes the English source spelling to US; once it does, en-GB needs an override for that string.
