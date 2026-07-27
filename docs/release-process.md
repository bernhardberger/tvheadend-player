# Release and rollback process

This process separates reproducible release preparation on the engineering host
from signing on the isolated owner-controlled signing host. The private key and
passwords never enter LXC 106, Git, release metadata, or command arguments.
Publication and production-device mutation still require explicit owner approval.

## Version policy

`at.bernhardberger.tvhplayer` is a clean application ID. Version `0.1.0`
(`versionCode` 1) is its first candidate and does not update the predecessor or
the temporary `at.leoville.tvhstream` diagnostic package. Every subsequently
distributed or device-installed product build must increase `versionCode`.
`versionName` follows semantic versioning; do not reuse an APK version for
different source or native binaries.

Android will not normally accept a lower version as an update. A product
rollback therefore uses the same stable key and a new, higher `versionCode`
built from the last-known-good source commit. During initial household
acceptance, keep Headent and `at.leoville.tvhstream` installed as independent
immediate rollback clients.

## Host boundary

- LXC 106 builds and verifies an unsigned, zip-aligned APK from a clean commit.
  It also packages application source, native corresponding source, checksums,
  and a JSON manifest. It has no release key and Gradle has no signing setup.
- LXC 117 holds the stable product key. It verifies every incoming byte before
  prompting for passwords, signs the already aligned APK, and rejects any
  certificate other than the pinned product certificate.

The canonical key alias is `tvhplayer-release`. Its public certificate SHA-256
fingerprint is:

`1E:18:48:62:F5:BB:A2:D1:C8:40:6D:6A:7A:79:65:7F:F3:A7:3D:25:8C:1E:1B:75:FA:25:02:58:75:E5:AB:C9`

Keep the LXC 117 keystore and its backup in separate owner-controlled secure
storage. Losing the key prevents future in-place updates; exposing it compromises
every installation signed by it.

## Unsigned preparation on LXC 106

### Guided workflow

Create an owner-only local orchestrator configuration once:

```bash
cp .tvhplayer-release.example.json .tvhplayer-release.json
chmod 600 .tvhplayer-release.json
```

The homelab uses the `tvh-signing` SSH alias for LXC 117. The trusted signing
checkout has the configured Git remote and branch, while the keystore and release
staging paths remain outside that checkout. The configuration contains paths and
host routing only, never passwords or private key data.

After pushing the intended clean commit to its configured upstream, the guided
commands are:

```bash
./tools/release prepare
./tools/release sign
./tools/release verify-signed build/release/signed/0.1.0
```

`prepare` rebuilds the pinned native dependency and creates the unsigned bundle.
`sign` checks that local `HEAD` is pushed, verifies the bundle, transfers it over
SSH, fetches the configured trusted branch on LXC 117, checks out the exact source
commit, and signs non-interactively using owner-only password files configured
in the LXC 117 SSH environment. It retrieves the signed
bundle and independently verifies checksums, source continuity, APK identity,
alignment, and the pinned certificate. It never installs to a TV or publishes a
release. Use `prepare --reuse-native-source` only when the existing ignored
native-source archive was produced and reviewed in the current workspace.

The lower-level commands below remain available for diagnosis and manual use.

Install the exact Android and native toolchains listed in `app/libs/README.md`,
then build the decoder and its corresponding source:

```bash
./tools/build-media3-ffmpeg
```

Commit the intended release source and confirm the worktree is clean, then run:

```bash
./tools/prepare-release
```

The command runs the strict native gate and full verifier, performs a clean
unsigned release build, proves the APK has no signature, zip-aligns it, and
creates `build/release/unsigned/<version>/` containing:

- the unsigned APK;
- `.tar.gz` and `.zip` application corresponding-source archives;
- exact Media3/FFmpeg corresponding source and build patch;
- `release-manifest.json` with identity, version, source commit, and hashes;
- `SHA256SUMS` covering the complete unsigned bundle.

Transfer that directory to LXC 117 over an authenticated channel. Verify the
transferred directory and expected source commit before signing.

## Isolated signing on LXC 117

Run the signing tool from a trusted, reviewed checkout on LXC 117, not from files
inside the incoming bundle. Identify the incoming bundle and protected keystore:

```bash
./tools/sign-release /path/to/incoming/0.1.0 /secure/path/release.jks
```

The tool uses `umask 077` and verifies incoming checksums, manifest identity,
version, unsigned state, and 16 KB alignment. On the isolated signing host,
`TVHPLAYER_SIGNING_STORE_PASS_FILE` and `TVHPLAYER_SIGNING_KEY_PASS_FILE` may
name non-empty owner-only files owned by the signing user; otherwise the tool
falls back to interactive prompts. Passwords are passed to `apksigner` only by
`file:` reference and never as command arguments. The tool then verifies the
signature and requires the pinned certificate fingerprint.

The signed output contains:

- the signed APK;
- both application corresponding-source formats;
- exact Media3/FFmpeg corresponding source and build patch;
- the original unsigned manifest;
- a signed `release-manifest.json` with source commit and certificate identity;
- `SHA256SUMS` and the complete `apksigner` verification report.

Publish that set together. Review it for private data before upload. The signing
fingerprint is public verification material; the private key and passwords are
never release artifacts.

## Test deployment

Read `docs/device-targets.md` and load the `android-tv-device-testing` workflow.
Run `./tools/device doctor` before every mutation and proceed only when the live
identity is the dining-room G10 with local role `test`. A release-signed APK
cannot update a debug-signed installation; uninstalling the debug package erases
its app-private settings, so reprovision the designated test credentials only
through the approved debug staging flow or enter them normally in the release
app.

Validate initial install and then an update signed by the same key with a higher
`versionCode`. Repeat progressive and interlaced playback, MP1/MP2 fallback,
D-pad and physical key behavior, HOME/GUIDE, standby/wake, reboot, and Back/Stop
checks. Record only package, version, source commit, APK checksum, and signing
fingerprint. Do not record addresses, credentials, or unrestricted logs.

## Production deployment

Production installation requires explicit owner approval. Configure the ignored
`.tvhplayer-device.json` for role `production` with the exact expected
manufacturer, model, device, and product, then run `doctor`. Install only the
complete signed bundle through the bounded wrapper:

```bash
./tools/device install-release \
  --bundle build/release/signed/0.1.0 \
  --confirm-production-install
```

The wrapper independently verifies bundle checksums, source continuity, APK
identity, alignment, and the pinned signing certificate before rechecking all
four live identity properties and invoking the Android package installer. It
does not launch the app, inject input, provision credentials, or remove rollback
clients. Record only the bounded package metadata printed after installation.

Removing the legacy `at.leoville.tvhstream` rollback client is a separate,
explicitly approved production mutation. After confirming the replacement is
running, use `./tools/device uninstall-legacy --confirm-legacy-uninstall`. The
wrapper rechecks exact production identity, requires the replacement package to
be installed, removes only the fixed legacy package name, and verifies removal.

## Rollback

For a failed G10 candidate, with the test target identity re-confirmed and no
credential screen visible, remove only the product package and return to the
retained diagnostic client:

```bash
adb -s "$TVHPLAYER_ADB_SERIAL" shell am force-stop at.bernhardberger.tvhplayer
adb -s "$TVHPLAYER_ADB_SERIAL" uninstall at.bernhardberger.tvhplayer
adb -s "$TVHPLAYER_ADB_SERIAL" shell monkey -p at.leoville.tvhstream 1
```

Use those raw commands only for an explicitly approved rollback; routine device
work remains behind `tools/device`. Do not remove Headent, the diagnostic client,
Google Basic TV, or their data during validation.

For an already accepted product release, prepare a forward-versioned rollback
APK from the last-known-good source commit, rebuild the pinned native artifact,
sign with the same stable product key, run the complete release and G10 matrix,
then install it as a normal update.

Do not mutate the bedroom G08 until the exact signed candidate has passed G10
acceptance and the owner explicitly approves production deployment. Keep the
previous clients installed until post-deployment wake/reboot checks pass.
