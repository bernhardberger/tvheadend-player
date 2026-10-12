from __future__ import annotations

import json
import io
import argparse
import runpy
import stat
import subprocess
import tempfile
import unittest
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[2]
RELEASE = runpy.run_path(str(ROOT / "tools/release"), run_name="release_tool")


class ReleaseOrchestratorTest(unittest.TestCase):
    def test_signed_verification_checks_both_apks_and_aab(self) -> None:
        metadata = RELEASE["release_metadata"]
        for wrong_appliance_id in (False, True):
            with self.subTest(wrong_appliance_id=wrong_appliance_id), tempfile.TemporaryDirectory() as directory:
                bundle = Path(directory)
                artifacts = {}
                for key, name in (("sourceTarGz", "source.tar.gz"), ("sourceZip", "source.zip"), ("nativeSourceTarGz", "native.tar.gz"), ("sdkSourceTarGz", "sdk.tar.gz")):
                    (bundle / name).write_bytes(b"source")
                    artifacts[key] = metadata._artifact(bundle / name)
                for flavor, kind in (("play", "Apk"), ("appliance", "Apk"), ("play", "Aab")):
                    name = f"{flavor}.{kind.lower()}"
                    (bundle / name).write_bytes(b"signed fixture")
                    artifacts[f"unsigned{flavor.title()}{kind}"] = {**metadata._binary_artifact(bundle / name, flavor), "file": "unsigned-" + name}
                unsigned = metadata.build_unsigned_manifest(application_id=RELEASE["APPLICATION_ID"], version_code=1, version_name="0.1.0", source_commit="a" * 40, sdk_source_commit="b" * 40, artifacts=artifacts)
                signed = metadata.build_signed_manifest(
                    unsigned_manifest=unsigned, signed_play_apk=bundle / "play.apk", signed_appliance_apk=bundle / "appliance.apk", signed_play_aab=bundle / "play.aab",
                    certificate_sha256=metadata.CANONICAL_CERTIFICATE_SHA256, source_tar_gz=bundle / "source.tar.gz", source_zip=bundle / "source.zip", native_source_tar_gz=bundle / "native.tar.gz", sdk_source_tar_gz=bundle / "sdk.tar.gz",
                )
                metadata._write_manifest(bundle / "release-manifest.json", signed)
                metadata._write_manifest(bundle / "unsigned-release-manifest.json", unsigned)
                for name in ("APKSIGNER-VERIFY.txt", "JARSIGNER-VERIFY.txt"):
                    (bundle / name).write_text("fixture report")
                files = sorted(bundle.iterdir())
                (bundle / "SHA256SUMS").write_text("".join(f"{metadata.sha256_file(p)}  {p.name}\n" for p in files))

                def fake_run(command, **kwargs):
                    if command[0] == "apksigner":
                        output = "Signer #1 certificate SHA-256 digest: " + metadata.CANONICAL_CERTIFICATE_SHA256.replace(":", "")
                    elif command[0] == "aapt":
                        application = RELEASE["APPLICATION_ID"]
                        if Path(command[-1]).name == "appliance.apk" and not wrong_appliance_id:
                            application += ".appliance"
                        output = f"package: name='{application}' versionCode='1' versionName='0.1.0'"
                    else:
                        output = ""
                    return subprocess.CompletedProcess(command, 0, stdout=output)

                with patch.dict(RELEASE["verify_signed_bundle"].__globals__, {"_find_android_tools": lambda: (Path("aapt"), Path("apksigner"), Path("zipalign"))}), patch.dict(RELEASE["verify_signed_bundle"].__globals__, {"_run": fake_run}), patch.object(metadata, "verify_aab_signature") as aab_signature, patch.object(metadata, "validate_aab") as aab_content, patch.object(metadata, "validate_apk_entry_points") as entry_points:
                    if wrong_appliance_id:
                        with self.assertRaisesRegex(ValueError, "identity"):
                            RELEASE["verify_signed_bundle"](bundle)
                        aab_signature.assert_not_called()
                    else:
                        self.assertEqual(RELEASE["verify_signed_bundle"](bundle), signed)
                        aab_signature.assert_called_once_with(bundle / "play.aab")
                        aab_content.assert_called_once_with(bundle / "play.aab", bundle / "play.apk", 1, "0.1.0")
                        self.assertEqual([call.args for call in entry_points.call_args_list], [(bundle / "play.apk", "play", Path("aapt")), (bundle / "appliance.apk", "appliance", Path("aapt"))])

    def test_main_reports_malformed_zip_and_xml_without_traceback(self):
        for error in (zipfile.BadZipFile("invalid ZIP"), ET.ParseError("invalid XML")):
            with self.subTest(error=error), patch.dict(RELEASE["main"].__globals__, {"build_parser": lambda: parser}), patch("sys.stderr", new_callable=io.StringIO) as stderr:
                parser = unittest.mock.Mock()
                parser.parse_args.return_value = argparse.Namespace(handler=unittest.mock.Mock(side_effect=error))
                self.assertEqual(RELEASE["main"](), 1)
                self.assertEqual(stderr.getvalue(), f"error: {error}\n")

    def test_config_must_be_owner_only(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "release.json"
            path.write_text(json.dumps(self._config()), encoding="utf-8")
            path.chmod(stat.S_IRUSR | stat.S_IWUSR | stat.S_IRGRP)

            with self.assertRaisesRegex(ValueError, "owner-only"):
                RELEASE["load_config"](path)

    def test_config_rejects_ssh_options_and_relative_remote_paths(self) -> None:
        config = self._config()
        config["signingHost"] = "-oProxyCommand=unsafe"
        with self.assertRaisesRegex(ValueError, "SSH host"):
            RELEASE["validate_config"](config)

        config = self._config()
        config["remoteReleaseRoot"] = "relative/releases"
        with self.assertRaisesRegex(ValueError, "absolute remote path"):
            RELEASE["validate_config"](config)

    def test_release_readiness_requires_clean_pushed_head(self) -> None:
        errors = RELEASE["release_readiness_errors"](
            porcelain=" M README.md\n",
            head="a" * 40,
            upstream_head="b" * 40,
            head_is_upstream_ancestor=False,
        )

        self.assertIn("worktree is not clean", errors)
        self.assertIn("HEAD is not present on the configured upstream", errors)

    def test_remote_sign_command_uses_trusted_checkout_without_passwords(self) -> None:
        command = RELEASE["remote_sign_command"](
            config=self._config(),
            source_commit="a" * 40,
            incoming_dir="/srv/releases/session/incoming/0.1.0",
            signed_dir="/srv/releases/session/signed/0.1.0",
        )

        self.assertIn("git checkout --detach", command)
        self.assertIn("tools/sign-release", command)
        self.assertNotIn("password", command.lower())
        self.assertNotIn("TVHPLAYER_RELEASE_", command)

    def test_parser_keeps_device_deployment_outside_release_tool(self) -> None:
        parser = RELEASE["build_parser"]()
        actions = parser._subparsers._group_actions[0].choices

        self.assertEqual(set(actions), {"prepare", "sign", "verify-signed"})

    def test_remote_orchestration_is_non_interactive(self) -> None:
        release = (ROOT / "tools/release").read_text(encoding="utf-8")

        self.assertNotIn('"-tt"', release)

    @staticmethod
    def _config() -> dict[str, object]:
        return {
            "schemaVersion": 1,
            "signingHost": "release-signer",
            "signingRepository": "/srv/tvheadend-player",
            "signingKeystore": "/secure/tvheadend-player/release.jks",
            "remoteReleaseRoot": "/srv/tvheadend-player-releases",
            "signingRemote": "fork",
            "signingBranch": "main",
        }


if __name__ == "__main__":
    unittest.main()
