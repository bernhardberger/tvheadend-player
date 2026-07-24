# Household TV device targets

> **Last verified**: 2026-07-24

This repository has two assigned TCL televisions with different lifecycle
roles. Do not treat “Mum's TV” or “the household TV” as an unambiguous target.

| Role | Location | Device/product | Runtime baseline | Allowed use |
|---|---|---|---|---|
| **Current development target** | Mum's dining room | TCL C655 / `G10` / `G10_4K_GB` | V548, Android TV 12/API 31, 32-bit ARMv7 | Debug and release-candidate installs, bounded ADB diagnostics, synthetic remote keys, playback and appliance regression tests |
| **Production appliance** | Mum's bedroom | TCL Smart TV Pro / `G08` / `G08_4K_GB` | V655, Android TV 14/API 34, 32-bit ARMv7 | Production-signed updates and bounded read-only maintenance only |

## Required target behavior

- The dining-room G10 is the default target for current development and device
  testing. Configure its ignored local device entry with role `test`.
- The bedroom G08 has been handed over. Keep its local role `production`; do not
  use it for debug installs, key injection, smoke tests, signing experiments, or
  routine development.
- Before any mutating operation, run `./tools/device doctor` and confirm the
  configured serial, role, manufacturer/model, and reported G10 device/product.
  Stop if the live identity does not match the intended dining-room target.
- Both sets report the generic model name `Smart TV Pro`, so the model string by
  itself is not sufficient human evidence. Confirm `G10` / `G10_4K_GB` for the
  development target and never substitute the G08.
- Keep private IP addresses, ADB serials, MAC addresses, credentials, and signing
  material out of tracked files. The active ADB serial belongs only in ignored
  `.tvhplayer-device.json` or `TVHPLAYER_ADB_SERIAL`.
- Device roles describe current lifecycle state, not permanent hardware
  capability. Update this document and the local role deliberately when the G10
  is handed over or a new development target is assigned.

TVHeadend credentials are immutable. Device testing may consume the existing
non-admin identity but must never rotate or change any TVHeadend account
credential.
