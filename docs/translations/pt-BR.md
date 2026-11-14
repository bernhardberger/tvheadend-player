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
- Live = Ao vivo; delayed status = Com atraso; `timeshift_setting` Timeshift; `timeshift_setting_description` Pausar e retroceder a TV ao vivo.
- Cancel Cancelar gravação, Stop Parar gravação, Delete Excluir gravação.
- Channel tags = etiquetas, groups = grupos; mux = multiplex, not a channel.

## Review status

0.5 remote key strings (`record_unavailable*`, `player_scrub_*`) drafted against `d7198998` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

Reconciled to frozen source `6d80a8ff` for 0.3.0 by GPT-6.1 Sol (medium), retaining unchanged round-1 text from `daebdd86`. Full resources reviewed by Sonnet 5.5 (medium); stop-confirmation ambiguity removed while preserving the recorded-part retention promise. No human reviewer; remains labeled machine-drafted.

0.4.0 Home strings drafted against `d2d9a9ab` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (high); still machine-drafted, no human reviewer.

0.4.1 setup search strings drafted against `e59ff38a` by GPT-6.1 Sol and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 connections strings (`server_*`) drafted against `996a93f9` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 "Edit connection" (`setup_connection`, formerly "Set up connection") drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page buttons "Cancel" and "Go to Home" (`cancel`, `server_go_home`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.
0.5 connection switch page texts "Back to Settings", "%1$s is still in use" and the unreachable explanation (`back_to_settings`, `server_still_in_use`, `server_switch_unreachable_body`) drafted by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 confirmation and playback card strings (`server_switch_question`, `server_remove_*`, `*_confirm_body`, `delete_folder_*_confirm_question`, `recording_recorded_of`) drafted against `90078f81` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

0.5 automatic recording, timer, Keep until and expiry strings (`automatic_recording_*`, `autorec_editor_*`, `timer_editor_*`, `context_edit_*`, `context_skip_*`, `context_unskip_*`, `context_record_*`, `context_keep_until`, `recording_keep_until_*`, `recording_expir*`, `home_expiring_soon`, `recording_from_automatic`, `recording_state_and_origin`) drafted against `9e30bc99` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer. Reviewer corrections accepted for `automatic_recording_new_choose`.

0.5 Schedule tab strings for timers and the list of automatic recordings (`schedule_*`) drafted against `b257da70` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer. Reviewer corrections accepted for `schedule_upcoming`.

0.5 leave-card label `autorec_editor_discard` (English now "Discard changes") redrafted against `dcad2205` by GPT-6.1 Sol (medium) and reviewed by Sonnet 5.5 (medium); still machine-drafted, no human reviewer.

## Open questions

- Review `player_scrub_backward` retrocesso versus retroceder as a media-label preference; check spoken speed multipliers in `player_scrub_*`.

- Confirm *Modo TV* / *Serviço de entrada do modo TV* and `tunneling_setting` *Reprodução em túnel*.
- `timeshift_behind_live` "atrás do ao vivo" is deliberately short; check naturalness.
