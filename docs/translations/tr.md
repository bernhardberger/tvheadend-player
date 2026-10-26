# Turkish

## Register

Use polite **siz** in instructions, with neutral labels where direct address is
unnecessary. Approved by the Player primary on 2026-10-26 under the owner's
delegated plan approval. Do not switch to informal “sen”.

## Review status

The existing Sol medium draft is machine-drafted and awaits Sonnet medium review.
Its provisional polite register is now approved; this does not approve individual
translations. Reconcile against the final source changes before linguistic review.

## Typography

- Android folder: `values-tr`.
- Placeholder indices may be reordered for natural syntax; no suffixes are attached directly to unknown names.
- Product and protocol names and the Back, TV/GUIDE and OK labels are preserved.
- Clock example keeps `\u00A0` before PM; seek-back keeps U+2212; ellipses use U+2026; no bidi control characters added. The draft contains no quotation marks, so none are decided.

## Plurals

`one` and `other`. Turkish nouns stay singular after numerals, so numeric strings match across categories. Bulk failures use the total `%2$d` as the containing set and failed `%1$d` as the affected subset.

## Term decisions

- `nav_channels` Kanallar; `nav_epg` Rehber (compact; long form *program rehberi*); `nav_recordings` Kayıtlar; `context_watch` İzle.
- *Kayıt* names both a file and an operation; the verbs keep the distinction.
- `timeshift_setting` Zaman kaydırma; `details_retention` Saklama süresi; channel tag = kanal etiketi.
- Tuner = *ayarlayıcı*; mux = *çoklayıcı*.
- Cancel iptal et, Stop durdur (recorded part kept), Delete sil.
- `player_tuning` Kanal değiştiriliyor, not scanning.
- Register: neutral labels (Kaydet, Sil, Kapat) and polite plural imperatives (seçin, deneyin) in instructions.

## Open questions

- A reviewer may prefer keeping *mux* and *tuner* over *çoklayıcı* and *ayarlayıcı*.
- *Sürükleyici* (immersive layout) and *Ses geçişi* (audio passthrough) need UI and AV review.
- Very long connection-field labels may need a shorter term than the current one for port.
