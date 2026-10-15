from __future__ import annotations

import importlib.util
import json
import subprocess
import struct
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / "tools/release_metadata.py"
SPEC = importlib.util.spec_from_file_location("release_metadata", MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Cannot load {MODULE_PATH}")
release_metadata = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release_metadata)


class ReleaseMetadataTest(unittest.TestCase):
    def unsigned_manifest(self):
        return release_metadata.build_unsigned_manifest(
            application_id="at.bernhardberger.tvhplayer", version_code=1, version_name="0.1.0",
            source_commit="a" * 40, sdk_source_commit="b" * 40, artifacts=self._artifacts("unsigned"),
        )

    def test_binary_artifacts_require_exact_identity_flavor_and_file_type(self) -> None:
        for stage in ("unsigned", "signed"):
            for binary in ("PlayApk", "ApplianceApk", "PlayAab"):
                for field, wrong in (("applicationId", "wrong.package"), ("flavor", "wrong"), ("file", "wrong.zip")):
                    with self.subTest(stage=stage, binary=binary, field=field):
                        manifest = self.unsigned_manifest()
                        manifest["stage"] = stage
                        manifest["artifacts"] = self._artifacts(stage)
                        if stage == "signed":
                            manifest["signing"] = {"keyAlias": release_metadata.CANONICAL_KEY_ALIAS, "certificateSha256": release_metadata.CANONICAL_CERTIFICATE_SHA256}
                        def validate():
                            if stage == "unsigned":
                                release_metadata.validate_unsigned_manifest(manifest, application_id="at.bernhardberger.tvhplayer", version_code=1, version_name="0.1.0")
                            else:
                                release_metadata.validate_signed_manifest(manifest)
                        validate()
                        manifest["artifacts"][stage + binary][field] = wrong
                        message = {"applicationId": "application ID", "flavor": "flavor", "file": "extension"}[field]
                        with self.assertRaisesRegex(ValueError, f"^artifact {message} does not match {stage}{binary}$"):
                            validate()

    def test_manifest_requires_exact_three_binaries_and_no_duplicate_files(self) -> None:
        for binary in ("unsignedPlayApk", "unsignedApplianceApk", "unsignedPlayAab"):
            manifest = self.unsigned_manifest()
            del manifest["artifacts"][binary]
            with self.assertRaisesRegex(ValueError, "exactly"):
                release_metadata.validate_unsigned_manifest(manifest, application_id="at.bernhardberger.tvhplayer", version_code=1, version_name="0.1.0")
        for change, error in (("extra", "exactly"), ("duplicate", "duplicate")):
            manifest = self.unsigned_manifest()
            if change == "extra":
                manifest["artifacts"]["anotherApk"] = manifest["artifacts"]["unsignedPlayApk"]
            else:
                manifest["artifacts"]["unsignedApplianceApk"]["file"] = manifest["artifacts"]["unsignedPlayApk"]["file"]
            with self.assertRaisesRegex(ValueError, error):
                release_metadata.validate_unsigned_manifest(manifest, application_id="at.bernhardberger.tvhplayer", version_code=1, version_name="0.1.0")

    def test_both_stages_require_each_mapping_and_current_schema(self) -> None:
        for stage in ("unsigned", "signed"):
            for missing in ("playR8Mapping", "applianceR8Mapping", "oldSchema"):
                with self.subTest(stage=stage, missing=missing):
                    manifest = self.unsigned_manifest()
                    self.assertEqual(manifest["schemaVersion"], 4)
                    manifest["stage"] = stage
                    manifest["artifacts"] = self._artifacts(stage)
                    manifest["signing"] = {"keyAlias": release_metadata.CANONICAL_KEY_ALIAS, "certificateSha256": release_metadata.CANONICAL_CERTIFICATE_SHA256}
                    if missing == "oldSchema":
                        manifest["schemaVersion"] = 3
                    else:
                        del manifest["artifacts"][missing]
                    with self.assertRaisesRegex(ValueError, "supported|exactly"):
                        if stage == "unsigned":
                            release_metadata.validate_unsigned_manifest(manifest, application_id="at.bernhardberger.tvhplayer", version_code=1, version_name="0.1.0")
                        else:
                            release_metadata.validate_signed_manifest(manifest)

    def test_json_loader_rejects_duplicate_artifact_keys(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "manifest.json"
            path.write_text('{"artifacts":{"unsignedPlayApk":{},"unsignedPlayApk":{}}}')
            with self.assertRaisesRegex(ValueError, "duplicate manifest field"):
                release_metadata._load_manifest(path)

    def test_aab_signature_requires_verified_entries_and_pinned_certificate(self) -> None:
        for status, report, fingerprint, accepted in (
            (0, "jar verified.", release_metadata.CANONICAL_CERTIFICATE_SHA256, True),
            (4, "jar verified, with signer errors. The signer certificate is self-signed.", release_metadata.CANONICAL_CERTIFICATE_SHA256, True),
            (4, "jar verified, with signer errors.", release_metadata.CANONICAL_CERTIFICATE_SHA256, False),
            (4, "jar verified. self-signed. certificate has expired.", release_metadata.CANONICAL_CERTIFICATE_SHA256, False),
            (4, "jar verified. self-signed. certificate is not yet valid.", release_metadata.CANONICAL_CERTIFICATE_SHA256, False),
            (16, "jar verified.", release_metadata.CANONICAL_CERTIFICATE_SHA256, False),
            (20, "jar verified.", release_metadata.CANONICAL_CERTIFICATE_SHA256, False),
            (0, "jar is unsigned.", release_metadata.CANONICAL_CERTIFICATE_SHA256, False),
            (0, "jar verified.", "00" * 32, False),
            (0, "jar verified.", release_metadata.CANONICAL_CERTIFICATE_SHA256 + "\nSHA256: " + "00" * 32, False),
        ):
            with self.subTest(status=status, accepted=accepted):
                results = [subprocess.CompletedProcess([], status, stdout=report), subprocess.CompletedProcess([], 0, stdout=f"SHA256: {fingerprint}")]
                with patch.object(release_metadata.subprocess, "run", side_effect=results) as run:
                    if accepted:
                        release_metadata.verify_aab_signature(Path("play.aab"))
                    else:
                        with self.assertRaises(ValueError):
                            release_metadata.verify_aab_signature(Path("play.aab"))
                    self.assertIn("-strict", run.call_args_list[0].args[0])
                    self.assertIn("-certs", run.call_args_list[0].args[0])

    def test_apk_certificate_rejects_wrong_or_multiple_signers(self) -> None:
        pinned = release_metadata.CANONICAL_CERTIFICATE_SHA256.replace(":", "")
        report = f"Signer #1 certificate SHA-256 digest: {pinned}"
        self.assertEqual(release_metadata.verify_apk_certificate(report), release_metadata.CANONICAL_CERTIFICATE_SHA256)
        for invalid in ("", report.replace(pinned, "0" * 64), report + "\n" + report.replace("#1", "#2")):
            with self.assertRaises(ValueError):
                release_metadata.verify_apk_certificate(invalid)

    def test_aab_native_and_manifest_contract(self) -> None:
        for failure in (None, "payload", "alignment", "compression", "identity", "signed", "elf", "abi", "home", "service"):
            with self.subTest(failure=failure), tempfile.TemporaryDirectory() as directory:
                apk, aab = Path(directory) / "play.apk", Path(directory) / "play.aab"
                elf = bytearray(120)
                elf[:6] = b"\x7fELF\x02\x01"
                struct.pack_into("<Q", elf, 32, 64)
                struct.pack_into("<HH", elf, 54, 56, 1)
                struct.pack_into("<I", elf, 64, 1)
                struct.pack_into("<Q", elf, 112, 4096 if failure == "elf" else 16384)
                with zipfile.ZipFile(apk, "w") as z, zipfile.ZipFile(aab, "w") as b:
                    abis = ("arm64-v8a",) if failure == "abi" else ("arm64-v8a", "armeabi-v7a")
                    for abi in abis:
                        name = f"lib/{abi}/libfixture.so"
                        z.writestr(name, elf)
                        b.writestr("base/" + name, b"wrong" if failure == "payload" else elf)
                    if failure == "signed":
                        b.writestr("META-INF/RELEASE.SF", b"signature")
                config = {"optimizations": {"uncompressNativeLibraries": {"enabled": failure != "compression", "alignment": "PAGE_ALIGNMENT_4K" if failure == "alignment" else "PAGE_ALIGNMENT_16K"}}}
                package = "wrong.package" if failure == "identity" else "at.bernhardberger.tvhplayer"
                entry = '<activity><intent-filter><category android:name="android.intent.category.HOME"/></intent-filter></activity>' if failure == "home" else '<service android:name=".accessibility.ApplianceEntryAccessibilityService"/>' if failure == "service" else ''
                xml = f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{package}" android:versionCode="1" android:versionName="0.1.0"><application>{entry}</application></manifest>'
                with patch.object(release_metadata, "bundletool_command", return_value=["bundletool"]), patch.object(release_metadata.subprocess, "run", side_effect=[subprocess.CompletedProcess([], 0, stdout=json.dumps(config)), subprocess.CompletedProcess([], 0, stdout=xml)]):
                    if failure is None:
                        release_metadata.validate_aab(aab, apk, 1, "0.1.0", unsigned=True)
                    else:
                        with self.assertRaises(ValueError):
                            release_metadata.validate_aab(aab, apk, 1, "0.1.0", unsigned=True)

    def test_unsigned_apk_requires_absence_of_signing_material_and_valid_zip(self):
        for kind in ("unsigned", "SF", "RSA", "DSA", "EC", "block", "garbage", "corrupt"):
            with self.subTest(kind=kind), tempfile.TemporaryDirectory() as directory:
                path = Path(directory) / "fixture.apk"
                with zipfile.ZipFile(path, "w") as archive:
                    archive.writestr("AndroidManifest.xml", b"fixture payload")
                    if kind in ("SF", "RSA", "DSA", "EC"):
                        archive.writestr(f"META-INF/RELEASE.{kind}", b"signature fixture")
                data = bytearray(path.read_bytes())
                if kind == "block":
                    end = data.rfind(b"PK\x05\x06")
                    central = struct.unpack_from("<I", data, end + 16)[0]
                    block = struct.pack("<Q", 24) * 2 + b"APK Sig Block 42"
                    data[central:central] = block
                    struct.pack_into("<I", data, end + len(block) + 16, central + len(block))
                elif kind == "garbage":
                    data = b"not a ZIP"
                elif kind == "corrupt":
                    data[data.index(b"fixture payload")] ^= 1
                path.write_bytes(data)
                if kind == "unsigned":
                    release_metadata.assert_unsigned_apk(path)
                else:
                    expected = "APK Signing Block" if kind == "block" else "malformed APK ZIP" if kind in ("garbage", "corrupt") else "v1 signing material"
                    with self.assertRaisesRegex(ValueError, expected):
                        release_metadata.assert_unsigned_apk(path)

    def test_bundletool_requires_pinned_version_and_all_dependencies(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch.dict(release_metadata.os.environ, {"ANDROID_HOME": str(root / "sdk"), "GRADLE_USER_HOME": str(root / "gradle")}):
                base = root / "gradle/caches/modules-2/files-2.1/com.android.tools.build/bundletool"
                wrong = base / "99.0/hash/bundletool-99.0.jar"
                wrong.parent.mkdir(parents=True)
                wrong.touch()
                with self.assertRaisesRegex(ValueError, "1.18.3 requires local dependency com.android.tools.build:bundletool"):
                    release_metadata.bundletool_command()
                pinned = base / "1.18.3/hash/bundletool-1.18.3.jar"
                pinned.parent.mkdir(parents=True)
                pinned.touch()
                with self.assertRaisesRegex(ValueError, "requires local dependency com.android.tools.build:aapt2-proto"):
                    release_metadata.bundletool_command()
                standalone = root / "sdk/bundletool-all-1.18.3.jar"
                standalone.parent.mkdir()
                standalone.touch()
                self.assertEqual(release_metadata.bundletool_command(), ["java", "-jar", str(standalone)])

    def test_apk_entry_point_split_for_each_flavor(self):
        for flavor in ("play", "appliance"):
            for home, service in ((False, False), (True, False), (False, True), (True, True)):
                with self.subTest(flavor=flavor, home=home, service=service):
                    tree = "  E: manifest\n    E: application\n"
                    if home:
                        tree += '      E: activity\n        E: intent-filter\n          E: category\n            A: android:name(0x01010003)="android.intent.category.HOME"\n'
                    if service:
                        tree += '      E: service\n        A: android:name(0x01010003)=".accessibility.ApplianceEntryAccessibilityService"\n'
                    with patch.object(release_metadata.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, stdout=tree)):
                        if (home, service) == ((True, True) if flavor == "appliance" else (False, False)):
                            release_metadata.validate_apk_entry_points(Path("fixture.apk"), flavor, Path("aapt"))
                        else:
                            with self.assertRaisesRegex(ValueError, f"{flavor} entry-point split is invalid"):
                                release_metadata.validate_apk_entry_points(Path("fixture.apk"), flavor, Path("aapt"))

    def test_apk_manifest_tool_failure_is_not_an_absent_entry_point(self):
        with patch.object(release_metadata.subprocess, "run", side_effect=subprocess.CalledProcessError(1, ["aapt"])):
            with self.assertRaises(subprocess.CalledProcessError):
                release_metadata.validate_apk_entry_points(Path("fixture.apk"), "play", Path("aapt"))
        with patch.object(release_metadata.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, stdout="")):
            with self.assertRaisesRegex(ValueError, "invalid APK manifest tree"):
                release_metadata.validate_apk_entry_points(Path("fixture.apk"), "play", Path("aapt"))

    def test_canonical_signing_identity_is_pinned(self) -> None:
        self.assertEqual(release_metadata.CANONICAL_KEY_ALIAS, "tvhplayer-release")
        self.assertEqual(
            release_metadata.CANONICAL_CERTIFICATE_SHA256,
            "1E:18:48:62:F5:BB:A2:D1:C8:40:6D:6A:7A:79:65:7F:"
            "F3:A7:3D:25:8C:1E:1B:75:FA:25:02:58:75:E5:AB:C9",
        )

    def test_fingerprint_normalization_accepts_apksigner_format(self) -> None:
        compact = "1e184862f5bba2d1c8406d6a7a79657ff3a73d258c1e1b75fa25025875e5abc9"
        self.assertEqual(
            release_metadata.normalize_fingerprint(compact),
            release_metadata.CANONICAL_CERTIFICATE_SHA256,
        )

    def test_unsigned_manifest_rejects_identity_mismatch(self) -> None:
        manifest = release_metadata.build_unsigned_manifest(
            application_id="at.bernhardberger.tvhplayer",
            version_code=1,
            version_name="0.1.0",
            source_commit="a" * 40,
            sdk_source_commit="b" * 40,
            artifacts=self._artifacts("unsigned"),
        )

        with self.assertRaisesRegex(ValueError, "application ID"):
            release_metadata.validate_unsigned_manifest(
                manifest,
                application_id="invalid.application",
                version_code=1,
                version_name="0.1.0",
            )

    def test_manifest_rejects_unsafe_artifact_path(self) -> None:
        artifacts = self._artifacts("unsigned")
        artifacts["unsignedPlayApk"]["file"] = "../unsigned.apk"

        with self.assertRaisesRegex(ValueError, "safe file name"):
            release_metadata.build_unsigned_manifest(
                application_id="at.bernhardberger.tvhplayer",
                version_code=1,
                version_name="0.1.0",
                source_commit="a" * 40,
                sdk_source_commit="b" * 40,
                artifacts=artifacts,
            )

    def test_sha256_file_matches_known_content(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "artifact"
            path.write_bytes(b"tvheadend-player\n")

            self.assertEqual(
                release_metadata.sha256_file(path),
                "4172e236b4d3ecb9ece97bd090218b288dee86981617b0f441f7eec0636915a1",
            )

    def test_signed_manifest_rejects_another_certificate(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            signed_apk = Path(directory) / "signed.apk"
            signed_apk.write_bytes(b"signed")
            unsigned = release_metadata.build_unsigned_manifest(
                application_id="at.bernhardberger.tvhplayer",
                version_code=1,
                version_name="0.1.0",
                source_commit="a" * 40,
                sdk_source_commit="b" * 40,
                artifacts=self._artifacts("unsigned"),
            )

            with self.assertRaisesRegex(ValueError, "canonical fingerprint"):
                release_metadata.build_signed_manifest(
                    unsigned_manifest=unsigned,
                    signed_play_apk=signed_apk,
                    signed_appliance_apk=signed_apk,
                    signed_play_aab=signed_apk,
                    play_r8_mapping=signed_apk,
                    appliance_r8_mapping=signed_apk,
                    certificate_sha256="00" * 32,
                    source_tar_gz=signed_apk,
                    source_zip=signed_apk,
                    native_source_tar_gz=signed_apk,
                    sdk_source_tar_gz=signed_apk,
                )

    def test_unsigned_manifest_requires_exact_sdk_source(self) -> None:
        artifacts = self._artifacts("unsigned")
        artifacts.pop("sdkSourceTarGz")

        with self.assertRaisesRegex(ValueError, "sdkSourceTarGz"):
            release_metadata.build_unsigned_manifest(
                application_id="at.bernhardberger.tvhplayer",
                version_code=1,
                version_name="0.1.0",
                source_commit="a" * 40,
                sdk_source_commit="b" * 40,
                artifacts=artifacts,
            )

        with self.assertRaisesRegex(ValueError, "SDK source commit"):
            release_metadata.build_unsigned_manifest(
                application_id="at.bernhardberger.tvhplayer",
                version_code=1,
                version_name="0.1.0",
                source_commit="a" * 40,
                sdk_source_commit="not-a-commit",
                artifacts=self._artifacts("unsigned"),
            )

    def test_signed_manifest_preserves_exact_sdk_source_and_mappings(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            files = {}
            for name in ("play.apk", "appliance.apk", "play.aab", "source.tar.gz", "source.zip", "native.tar.gz", "sdk.tar.gz", "play-mapping.txt", "appliance-mapping.txt"):
                files[name] = Path(directory) / name
                files[name].write_bytes(name.encode())
            unsigned = release_metadata.build_unsigned_manifest(
                application_id="at.bernhardberger.tvhplayer",
                version_code=1,
                version_name="0.1.0",
                source_commit="a" * 40,
                sdk_source_commit="b" * 40,
                artifacts=self._artifacts("unsigned"),
            )

            for flavor in ("play", "appliance"):
                unsigned["artifacts"][flavor + "R8Mapping"] = release_metadata._artifact(files[flavor + "-mapping.txt"])
            arguments = dict(
                unsigned_manifest=unsigned,
                signed_play_apk=files["play.apk"],
                signed_appliance_apk=files["appliance.apk"],
                signed_play_aab=files["play.aab"],
                play_r8_mapping=files["play-mapping.txt"],
                appliance_r8_mapping=files["appliance-mapping.txt"],
                certificate_sha256=release_metadata.CANONICAL_CERTIFICATE_SHA256,
                source_tar_gz=files["source.tar.gz"],
                source_zip=files["source.zip"],
                native_source_tar_gz=files["native.tar.gz"],
                sdk_source_tar_gz=files["sdk.tar.gz"],
            )
            signed = release_metadata.build_signed_manifest(**arguments)

            self.assertEqual(signed["sdkSourceCommit"], "b" * 40)
            self.assertEqual(
                signed["artifacts"]["sdkSourceTarGz"]["sha256"],
                release_metadata.sha256_file(files["sdk.tar.gz"]),
            )
            for flavor in ("play", "appliance"):
                key = flavor + "R8Mapping"
                self.assertEqual(signed["artifacts"][key], unsigned["artifacts"][key])
                mapping = files[flavor + "-mapping.txt"]
                original = mapping.read_bytes()
                mapping.write_bytes(b"different build mapping")
                with self.assertRaisesRegex(ValueError, f"changed mapping artifact: {key}"):
                    release_metadata.build_signed_manifest(**arguments)
                mapping.write_bytes(original)

    def test_signed_lineage_rejects_mapping_substitution(self) -> None:
        unsigned = self.unsigned_manifest()
        signed = json.loads(json.dumps(unsigned))
        release_metadata.validate_source_lineage(unsigned, signed)
        for key in ("playR8Mapping", "applianceR8Mapping"):
            for field in ("file", "sha256"):
                with self.subTest(key=key, field=field):
                    signed = json.loads(json.dumps(unsigned))
                    signed["artifacts"][key][field] = "substituted"
                    with self.assertRaisesRegex(ValueError, key):
                        release_metadata.validate_source_lineage(unsigned, signed)

    def test_checksum_file_rejects_paths_outside_bundle(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            bundle = Path(directory)
            checksums = bundle / "SHA256SUMS"
            checksums.write_text(f"{'1' * 64}  ../outside\n", encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "safe file name"):
                release_metadata.validate_checksum_file(
                    checksums,
                    bundle,
                    {"artifact"},
                )

    def test_two_stage_scripts_keep_signing_out_of_unsigned_builder(self) -> None:
        prepare = (ROOT / "tools/prepare-release").read_text(encoding="utf-8")
        sign = (ROOT / "tools/sign-release").read_text(encoding="utf-8")

        self.assertNotIn("TVHPLAYER_RELEASE_", prepare)
        self.assertNotIn("apksigner sign", prepare)
        self.assertEqual(prepare.count("assert-unsigned-apk"), 2)
        self.assertEqual(sign.count("assert-unsigned-apk"), 1)
        self.assertIn("validate-apk-entry-points", prepare)
        self.assertEqual(sign.count("validate-apk-entry-points"), 2)
        self.assertNotIn('if "$apksigner" verify', prepare + sign)
        self.assertIn("--ks-pass", sign)
        self.assertIn("file:", sign)
        self.assertNotIn("pass:", sign.replace("-storepass:file", "").replace("-keypass:file", ""))

    def test_signing_supports_server_side_password_files(self) -> None:
        sign = (ROOT / "tools/sign-release").read_text(encoding="utf-8")

        self.assertIn("TVHPLAYER_SIGNING_STORE_PASS_FILE", sign)
        self.assertIn("TVHPLAYER_SIGNING_KEY_PASS_FILE", sign)

    def test_release_scripts_preserve_external_sdk_source(self) -> None:
        prepare = (ROOT / "tools/prepare-release").read_text(encoding="utf-8")
        sign = (ROOT / "tools/sign-release").read_text(encoding="utf-8")
        release = (ROOT / "tools/release").read_text(encoding="utf-8")
        metadata = (ROOT / "tools/release_metadata.py").read_text(encoding="utf-8")

        self.assertNotIn('git -C "$SDK_ROOT" archive', prepare)
        self.assertIn('SDK_VERSION="0.30.0"', prepare)
        self.assertIn(
            'SDK_SOURCE_COMMIT="9de0877300420efc3cf99748af4f1bc6f3f82224"',
            prepare,
        )
        self.assertIn(":app:syncReleasedSdkEvidence", prepare)
        self.assertNotIn("sdk-media3-${SDK_VERSION}-ffmpeg-sources", prepare)
        self.assertIn('cp "$ROOT_DIR/decoder-ffmpeg/$NATIVE_SOURCE_NAME"', prepare)
        self.assertIn("--sdk-source-commit", prepare)
        self.assertIn("--sdk-source-tar-gz", prepare)
        self.assertIn('["artifacts"]["sdkSourceTarGz"]', sign)
        self.assertIn("--sdk-source-tar-gz", sign)
        self.assertIn("validate_source_lineage", release)
        self.assertIn('"sdkSourceTarGz"', metadata)
        self.assertIn('"sdkSourceCommit"', metadata)

    def test_signed_lineage_rejects_sdk_source_substitution(self) -> None:
        unsigned = {
            "sourceCommit": "a" * 40,
            "sdkSourceCommit": "b" * 40,
            "artifacts": self._artifacts("unsigned"),
        }
        signed = {
            "sourceCommit": "a" * 40,
            "sdkSourceCommit": "c" * 40,
            "artifacts": {
                **self._artifacts("signed"),
                "sourceTarGz": unsigned["artifacts"]["sourceTarGz"],
                "sourceZip": unsigned["artifacts"]["sourceZip"],
                "nativeSourceTarGz": unsigned["artifacts"]["nativeSourceTarGz"],
                "sdkSourceTarGz": unsigned["artifacts"]["sdkSourceTarGz"],
            },
        }
        with self.assertRaisesRegex(ValueError, "sdkSourceCommit"):
            release_metadata.validate_source_lineage(unsigned, signed)

        signed["sdkSourceCommit"] = unsigned["sdkSourceCommit"]
        signed["artifacts"]["sdkSourceTarGz"] = {
            "file": "substituted-sdk-source.tar.gz",
            "sha256": "f" * 64,
        }
        with self.assertRaisesRegex(ValueError, "sdkSourceTarGz"):
            release_metadata.validate_source_lineage(unsigned, signed)

    @staticmethod
    def _artifacts(stage: str) -> dict[str, dict[str, str]]:
        return {
            f"{stage}PlayApk": {"file": f"{stage}-play.apk", "sha256": "1" * 64, "flavor": "play", "applicationId": "at.bernhardberger.tvhplayer"},
            f"{stage}ApplianceApk": {"file": f"{stage}-appliance.apk", "sha256": "6" * 64, "flavor": "appliance", "applicationId": "at.bernhardberger.tvhplayer.appliance"},
            f"{stage}PlayAab": {"file": f"{stage}-play.aab", "sha256": "7" * 64, "flavor": "play", "applicationId": "at.bernhardberger.tvhplayer"},
            "playR8Mapping": {"file": "play-mapping.txt", "sha256": "8" * 64},
            "applianceR8Mapping": {"file": "appliance-mapping.txt", "sha256": "9" * 64},
            "sourceTarGz": {"file": "source.tar.gz", "sha256": "2" * 64},
            "sourceZip": {"file": "source.zip", "sha256": "3" * 64},
            "nativeSourceTarGz": {
                "file": "native-source.tar.gz",
                "sha256": "4" * 64,
            },
            "sdkSourceTarGz": {
                "file": "sdk-source.tar.gz",
                "sha256": "5" * 64,
            },
        }


if __name__ == "__main__":
    unittest.main()
