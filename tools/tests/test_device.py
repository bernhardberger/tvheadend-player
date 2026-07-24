from __future__ import annotations

import runpy
import stat
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[2]
DEVICE = runpy.run_path(str(ROOT / "tools/device"), run_name="device_tool")
mutation_error = DEVICE["mutation_error"]
identity_errors = DEVICE["identity_errors"]
load_credential_payload = DEVICE["load_credential_payload"]
run = DEVICE["run"]
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

    def test_production_and_unclassified_devices_reject_mutation(self) -> None:
        for role in ("production", "unclassified"):
            for action in (
                "install-debug",
                "launch",
                "force-stop",
                "smoke",
                "key",
                "provision-test-credentials",
            ):
                with self.subTest(role=role, action=action):
                    self.assertIsNotNone(mutation_error(role, action))

    def test_test_device_allows_mutation(self) -> None:
        for action in (
            "install-debug",
            "launch",
            "force-stop",
            "smoke",
            "key",
            "provision-test-credentials",
        ):
            with self.subTest(action=action):
                self.assertIsNone(mutation_error("test", action))

    def test_read_only_actions_are_allowed_for_every_role(self) -> None:
        for role in ("production", "test", "unclassified"):
            for action in ("connect", "doctor", "current", "package-info"):
                with self.subTest(role=role, action=action):
                    self.assertIsNone(mutation_error(role, action))

    def test_test_mutation_requires_matching_expected_identity(self) -> None:
        self.assertEqual(
            identity_errors(
                actual_manufacturer="TCL",
                actual_model="Test TV",
                expected_manufacturer="replace-after-running-doctor",
                expected_model="replace-after-running-doctor",
                require_expected=False,
            ),
            [],
        )
        self.assertEqual(
            identity_errors(
                actual_manufacturer="TCL",
                actual_model="Test TV",
                expected_manufacturer="replace-after-running-doctor",
                expected_model="replace-after-running-doctor",
                require_expected=True,
            ),
            [
                "expected_manufacturer is required for mutating test-device actions",
                "expected_model is required for mutating test-device actions",
            ],
        )
        self.assertEqual(
            identity_errors(
                actual_manufacturer="TCL",
                actual_model="Test TV",
                expected_manufacturer=None,
                expected_model=None,
                require_expected=True,
            ),
            [
                "expected_manufacturer is required for mutating test-device actions",
                "expected_model is required for mutating test-device actions",
            ],
        )
        self.assertEqual(
            identity_errors(
                actual_manufacturer="TCL",
                actual_model="Test TV",
                expected_manufacturer="tcl",
                expected_model="test tv",
                require_expected=True,
            ),
            [],
        )
        self.assertEqual(
            identity_errors(
                actual_manufacturer="TCL",
                actual_model="Household TV",
                expected_manufacturer="TCL",
                expected_model="Test TV",
                require_expected=True,
            ),
            ["device model 'Household TV' does not match expected 'Test TV'"],
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


if __name__ == "__main__":
    unittest.main()
