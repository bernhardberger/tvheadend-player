# Austrian German

## Register

Inherits German informal **du**. No separate register decisions.

## Typography

- No `values-de-rAT` folder; the language falls back to `values-de`.
- Austrian month names such as Jänner and date/number formats come from locale data, not string overrides.

## Plurals

Inherits German (`one`, `other`).

## Term decisions

- No term differs from German in the supplied UI strings, so none is overridden.
- Do not copy the base file to fill the folder.

## Review status

Assessed against frozen source `6d80a8ff` for 0.3.0. No overrides needed; `de-AT` inherits reviewed `de` and its false machine-translated flag. The regional assessment originated with GPT-6.1 Sol (medium) and was checked by Sonnet 5.5 (medium). No Austrian human reviewer yet; this is not regional native-speaker acceptance.

## Open questions

- A native Austrian review could find a genuinely regional term; add an override only then.
