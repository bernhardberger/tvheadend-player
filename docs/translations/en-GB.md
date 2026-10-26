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

Machine-drafted by GPT-6.1 Sol (medium) in round 1 from source commit daebdd86; Sonnet 5.5 review pending; no human reviewer yet. The override file holds only the differing strings.

## Open questions

- The plan changes the English source spelling to US; once it does, en-GB needs an override for that string.
