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
    def test_prepare_requires_nonempty_regular_mapping_for_each_flavor(self):
        script = (ROOT / "tools/prepare-release").read_text()
        guard = script[script.index('for mapping in '):script.index('for apk in ')]
        for flavor in ("play", "appliance"):
            for kind in ("present", "missing", "empty", "symlink", "directory"):
                with self.subTest(flavor=flavor, kind=kind), tempfile.TemporaryDirectory() as directory:
                    paths = {name: Path(directory) / (name + "-mapping.txt") for name in ("play", "appliance")}
                    for path in paths.values():
                        path.write_text("fixture.Class -> a:\n")
                    if kind == "missing":
                        paths[flavor].unlink()
                    elif kind == "empty":
                        paths[flavor].write_bytes(b"")
                    elif kind == "directory":
                        paths[flavor].unlink()
                        paths[flavor].mkdir()
                    elif kind == "symlink":
                        paths[flavor].unlink()
                        paths[flavor].symlink_to(paths["appliance" if flavor == "play" else "play"])
                    preamble = "\n".join(f"built_{name}_mapping={shlex.quote(str(path))}" for name, path in paths.items())
                    result = subprocess.run(["bash", "-eu", "-c", preamble + "\n" + guard], capture_output=True, text=True)
                    self.assertEqual(result.returncode, 0 if kind == "present" else 1)
                    if kind != "present":
                        self.assertIn("release R8 mapping is missing, empty, or a symbolic link", result.stderr)

    def test_prepare_refuses_a_development_sdk_before_building(self):
        release_pin = next(
            line.split('"')[1]
            for line in (ROOT / "tools/prepare-release").read_text().splitlines()
            if line.startswith('SDK_VERSION="')
        )
        for version, refusal in (
            ("0.36.0-20261024.143000-1", "a Player release needs a released SDK"),
            ("0.36.0-SNAPSHOT", "a Player release needs a released SDK"),
            ("0.36.0-rc1", "a Player release needs a released SDK"),
            ("0.99.0", "does not match the release pin"),
            (release_pin, None),
        ):
            with self.subTest(version=version), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                (root / "tools").mkdir()
                (root / "gradle").mkdir()
                (root / "bin").mkdir()
                (root / "android").mkdir()
                script = root / "tools/prepare-release"
                script.write_bytes((ROOT / "tools/prepare-release").read_bytes())
                script.chmod(0o755)
                (root / "gradle/libs.versions.toml").write_text(f'[versions]\ntvheadend-sdk = "{version}"\n')
                # A clean fake worktree; with no build tools the script stops before any build.
                git = root / "bin/git"
                git.write_text("#!/bin/sh\nexit 0\n")
                git.chmod(0o755)
                environment = {"PATH": f"{root / 'bin'}:/usr/bin:/bin", "ANDROID_HOME": str(root / "android")}
                result = subprocess.run([str(script)], capture_output=True, text=True, env=environment, timeout=30)
                self.assertEqual(result.returncode, 1)
                if refusal is None:
                    self.assertIn("apksigner, aapt, and zipalign are required", result.stderr)
                else:
                    self.assertIn(refusal, result.stderr)
                    self.assertIn(version, result.stderr)

    def test_sign_refuses_a_development_sdk_bundle_before_any_signing(self):
        sdk_version, sdk_source_commit = metadata.released_sdk_pin()
        for change, refusal in (
            ({"sdkVersion": "0.36.0-20261024.143000-1"}, "a Player release needs a released SDK"),
            ({"sdkVersion": "0.36.0-SNAPSHOT"}, "a Player release needs a released SDK"),
            ({"sdkSourceCommit": "c" * 40}, "is not the released SDK"),
            ({}, "release keystore does not exist"),
        ):
            with self.subTest(change=change), tempfile.TemporaryDirectory() as directory:
                incoming = Path(directory) / "incoming"
                incoming.mkdir()
                (incoming / "SHA256SUMS").write_text("")
                metadata._write_manifest(incoming / "release-manifest.json", {
                    "schemaVersion": metadata.SCHEMA_VERSION, "stage": "unsigned",
                    "sdkVersion": sdk_version, "sdkSourceCommit": sdk_source_commit, **change,
                })
                # No keystore, password or build tool exists; the released pin only reaches the keystore check.
                result = subprocess.run(
                    [str(ROOT / "tools/sign-release"), str(incoming), str(Path(directory) / "missing.jks")],
                    capture_output=True, text=True, timeout=30,
                    env={"PATH": "/usr/bin:/bin", "ANDROID_HOME": str(Path(directory) / "android")},
                )
                self.assertEqual(result.returncode, 1)
                self.assertIn(refusal, result.stderr)
                if change:
                    self.assertNotIn("keystore", result.stderr)

    def test_prepare_and_signed_staging_preserve_artifacts_and_mappings(self):
        script = (ROOT / "tools/prepare-release").read_text()
        self.assertIn(":app:assemblePlayRelease :app:assembleApplianceRelease :app:bundlePlayRelease", script)
        staging = script[script.index('release_dir="'):]
        with tempfile.TemporaryDirectory() as directory:
            temporary = Path(directory)
            for name in ("play-unsigned.apk", "appliance-unsigned.apk", "play.aab", "native.tar.xz", "sdk.tar.gz", "play-mapping.txt", "appliance-mapping.txt"):
                (temporary / name).write_bytes(name.encode())
            output = temporary / "bundle"
            variables = {
                "ROOT_DIR": str(ROOT), "temporary_dir": str(temporary),
                "aligned_apk": str(temporary / "play-unsigned.apk"),
                "built_aab": str(temporary / "play.aab"),
                "built_play_mapping": str(temporary / "play-mapping.txt"),
                "built_appliance_mapping": str(temporary / "appliance-mapping.txt"),
                "NATIVE_SOURCE_NAME": "native.tar.xz", "SDK_SOURCE_NAME": "sdk.tar.gz",
                "SDK_VERSION": metadata.released_sdk_pin()[0], "SDK_SOURCE_COMMIT": metadata.released_sdk_pin()[1],
                "application_id": "at.bernhardberger.tvhplayer",
                "version_code": "1", "version_name": "0.1.0",
            }
            preamble = "\n".join(f"{name}={shlex.quote(value)}" for name, value in variables.items())
            # Only Git archive output is faked; run the production staging and metadata code.
            preamble += '\ngit() { if [[ "$1" == rev-parse ]]; then printf "%040d\\n" 0; else while [[ "$1" != -o ]]; do shift; done; printf source > "$2"; fi; }\n'
            subprocess.run(["bash", "-eu", "-c", preamble + staging, "staging-test", str(output)], cwd=ROOT, check=True, capture_output=True, text=True)
            manifest = metadata._load_manifest(output / "release-manifest.json")
            metadata.validate_unsigned_manifest(manifest, application_id=variables["application_id"], version_code=1, version_name="0.1.0")
            expected = {artifact["file"] for artifact in manifest["artifacts"].values()}
            self.assertEqual(len(expected), 9)
            metadata.validate_checksum_file(output / "SHA256SUMS", output, expected | {"release-manifest.json"})
            for key, original in (("unsignedPlayApk", "play-unsigned.apk"), ("unsignedApplianceApk", "appliance-unsigned.apk"), ("unsignedPlayAab", "play.aab"), ("playR8Mapping", "play-mapping.txt"), ("applianceR8Mapping", "appliance-mapping.txt")):
                self.assertEqual((output / manifest["artifacts"][key]["file"]).read_bytes(), (temporary / original).read_bytes())

            # Exercise only post-signing staging with synthetic binaries: no signer or key is used.
            sign = (ROOT / "tools/sign-release").read_text()
            staging = sign[sign.index('staged_output="'):]
            signed_output = temporary / "signed"
            variables.update({
                "INCOMING_DIR": str(output), "OUTPUT_DIR": str(signed_output),
                "manifest": str(output / "release-manifest.json"),
                "certificate_sha256": metadata.CANONICAL_CERTIFICATE_SHA256,
                "signed_apk_name": "play.apk", "signed_appliance_name": "appliance.apk",
                "signed_aab_name": "play.aab",
            })
            for variable, key in (("source_tar_name", "sourceTarGz"), ("source_zip_name", "sourceZip"),
                                  ("native_source_name", "nativeSourceTarGz"), ("sdk_source_name", "sdkSourceTarGz"),
                                  ("play_mapping_name", "playR8Mapping"), ("appliance_mapping_name", "applianceR8Mapping")):
                variables[variable] = manifest["artifacts"][key]["file"]
            for name in ("play.apk", "appliance.apk", "play.aab", "play-verify.txt", "appliance-verify.txt", "aab-verify.txt"):
                (temporary / name).write_bytes(name.encode())
            preamble = "\n".join(f"{name}={shlex.quote(value)}" for name, value in variables.items())
            subprocess.run(["bash", "-eu", "-c", preamble + "\n" + staging], cwd=ROOT, check=True, capture_output=True, text=True)
            signed = metadata._load_manifest(signed_output / "release-manifest.json")
            metadata.validate_signed_manifest(signed)
            metadata.validate_source_lineage(manifest, signed)
            metadata._verify_artifact_files(signed, signed_output)
            expected = {artifact["file"] for artifact in signed["artifacts"].values()}
            expected |= {"release-manifest.json", "unsigned-release-manifest.json", "APKSIGNER-VERIFY.txt", "JARSIGNER-VERIFY.txt"}
            metadata.validate_checksum_file(signed_output / "SHA256SUMS", signed_output, expected)
            for key in ("playR8Mapping", "applianceR8Mapping"):
                self.assertEqual(signed["artifacts"][key], manifest["artifacts"][key])
                mapping = signed_output / signed["artifacts"][key]["file"]
                original = mapping.read_bytes()
                for missing in (False, True):
                    with self.subTest(key=key, missing=missing):
                        if missing:
                            mapping.unlink()
                        else:
                            mapping.write_bytes(b"modified mapping")
                        with self.assertRaisesRegex(ValueError, "checksum"):
                            metadata._verify_artifact_files(signed, signed_output)
                        with self.assertRaisesRegex(ValueError, "checksum"):
                            metadata.validate_checksum_file(signed_output / "SHA256SUMS", signed_output, expected)
                        mapping.write_bytes(original)

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
