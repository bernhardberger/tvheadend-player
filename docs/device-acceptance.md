# G10 debug device acceptance

This document defines the bounded application-acceptance harness used on the
named G10 test target. It does not authorize a device run by itself. A live
package ledger must separately authorize the device, server, and credential
operations.

## Preconditions

- Use only `./tools/device --target g10 accept-debug` after the ordinary
  `doctor`, debug install, and documented test-credential provisioning steps.
- The ignored `.tvhplayer-device.json` target must be named `g10`, have role
  `test`, and declare all four exact expected identity values: manufacturer
  `TCL`, model `Smart TV Pro`, device `G10`, and product `G10_4K_GB`.
- Configure two distinct positive scalar values,
  `acceptance_progressive_channel_id` and
  `acceptance_interlaced_channel_id`. Their labels record the operator's known
  server fixtures; the harness does not infer scan type from dimensions,
  decoder names, or frame rate.
- Run `./tools/verify` from a clean committed app tree first. It produces both
  `app-debug.apk`, the matching debug instrumentation APK, and an owner-only
  attestation binding clean HEAD, the released SDK coordinate, and both hashes.
- Do not use `--serial`, `--package`, or their environment equivalents. The
  acceptance command rejects identity overrides, unnamed targets, G08,
  production/unclassified roles, and any live or configured identity mismatch.

## Bounded checks

The debug-only instrumentation invokes these app-level methods independently:

1. `readMetadataProfilesAndArtwork`
2. `progressiveLivePlayback`
3. `interlacedLivePlayback`
4. `channelReplacement`
5. `timeshiftControls`
6. `reconnectAndTeardown`

Each invocation has a fixed timeout. Assertions report only typed acceptance
codes. Raw instrumentation stdout and stderr are discarded rather than copied
to evidence. The wrapper never requests broad logcat, UI hierarchy dumps,
app-data export, preferences, keystore data, server addresses, credentials, or
raw errors.

The selected channel labels and automated decoder state prove only that the
configured fixtures started through the app-owned runtime. They do not prove
scan type, deinterlacing, SurfaceView visibility, motion quality, audio quality,
remote feel, overscan, HDR, or readability. Those remain attended physical-TV
gates.

## Cleanup and evidence

Before installing instrumentation, the wrapper proves that the installed app
supports `run-as`; a non-debuggable app is rejected before cleanup or other
mutation. It validates the clean-HEAD attestation, reinstalls that exact attested
debug app with Android's data-retaining `-r` behavior, rechecks `run-as`, removes
any stale test package, and installs only the matching attested
instrumentation APK, then always force-stops the app and test package, uninstalls
the test package, and verifies that the test package is absent. It does not clear
the app, export app data, or remove provisioned app-private credentials.

The result is an ignored owner-only JSON file below
`captures/device/acceptance/<12-char-commit>/`. Its schema contains only the full
app commit, released SDK coordinate, debug APK SHA-256, public target name/role
and four-property identity, method names, typed outcomes, bounded durations,
cleanup result, and overall pass/fail. A failure prints no raw instrumentation
detail; inspect only the curated evidence and fix the named failing method before
another authorized run.
