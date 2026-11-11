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

0.4.0 Home strings drafted against `d2d9a9ab` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (high); still machine-drafted, no human reviewer.

0.4.1 setup search strings drafted against `e59ff38a` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 connections strings (`server_*`) drafted against `996a93f9` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 "Edit connection" (`setup_connection`, formerly "Set up connection") drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page buttons "Cancel" and "Go to Home" (`cancel`, `server_go_home`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page texts "Back to Settings", "%1$s is still in use" and the unreachable explanation (`back_to_settings`, `server_still_in_use`, `server_switch_unreachable_body`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

## Open questions

- Confirm whether the slate wants Spain-specific vocabulary (vídeo, mando, ajustes) or a more Latin-American one.
- *Horario estelar* (`guide_time_prime_time`) and *Modo televisor* need review.
