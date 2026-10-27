# Spanish

## Register

Informal **tú** in directions; infinitives on action labels. Vocabulary leans toward Spain without a region qualifier.

## Typography

- Android folder: `values-es`.
- Clock example keeps `\u00A0` before PM; seek-back keeps U+2212; ellipses use U+2026; no bidi control characters added. The draft contains no quotation marks, so none are decided.

## Plurals

`one`, `many`, `other`. Integer 1 is singular; 0 and integers above 1 are plural; `many` covers million multiples.

## Term decisions

- `nav_channels` Canales; `nav_epg` Guía; `nav_recordings` Grabaciones; `context_watch` Ver.
- `recording_state_missed` No realizada, distinct from Fallida and Cancelada; `details_retention` Conservación.
- Timeshift as a concept = *reproducción en diferido*; `timeshift_setting` Reproducción en diferido; `timeshift_setting_description` Pausar y rebobinar la TV en directo; `timeshift_live` Directo; `player_tuning` Cambiando canal.
- Cancel cancelar, Stop detener, Delete eliminar.
- Appliance = *Modo televisor*.
- New broadcast Estreno, repeat Reposición; mux = múltiplex.

## Review status

Reconciled to frozen source `6d80a8ff` for 0.3.0 by GPT-6.1 Sol (medium), retaining unchanged round-1 text from `daebdd86`. Full resources reviewed by Sonnet 5.5 (medium). The keycap-localization suggestion was rejected because literal remote key names are preserved; style alternatives remain unchanged. No human reviewer; remains labeled machine-drafted.

## Open questions

- Confirm whether the slate wants Spain-specific vocabulary (vídeo, mando, ajustes) or a more Latin-American one.
- *Horario estelar* (`guide_time_prime_time`) and *Modo televisor* need review.
