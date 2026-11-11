# European Portuguese

## Register

Informal singular guidance (Seleciona, Prime, Verifica, Tenta, teu). Status text uses *A gravar* / *A reproduzir*, not Brazilian gerunds. Regional sentence wording, not only spelling.

## Typography

- Android folder: `values-pt-rPT`.
- Clock example keeps `\u00A0` before PM; seek-back keeps U+2212; ellipses use U+2026; no bidi control characters added. The draft contains no quotation marks, so none are decided.

## Plurals

`one`, `many`, `other`. Bulk partial-delete grammar follows the total `%2$d`.

## Term decisions

- `timeshift_setting` Timeshift; `timeshift_setting_description` Pausar e recuar na TV em direto.

- European UI words: Ver, Eliminar, Guardar, Definições, Ligação, Palavra-passe, Utilizador, Grelha, Ecrã, Comando, Ficheiro.
- `details_broadcast_repeat` Repetição, `details_broadcast_new` Estreia.
- `player_tuning` A sintonizar; live = Em direto; delayed = Em diferido.
- `details_retention` Conservação; Cancel Cancelar, Stop Parar, Delete Eliminar.
- Decoder = descodificador; frame rate = taxa de fotogramas; channel tags = etiquetas.

## Review status

0.5 remote key strings (`record_unavailable*`, `player_scrub_*`) drafted against `d7198998` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

Reconciled to frozen source `6d80a8ff` for 0.3.0 by GPT-6.1 Sol (medium), retaining unchanged round-1 text from `daebdd86`. Full resources reviewed by Sonnet 5.5 (medium); stop-confirmation ambiguity removed while preserving the recorded-part retention promise. No human reviewer; remains labeled machine-drafted.

0.4.0 Home strings drafted against `d2d9a9ab` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (high); still machine-drafted, no human reviewer.

0.4.1 setup search strings drafted against `e59ff38a` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 connections strings (`server_*`) drafted against `996a93f9` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 "Edit connection" (`setup_connection`, formerly "Set up connection") drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page buttons "Cancel" and "Go to Home" (`cancel`, `server_go_home`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page texts "Back to Settings", "%1$s is still in use" and the unreachable explanation (`back_to_settings`, `server_still_in_use`, `server_switch_unreachable_body`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

## Open questions

- Review `player_scrub_backward` recuo versus retrocesso as a media-label preference; check spoken speed multipliers in `player_scrub_*`.

- Confirm *Modo TV* and `tunneling_setting` *Reprodução em túnel* as product terms.
- Check `details_started` *Iniciado* as a standalone time label.
