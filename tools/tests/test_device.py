from __future__ import annotations

import json
import runpy
import stat
import subprocess
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import Mock, patch


ROOT = Path(__file__).resolve().parents[2]
DEVICE = runpy.run_path(str(ROOT / "tools/device"), run_name="device_tool")
action_policy_error = DEVICE["action_policy_error"]
identity_errors = DEVICE["identity_errors"]
select_device_config = DEVICE["select_device_config"]
load_credential_payload = DEVICE["load_credential_payload"]
resolve_verified_release_apk = DEVICE["resolve_verified_release_apk"]
capture_screenshot = DEVICE["capture_screenshot"]
debug_video_backdrop_command = DEVICE["debug_video_backdrop_command"]
debug_video_backdrop_applied = DEVICE["debug_video_backdrop_applied"]
resolve_screenshot_output = DEVICE["resolve_screenshot_output"]
default_screenshot_output = DEVICE["default_screenshot_output"]
sanitize_screenshot_name = DEVICE["sanitize_screenshot_name"]
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

    def test_active_named_target_is_selected_without_exposing_other_profiles(self) -> None:
        target_name, config = select_device_config(
            {
                "active_target": "g10",
                "targets": {
                    "g10": {
                        "serial": "g10-serial",
                        "role": "test",
                        "expected_device": "G10",
                    },
                    "nvidia-shield": {
                        "serial": "shield-serial",
                        "role": "test",
                        "expected_device": "darcy",
                    },
                },
            },
            requested_target=None,
        )

        self.assertEqual(target_name, "g10")
        self.assertEqual(
            config,
            {
                "serial": "g10-serial",
                "role": "test",
                "expected_device": "G10",
            },
        )

    def test_explicit_named_target_overrides_the_active_target(self) -> None:
        target_name, config = select_device_config(
            {
                "active_target": "g10",
                "targets": {
                    "g10": {"serial": "g10-serial"},
                    "nvidia-shield": {"serial": "shield-serial"},
                },
            },
            requested_target="nvidia-shield",
        )

        self.assertEqual(target_name, "nvidia-shield")
        self.assertEqual(config["serial"], "shield-serial")

    def test_unknown_or_unsafe_named_target_is_rejected(self) -> None:
        value = {
            "active_target": "g10",
            "targets": {"g10": {"serial": "g10-serial"}},
        }

        for requested_target in ("missing", "../g10"):
            with self.subTest(requested_target=requested_target):
                with self.assertRaisesRegex(SystemExit, "2"):
                    select_device_config(
                        value,
                        requested_target=requested_target,
                    )

    def test_legacy_single_target_config_remains_supported(self) -> None:
        target_name, config = select_device_config(
            {"serial": "legacy-serial", "role": "test"},
            requested_target=None,
        )

        self.assertIsNone(target_name)
        self.assertEqual(config, {"serial": "legacy-serial", "role": "test"})

    def test_parser_accepts_a_named_target_before_the_action(self) -> None:
        parser = DEVICE["build_parser"]()

        args = parser.parse_args(["--target", "g10", "doctor"])

        self.assertEqual(args.target, "g10")

    def test_accept_debug_is_an_exact_named_g10_action_without_overrides(self) -> None:
        parser = DEVICE["build_parser"]()
        args = parser.parse_args(["--target", "g10", "accept-debug"])

        self.assertEqual(args.action, "accept-debug")
        self.assertEqual(
            DEVICE["acceptance_policy_errors"](
                requested_target=args.target,
                selected_target="g10",
                role="test",
                package_name=DEVICE["DEFAULT_PACKAGE"],
                serial_overridden=False,
                package_overridden=False,
                config={
                    "expected_manufacturer": "TCL",
                    "expected_model": "Smart TV Pro",
                    "expected_device": "G10",
                    "expected_product": "G10_4K_GB",
                },
                properties={
                    "manufacturer": "TCL",
                    "model": "Smart TV Pro",
                    "device": "G10",
                    "product": "G10_4K_GB",
                },
            ),
            [],
        )

    def test_accept_debug_rejects_g08_unnamed_unclassified_and_identity_overrides(self) -> None:
        valid_config = {
            "expected_manufacturer": "TCL",
            "expected_model": "Smart TV Pro",
            "expected_device": "G10",
            "expected_product": "G10_4K_GB",
        }
        valid_properties = {
            "manufacturer": "TCL",
            "model": "Smart TV Pro",
            "device": "G10",
            "product": "G10_4K_GB",
        }
        cases = (
            {"requested_target": None},
            {"requested_target": "g08", "selected_target": "g08"},
            {"role": "unclassified"},
            {"serial_overridden": True},
            {"package_overridden": True},
            {"properties": {**valid_properties, "device": "G08", "product": "G08_4K_GB"}},
        )
        defaults = {
            "requested_target": "g10",
            "selected_target": "g10",
            "role": "test",
            "package_name": DEVICE["DEFAULT_PACKAGE"],
            "serial_overridden": False,
            "package_overridden": False,
            "config": valid_config,
            "properties": valid_properties,
        }

        for case in cases:
            with self.subTest(case=case):
                self.assertTrue(DEVICE["acceptance_policy_errors"](**(defaults | case)))

    def test_acceptance_fixture_requires_two_distinct_positive_channel_ids(self) -> None:
        self.assertEqual(
            DEVICE["acceptance_fixture"](
                {
                    "acceptance_progressive_channel_id": "101",
                    "acceptance_interlaced_channel_id": "202",
                },
            ),
            {"progressiveChannelId": "101", "interlacedChannelId": "202"},
        )
        for config in (
            {},
            {"acceptance_progressive_channel_id": "0", "acceptance_interlaced_channel_id": "2"},
            {"acceptance_progressive_channel_id": "2", "acceptance_interlaced_channel_id": "2"},
            {"acceptance_progressive_channel_id": "secret", "acceptance_interlaced_channel_id": "2"},
        ):
            with self.subTest(config=config):
                with self.assertRaisesRegex(SystemExit, "2"):
                    DEVICE["acceptance_fixture"](config)

    def test_acceptance_instrumentation_is_bounded_to_named_methods_and_safe_arguments(self) -> None:
        command = DEVICE["acceptance_instrumentation_command"](
            "adb",
            "configured-serial",
            "progressiveLivePlayback",
            {"progressiveChannelId": "101", "interlacedChannelId": "202"},
        )

        self.assertEqual(command[:6], ["adb", "-s", "configured-serial", "shell", "am", "instrument"])
        self.assertIn(
            "at.bernhardberger.tvhplayer.acceptance.DeviceAcceptanceTest#progressiveLivePlayback",
            command,
        )
        self.assertEqual(command[-1], "at.bernhardberger.tvhplayer.test/androidx.test.runner.AndroidJUnitRunner")
        self.assertNotIn("logcat", command)
        self.assertNotIn("uiautomator", command)
        self.assertNotIn("dumpsys", command)

    def test_acceptance_playback_surface_requires_no_activity_component(self) -> None:
        activity = "at.bernhardberger.tvhplayer.acceptance.AcceptancePlaybackSurfaceActivity"
        debug_manifest = DEVICE["ROOT"] / "app/src/debug/AndroidManifest.xml"
        instrumentation_manifest = DEVICE["ROOT"] / "app/src/androidTest/AndroidManifest.xml"

        self.assertNotIn(activity, debug_manifest.read_text(encoding="utf-8"))
        self.assertFalse(
            instrumentation_manifest.exists()
            and activity in instrumentation_manifest.read_text(encoding="utf-8")
        )

    def test_acceptance_result_parser_fails_closed(self) -> None:
        passed = subprocess.CompletedProcess(
            args=["adb"],
            returncode=0,
            stdout="OK (1 test)\nINSTRUMENTATION_CODE: -1\n",
            stderr="",
        )
        failed = subprocess.CompletedProcess(
            args=["adb"],
            returncode=0,
            stdout="FAILURES!!!\nTests run: 1, Failures: 1\nINSTRUMENTATION_CODE: -1\n",
            stderr="server detail that must not be retained",
        )

        self.assertEqual(DEVICE["acceptance_instrumentation_outcome"](passed), "PASS")
        self.assertEqual(DEVICE["acceptance_instrumentation_outcome"](failed), "FAIL")

    def test_acceptance_failure_code_parser_retains_only_allowlisted_code(self) -> None:
        failed = subprocess.CompletedProcess(
            args=["adb"],
            returncode=0,
            stdout=(
                "java.lang.AssertionError: ACCEPTANCE_PLAYBACK_FAILED_BAD_SIGNAL\n"
                "private server detail\n"
            ),
            stderr="private transport detail",
        )
        unknown = subprocess.CompletedProcess(
            args=["adb"],
            returncode=0,
            stdout="java.lang.AssertionError: private server detail\n",
            stderr="ACCEPTANCE_CONNECTION_FAILED private transport detail",
        )

        self.assertEqual(
            DEVICE["acceptance_instrumentation_failure_code"](failed),
            "ACCEPTANCE_PLAYBACK_FAILED_BAD_SIGNAL",
        )
        self.assertEqual(
            DEVICE["acceptance_instrumentation_failure_code"](unknown),
            "UNCLASSIFIED",
        )

    def test_acceptance_orchestration_retains_only_typed_failure_code(self) -> None:
        package_queries = 0

        def fake_run(command: list[str], *, timeout_seconds: int):
            nonlocal package_queries
            if command == DEVICE["package_query_command"]("adb", "configured-serial"):
                package_queries += 1
                stdout = (
                    f"package:{DEVICE['TEST_PACKAGE']}\n"
                    if package_queries == 2
                    else ""
                )
            elif "instrument" in command:
                stdout = (
                    "java.lang.AssertionError: ACCEPTANCE_PLAYBACK_FAILED_BAD_SIGNAL\n"
                    "private server detail\nFAILURES!!!\nINSTRUMENTATION_CODE: -1\n"
                )
            else:
                stdout = "Success\n"
            return subprocess.CompletedProcess(
                command,
                0,
                stdout=stdout,
                stderr="private transport detail",
            )

        with patch.dict(
            DEVICE["run_debug_acceptance"].__globals__,
            {"run_bounded_redacted": fake_run},
        ):
            methods, cleanup = DEVICE["run_debug_acceptance"](
                "adb",
                "configured-serial",
                {"progressiveChannelId": "101", "interlacedChannelId": "202"},
                Path("/private/app-debug.apk"),
                Path("/private/app-debug-androidTest.apk"),
            )

        self.assertEqual(len(methods), 1)
        self.assertEqual(methods[0]["name"], "readMetadataProfilesAndArtwork")
        self.assertEqual(methods[0]["outcome"], "FAIL")
        self.assertEqual(methods[0]["failureCode"], "ACCEPTANCE_PLAYBACK_FAILED_BAD_SIGNAL")
        self.assertEqual(cleanup, "PASS")

    def test_acceptance_package_query_distinguishes_exact_presence_absence_and_invalid_output(self) -> None:
        present = subprocess.CompletedProcess(
            args=["adb"],
            returncode=0,
            stdout=f"package:{DEVICE['TEST_PACKAGE']}\n",
            stderr="",
        )
        absent = subprocess.CompletedProcess(
            args=["adb"],
            returncode=0,
            stdout="",
            stderr="",
        )
        wrong = subprocess.CompletedProcess(
            args=["adb"],
            returncode=0,
            stdout=(
                f"package:{DEVICE['TEST_PACKAGE']}\n"
                "package:wrong.package\n"
            ),
            stderr="",
        )

        self.assertEqual(DEVICE["package_query_outcome"](present), "PRESENT")
        self.assertEqual(DEVICE["package_query_outcome"](absent), "ABSENT")
        self.assertEqual(DEVICE["package_query_outcome"](wrong), "INVALID")

    def test_acceptance_package_mutation_requires_explicit_success_output(self) -> None:
        success = subprocess.CompletedProcess(
            args=["adb"],
            returncode=0,
            stdout="Performing Streamed Install\nSuccess\n",
            stderr="",
        )
        ambiguous = subprocess.CompletedProcess(
            args=["adb"],
            returncode=0,
            stdout="Failure [unknown]\n",
            stderr="",
        )

        self.assertTrue(DEVICE["package_mutation_succeeded"](success))
        self.assertFalse(DEVICE["package_mutation_succeeded"](ambiguous))

    def test_acceptance_orchestration_runs_named_methods_and_always_removes_test_package(self) -> None:
        commands: list[list[str]] = []
        package_queries = 0

        def fake_run(command: list[str], *, timeout_seconds: int):
            nonlocal package_queries
            commands.append(command)
            if "instrument" in command:
                stdout = "OK (1 test)\nINSTRUMENTATION_CODE: -1\n"
            elif command == DEVICE["package_query_command"]("adb", "configured-serial"):
                package_queries += 1
                stdout = (
                    f"package:{DEVICE['TEST_PACKAGE']}\n"
                    if package_queries == 2
                    else ""
                )
            else:
                stdout = "Success\n"
            return subprocess.CompletedProcess(command, 0, stdout=stdout, stderr="")

        with patch.dict(
            DEVICE["run_debug_acceptance"].__globals__,
            {"run_bounded_redacted": fake_run},
        ):
            methods, cleanup = DEVICE["run_debug_acceptance"](
                "adb",
                "configured-serial",
                {"progressiveChannelId": "101", "interlacedChannelId": "202"},
                Path("/private/app-debug.apk"),
                Path("/private/app-debug-androidTest.apk"),
            )

        self.assertEqual([item["name"] for item in methods], list(DEVICE["ACCEPTANCE_METHODS"]))
        self.assertTrue(all(item["outcome"] == "PASS" for item in methods))
        self.assertEqual(cleanup, "PASS")
        self.assertIn(
            ["adb", "-s", "configured-serial", "uninstall", DEVICE["TEST_PACKAGE"]],
            commands,
        )

    def test_acceptance_preparation_fails_closed_for_wrong_package_query_output(self) -> None:
        for wrong_output in (
            "package:wrong.package\n",
            f"package:{DEVICE['TEST_PACKAGE']}\npackage:wrong.package\n",
        ):
            with self.subTest(wrong_output=wrong_output):
                package_queries = 0

                def fake_run(command: list[str], *, timeout_seconds: int):
                    nonlocal package_queries
                    if command == DEVICE["package_query_command"]("adb", "configured-serial"):
                        package_queries += 1
                        stdout = wrong_output if package_queries == 1 else ""
                        return subprocess.CompletedProcess(command, 0, stdout=stdout, stderr="")
                    return subprocess.CompletedProcess(command, 0, stdout="Success\n", stderr="")

                with patch.dict(
                    DEVICE["run_debug_acceptance"].__globals__,
                    {"run_bounded_redacted": fake_run},
                ):
                    methods, cleanup = DEVICE["run_debug_acceptance"](
                        "adb",
                        "configured-serial",
                        {"progressiveChannelId": "101", "interlacedChannelId": "202"},
                        Path("/private/app-debug.apk"),
                        Path("/private/app-debug-androidTest.apk"),
                    )

                self.assertEqual(
                    methods,
                    [{"name": "prepareInstrumentation", "outcome": "FAIL", "durationMs": 0}],
                )
                self.assertEqual(cleanup, "PASS")

    def test_acceptance_cleanup_fails_closed_for_wrong_package_query_output(self) -> None:
        package_queries = 0

        def fake_run(command: list[str], *, timeout_seconds: int):
            nonlocal package_queries
            if command == DEVICE["package_query_command"]("adb", "configured-serial"):
                package_queries += 1
                stdout = "" if package_queries == 1 else "package:wrong.package\n"
                return subprocess.CompletedProcess(command, 0, stdout=stdout, stderr="")
            if "instrument" in command:
                stdout = "OK (1 test)\nINSTRUMENTATION_CODE: -1\n"
            else:
                stdout = "Success\n"
            return subprocess.CompletedProcess(command, 0, stdout=stdout, stderr="")

        with patch.dict(
            DEVICE["run_debug_acceptance"].__globals__,
            {"run_bounded_redacted": fake_run},
        ):
            methods, cleanup = DEVICE["run_debug_acceptance"](
                "adb",
                "configured-serial",
                {"progressiveChannelId": "101", "interlacedChannelId": "202"},
                Path("/private/app-debug.apk"),
                Path("/private/app-debug-androidTest.apk"),
            )

        self.assertTrue(all(item["outcome"] == "PASS" for item in methods))
        self.assertEqual(cleanup, "FAIL")

    def test_acceptance_preparation_timeout_records_elapsed_time_and_cleanup_truthfully(self) -> None:
        package_queries = 0

        def fake_run(command: list[str], *, timeout_seconds: int):
            nonlocal package_queries
            if command == DEVICE["package_query_command"]("adb", "configured-serial"):
                package_queries += 1
                if package_queries == 1:
                    return None
                return subprocess.CompletedProcess(command, 0, stdout="", stderr="")
            return subprocess.CompletedProcess(command, 0, stdout="Success\n", stderr="")

        with patch.dict(
            DEVICE["run_debug_acceptance"].__globals__,
            {"run_bounded_redacted": fake_run},
        ), patch.object(
            DEVICE["time"],
            "monotonic",
            side_effect=[0.0, 1.0, 10.0, 40.0],
        ):
            methods, cleanup = DEVICE["run_debug_acceptance"](
                "adb",
                "configured-serial",
                {"progressiveChannelId": "101", "interlacedChannelId": "202"},
                Path("/private/app-debug.apk"),
                Path("/private/app-debug-androidTest.apk"),
            )

        self.assertEqual(
            methods,
            [{"name": "prepareInstrumentation", "outcome": "TIMEOUT", "durationMs": 30_000}],
        )
        self.assertEqual(cleanup, "PASS")

    def test_acceptance_attestation_binds_commit_coordinate_and_both_apks(self) -> None:
        expected = {
            "schemaVersion": 1,
            "appCommit": "0123456789abcdef",
            "sdkCoordinate": "at.bernhardberger.tvheadend:sdk-media3:0.2.0",
            "appApkSha256": "a" * 64,
            "testApkSha256": "b" * 64,
        }

        self.assertEqual(
            DEVICE["acceptance_attestation_errors"](
                expected,
                revision="0123456789abcdef",
                sdk_coordinate="at.bernhardberger.tvheadend:sdk-media3:0.2.0",
                app_apk_sha256="a" * 64,
                test_apk_sha256="b" * 64,
            ),
            [],
        )
        self.assertTrue(
            DEVICE["acceptance_attestation_errors"](
                {**expected, "testApkSha256": "c" * 64},
                revision="0123456789abcdef",
                sdk_coordinate="at.bernhardberger.tvheadend:sdk-media3:0.2.0",
                app_apk_sha256="a" * 64,
                test_apk_sha256="b" * 64,
            ),
        )

    def test_acceptance_evidence_is_owner_only_and_contains_only_curated_fields(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "acceptance.json"
            DEVICE["write_acceptance_evidence"](
                output,
                revision="0123456789abcdef",
                sdk_coordinate="at.bernhardberger.tvheadend:sdk-media3:0.2.0",
                apk_sha256="a" * 64,
                target={
                    "name": "g10",
                    "role": "test",
                    "manufacturer": "TCL",
                    "model": "Smart TV Pro",
                    "device": "G10",
                    "product": "G10_4K_GB",
                },
                methods=[
                    {"name": "readMetadataProfilesAndArtwork", "outcome": "PASS", "durationMs": 123},
                ],
                cleanup="PASS",
            )

            evidence = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(
                set(evidence),
                {"schemaVersion", "appCommit", "sdkCoordinate", "apkSha256", "target", "methods", "cleanup", "result"},
            )
            self.assertEqual(evidence["result"], "PASS")
            self.assertNotIn("configured-serial", output.read_text(encoding="utf-8"))
            self.assertEqual(stat.S_IMODE(output.stat().st_mode), 0o600)

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
        self.assertIsNone(args.output)
        self.assertEqual(args.name, "current-screen")

    def test_default_screenshot_uses_revision_dirty_state_timestamp_and_name(self) -> None:
        output = default_screenshot_output(
            "Channels trailing clipping",
            revision="b5856e7abcde",
            dirty=True,
            now=datetime(2026, 7, 28, 21, 15, 30, tzinfo=timezone.utc),
        )

        self.assertEqual(
            output,
            ROOT
            / "captures/device/b5856e7abcde-dirty"
            / "20260728T211530Z-channels-trailing-clipping.png",
        )
        self.assertEqual(
            default_screenshot_output(
                "Guide scope tabs",
                revision="b5856e7abcde",
                dirty=False,
                now=datetime(2026, 7, 28, 21, 16, 2, tzinfo=timezone.utc),
            ),
            ROOT
            / "captures/device/b5856e7abcde"
            / "20260728T211602Z-guide-scope-tabs.png",
        )

    def test_screenshot_name_is_safe_and_has_a_useful_fallback(self) -> None:
        self.assertEqual(
            sanitize_screenshot_name("  Guide / scope tabs?!  "),
            "guide-scope-tabs",
        )
        self.assertEqual(sanitize_screenshot_name("---"), "current-screen")

    def test_screenshot_can_request_synthetic_video_backdrop(self) -> None:
        parser = DEVICE["build_parser"]()

        args = parser.parse_args(["screenshot", "--synthetic-video-backdrop"])

        self.assertTrue(args.synthetic_video_backdrop)

    def test_synthetic_video_backdrop_uses_bounded_package_broadcast(self) -> None:
        self.assertEqual(
            debug_video_backdrop_command(
                "adb",
                "test-device",
                "at.bernhardberger.tvhplayer",
                visible=True,
            ),
            [
                "adb",
                "-s",
                "test-device",
                "shell",
                "am",
                "broadcast",
                "-a",
                DEVICE["DEBUG_VIDEO_BACKDROP_ACTION"],
                "-p",
                "at.bernhardberger.tvhplayer",
                "--receiver-foreground",
                "--ez",
                DEVICE["DEBUG_VIDEO_BACKDROP_EXTRA"],
                "true",
            ],
        )

    def test_synthetic_video_backdrop_requires_debug_app_acknowledgement(self) -> None:
        self.assertTrue(
            debug_video_backdrop_applied("Broadcast completed: result=-1, data=null")
        )
        self.assertFalse(
            debug_video_backdrop_applied("Broadcast completed: result=0, data=null")
        )

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
                "allow-appliance-autostart",
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
            "allow-appliance-autostart",
        ):
            with self.subTest(action=action):
                self.assertIsNone(action_policy_error("test", action))

    def test_read_only_actions_are_allowed_for_every_role(self) -> None:
        for role in ("production", "test", "unclassified"):
            for action in (
                "connect",
                "doctor",
                "current",
                "package-info",
                "appliance-status",
            ):
                with self.subTest(role=role, action=action):
                    self.assertIsNone(action_policy_error(role, action))

    def test_appliance_status_is_a_bounded_read_only_action(self) -> None:
        parser = DEVICE["build_parser"]()

        args = parser.parse_args(["appliance-status"])

        self.assertEqual(args.action, "appliance-status")

    def test_auto_start_repair_requires_accessibility_confirmation(self) -> None:
        parser = DEVICE["build_parser"]()

        args = parser.parse_args(["allow-appliance-autostart"])

        self.assertFalse(args.confirm_user_enabled_accessibility)

    def test_release_install_is_available_for_exact_identity_deployment_targets(self) -> None:
        self.assertIsNone(action_policy_error("production", "install-release"))
        self.assertIsNone(action_policy_error("test", "install-release"))
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

        self.assertFalse(args.confirm_release_install)

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

    def test_acceptance_identity_read_is_bounded_and_does_not_announce_the_serial(self) -> None:
        ready = subprocess.CompletedProcess(
            args=["adb"],
            returncode=0,
            stdout="device\n",
            stderr="",
        )
        identity = subprocess.CompletedProcess(
            args=["adb"],
            returncode=0,
            stdout="TCL\nSmart TV Pro\nG10\nG10_4K_GB\n12\narmeabi-v7a\n",
            stderr="",
        )
        with patch.dict(
            DEVICE["read_acceptance_device_properties"].__globals__,
            {"run_bounded_redacted": Mock(side_effect=[ready, identity])},
        ), patch("builtins.print") as print_mock:
            properties = DEVICE["read_acceptance_device_properties"](
                "adb",
                "private-configured-serial",
            )

        self.assertEqual(properties["device"], "G10")
        print_mock.assert_not_called()

    def test_acceptance_identity_failure_discards_device_output_and_serial(self) -> None:
        failed = subprocess.CompletedProcess(
            args=["adb"],
            returncode=1,
            stdout="private-configured-serial",
            stderr="private device detail",
        )
        with patch.dict(
            DEVICE["read_acceptance_device_properties"].__globals__,
            {"run_bounded_redacted": Mock(return_value=failed)},
        ), patch("builtins.print") as print_mock:
            with self.assertRaisesRegex(SystemExit, "2"):
                DEVICE["read_acceptance_device_properties"](
                    "adb",
                    "private-configured-serial",
                )

        output = "\n".join(" ".join(map(str, call.args)) for call in print_mock.call_args_list)
        self.assertNotIn("private-configured-serial", output)
        self.assertNotIn("private device detail", output)
        self.assertIn("redacted", output)

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

    def test_screenshot_output_allows_only_the_ignored_capture_tree_in_repository(self) -> None:
        with self.assertRaisesRegex(SystemExit, "2"):
            resolve_screenshot_output(ROOT / "screenshot.png")
        with self.assertRaisesRegex(SystemExit, "2"):
            resolve_screenshot_output(Path("/tmp/tvheadend-player-screenshot.jpg"))

        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "screen.png"
            self.assertEqual(resolve_screenshot_output(output), output.resolve())

            project_root = Path(directory) / "project"
            capture_root = project_root / "captures/device"
            capture = capture_root / "b5856e7abcde-dirty/screen.png"
            with patch.dict(
                resolve_screenshot_output.__globals__,
                {"ROOT": project_root, "CAPTURE_ROOT": capture_root},
            ):
                self.assertEqual(resolve_screenshot_output(capture), capture.resolve())
            self.assertEqual(stat.S_IMODE((project_root / "captures").stat().st_mode), 0o700)
            self.assertEqual(stat.S_IMODE(capture_root.stat().st_mode), 0o700)
            self.assertEqual(stat.S_IMODE(capture.parent.stat().st_mode), 0o700)

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
