# Test-device credential provisioning

This workflow exists only to make repeatable development setup possible on an
explicitly designated Android TV test device. It is appliance-specific tooling,
not a release feature or a general credential-import API.

## Policy

- The local `.tvhplayer-device.json` must set `role` to `test` and contain the
  exact expected manufacturer, model, device, and product. `tools/device`
  re-reads all four properties from the connected TV before provisioning.
- Production and unclassified devices are rejected before the local secret is
  read.
- Credential values must come from an ignored, owner-only local file. They must
  not appear in command arguments, environment variables, Git, committed config,
  console output, logs, screenshots, or generated reports.
- Only a debuggable APK supports import. No activity, receiver, service, or
  provider is added for provisioning, exported or otherwise.
- Synthetic keyboard input and focus-dependent UI automation are prohibited.

## Setup

Copy `.tvhplayer-device.example.json` to the ignored local device file. Change
the role only for a device explicitly assigned to testing, then fill in the live
identity reported by `doctor`:

```bash
cp .tvhplayer-device.example.json .tvhplayer-device.json
./tools/device doctor
```

Create the configured `.tvhplayer-credentials.json` locally with this shape:

```json
{
  "host": "<TVHeadend host>",
  "htsp_port": 9982,
  "username": "<TVHeadend username>",
  "password": "<TVHeadend password>",
  "auto_connect": true
}
```

Restrict the file and provision an installed debug build:

```bash
chmod 600 .tvhplayer-credentials.json
./tools/device install-debug
./tools/device provision-test-credentials
```

`TVHPLAYER_CREDENTIAL_FILE` may select a different owner-only local file by
path. It carries only the path; never put credential values in that variable.

## Data flow

1. `tools/device` rejects every role except `test`, verifies ADB readiness, and
   compares all four live identity properties with local expected values.
2. It reads and validates the local JSON without printing it, force-stops the
   app, and streams canonical JSON through subprocess stdin.
3. ADB `run-as` writes the payload with mode `0600` into the debug app's private
   files directory. The values are not present in host or device command lines.
4. The debug `Application` startup importer validates the bounded payload, saves
   the password through `SecurePasswordStore`, and saves server settings through
   `ServerSettingsStore`.
5. The importer deletes the plaintext staging file on success or failure and
   writes only `ok` or `failed`. The wrapper suppresses sensitive subprocess
   output and removes that result marker.

The stored password remains encrypted with AES-GCM using the existing
non-exportable Android Keystore key. Existing app-wide backup and device-transfer
exclusions cover the encrypted password, settings, staging file, and result
marker. Release builds do not contain the importer.

## Cleanup

Delete the host-side secret when repeated provisioning is no longer needed:

```bash
rm .tvhplayer-credentials.json
```

To remove credentials from the TV, use **Clear saved password** in connection
settings, clear the app's Android data, or uninstall the debug app. Clearing app
data or uninstalling also removes the app's Keystore key. Do not inspect or
export app-private files to confirm cleanup.

## Threat model and limitations

- This protects against accidental disclosure through Git, normal command
  history, process argument lists, wrapper output, broad logs, screenshots, and
  reports. It also prevents an agent from targeting a device whose configured
  role or live identity does not match policy.
- The operator, local agent host, ADB client/server, USB or trusted-LAN ADB
  transport, Android shell, and designated test TV are trusted during setup. A
  compromised host, ADB endpoint, rooted TV, malicious same-UID process, or
  privileged device software can observe plaintext.
- The payload exists briefly as a mode-`0600` app-private plaintext file and in
  process memory. Deletion is best-effort and is not a forensic secure erase on
  flash storage.
- The local role is a policy control, not device attestation. Anyone who can
  rewrite ignored local configuration or the tool can defeat it; repository
  agents must not do so to target a production TV.
- An `ok` acknowledgment proves only that local storage calls completed. It does
  not prove TVHeadend accepted the credentials or that playback works. Validate
  connection and playback separately without exposing settings or logs.
- Direct HTSP remains unencrypted. Provisioning does not improve TVHeadend
  transport security; use it only on a trusted LAN or protected tunnel.
