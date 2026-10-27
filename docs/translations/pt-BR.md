# Brazilian Portuguese

## Register

Neutral action labels; natural Brazilian imperatives in guidance, no formal address. Separately worded from pt-PT, not a spelling conversion.

## Typography

- Android folder: `values-pt-rBR`.
- Clock example keeps `\u00A0` before PM; seek-back keeps U+2212; ellipses use U+2026; no bidi control characters added. The draft contains no quotation marks, so none are decided.

## Plurals

`one`, `many`, `other`. `many` uses plural grammar.

## Term decisions

- Brazilian UI words: Assistir, Excluir, Salvar, Configurações, Conexão, Senha, Usuário, Grade.
- `details_broadcast_repeat` Reprise, `details_broadcast_new` Inédito (broadcast status, not playback repeat).
- `player_tuning` Sintonizando: switching to the chosen channel, not scanning.
- Live = Ao vivo; delayed status = Com atraso; *Timeshift* is the technical concept, while `timeshift_setting` is Pausar e retroceder a TV ao vivo.
- Cancel Cancelar gravação, Stop Parar gravação, Delete Excluir gravação.
- Channel tags = etiquetas, groups = grupos; mux = multiplex, not a channel.

## Review status

Reconciled to frozen source `6d80a8ff` for 0.3.0 by GPT-6.1 Sol (medium), retaining unchanged round-1 text from `daebdd86`. Full resources reviewed by Sonnet 5.5 (medium); stop-confirmation ambiguity removed while preserving the recorded-part retention promise. No human reviewer; remains labeled machine-drafted.

## Open questions

- Confirm *Modo TV* / *Serviço de entrada do modo TV* and `tunneling_setting` *Reprodução em túnel*.
- `timeshift_behind_live` "atrás do ao vivo" is deliberately short; check naturalness.
