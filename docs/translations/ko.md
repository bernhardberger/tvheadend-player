# Korean

## Register

Neutral noun and action labels with conventional polite explanations (approved 2026-10-26).

## Typography

- Android folder: `values-ko`.
- Clock example keeps `\u00A0` before PM; seek-back keeps U+2212; ellipses use U+2026; no bidi control characters added. The draft contains no quotation marks, so none are decided.

## Plurals

`other` only; numeric placeholders are kept.

## Term decisions

- `nav_channels` 채널; `nav_epg` 편성표; `nav_recordings` 녹화; `context_watch` 시청.
- Archive = 보관함; `timeshift_setting` 타임시프트; live = 실시간.
- Cancel 녹화 예약 취소, Stop 녹화 중지 (recorded part kept), Delete 삭제.
- `player_tuning` 채널 전환 중, not scanning; mux = 멀티플렉스.
- Failed folder actions read 전체 `%2$d`개 중 `%1$d`개.

## Review status

Machine-drafted by GPT-6.1 Sol (medium) in round 1 from source commit daebdd86; Sonnet 5.5 review pending; no human reviewer yet. Ships labeled machine-drafted.

## Open questions

- Review 전용 기기 모드 (`settings_appliance`) and 몰입형 (`channels_layout_immersive`).
- Passthrough, tunneling and long accessibility text need length review; no font-fit check.
