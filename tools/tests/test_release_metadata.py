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
                        manifest["artifacts"][stage + binary][field] = wrong
                        with self.assertRaises(ValueError):
                            if stage == "unsigned":
                                release_metadata.validate_unsigned_manifest(manifest, application_id="at.bernhardberger.tvhplayer", version_code=1, version_name="0.1.0")
                            else:
                                release_metadata.validate_signed_manifest(manifest)

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

    def test_json_loader_rejects_duplicate_artifact_keys(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "manifest.json"
            path.write_text('{"artifacts":{"unsignedPlayApk":{},"unsignedPlayApk":{}}}')
            with self.assertRaisesRegex(ValueError, "duplicate manifest field"):
                release_metadata._load_manifest(path)

    def test_aab_signature_requires_verified_entries_and_pinned_certificate(self) -> None:
        for status, report, fingerprint, accepted in (
            (0, "jar verified.", release_metadata.CANONICAL_CERTIFICATE_SHA256, True),
            (4, "jar verified, with signer errors.", release_metadata.CANONICAL_CERTIFICATE_SHA256, True),
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
        for failure in (None, "payload", "alignment", "compression", "identity", "signed", "elf", "abi"):
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
                xml = f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{package}" android:versionCode="1" android:versionName="0.1.0"/>'
                with patch.object(release_metadata, "bundletool_command", return_value=["bundletool"]), patch.object(release_metadata.subprocess, "run", side_effect=[subprocess.CompletedProcess([], 0, stdout=json.dumps(config)), subprocess.CompletedProcess([], 0, stdout=xml)]):
                    if failure is None:
                        release_metadata.validate_aab(aab, apk, 1, "0.1.0", unsigned=True)
                    else:
                        with self.assertRaises(ValueError):
                            release_metadata.validate_aab(aab, apk, 1, "0.1.0", unsigned=True)

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

    def test_signed_manifest_preserves_exact_sdk_source(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            files = {}
            for name in ("play.apk", "appliance.apk", "play.aab", "source.tar.gz", "source.zip", "native.tar.gz", "sdk.tar.gz"):
                files[name] = Path(directory) / name
                files[name].write_bytes(b"artifact")
            unsigned = release_metadata.build_unsigned_manifest(
                application_id="at.bernhardberger.tvhplayer",
                version_code=1,
                version_name="0.1.0",
                source_commit="a" * 40,
                sdk_source_commit="b" * 40,
                artifacts=self._artifacts("unsigned"),
            )

            signed = release_metadata.build_signed_manifest(
                unsigned_manifest=unsigned,
                signed_play_apk=files["play.apk"],
                signed_appliance_apk=files["appliance.apk"],
                signed_play_aab=files["play.aab"],
                certificate_sha256=release_metadata.CANONICAL_CERTIFICATE_SHA256,
                source_tar_gz=files["source.tar.gz"],
                source_zip=files["source.zip"],
                native_source_tar_gz=files["native.tar.gz"],
                sdk_source_tar_gz=files["sdk.tar.gz"],
            )

            self.assertEqual(signed["sdkSourceCommit"], "b" * 40)
            self.assertEqual(
                signed["artifacts"]["sdkSourceTarGz"]["sha256"],
                release_metadata.sha256_file(files["sdk.tar.gz"]),
            )

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
        self.assertIn("apksigner verify", prepare)
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
        self.assertIn('SDK_VERSION="0.27.0"', prepare)
        self.assertIn(
            'SDK_SOURCE_COMMIT="df21863743c909507192bd01e54a80219e1c87be"',
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
