# Household TV device targets

> **Last verified**: 2026-07-29

This repository has two assigned TCL televisions with different lifecycle
roles. Do not treat “Mum's TV” or “the household TV” as an unambiguous target.

| Role | Location | Device/product | Runtime baseline | Allowed use |
|---|---|---|---|---|
| **Current development target** | Mum's dining room | TCL C655 / `G10` / `G10_4K_GB` | V548, Android TV 12/API 31, 32-bit ARMv7 | Debug and release-candidate installs, bounded ADB diagnostics, synthetic remote keys, playback and appliance regression tests |
| **Temporary deployment target** | Owner-controlled | NVIDIA SHIELD Android TV / `darcy` | Android TV 11, ARM64 with ARMv7 compatibility | Debug and signed release-candidate installs, bounded ADB diagnostics, and remote-first product checks while the G10 handover is pending |
| **Production appliance** | Mum's bedroom | TCL Smart TV Pro / `G08` / `G08_4K_GB` | V655, Android TV 14/API 34, 32-bit ARMv7 | Production-signed updates and bounded read-only maintenance only |

## Required target behavior

- The dining-room G10 remains the household acceptance target. The NVIDIA Shield
  may be selected in ignored local configuration with role `test` as a temporary
  deployment target while the G10 handover is pending.
- The bedroom G08 has been handed over. Keep its local role `production`; do not
  use it for debug installs, key injection, smoke tests, signing experiments, or
  routine development.
- Before any mutating operation, run `./tools/device doctor` and confirm the
  configured serial, role, manufacturer/model, and device/product against the
  selected indexed target. Stop if any live identity property differs.
- Both sets report the generic model name `Smart TV Pro`, so the model string by
  itself is not sufficient human evidence. Confirm `G10` / `G10_4K_GB` for the
  development target and never substitute the G08.
- Keep private IP addresses, ADB serials, MAC addresses, credentials, and signing
  material out of tracked files. Named local profiles and their ADB serials
  belong only in ignored `.tvhplayer-device.json`. Keep `active_target` set to
  `g10` for routine debug work; use `--target` or
  `TVHPLAYER_DEVICE_TARGET` for an explicit one-command override.
- Device roles describe current lifecycle state, not permanent hardware
  capability. Update this document and the local role deliberately when the G10
  is handed over or a new development target is assigned.
- On the Shield's current Android TV 11 firmware, the standard
  `Settings.ACTION_ACCESSIBILITY_SETTINGS` intent resolves to Google's
  `frameworkpackagestubs` settings stub and displays that no app can perform the
  action. Manual navigation through Shield Settings → Device Preferences →
  Accessibility works. Keep this as a compatibility fix: detect the unusable
  resolver and provide a safe fallback without attempting to enable the service
  programmatically.
- With the Shield connected through the current Philips TV, the Philips remote's
  channel buttons arrive over HDMI-CEC as `<User Control Pressed>` Forward
  (`0x4B`) and Backward (`0x4C`), not the CEC Channel Up/Down commands (`0x30` and
  `0x31`). Android 11 maps those commands to `KEYCODE_MEDIA_NEXT` and
  `KEYCODE_MEDIA_PREVIOUS`. Live playback therefore accepts those Android media
  keys as channel-up/down compatibility aliases. This is evidence for the
  Philips/Shield CEC path, not a claim about every Philips television or remote.

TVHeadend credentials are immutable. Device testing may consume the existing
non-admin identity but must never rotate or change any TVHeadend account
credential.
