from __future__ import annotations

import importlib.util
import shlex
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("metadata", ROOT / "tools/release_metadata.py")
metadata = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(metadata)


class ReleaseScriptsTest(unittest.TestCase):
    def test_prepare_stages_all_three_artifacts_and_one_source_set(self):
        script = (ROOT / "tools/prepare-release").read_text()
        self.assertIn(":app:assemblePlayRelease :app:assembleApplianceRelease :app:bundlePlayRelease", script)
        staging = script[script.index('release_dir="'):]
        with tempfile.TemporaryDirectory() as directory:
            temporary = Path(directory)
            for name in ("play-unsigned.apk", "appliance-unsigned.apk", "play.aab", "native.tar.xz", "sdk.tar.gz"):
                (temporary / name).write_bytes(name.encode())
            output = temporary / "bundle"
            variables = {
                "ROOT_DIR": str(ROOT), "temporary_dir": str(temporary),
                "aligned_apk": str(temporary / "play-unsigned.apk"),
                "built_aab": str(temporary / "play.aab"),
                "NATIVE_SOURCE_NAME": "native.tar.xz", "SDK_SOURCE_NAME": "sdk.tar.gz",
                "SDK_SOURCE_COMMIT": "b" * 40, "application_id": "at.bernhardberger.tvhplayer",
                "version_code": "1", "version_name": "0.1.0",
            }
            preamble = "\n".join(f"{name}={shlex.quote(value)}" for name, value in variables.items())
            # Only Git archive output is faked; run the production staging and metadata code.
            preamble += '\ngit() { if [[ "$1" == rev-parse ]]; then printf "%040d\\n" 0; else while [[ "$1" != -o ]]; do shift; done; printf source > "$2"; fi; }\n'
            subprocess.run(["bash", "-eu", "-c", preamble + staging, "staging-test", str(output)], cwd=ROOT, check=True, capture_output=True, text=True)
            manifest = metadata._load_manifest(output / "release-manifest.json")
            metadata.validate_unsigned_manifest(manifest, application_id=variables["application_id"], version_code=1, version_name="0.1.0")
            expected = {artifact["file"] for artifact in manifest["artifacts"].values()}
            self.assertEqual(len(expected), 7)
            metadata.validate_checksum_file(output / "SHA256SUMS", output, expected | {"release-manifest.json"})
            for key, original in (("unsignedPlayApk", "play-unsigned.apk"), ("unsignedApplianceApk", "appliance-unsigned.apk"), ("unsignedPlayAab", "play.aab")):
                self.assertEqual((output / manifest["artifacts"][key]["file"]).read_bytes(), (temporary / original).read_bytes())

    def test_signing_arguments_use_two_apks_and_one_aab_with_file_references(self):
        script = (ROOT / "tools/sign-release").read_text()
        commands = script[script.index('\nsigned_apk_name="'):script.index('\nstaged_output="')]
        with tempfile.TemporaryDirectory() as directory:
            temporary = Path(directory)
            variables = {
                "ROOT_DIR": str(ROOT), "temporary_dir": str(temporary), "INCOMING_DIR": str(temporary),
                "version_name": "0.1.0", "version_code": "1", "KEYSTORE": str(temporary / "key.jks"),
                "KEY_ALIAS": "tvhplayer-release", "store_password_file": str(temporary / "store-file"),
                "key_password_file": str(temporary / "key-file"), "temporary_password_files": "false",
                "unsigned_apk": str(temporary / "play-unsigned.apk"),
                "unsigned_appliance_name": "appliance-unsigned.apk", "unsigned_aab_name": "play-unsigned.aab",
                "apksigner": "fake_apksigner", "zipalign": "fake_zipalign", "calls": str(temporary / "calls"),
            }
            preamble = "\n".join(f"{name}={shlex.quote(value)}" for name, value in variables.items())
            # Shell functions intercept every external command in this section. No key or password
            # file exists and no actual signing/verification process can be launched.
            preamble += '''
record() { printf '%s\\0' "$@" >> "$calls"; printf '\\n' >> "$calls"; }
fake_apksigner() { record apksigner "$@"; }
fake_zipalign() { record zipalign "$@"; }
jarsigner() { record jarsigner "$@"; }
python3() { record metadata "$@"; }
'''
            subprocess.run(["bash", "-eu", "-c", preamble + commands], check=True, capture_output=True)
            calls = [line.rstrip(b"\0").decode().split("\0") for line in (temporary / "calls").read_bytes().splitlines()]
            apk_calls = [call for call in calls if call[:2] == ["apksigner", "sign"]]
            self.assertEqual(len(apk_calls), 2)
            self.assertEqual({Path(call[-1]).name for call in apk_calls}, {"play-unsigned.apk", "appliance-unsigned.apk"})
            for call in apk_calls:
                self.assertEqual(call[call.index("--ks-pass") + 1], "file:" + variables["store_password_file"])
                self.assertEqual(call[call.index("--key-pass") + 1], "file:" + variables["key_password_file"])
                self.assertEqual(call[call.index("--ks-key-alias") + 1], variables["KEY_ALIAS"])
                self.assertEqual(call[call.index("--ks") + 1], variables["KEYSTORE"])
            jar_calls = [call for call in calls if call[0] == "jarsigner"]
            self.assertEqual(len(jar_calls), 1)
            jar = jar_calls[0]
            self.assertEqual(jar[jar.index("-storepass:file") + 1], variables["store_password_file"])
            self.assertEqual(jar[jar.index("-keypass:file") + 1], variables["key_password_file"])
            self.assertNotIn("-storepass", jar)
            self.assertNotIn("-keypass", jar)
            self.assertEqual(jar[jar.index("-keystore") + 1], variables["KEYSTORE"])
            self.assertEqual(jar[-1], variables["KEY_ALIAS"])
            self.assertEqual(Path(jar[-2]).name, "play-unsigned.aab")
            self.assertEqual(len([call for call in calls if call[:2] == ["apksigner", "verify"]]), 2)
            self.assertEqual(len([call for call in calls if "verify-apk-certificate" in call]), 2)
            self.assertEqual(len([call for call in calls if "verify-aab-signature" in call]), 1)


if __name__ == "__main__":
    unittest.main()
