from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / "tools/release_metadata.py"
SPEC = importlib.util.spec_from_file_location("release_metadata", MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Cannot load {MODULE_PATH}")
release_metadata = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release_metadata)


class ReleaseMetadataTest(unittest.TestCase):
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
        artifacts["unsignedApk"]["file"] = "../unsigned.apk"

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
                    signed_apk=signed_apk,
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
            artifact = Path(directory) / "artifact"
            artifact.write_bytes(b"artifact")
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
                signed_apk=artifact,
                certificate_sha256=release_metadata.CANONICAL_CERTIFICATE_SHA256,
                source_tar_gz=artifact,
                source_zip=artifact,
                native_source_tar_gz=artifact,
                sdk_source_tar_gz=artifact,
            )

            self.assertEqual(signed["sdkSourceCommit"], "b" * 40)
            self.assertEqual(
                signed["artifacts"]["sdkSourceTarGz"]["sha256"],
                release_metadata.sha256_file(artifact),
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
        self.assertNotIn("pass:", sign)

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
        self.assertIn('SDK_VERSION="0.3.2"', prepare)
        self.assertIn(
            'SDK_SOURCE_COMMIT="fa39e5e24d1103339210969125905ef1bc6f11a1"',
            prepare,
        )
        self.assertIn(":app:syncReleasedSdkEvidence", prepare)
        self.assertIn("sdk-media3-${SDK_VERSION}-ffmpeg-sources.tar.xz", prepare)
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
        apk_name = "unsigned.apk" if stage == "unsigned" else "signed.apk"
        return {
            f"{stage}Apk": {"file": apk_name, "sha256": "1" * 64},
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
