from __future__ import annotations

import runpy
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[2]
DEVICE = runpy.run_path(str(ROOT / "tools/device"), run_name="device_tool")
action_policy_error = DEVICE["action_policy_error"]
identity_errors = DEVICE["identity_errors"]
load_credential_payload = DEVICE["load_credential_payload"]
resolve_verified_release_apk = DEVICE["resolve_verified_release_apk"]
capture_screenshot = DEVICE["capture_screenshot"]
resolve_screenshot_output = DEVICE["resolve_screenshot_output"]
run = DEVICE["run"]
read_device_properties = DEVICE["read_device_properties"]
key_events = DEVICE["KEY_EVENTS"]


class DevicePolicyTest(unittest.TestCase):
    def test_product_identity_defaults_are_current(self) -> None:
        self.assertEqual(DEVICE["LOCAL_CONFIG"].name, ".tvhplayer-device.json")
        self.assertEqual(DEVICE["DEFAULT_PACKAGE"], "at.bernhardberger.tvhplayer")
        self.assertEqual(
            DEVICE["DEFAULT_CREDENTIAL_FILE"].name,
            ".tvhplayer-credentials.json",
        )

    def test_bounded_remote_navigation_keys_are_available(self) -> None:
        self.assertEqual(key_events["up"], "KEYCODE_DPAD_UP")
        self.assertEqual(key_events["down"], "KEYCODE_DPAD_DOWN")
        self.assertEqual(key_events["left"], "KEYCODE_DPAD_LEFT")
        self.assertEqual(key_events["right"], "KEYCODE_DPAD_RIGHT")
        self.assertEqual(key_events["center"], "KEYCODE_DPAD_CENTER")

    def test_screenshot_requires_explicit_safe_screen_confirmation(self) -> None:
        parser = DEVICE["build_parser"]()
        args = parser.parse_args(["screenshot"])

        self.assertFalse(args.confirm_safe_screen)
        self.assertEqual(args.output, DEVICE["DEFAULT_SCREENSHOT"])

    def test_production_and_unclassified_devices_reject_mutation(self) -> None:
        for role in ("production", "unclassified"):
            for action in (
                "install-debug",
                "launch",
                "force-stop",
                "smoke",
                "key",
                "screenshot",
                "provision-test-credentials",
            ):
                with self.subTest(role=role, action=action):
                    self.assertIsNotNone(action_policy_error(role, action))

    def test_test_device_allows_mutation(self) -> None:
        for action in (
            "install-debug",
            "launch",
            "force-stop",
            "smoke",
            "key",
            "screenshot",
            "provision-test-credentials",
        ):
            with self.subTest(action=action):
                self.assertIsNone(action_policy_error("test", action))

    def test_read_only_actions_are_allowed_for_every_role(self) -> None:
        for role in ("production", "test", "unclassified"):
            for action in ("connect", "doctor", "current", "package-info"):
                with self.subTest(role=role, action=action):
                    self.assertIsNone(action_policy_error(role, action))

    def test_release_install_is_available_only_for_production(self) -> None:
        self.assertIsNone(action_policy_error("production", "install-release"))
        self.assertIsNotNone(action_policy_error("test", "install-release"))
        self.assertIsNotNone(action_policy_error("unclassified", "install-release"))

    def test_legacy_uninstall_is_available_only_for_production(self) -> None:
        self.assertIsNone(action_policy_error("production", "uninstall-legacy"))
        self.assertIsNotNone(action_policy_error("test", "uninstall-legacy"))
        self.assertIsNotNone(action_policy_error("unclassified", "uninstall-legacy"))

    def test_release_install_requires_explicit_confirmation(self) -> None:
        parser = DEVICE["build_parser"]()
        args = parser.parse_args(
            ["install-release", "--bundle", "build/release/signed/0.1.0"]
        )

        self.assertFalse(args.confirm_production_install)

    def test_legacy_uninstall_requires_explicit_confirmation(self) -> None:
        parser = DEVICE["build_parser"]()
        args = parser.parse_args(["uninstall-legacy"])

        self.assertFalse(args.confirm_legacy_uninstall)
        self.assertEqual(DEVICE["LEGACY_PACKAGE"], "at.leoville.tvhstream")

    def test_release_apk_is_taken_from_verified_bundle_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            bundle = Path(directory)
            apk = bundle / "product.apk"
            apk.write_bytes(b"signed apk")
            (bundle / "release-manifest.json").write_text(
                '{"applicationId":"at.bernhardberger.tvhplayer",'
                '"artifacts":{"signedApk":{"file":"product.apk"}}}',
                encoding="utf-8",
            )

            with patch.object(DEVICE["subprocess"], "run") as run_mock:
                resolved = resolve_verified_release_apk(bundle)

            self.assertEqual(resolved, apk)
            run_mock.assert_called_once_with(
                [
                    str(ROOT / "tools/release"),
                    "verify-signed",
                    str(bundle),
                ],
                check=True,
            )

    def test_test_mutation_requires_matching_expected_identity(self) -> None:
        self.assertEqual(
            identity_errors(
                actual_manufacturer="TCL",
                actual_model="Test TV",
                actual_device="G10",
                actual_product="G10_4K_GB",
                expected_manufacturer="replace-after-running-doctor",
                expected_model="replace-after-running-doctor",
                expected_device="replace-after-running-doctor",
                expected_product="replace-after-running-doctor",
                require_expected=False,
            ),
            [],
        )
        self.assertEqual(
            identity_errors(
                actual_manufacturer="TCL",
                actual_model="Test TV",
                actual_device="G10",
                actual_product="G10_4K_GB",
                expected_manufacturer="replace-after-running-doctor",
                expected_model="replace-after-running-doctor",
                expected_device="replace-after-running-doctor",
                expected_product="replace-after-running-doctor",
                require_expected=True,
            ),
            [
                "expected_manufacturer is required for restricted test-device actions",
                "expected_model is required for restricted test-device actions",
                "expected_device is required for restricted test-device actions",
                "expected_product is required for restricted test-device actions",
            ],
        )
        self.assertEqual(
            identity_errors(
                actual_manufacturer="TCL",
                actual_model="Test TV",
                actual_device="G10",
                actual_product="G10_4K_GB",
                expected_manufacturer=None,
                expected_model=None,
                expected_device=None,
                expected_product=None,
                require_expected=True,
            ),
            [
                "expected_manufacturer is required for restricted test-device actions",
                "expected_model is required for restricted test-device actions",
                "expected_device is required for restricted test-device actions",
                "expected_product is required for restricted test-device actions",
            ],
        )
        self.assertEqual(
            identity_errors(
                actual_manufacturer="TCL",
                actual_model="Test TV",
                actual_device="G10",
                actual_product="G10_4K_GB",
                expected_manufacturer="tcl",
                expected_model="test tv",
                expected_device="g10",
                expected_product="g10_4k_gb",
                require_expected=True,
            ),
            [],
        )
        self.assertEqual(
            identity_errors(
                actual_manufacturer="TCL",
                actual_model="Household TV",
                actual_device="G08",
                actual_product="G08_4K_GB",
                expected_manufacturer="TCL",
                expected_model="Test TV",
                expected_device="G10",
                expected_product="G10_4K_GB",
                require_expected=True,
            ),
            [
                "device model 'Household TV' does not match expected 'Test TV'",
                "device code 'G08' does not match expected 'G10'",
                "device product 'G08_4K_GB' does not match expected 'G10_4K_GB'",
            ],
        )

    def test_device_properties_are_read_in_one_adb_call(self) -> None:
        completed = subprocess.CompletedProcess(
            args=["adb"],
            returncode=0,
            stdout="TCL\nSmart TV Pro\nG10\nG10_4K_GB\n12\narmeabi-v7a\n",
            stderr="",
        )

        with patch.object(DEVICE["subprocess"], "run", return_value=completed) as run_mock:
            properties = read_device_properties("adb", "test-device")

        self.assertEqual(
            properties,
            {
                "manufacturer": "TCL",
                "model": "Smart TV Pro",
                "device": "G10",
                "product": "G10_4K_GB",
                "android": "12",
                "abis": "armeabi-v7a",
            },
        )
        run_mock.assert_called_once()
        self.assertEqual(
            run_mock.call_args.args[0],
            [
                "adb",
                "-s",
                "test-device",
                "shell",
                "for property in ro.product.manufacturer ro.product.model "
                "ro.product.device ro.product.name ro.build.version.release "
                'ro.product.cpu.abilist; do getprop "$property"; done',
            ],
        )

    def test_missing_credential_file_is_rejected_without_secret_output(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            missing = Path(directory) / "missing.json"
            with self.assertRaisesRegex(SystemExit, "2"):
                load_credential_payload(missing)

    def test_credential_file_must_be_private_and_has_bounded_valid_json(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "credentials.json"
            path.write_text(
                '{"host":"tvh.test","htsp_port":9982,'
                '"username":"agent","password":"super-secret",'
                '"auto_connect":true}',
                encoding="utf-8",
            )
            path.chmod(stat.S_IRUSR | stat.S_IWUSR)

            payload = load_credential_payload(path)

            self.assertIn('"password":"super-secret"', payload)

            path.chmod(stat.S_IRUSR | stat.S_IWUSR | stat.S_IRGRP)
            with self.assertRaisesRegex(SystemExit, "2"):
                load_credential_payload(path)

    def test_sensitive_subprocess_output_is_redacted(self) -> None:
        completed = DEVICE["subprocess"].CompletedProcess(
            args=["adb"],
            returncode=1,
            stdout="super-secret",
            stderr="device repeated super-secret",
        )
        with patch.object(DEVICE["subprocess"], "run", return_value=completed):
            with patch("builtins.print") as print_mock:
                with self.assertRaisesRegex(SystemExit, "2"):
                    run(["adb", "sensitive-operation"], capture=True, sensitive=True)

        output = "\n".join(
            " ".join(str(argument) for argument in call.args)
            for call in print_mock.call_args_list
        )
        self.assertNotIn("super-secret", output)
        self.assertIn("redacted", output)

    def test_screenshot_output_must_be_png_outside_repository(self) -> None:
        with self.assertRaisesRegex(SystemExit, "2"):
            resolve_screenshot_output(ROOT / "screenshot.png")
        with self.assertRaisesRegex(SystemExit, "2"):
            resolve_screenshot_output(Path("/tmp/tvheadend-player-screenshot.jpg"))

        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "screen.png"
            self.assertEqual(resolve_screenshot_output(output), output.resolve())

    def test_screenshot_is_validated_and_written_owner_only(self) -> None:
        png = b"\x89PNG\r\n\x1a\ncontent"

        def fake_run(command, **kwargs):
            kwargs["stdout"].write(png)
            return subprocess.CompletedProcess(command, 0, stderr=b"")

        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "screen.png"
            with patch.object(DEVICE["subprocess"], "run", side_effect=fake_run):
                capture_screenshot("adb", "test-device", output)

            self.assertEqual(output.read_bytes(), png)
            self.assertEqual(stat.S_IMODE(output.stat().st_mode), stat.S_IRUSR | stat.S_IWUSR)


if __name__ == "__main__":
    unittest.main()
