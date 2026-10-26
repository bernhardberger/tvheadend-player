# European Portuguese

## Register

Informal singular guidance (Seleciona, Prime, Verifica, Tenta, teu). Status text uses *A gravar* / *A reproduzir*, not Brazilian gerunds. Regional sentence wording, not only spelling.

## Typography

- Android folder: `values-pt-rPT`.
- Clock example keeps `\u00A0` before PM; seek-back keeps U+2212; ellipses use U+2026; no bidi control characters added. The draft contains no quotation marks, so none are decided.

## Plurals

`one`, `many`, `other`. Bulk partial-delete grammar follows the total `%2$d`.

## Term decisions

- European UI words: Ver, Eliminar, Guardar, Definições, Ligação, Palavra-passe, Utilizador, Grelha, Ecrã, Comando, Ficheiro.
- `details_broadcast_repeat` Repetição, `details_broadcast_new` Estreia.
- `player_tuning` A sintonizar; live = Em direto; delayed = Em diferido.
- `details_retention` Conservação; Cancel Cancelar, Stop Parar, Delete Eliminar.
- Decoder = descodificador; frame rate = taxa de fotogramas; channel tags = etiquetas.

## Review status

Machine-drafted by GPT-6.1 Sol (medium) in round 1 from source commit daebdd86; Sonnet 5.5 review pending; no human reviewer yet. Ships labeled machine-drafted.

## Open questions

- Confirm *Modo TV* and `tunneling_setting` *Reprodução em túnel* as product terms.
- Check `details_started` *Iniciado* as a standalone time label.
