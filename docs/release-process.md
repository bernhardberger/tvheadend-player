# Release and rollback process

One authorized maintainer may prepare, verify, sign, publish and check a release
in one task. Routine use of this path needs no extra model review or separate
administrative packages. Retain exact operation approval, native and source
checks, signing checks, device identity checks and attended acceptance below.

Release preparation and signing run on separate hosts. A build host produces a
reproducible unsigned bundle; an isolated signing host holds the private key and
signs it. The private key and its passwords never reach the build host, Git,
release metadata or command arguments. Publication and production-device
mutation need explicit owner approval.

Host names, container numbers, device inventory and rollback clients are specific
to one installation. The maintainer keeps them in the ignored
`.tvhplayer-release-operator.md` at the repository root; this document uses the
roles "build host" and "signing host".

- [ ] Reminder, not a gate: regenerate the [baseline profile](profiling.md#baseline-profile-generation) after a UI batch changes screens or navigation.

## Builds

Each release produces two builds from one commit: the Play build
`at.bernhardberger.tvhplayer` and the Appliance build
`at.bernhardberger.tvhplayer.appliance`. Only the Appliance build carries the HOME
filter, the GUIDE accessibility service and the Appliance settings. Both APKs are
attached to every GitHub release. The bundle also includes a Play AAB for Google
Play. Play App Signing must use this project's own release key ("use your own
key" at enrollment), so a GitHub APK and a Play install update each other.

## Version policy

`versionName` follows semantic versioning and `versionCode` increases with every
build that is distributed or installed on a device; both are set in
`app/build.gradle.kts`. Never reuse a version for different source or native
binaries. [`CHANGELOG.md`](../CHANGELOG.md) records what each release contains.

Android does not normally accept a lower version as an update. A rollback
therefore uses the same stable key and a new, higher `versionCode`, built from the
last-known-good source commit.

## Release key

The canonical key alias is `tvhplayer-release`. Its public certificate SHA-256
fingerprint is:

`1E:18:48:62:F5:BB:A2:D1:C8:40:6D:6A:7A:79:65:7F:F3:A7:3D:25:8C:1E:1B:75:FA:25:02:58:75:E5:AB:C9`

Keep the keystore and its backup in separate, owner-controlled secure storage.
Losing the key prevents future in-place updates; exposing it compromises every
installation signed with it.

## SDK and library pins

A release resolves byte-pinned SDK artifacts from the public repositories. The
SDK version appears in these places, and a bump changes all of them together:

1. `gradle/libs.versions.toml` (`tvheadend-sdk`).
2. The HTSP version check in the dependency-proof task in
   `app/build.gradle.kts`.
3. `SDK_VERSION`, the AAR hash and the source-classifier hashes in
   `tools/check-native-libs`.
4. `SDK_VERSION` and `SDK_SOURCE_COMMIT` in `tools/prepare-release`.
5. The pinned values asserted in `tools/tests/test_release_metadata.py`.

Run `./tools/verify` afterwards; it fails when one of them lags.

### SDK development builds

`main` may pin an SDK development build from Maven Central's snapshot
repository: a timestamped version such as `0.36.0-20261024.143000-1` instead of
a plain `X.Y.Z`. A floating `X.Y.Z-SNAPSHOT` is rejected, because its hashes
change with every publication. Change the catalog and item 3 as for a release,
using the hashes the SDK session gives for that publication, and leave items 4
and 5 at the last released SDK. `./tools/verify` then passes,
but `tools/prepare-release` and `tools/check-native-libs --release` refuse the
pin: a Player release needs a released SDK. Central deletes snapshots after 90
days, so an old `main` commit that pins one may stop building. That is accepted;
release commits always pin a released SDK.

## Unsigned preparation on the build host

### Guided workflow

Create an owner-only local orchestrator configuration once, starting from the
committed example, and replace the example values with your signing host's:

```bash
cp .tvhplayer-release.example.json .tvhplayer-release.json
chmod 600 .tvhplayer-release.json
```

The configuration names the SSH alias of the signing host, the trusted signing
checkout with its Git remote and branch, the keystore path and the release
staging root. The keystore and staging paths stay outside that checkout. The file
holds paths and host routing only, never passwords or private key data.

After pushing the intended clean commit to its configured upstream, the guided
commands are:

```bash
./tools/release prepare
./tools/release sign
./tools/release verify-signed build/release/signed/<version>
```

`prepare` resolves the byte-pinned SDK artifacts and source classifiers from the
public repositories, verifies them, and creates the unsigned bundle. `sign` checks
that local `HEAD` is pushed, verifies the bundle, transfers it over SSH, fetches
the configured trusted branch on the signing host, checks out the exact source
commit, and signs non-interactively using owner-only password files configured in
the signing host's SSH environment. It retrieves the signed bundle and
independently verifies checksums, source continuity, all three artifact
identities, native alignment, and the pinned certificate. It never installs to a
TV or publishes a release. It never reads or builds a sibling SDK checkout.

The lower-level commands below remain available for diagnosis and manual use.

Commit the intended release source, confirm the worktree is clean, and ensure
the pinned public dependencies are available through Google Maven and Maven
Central. Then run:

```bash
./tools/prepare-release
```

The command runs the strict native gate and full verifier, performs a clean
unsigned release build, proves both APKs have no signature, zip-aligns them, and
creates `build/release/unsigned/<version>/` containing:

- `tvheadend-player-<version>-play-unsigned.apk`;
- `tvheadend-player-<version>-appliance-unsigned.apk`;
- `tvheadend-player-<version>-play-unsigned.aab`;
- `tvheadend-player-<version>-play-mapping.txt` and `-appliance-mapping.txt`, the R8
  mapping for each flavor (the Play APK and AAB share one), needed to retrace crash
  reports;
- `.tar.gz` and `.zip` application corresponding-source archives;
- exact released SDK source classifiers and the app-owned Media3/FFmpeg
  corresponding-source archive (see `decoder-ffmpeg/README.md`);
- `release-manifest.json` with per-artifact application ID/flavor, version, source commit, and hashes;
- `SHA256SUMS` covering the complete unsigned bundle.

Transfer that directory to the signing host over an authenticated channel. Verify the
transferred directory and expected source commit before signing.

The build host, the signing host and the install host require bundletool **1.18.3**, either
`bundletool-all-1.18.3.jar` under `ANDROID_HOME` or version `1.18.3` in the
Gradle Maven cache with all runtime dependencies. Local bundletool must confirm the AAB's
Play identity and uncompressed, 16 KB aligned native-library configuration for
generated APKs. The native bytes must match the Play APK, with both ARM ABIs and
16 KB ELF LOAD alignment on 64-bit libraries. The AAB itself is a compressed
upload container, not an APK to zip-align. Missing bundletool fails closed.

## Isolated signing on the signing host

Run the signing tool from a trusted, reviewed checkout on the signing host, not
from files inside the incoming bundle. Identify the incoming bundle and protected
keystore:

```bash
./tools/sign-release /path/to/incoming/<version> /secure/path/release.jks
```

The tool uses `umask 077` and verifies incoming checksums, manifest identity,
version, unsigned state, and 16 KB alignment. On the signing host,
`TVHPLAYER_SIGNING_STORE_PASS_FILE` and `TVHPLAYER_SIGNING_KEY_PASS_FILE` may
name non-empty owner-only files owned by the signing user; otherwise the tool
falls back to interactive prompts. Both APKs use `apksigner`; the AAB uses
`jarsigner` with the same keystore and alias. Passwords use only file references
(`file:` for apksigner, `-storepass:file` / `-keypass:file` for jarsigner), never
password values in arguments. All three signatures must match the pinned
certificate. AAB verification uses strict jarsigner verification and keytool's
certificate fingerprint; the pinned self-signed chain is allowed, unsigned
entries are not.

The signed output contains:

- the signed `-play.apk`, `-appliance.apk`, and `-play.aab` artifacts;
- both R8 mapping files, unchanged from the unsigned bundle;
- both application corresponding-source formats;
- exact released SDK source classifiers and the app-owned Media3/FFmpeg
  corresponding-source archive;
- the original unsigned manifest;
- a signed `release-manifest.json` with source commit and certificate identity;
- `SHA256SUMS`, `APKSIGNER-VERIFY.txt` (both APKs), and `JARSIGNER-VERIFY.txt`.

Publish that set together; the mapping files are published with it (they reveal
nothing beyond the corresponding source). Release builds are R8-minified, so before
publishing smoke both a debug-key-signed `playRelease` and `applianceRelease` on the
a test TV; for Appliance include HOME launch, the accessibility service and Settings →
Appliance. Review it for private data before upload. The signing
fingerprint is public verification material; the private key and passwords are
never release artifacts.

## Publish

After the signed bundle passes `verify-signed` and device acceptance, publish it
as a GitHub release. The release title is the bare version, for example `0.3.0`,
and the tag is `v<version>`. Pass the repository explicitly, because the default
repository can resolve to the upstream project on some machines.

1. Copy the section for this version out of `CHANGELOG.md` into a notes file.
2. Create the release from the exact source commit named in the signed manifest
   and upload the signed set:

   ```bash
   gh release create "v<version>" \
     -R bernhardberger/tvheadend-player \
     --target <sourceCommit> \
     --title "<version>" \
     --notes-file <notes-file> \
     <signed-bundle>/*-play.apk \
     <signed-bundle>/*-appliance.apk \
     <the remaining files of the signed bundle, except the AAB>
   ```

   Attach both APKs, the application source archives, the SDK source classifiers,
   the Media3/FFmpeg corresponding-source archive, both R8 mapping files, the
   manifest, `SHA256SUMS` and the verification logs. The AAB is the Google Play
   upload artifact and is not attached.
3. Download one APK from the release page and compare its SHA-256 with
   `SHA256SUMS`.

Do not publish before the owner approves; a published release and its assets can
be cached or mirrored even if they are later deleted.

## Test deployment

Read `docs/device-targets.md` and load the `android-tv-device-testing` workflow.
Use `./tools/device --target <test-target> package-info` and `package-certificate` before
staging to compare installed version and verified APK certificate. The latter
temporarily retrieves only the installed base APK, never app-private data.
Confirm the intended serial, role and all four live identity properties before
mutation; `doctor` is optional, not an extra mandatory preflight. Ordinary debug
install/capture uses official CLI with explicit `--device` under
`android-tooling.md`. Proceed only on an explicitly indexed test target. A
release-signed APK cannot update a debug-signed installation; uninstalling the debug package erases
its app-private settings. Stop on a signature mismatch; never automatically
uninstall or clear data. After a separately approved signer migration, reprovision
the designated test credentials only
through the approved debug staging flow or enter them normally in the release
app.

Validate initial install and then an update signed by the same key with a higher
`versionCode`. Repeat progressive and interlaced playback, representative
AC-3/E-AC-3/MP3 platform-first fallback,
D-pad and physical key behavior, HOME/GUIDE, standby/wake, reboot, and Back/Stop
checks. Record only package, version, source commit, APK checksum, and signing
fingerprint. Do not record addresses, credentials, or unrestricted logs.

## Production deployment

Production installation requires explicit owner approval. Select a profile in
the ignored `.tvhplayer-device.json` whose role is `production` and whose exact
expected manufacturer, model, device, and product identify the intended TV,
then run `doctor` with that same target. Install only the complete signed bundle
through the bounded wrapper:

```bash
./tools/device --target <production-target> --package at.bernhardberger.tvhplayer.appliance install-release \
  --bundle build/release/signed/<version> \
  --confirm-release-install
```

The wrapper verifies all three artifacts, then selects the APK using the target's
configured package (overridden by `--package` or `TVHPLAYER_ANDROID_PACKAGE`).
The Appliance package selects the Appliance APK; it never installs the AAB.
Play installation refuses to replace an installed package with Appliance HOME
or GUIDE entry points. It verifies bundle checksums, source continuity, artifact
identity, alignment, and the pinned signing certificate before rechecking all
four live identity properties and invoking the Android package installer. It
does not launch the app, inject input, provision credentials, or remove other
installed clients. Record only the bounded package metadata printed after
installation.

Do not mutate a production TV until the exact signed candidate has passed
acceptance on a test target and the owner approves production deployment. Keep
the previous clients installed until post-deployment wake, reboot and Back/Stop
checks pass.

## Rollback

For a failed test candidate, with the target identity re-confirmed and no
credential screen visible, force-stop and uninstall only the product package.
Use raw ADB commands only for an explicitly approved rollback; routine device work
follows the CLI-first `android-tooling.md` workflow. The signed-bundle
installation above remains a specialized provenance gate, not an ordinary
install/capture mandate.

For an already accepted release, prepare a forward-versioned rollback APK from
the last-known-good source commit, resolve and verify the byte-pinned public SDK
artifacts and corresponding source, sign it with the same stable key, run the
complete release and test-target matrix, then install it as a normal update.
