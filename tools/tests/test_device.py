from __future__ import annotations

import io
import runpy
import stat
import subprocess
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import Mock, call, patch


ROOT = Path(__file__).resolve().parents[2]
DEVICE = runpy.run_path(str(ROOT / "tools/device"), run_name="device_tool")
DEVICE_GLOBALS = DEVICE["main"].__globals__
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
package_installation_status = DEVICE["package_installation_status"]
run_channel_guide_check = DEVICE["run_channel_guide_check"]
run_channel_guide_configured_connection_check = DEVICE[
    "run_channel_guide_configured_connection_check"
]
run_timeshift_command_check = DEVICE["run_timeshift_command_check"]
run_foreground_playback_lifecycle_check = DEVICE[
    "run_foreground_playback_lifecycle_check"
]
run_connection_credential_check = DEVICE["run_connection_credential_check"]
run = DEVICE["run"]
read_device_properties = DEVICE["read_device_properties"]
key_events = DEVICE["KEY_EVENTS"]

RUNNER_SUCCESS = subprocess.CompletedProcess(
    ["adb"],
    0,
    stdout=(
        "INSTRUMENTATION_STATUS_CODE: 0\n"
        "INSTRUMENTATION_RESULT: stream=\n"
        "OK (2 tests)\n"
        "INSTRUMENTATION_CODE: -1\n"
    ),
    stderr="",
)
CHANNEL_GUIDE_RUNNER_SUCCESS = subprocess.CompletedProcess(
    ["adb"],
    0,
    stdout=(
        "at.bernhardberger.tvhplayer.ui.ChannelGuideDeviceAcceptanceTest:."
        "INSTRUMENTATION_STATUS: configuredPasswordPresent=true\n"
        "INSTRUMENTATION_STATUS: configuredUsernamePresent=true\n"
        "INSTRUMENTATION_STATUS_CODE: 2\n"
        "INSTRUMENTATION_RESULT: stream=\n"
        "OK (2 tests)\n"
        "INSTRUMENTATION_CODE: -1\n"
    ),
    stderr="",
)
CONFIGURED_CONNECTION_RUNNER_SUCCESS = subprocess.CompletedProcess(
    ["adb"],
    0,
    stdout=(
        "INSTRUMENTATION_STATUS: configuredUsernamePresent=true\n"
        "INSTRUMENTATION_STATUS: configuredPasswordPresent=true\n"
        "INSTRUMENTATION_STATUS_CODE: 2\n"
        "INSTRUMENTATION_RESULT: stream=\n"
        "OK (1 test)\n"
        "INSTRUMENTATION_CODE: -1\n"
    ),
    stderr="",
)
CLEAR_SUCCESS = subprocess.CompletedProcess(
    ["adb"],
    0,
    stdout="Success\n",
    stderr="",
)
class DevicePolicyTest(unittest.TestCase):
    def test_product_identity_defaults_are_current(self) -> None:
        self.assertEqual(DEVICE["LOCAL_CONFIG"].name, ".tvhplayer-device.json")
        self.assertEqual(DEVICE["DEFAULT_PACKAGE"], "at.bernhardberger.tvhplayer")
        self.assertEqual(
            DEVICE["DEFAULT_CREDENTIAL_FILE"].name,
            ".tvhplayer-credentials.json",
        )

    def test_run_timeout_is_a_controlled_failure(self) -> None:
        with patch(
            "subprocess.run",
            side_effect=subprocess.TimeoutExpired(["adb"], 1),
        ):
            with self.assertRaisesRegex(SystemExit, "2"):
                run(["adb"], timeout_seconds=1, announce=False)

    def test_connection_credential_check_force_stops_before_instrumentation(self) -> None:
        completed = subprocess.CompletedProcess(["adb"], 0, stdout="", stderr="")
        instrumentation = subprocess.CompletedProcess(
            ["adb"],
            0,
            stdout=(
                "instrumentation:at.bernhardberger.tvhplayer.test/"
                "androidx.test.runner.AndroidJUnitRunner "
                "(target=at.bernhardberger.tvhplayer)\n"
            ),
            stderr="",
        )
        connection_runner_success = subprocess.CompletedProcess(
            ["adb"],
            0,
            stdout=(
                "INSTRUMENTATION_STATUS_CODE: 0\n"
                "OK (4 tests)\n"
                "INSTRUMENTATION_CODE: -1\n"
            ),
            stderr="benign adb diagnostic\nINSTRUMENTATION_CODE: 0\n",
        )
        run_mock = Mock(
            side_effect=[
                completed,
                completed,
                instrumentation,
                completed,
                connection_runner_success,
                completed,
                completed,
            ]
        )
        with tempfile.TemporaryDirectory() as directory:
            test_apk = Path(directory) / "app-debug-androidTest.apk"
            test_apk.touch()
            with patch.dict(
                DEVICE_GLOBALS,
                {
                    "DEFAULT_TEST_APK": test_apk,
                    "run": run_mock,
                    "package_installation_status": Mock(side_effect=[False, False]),
                },
            ):
                run_connection_credential_check(
                    "adb",
                    "test-device",
                    "at.bernhardberger.tvhplayer",
                )

        commands = [call.args[0] for call in run_mock.call_args_list]
        runner_command = next(command for command in commands if "instrument" in command)
        identity_command = next(command for command in commands if "instrumentation" in command)
        force_stop_command = [
            "adb",
            "-s",
            "test-device",
            "shell",
            "am",
            "force-stop",
            "at.bernhardberger.tvhplayer",
        ]
        identity_index = commands.index(identity_command)
        runner_index = commands.index(runner_command)
        force_stop_indexes = [
            index for index, command in enumerate(commands) if command == force_stop_command
        ]
        self.assertEqual(len(force_stop_indexes), 2)
        self.assertLess(identity_index, force_stop_indexes[0])
        self.assertLess(force_stop_indexes[0], runner_index)
        self.assertIn(DEVICE["CONNECTION_CREDENTIAL_TEST"], runner_command)
        self.assertEqual(commands[-2][-2:], ["uninstall", "at.bernhardberger.tvhplayer.test"])
        self.assertEqual(commands[-1][-2:], ["force-stop", "at.bernhardberger.tvhplayer"])

    def test_connection_credential_check_requires_all_four_tests(self) -> None:
        completed = subprocess.CompletedProcess(["adb"], 0, stdout="", stderr="")
        instrumentation = subprocess.CompletedProcess(
            ["adb"],
            0,
            stdout=(
                "instrumentation:at.bernhardberger.tvhplayer.test/"
                "androidx.test.runner.AndroidJUnitRunner "
                "(target=at.bernhardberger.tvhplayer)\n"
            ),
            stderr="",
        )
        runner_success = subprocess.CompletedProcess(
            ["adb"],
            0,
            stdout="OK (3 tests)\nINSTRUMENTATION_CODE: -1\n",
            stderr="",
        )
        run_mock = Mock(
            side_effect=[
                completed,
                completed,
                instrumentation,
                completed,
                runner_success,
                completed,
                completed,
                CLEAR_SUCCESS,
            ]
        )
        with tempfile.TemporaryDirectory() as directory:
            test_apk = Path(directory) / "app-debug-androidTest.apk"
            test_apk.touch()
            with patch.dict(
                DEVICE_GLOBALS,
                {
                    "DEFAULT_TEST_APK": test_apk,
                    "run": run_mock,
                    "package_installation_status": Mock(side_effect=[False, False]),
                },
            ):
                with self.assertRaisesRegex(SystemExit, "2"):
                    run_connection_credential_check(
                        "adb",
                        "test-device",
                        "at.bernhardberger.tvhplayer",
                    )
        commands = [call.args[0] for call in run_mock.call_args_list]
        self.assertEqual(commands[-2][-2:], ["uninstall", "at.bernhardberger.tvhplayer.test"])
        self.assertEqual(commands[-1][-2:], ["force-stop", "at.bernhardberger.tvhplayer"])

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

    def test_bounded_remote_navigation_keys_are_available(self) -> None:
        self.assertEqual(key_events["up"], "KEYCODE_DPAD_UP")
        self.assertEqual(key_events["down"], "KEYCODE_DPAD_DOWN")
        self.assertEqual(key_events["left"], "KEYCODE_DPAD_LEFT")
        self.assertEqual(key_events["right"], "KEYCODE_DPAD_RIGHT")
        self.assertEqual(key_events["center"], "KEYCODE_DPAD_CENTER")

    def test_parser_accepts_ordered_keys_repeat_delay_and_long_press(self) -> None:
        parser = DEVICE["build_parser"]()

        sequence = parser.parse_args(
            ["keys", "down", "down", "right", "center", "--delay-ms", "250"]
        )
        repeated = parser.parse_args(
            ["key", "down", "--repeat", "3", "--delay-ms", "400", "--long-press"]
        )

        self.assertEqual(sequence.names, ["down", "down", "right", "center"])
        self.assertEqual(sequence.delay_ms, 250)
        self.assertFalse(sequence.long_press)
        self.assertEqual(repeated.repeat, 3)
        self.assertEqual(repeated.delay_ms, 400)
        self.assertTrue(repeated.long_press)

    def test_legacy_key_defaults_and_inclusive_bounds_are_preserved(self) -> None:
        parser = DEVICE["build_parser"]()

        legacy = parser.parse_args(["key", "down"])
        lower_bounds = parser.parse_args(
            ["key", "down", "--repeat", "1", "--delay-ms", "0"]
        )
        upper_bounds = parser.parse_args(
            ["key", "down", "--repeat", "100", "--delay-ms", "5000"]
        )

        self.assertEqual(legacy.repeat, 1)
        self.assertEqual(legacy.delay_ms, 300)
        self.assertFalse(legacy.long_press)
        self.assertEqual(lower_bounds.repeat, 1)
        self.assertEqual(lower_bounds.delay_ms, 0)
        self.assertEqual(upper_bounds.repeat, 100)
        self.assertEqual(upper_bounds.delay_ms, 5000)

    def test_parser_rejects_unbounded_repeat_and_delay(self) -> None:
        parser = DEVICE["build_parser"]()

        for arguments in (
            ["key", "down", "--repeat", "0"],
            ["key", "down", "--repeat", "101"],
            ["keys", "down", "right", "--delay-ms", "-1"],
            ["keys", "down", "right", "--delay-ms", "5001"],
        ):
            with self.subTest(arguments=arguments):
                with patch.object(DEVICE["sys"], "stderr"):
                    with self.assertRaises(SystemExit):
                        parser.parse_args(arguments)

    def test_key_sequence_limit_is_rejected_before_device_configuration(self) -> None:
        load_config_mock = Mock()

        with (
            patch.dict(DEVICE_GLOBALS, {"load_local_config": load_config_mock}),
            patch.object(
                DEVICE["sys"],
                "argv",
                ["device", "keys", *(["down"] * 101)],
            ),
            patch.object(DEVICE["sys"], "stderr"),
        ):
            with self.assertRaises(SystemExit):
                DEVICE["main"]()

        load_config_mock.assert_not_called()

    def test_key_events_are_sent_in_order_with_compact_output(self) -> None:
        run_mock = Mock()

        with (
            patch.dict(DEVICE_GLOBALS, {"run": run_mock}),
            patch.object(DEVICE["time"], "sleep") as sleep_mock,
            patch("builtins.print") as print_mock,
        ):
            DEVICE["send_key_events"](
                "adb",
                "private-device",
                ["down", "down", "right", "center"],
                delay_ms=250,
                long_press=False,
            )

        self.assertEqual(
            run_mock.call_args_list,
            [
                call(
                    [
                        "adb",
                        "-s",
                        "private-device",
                        "shell",
                        "input",
                        "keyevent",
                        key_events[name],
                    ],
                    announce=False,
                )
                for name in ("down", "down", "right", "center")
            ],
        )
        self.assertEqual(
            sleep_mock.call_args_list,
            [call(0.25), call(0.25), call(0.25)],
        )
        print_mock.assert_called_once_with("sentKeyEvents=4")

    def test_long_press_uses_android_keyevent_longpress_flag(self) -> None:
        run_mock = Mock()

        with patch.dict(DEVICE_GLOBALS, {"run": run_mock}), patch("builtins.print"):
            DEVICE["send_key_events"](
                "adb",
                "private-device",
                ["center"],
                delay_ms=300,
                long_press=True,
            )

        run_mock.assert_called_once_with(
            [
                "adb",
                "-s",
                "private-device",
                "shell",
                "input",
                "keyevent",
                "--longpress",
                "KEYCODE_DPAD_CENTER",
            ],
            announce=False,
        )

    def test_key_sequence_stops_without_success_output_after_a_failed_event(self) -> None:
        run_mock = Mock(side_effect=[None, SystemExit(2)])

        with (
            patch.dict(DEVICE_GLOBALS, {"run": run_mock}),
            patch.object(DEVICE["time"], "sleep") as sleep_mock,
            patch("builtins.print") as print_mock,
        ):
            with self.assertRaises(SystemExit):
                DEVICE["send_key_events"](
                    "adb",
                    "private-device",
                    ["down", "right", "center"],
                    delay_ms=250,
                    long_press=False,
                )

        self.assertEqual(run_mock.call_count, 2)
        sleep_mock.assert_called_once_with(0.25)
        print_mock.assert_not_called()

    def test_batched_key_main_checks_device_identity_once(self) -> None:
        properties = {
            "manufacturer": "TCL",
            "model": "Smart TV Pro",
            "device": "G10",
            "product": "G10_4K_GB",
            "android": "12",
            "abis": "armeabi-v7a",
        }
        ready_mock = Mock()
        properties_mock = Mock(return_value=properties)
        identity_mock = Mock()
        send_mock = Mock()

        with (
            patch.dict(
                DEVICE_GLOBALS,
                {
                    "load_local_config": Mock(
                        return_value=(
                            "g10",
                            {
                                "serial": "private-device",
                                "role": "test",
                                "expected_manufacturer": "TCL",
                                "expected_model": "Smart TV Pro",
                                "expected_device": "G10",
                                "expected_product": "G10_4K_GB",
                            },
                        )
                    ),
                    "require_device_ready": ready_mock,
                    "read_device_properties": properties_mock,
                    "verify_configured_identity": identity_mock,
                    "send_key_events": send_mock,
                },
            ),
            patch.object(DEVICE["shutil"], "which", return_value="adb"),
            patch.object(
                DEVICE["sys"],
                "argv",
                ["device", "keys", "down", "down", "right", "center"],
            ),
        ):
            result = DEVICE["main"]()

        self.assertEqual(result, 0)
        ready_mock.assert_called_once_with("adb", "private-device", announce=False)
        properties_mock.assert_called_once_with("adb", "private-device", announce=False)
        identity_mock.assert_called_once()
        send_mock.assert_called_once_with(
            "adb",
            "private-device",
            ["down", "down", "right", "center"],
            delay_ms=300,
            long_press=False,
        )

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
                "keys",
                "screenshot",
                "provision-test-credentials",
                "channel-guide-check",
                "timeshift-command-check",
                "foreground-playback-lifecycle-check",
                "navigation-back-check",
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
            "keys",
            "screenshot",
            "provision-test-credentials",
            "channel-guide-check",
            "timeshift-command-check",
            "foreground-playback-lifecycle-check",
            "navigation-back-check",
            "allow-appliance-autostart",
        ):
            with self.subTest(action=action):
                self.assertIsNone(action_policy_error("test", action))

    def test_channel_guide_check_is_a_bounded_test_device_action(self) -> None:
        parser = DEVICE["build_parser"]()

        args = parser.parse_args(["channel-guide-check"])

        self.assertEqual(args.action, "channel-guide-check")
        self.assertFalse(hasattr(args, "test_apk"))

    def test_configured_connection_check_selects_only_the_named_acceptance_test(self) -> None:
        parser = DEVICE["build_parser"]()

        args = parser.parse_args(["channel-guide-configured-connection-check"])

        self.assertEqual(args.action, "channel-guide-configured-connection-check")
        self.assertEqual(
            DEVICE["CHANNEL_GUIDE_CONFIGURED_CONNECTION_TEST"],
            "at.bernhardberger.tvhplayer.ui.ChannelGuideDeviceAcceptanceTest"
            "#configuredConnectionUsesRedactedFieldPresenceSemantics",
        )
        self.assertFalse(hasattr(args, "test_apk"))

    def test_navigation_back_check_is_bounded_and_reports_fixed_evidence(self) -> None:
        parser = DEVICE["build_parser"]()
        args = parser.parse_args(["navigation-back-check"])
        self.assertEqual(args.action, "navigation-back-check")
        self.assertFalse(hasattr(args, "test_apk"))

        completed = subprocess.CompletedProcess(["adb"], 0, stdout="", stderr="")
        instrumentation = subprocess.CompletedProcess(
            ["adb"],
            0,
            stdout=(
                "instrumentation:at.bernhardberger.tvhplayer.test/"
                "androidx.test.runner.AndroidJUnitRunner "
                "(target=at.bernhardberger.tvhplayer)\n"
            ),
            stderr="",
        )
        runner_success = subprocess.CompletedProcess(
            ["adb"],
            0,
            stdout=(
                "INSTRUMENTATION_STATUS: guideRailFocusLatencyMs=37\n"
                "INSTRUMENTATION_STATUS: sideRailRequestTrace=epg>recordings>epg>channels>channels\n"
                "INSTRUMENTATION_STATUS: awaitRootDestinationRootBackCount=0\n"
                "INSTRUMENTATION_STATUS: backDispatchApiLevel=36\n"
                "INSTRUMENTATION_STATUS: nestedBackOwnerTrace=nested>player(0)\n"
                "INSTRUMENTATION_STATUS: rootBackOwnerTrace=shell>root\n"
                "INSTRUMENTATION_STATUS: rapidBackActionTrace=confirmation>info>player\n"
                "INSTRUMENTATION_STATUS: channelsVisibleRows=6\n"
                "INSTRUMENTATION_STATUS: channelsRecomposedRows=6\n"
                "INSTRUMENTATION_STATUS: channelsMaxRowRecompositions=1\n"
                "INSTRUMENTATION_RESULT: stream=\n"
                "OK (84 tests)\n"
                "INSTRUMENTATION_CODE: -1\n"
            ),
            stderr="",
        )
        run_mock = Mock(
            side_effect=[
                completed,
                completed,
                instrumentation,
                completed,
                runner_success,
                completed,
                completed,
            ]
        )
        output = io.StringIO()
        with tempfile.TemporaryDirectory() as directory:
            test_apk = Path(directory) / "app-debug-androidTest.apk"
            test_apk.touch()
            with patch.dict(
                DEVICE_GLOBALS,
                {
                    "DEFAULT_TEST_APK": test_apk,
                    "run": run_mock,
                    "package_installation_status": Mock(side_effect=[False, False]),
                },
            ), redirect_stdout(output):
                DEVICE["run_navigation_back_check"](
                    "adb",
                    "test-device",
                    "at.bernhardberger.tvhplayer",
                )

        commands = [call.args[0] for call in run_mock.call_args_list]
        self.assertIn(
            [
                "adb",
                "-s",
                "test-device",
                "shell",
                "am",
                "instrument",
                "-w",
                "-e",
                "class",
                DEVICE["NAVIGATION_BACK_TESTS"],
                "at.bernhardberger.tvhplayer.test/"
                "androidx.test.runner.AndroidJUnitRunner",
            ],
            commands,
        )
        self.assertIn("guideRailFocusLatencyMs=37", output.getvalue())
        self.assertIn(
            "at.bernhardberger.tvhplayer.ui.player.LiveProgrammeInfoOverlayTest",
            DEVICE["NAVIGATION_BACK_TESTS"],
        )
        self.assertIn(
            "at.bernhardberger.tvhplayer.ui.player.PlayerTimelineTruthfulnessTest",
            DEVICE["NAVIGATION_BACK_TESTS"],
        )
        self.assertIn("backDispatchApiLevel=36", output.getvalue())
        self.assertIn("rapidBackActionTrace=confirmation>info>player", output.getvalue())
        self.assertIn("channelsRecomposedRows=6", output.getvalue())
        runner_call = next(
            invocation
            for invocation in run_mock.call_args_list
            if "instrument" in invocation.args[0]
        )
        self.assertEqual(
            runner_call.kwargs["timeout_seconds"],
            DEVICE["INSTRUMENTATION_TIMEOUT_SECONDS"],
        )
        self.assertEqual(commands[-2][-2:], ["uninstall", "at.bernhardberger.tvhplayer.test"])
        self.assertEqual(commands[-1][-2:], ["force-stop", "at.bernhardberger.tvhplayer"])

    def test_navigation_back_check_rejects_incomplete_runner_and_cleans_up(self) -> None:
        completed = subprocess.CompletedProcess(["adb"], 0, stdout="", stderr="")
        instrumentation = subprocess.CompletedProcess(
            ["adb"],
            0,
            stdout=(
                "instrumentation:at.bernhardberger.tvhplayer.test/"
                "androidx.test.runner.AndroidJUnitRunner "
                "(target=at.bernhardberger.tvhplayer)\n"
            ),
            stderr="",
        )
        incomplete_runner = subprocess.CompletedProcess(
            ["adb"],
            0,
            stdout="INSTRUMENTATION_RESULT: stream=\nOK (27 tests)\n",
            stderr="",
        )
        run_mock = Mock(
            side_effect=[
                completed,
                completed,
                instrumentation,
                completed,
                incomplete_runner,
                completed,
                completed,
            ]
        )
        with tempfile.TemporaryDirectory() as directory:
            test_apk = Path(directory) / "app-debug-androidTest.apk"
            test_apk.touch()
            with patch.dict(
                DEVICE_GLOBALS,
                {
                    "DEFAULT_TEST_APK": test_apk,
                    "run": run_mock,
                    "package_installation_status": Mock(side_effect=[False, False]),
                },
            ):
                with self.assertRaisesRegex(SystemExit, "2"):
                    DEVICE["run_navigation_back_check"](
                        "adb",
                        "test-device",
                        "at.bernhardberger.tvhplayer",
                    )

        commands = [invocation.args[0] for invocation in run_mock.call_args_list]
        self.assertEqual(commands[-2][-2:], ["uninstall", "at.bernhardberger.tvhplayer.test"])
        self.assertEqual(commands[-1][-2:], ["force-stop", "at.bernhardberger.tvhplayer"])

    def test_timeshift_command_check_is_a_bounded_test_device_action(self) -> None:
        parser = DEVICE["build_parser"]()

        args = parser.parse_args(["timeshift-command-check"])

        self.assertEqual(args.action, "timeshift-command-check")
        self.assertFalse(hasattr(args, "test_apk"))

    def test_timeshift_command_check_uses_fixed_instrumentation_and_cleans_up(self) -> None:
        completed = subprocess.CompletedProcess(["adb"], 0, stdout="", stderr="")
        instrumentation = subprocess.CompletedProcess(
            ["adb"],
            0,
            stdout=(
                "instrumentation:at.bernhardberger.tvhplayer.test/"
                "androidx.test.runner.AndroidJUnitRunner "
                "(target=at.bernhardberger.tvhplayer)\n"
            ),
            stderr="",
        )
        run_mock = Mock(
            side_effect=[
                completed,
                completed,
                instrumentation,
                completed,
                RUNNER_SUCCESS,
                completed,
                completed,
            ]
        )
        with tempfile.TemporaryDirectory() as directory:
            test_apk = Path(directory) / "app-debug-androidTest.apk"
            test_apk.touch()
            with patch.dict(
                DEVICE_GLOBALS,
                {
                    "DEFAULT_TEST_APK": test_apk,
                    "run": run_mock,
                    "package_installation_status": Mock(side_effect=[False, False]),
                },
            ):
                run_timeshift_command_check(
                    "adb",
                    "test-device",
                    "at.bernhardberger.tvhplayer",
                )

        commands = [call.args[0] for call in run_mock.call_args_list]
        self.assertIn(
            [
                "adb",
                "-s",
                "test-device",
                "shell",
                "am",
                "instrument",
                "-w",
                "-e",
                "class",
                DEVICE["TIMESHIFT_COMMAND_TEST"],
                "-e",
                DEVICE["EXTERNAL_TARGET_ACCEPTANCE_ARGUMENT"],
                DEVICE["EXTERNAL_TARGET_ACCEPTANCE_ENABLED"],
                "at.bernhardberger.tvhplayer.test/"
                "androidx.test.runner.AndroidJUnitRunner",
            ],
            commands,
        )
        self.assertEqual(commands[-2][-2:], ["uninstall", "at.bernhardberger.tvhplayer.test"])
        self.assertEqual(commands[-1][-2:], ["force-stop", "at.bernhardberger.tvhplayer"])

    def test_timeshift_command_check_rejects_runner_failure_with_zero_exit(self) -> None:
        completed = subprocess.CompletedProcess(["adb"], 0, stdout="", stderr="")
        instrumentation = subprocess.CompletedProcess(
            ["adb"],
            0,
            stdout=(
                "instrumentation:at.bernhardberger.tvhplayer.test/"
                "androidx.test.runner.AndroidJUnitRunner "
                "(target=at.bernhardberger.tvhplayer)\n"
            ),
            stderr="",
        )
        runner_failure = subprocess.CompletedProcess(
            ["adb"],
            0,
            stdout=(
                "INSTRUMENTATION_STATUS: test=terrestrialTimeshiftCommandsPreserveTargetContinuity\n"
                "INSTRUMENTATION_STATUS_CODE: -2\n"
                "INSTRUMENTATION_RESULT: stream=\n"
                "FAILURES!!!\n"
                "Tests run: 2,  Failures: 1\n"
                "INSTRUMENTATION_CODE: -1\n"
            ),
            stderr="",
        )
        run_mock = Mock(
            side_effect=[
                completed,
                completed,
                instrumentation,
                completed,
                runner_failure,
                completed,
                completed,
            ]
        )
        with tempfile.TemporaryDirectory() as directory:
            test_apk = Path(directory) / "app-debug-androidTest.apk"
            test_apk.touch()
            with patch.dict(
                DEVICE_GLOBALS,
                {
                    "DEFAULT_TEST_APK": test_apk,
                    "run": run_mock,
                    "package_installation_status": Mock(side_effect=[False, False]),
                },
            ):
                with self.assertRaisesRegex(SystemExit, "2"):
                    run_timeshift_command_check(
                        "adb",
                        "test-device",
                        "at.bernhardberger.tvhplayer",
                    )

        commands = [call.args[0] for call in run_mock.call_args_list]
        self.assertEqual(commands[-2][-2:], ["uninstall", "at.bernhardberger.tvhplayer.test"])
        self.assertEqual(commands[-1][-2:], ["force-stop", "at.bernhardberger.tvhplayer"])

    def test_external_target_checks_reject_skipped_or_ignored_tests(self) -> None:
        completed = subprocess.CompletedProcess(["adb"], 0, stdout="", stderr="")
        instrumentation = subprocess.CompletedProcess(
            ["adb"],
            0,
            stdout=(
                "instrumentation:at.bernhardberger.tvhplayer.test/"
                "androidx.test.runner.AndroidJUnitRunner "
                "(target=at.bernhardberger.tvhplayer)\n"
            ),
            stderr="",
        )
        checks = (
            (run_channel_guide_check, 2, True),
            (run_timeshift_command_check, 2, False),
            (run_foreground_playback_lifecycle_check, 5, False),
        )
        for check, test_count, clears_target_data in checks:
            for status_code in (-3, -4):
                with self.subTest(check=check.__name__, status_code=status_code):
                    runner_skip = subprocess.CompletedProcess(
                        ["adb"],
                        0,
                        stdout=(
                            f"INSTRUMENTATION_STATUS_CODE: {status_code}\n"
                            f"OK ({test_count} tests)\n"
                            "INSTRUMENTATION_CODE: -1\n"
                        ),
                        stderr="",
                    )
                    side_effect = [
                        completed,
                        completed,
                        instrumentation,
                        completed,
                        runner_skip,
                        completed,
                        completed,
                    ]
                    if clears_target_data:
                        side_effect.append(CLEAR_SUCCESS)
                    run_mock = Mock(side_effect=side_effect)
                    with tempfile.TemporaryDirectory() as directory:
                        test_apk = Path(directory) / "app-debug-androidTest.apk"
                        test_apk.touch()
                        with patch.dict(
                            DEVICE_GLOBALS,
                            {
                                "DEFAULT_TEST_APK": test_apk,
                                "run": run_mock,
                                "package_installation_status": Mock(
                                    side_effect=[False, False]
                                ),
                            },
                        ):
                            with self.assertRaisesRegex(SystemExit, "2"):
                                check(
                                    "adb",
                                    "test-device",
                                    "at.bernhardberger.tvhplayer",
                                )

    def test_timeshift_command_check_requires_both_acceptance_tests(self) -> None:
        completed = subprocess.CompletedProcess(["adb"], 0, stdout="", stderr="")
        instrumentation = subprocess.CompletedProcess(
            ["adb"],
            0,
            stdout=(
                "instrumentation:at.bernhardberger.tvhplayer.test/"
                "androidx.test.runner.AndroidJUnitRunner "
                "(target=at.bernhardberger.tvhplayer)\n"
            ),
            stderr="",
        )
        for test_count in (0, 1):
            with self.subTest(test_count=test_count):
                noun = "test" if test_count == 1 else "tests"
                runner_success = subprocess.CompletedProcess(
                    ["adb"],
                    0,
                    stdout=(
                        "INSTRUMENTATION_RESULT: stream=\n"
                        f"OK ({test_count} {noun})\n"
                        "INSTRUMENTATION_CODE: -1\n"
                    ),
                    stderr="",
                )
                run_mock = Mock(
                    side_effect=[
                        completed,
                        completed,
                        instrumentation,
                        completed,
                        runner_success,
                        completed,
                        completed,
                    ]
                )
                with tempfile.TemporaryDirectory() as directory:
                    test_apk = Path(directory) / "app-debug-androidTest.apk"
                    test_apk.touch()
                    with patch.dict(
                        DEVICE_GLOBALS,
                        {
                            "DEFAULT_TEST_APK": test_apk,
                            "run": run_mock,
                            "package_installation_status": Mock(side_effect=[False, False]),
                        },
                    ):
                        with self.assertRaisesRegex(SystemExit, "2"):
                            run_timeshift_command_check(
                                "adb",
                                "test-device",
                                "at.bernhardberger.tvhplayer",
                            )

                commands = [call.args[0] for call in run_mock.call_args_list]
                self.assertEqual(commands[-2][-2:], ["uninstall", "at.bernhardberger.tvhplayer.test"])
                self.assertEqual(commands[-1][-2:], ["force-stop", "at.bernhardberger.tvhplayer"])

    def test_foreground_playback_lifecycle_check_uses_fixed_instrumentation_and_cleans_up(self) -> None:
        completed = subprocess.CompletedProcess(["adb"], 0, stdout="", stderr="")
        instrumentation = subprocess.CompletedProcess(
            ["adb"],
            0,
            stdout=(
                "instrumentation:at.bernhardberger.tvhplayer.test/"
                "androidx.test.runner.AndroidJUnitRunner "
                "(target=at.bernhardberger.tvhplayer)\n"
            ),
            stderr="",
        )
        runner_success = subprocess.CompletedProcess(
            ["adb"],
            0,
            stdout=(
                "INSTRUMENTATION_RESULT: stream=\n"
                "OK (5 tests)\n"
                "INSTRUMENTATION_CODE: -1\n"
            ),
            stderr="",
        )
        run_mock = Mock(
            side_effect=[
                completed,
                completed,
                instrumentation,
                completed,
                runner_success,
                completed,
                completed,
            ]
        )
        with tempfile.TemporaryDirectory() as directory:
            test_apk = Path(directory) / "app-debug-androidTest.apk"
            test_apk.touch()
            with patch.dict(
                DEVICE_GLOBALS,
                {
                    "DEFAULT_TEST_APK": test_apk,
                    "run": run_mock,
                    "package_installation_status": Mock(side_effect=[False, False]),
                },
            ):
                run_foreground_playback_lifecycle_check(
                    "adb",
                    "test-device",
                    "at.bernhardberger.tvhplayer",
                )

        commands = [call.args[0] for call in run_mock.call_args_list]
        self.assertIn(
            [
                "adb",
                "-s",
                "test-device",
                "shell",
                "am",
                "instrument",
                "-w",
                "-e",
                "class",
                DEVICE["FOREGROUND_PLAYBACK_LIFECYCLE_TEST"],
                "-e",
                DEVICE["EXTERNAL_TARGET_ACCEPTANCE_ARGUMENT"],
                DEVICE["EXTERNAL_TARGET_ACCEPTANCE_ENABLED"],
                "at.bernhardberger.tvhplayer.test/"
                "androidx.test.runner.AndroidJUnitRunner",
            ],
            commands,
        )
        self.assertEqual(commands[-2][-2:], ["uninstall", "at.bernhardberger.tvhplayer.test"])
        self.assertEqual(commands[-1][-2:], ["force-stop", "at.bernhardberger.tvhplayer"])

    def test_channel_guide_check_requires_both_acceptance_tests(self) -> None:
        completed = subprocess.CompletedProcess(["adb"], 0, stdout="", stderr="")
        instrumentation = subprocess.CompletedProcess(
            ["adb"],
            0,
            stdout=(
                "instrumentation:at.bernhardberger.tvhplayer.test/"
                "androidx.test.runner.AndroidJUnitRunner "
                "(target=at.bernhardberger.tvhplayer)\n"
            ),
            stderr="",
        )
        runner_success = subprocess.CompletedProcess(
            ["adb"],
            0,
            stdout=(
                "INSTRUMENTATION_STATUS: configuredUsernamePresent=true\n"
                "INSTRUMENTATION_STATUS: configuredPasswordPresent=true\n"
                "INSTRUMENTATION_STATUS_CODE: 2\n"
                "INSTRUMENTATION_RESULT: stream=\n"
                "OK (1 test)\n"
                "INSTRUMENTATION_CODE: -1\n"
            ),
            stderr="",
        )
        run_mock = Mock(
            side_effect=[
                completed,
                completed,
                instrumentation,
                completed,
                runner_success,
                completed,
                completed,
                CLEAR_SUCCESS,
            ]
        )
        with tempfile.TemporaryDirectory() as directory:
            test_apk = Path(directory) / "app-debug-androidTest.apk"
            test_apk.touch()
            with patch.dict(
                DEVICE_GLOBALS,
                {
                    "DEFAULT_TEST_APK": test_apk,
                    "run": run_mock,
                    "package_installation_status": Mock(side_effect=[False, False]),
                },
            ):
                with self.assertRaisesRegex(SystemExit, "2"):
                    run_channel_guide_check(
                        "adb",
                        "test-device",
                        "at.bernhardberger.tvhplayer",
                    )

    def test_foreground_playback_lifecycle_check_requires_all_acceptance_tests(self) -> None:
        completed = subprocess.CompletedProcess(["adb"], 0, stdout="", stderr="")
        instrumentation = subprocess.CompletedProcess(
            ["adb"],
            0,
            stdout=(
                "instrumentation:at.bernhardberger.tvhplayer.test/"
                "androidx.test.runner.AndroidJUnitRunner "
                "(target=at.bernhardberger.tvhplayer)\n"
            ),
            stderr="",
        )
        runner_success = subprocess.CompletedProcess(
            ["adb"],
            0,
            stdout=(
                "INSTRUMENTATION_RESULT: stream=\n"
                "OK (4 tests)\n"
                "INSTRUMENTATION_CODE: -1\n"
            ),
            stderr="",
        )
        run_mock = Mock(
            side_effect=[
                completed,
                completed,
                instrumentation,
                completed,
                runner_success,
                completed,
                completed,
            ]
        )
        with tempfile.TemporaryDirectory() as directory:
            test_apk = Path(directory) / "app-debug-androidTest.apk"
            test_apk.touch()
            with patch.dict(
                DEVICE_GLOBALS,
                {
                    "DEFAULT_TEST_APK": test_apk,
                    "run": run_mock,
                    "package_installation_status": Mock(side_effect=[False, False]),
                },
            ):
                with self.assertRaisesRegex(SystemExit, "2"):
                    run_foreground_playback_lifecycle_check(
                        "adb",
                        "test-device",
                        "at.bernhardberger.tvhplayer",
                    )

        commands = [call.args[0] for call in run_mock.call_args_list]
        self.assertEqual(commands[-2][-2:], ["uninstall", "at.bernhardberger.tvhplayer.test"])
        self.assertEqual(commands[-1][-2:], ["force-stop", "at.bernhardberger.tvhplayer"])

    def test_timeshift_command_check_cleans_up_for_invalid_test_apk(self) -> None:
        completed = subprocess.CompletedProcess(["adb"], 0, stdout="", stderr="")
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "target.apk"
            target.touch()
            invalid_apks = (
                Path(directory) / "missing.apk",
                Path(directory) / "linked.apk",
            )
            invalid_apks[1].symlink_to(target)
            for test_apk in invalid_apks:
                with self.subTest(test_apk=test_apk.name):
                    run_mock = Mock(side_effect=[completed, completed])
                    with patch.dict(
                        DEVICE_GLOBALS,
                        {
                            "DEFAULT_TEST_APK": test_apk,
                            "run": run_mock,
                            "package_installation_status": Mock(return_value=False),
                        },
                    ):
                        with self.assertRaisesRegex(SystemExit, "2"):
                            run_timeshift_command_check(
                                "adb",
                                "test-device",
                                "at.bernhardberger.tvhplayer",
                            )

                    commands = [call.args[0] for call in run_mock.call_args_list]
                    self.assertEqual(commands[-2][-2:], ["uninstall", "at.bernhardberger.tvhplayer.test"])
                    self.assertEqual(commands[-1][-2:], ["force-stop", "at.bernhardberger.tvhplayer"])

    def test_channel_guide_check_uses_fixed_instrumentation_and_cleans_up(self) -> None:
        completed = subprocess.CompletedProcess(["adb"], 0, stdout="", stderr="")
        instrumentation = subprocess.CompletedProcess(
            ["adb"],
            0,
            stdout=(
                "instrumentation:at.bernhardberger.tvhplayer.test/"
                "androidx.test.runner.AndroidJUnitRunner "
                "(target=at.bernhardberger.tvhplayer)\n"
            ),
            stderr="",
        )
        run_mock = Mock(
            side_effect=[
                completed,
                completed,
                instrumentation,
                completed,
                CHANNEL_GUIDE_RUNNER_SUCCESS,
                completed,
                completed,
                CLEAR_SUCCESS,
            ]
        )
        with tempfile.TemporaryDirectory() as directory:
            test_apk = Path(directory) / "app-debug-androidTest.apk"
            test_apk.touch()
            with patch.dict(
                DEVICE_GLOBALS,
                {
                    "DEFAULT_TEST_APK": test_apk,
                    "run": run_mock,
                    "package_installation_status": Mock(side_effect=[False, False]),
                },
            ):
                run_channel_guide_check(
                    "adb",
                    "test-device",
                    "at.bernhardberger.tvhplayer",
                )

        commands = [call.args[0] for call in run_mock.call_args_list]
        self.assertIn(
            [
                "adb",
                "-s",
                "test-device",
                "shell",
                "am",
                "instrument",
                "-w",
                "-e",
                "class",
                DEVICE["CHANNEL_GUIDE_TEST"],
                "-e",
                DEVICE["EXTERNAL_TARGET_ACCEPTANCE_ARGUMENT"],
                DEVICE["EXTERNAL_TARGET_ACCEPTANCE_ENABLED"],
                "at.bernhardberger.tvhplayer.test/"
                "androidx.test.runner.AndroidJUnitRunner",
            ],
            commands,
        )
        self.assertEqual(commands[-3][-2:], ["uninstall", "at.bernhardberger.tvhplayer.test"])
        self.assertEqual(commands[-2][-2:], ["force-stop", "at.bernhardberger.tvhplayer"])
        self.assertEqual(
            commands[-1][-3:],
            ["pm", "clear", "at.bernhardberger.tvhplayer"],
        )

    def test_configured_connection_check_runs_one_named_test_and_cleans_up(self) -> None:
        completed = subprocess.CompletedProcess(["adb"], 0, stdout="", stderr="")
        instrumentation = subprocess.CompletedProcess(
            ["adb"],
            0,
            stdout=(
                "instrumentation:at.bernhardberger.tvhplayer.test/"
                "androidx.test.runner.AndroidJUnitRunner "
                "(target=at.bernhardberger.tvhplayer)\n"
            ),
            stderr="",
        )
        run_mock = Mock(
            side_effect=[
                completed,
                completed,
                instrumentation,
                completed,
                CONFIGURED_CONNECTION_RUNNER_SUCCESS,
                completed,
                completed,
                CLEAR_SUCCESS,
            ]
        )
        with tempfile.TemporaryDirectory() as directory:
            test_apk = Path(directory) / "app-debug-androidTest.apk"
            test_apk.touch()
            with patch.dict(
                DEVICE_GLOBALS,
                {
                    "DEFAULT_TEST_APK": test_apk,
                    "run": run_mock,
                    "package_installation_status": Mock(side_effect=[False, False]),
                },
            ):
                run_channel_guide_configured_connection_check(
                    "adb",
                    "test-device",
                    "at.bernhardberger.tvhplayer",
                )

        commands = [call.args[0] for call in run_mock.call_args_list]
        runner_command = next(command for command in commands if "instrument" in command)
        self.assertIn(DEVICE["CHANNEL_GUIDE_CONFIGURED_CONNECTION_TEST"], runner_command)
        self.assertEqual(commands[-3][-2:], ["uninstall", "at.bernhardberger.tvhplayer.test"])
        self.assertEqual(commands[-2][-2:], ["force-stop", "at.bernhardberger.tvhplayer"])
        self.assertEqual(
            commands[-1][-3:],
            ["pm", "clear", "at.bernhardberger.tvhplayer"],
        )

    def test_configured_connection_check_accepts_g10_runner_summary_without_instrumentation_code(
        self,
    ) -> None:
        completed = subprocess.CompletedProcess(["adb"], 0, stdout="", stderr="")
        instrumentation = subprocess.CompletedProcess(
            ["adb"],
            0,
            stdout=(
                "instrumentation:at.bernhardberger.tvhplayer.test/"
                "androidx.test.runner.AndroidJUnitRunner "
                "(target=at.bernhardberger.tvhplayer)\n"
            ),
            stderr="",
        )
        runner_success = subprocess.CompletedProcess(
            ["adb"],
            0,
            stdout=(
                "at.bernhardberger.tvhplayer.ui.ChannelGuideDeviceAcceptanceTest:."
                "INSTRUMENTATION_STATUS: configuredPasswordPresent=true\n"
                "INSTRUMENTATION_STATUS: configuredUsernamePresent=true\n"
                "INSTRUMENTATION_STATUS_CODE: 2\n"
                "Time: 14.155\n"
                "OK (1 test)\n"
            ),
            stderr="",
        )
        run_mock = Mock(
            side_effect=[
                completed,
                completed,
                instrumentation,
                completed,
                runner_success,
                completed,
                completed,
                CLEAR_SUCCESS,
            ]
        )
        with tempfile.TemporaryDirectory() as directory:
            test_apk = Path(directory) / "app-debug-androidTest.apk"
            test_apk.touch()
            with patch.dict(
                DEVICE_GLOBALS,
                {
                    "DEFAULT_TEST_APK": test_apk,
                    "run": run_mock,
                    "package_installation_status": Mock(side_effect=[False, False]),
                },
            ):
                run_channel_guide_configured_connection_check(
                    "adb",
                    "test-device",
                    "at.bernhardberger.tvhplayer",
                )

    def test_unrelated_instrumentation_check_rejects_missing_instrumentation_code(
        self,
    ) -> None:
        completed = subprocess.CompletedProcess(["adb"], 0, stdout="", stderr="")
        instrumentation = subprocess.CompletedProcess(
            ["adb"],
            0,
            stdout=(
                "instrumentation:at.bernhardberger.tvhplayer.test/"
                "androidx.test.runner.AndroidJUnitRunner "
                "(target=at.bernhardberger.tvhplayer)\n"
            ),
            stderr="",
        )
        runner_success = subprocess.CompletedProcess(
            ["adb"],
            0,
            stdout=(
                "INSTRUMENTATION_RESULT: stream=\n"
                "OK (2 tests)\n"
            ),
            stderr="",
        )
        run_mock = Mock(
            side_effect=[
                completed,
                completed,
                instrumentation,
                completed,
                runner_success,
                completed,
                completed,
            ]
        )
        with tempfile.TemporaryDirectory() as directory:
            test_apk = Path(directory) / "app-debug-androidTest.apk"
            test_apk.touch()
            with patch.dict(
                DEVICE_GLOBALS,
                {
                    "DEFAULT_TEST_APK": test_apk,
                    "run": run_mock,
                    "package_installation_status": Mock(side_effect=[False, False]),
                },
            ):
                stderr = io.StringIO()
                with redirect_stderr(stderr):
                    with self.assertRaisesRegex(SystemExit, "2"):
                        run_timeshift_command_check(
                            "adb",
                            "test-device",
                            "at.bernhardberger.tvhplayer",
                        )
        self.assertIn(
            "did not report successful instrumentation completion",
            stderr.getvalue(),
        )

    def test_channel_guide_check_cleans_up_after_install_failure(self) -> None:
        completed = subprocess.CompletedProcess(["adb"], 0, stdout="", stderr="")
        run_mock = Mock(
            side_effect=[
                completed,
                SystemExit(2),
                completed,
                completed,
                CLEAR_SUCCESS,
            ]
        )
        with tempfile.TemporaryDirectory() as directory:
            test_apk = Path(directory) / "app-debug-androidTest.apk"
            test_apk.touch()
            with patch.dict(
                DEVICE_GLOBALS,
                {
                    "DEFAULT_TEST_APK": test_apk,
                    "run": run_mock,
                    "package_installation_status": Mock(side_effect=[False, False]),
                },
            ):
                with self.assertRaisesRegex(SystemExit, "2"):
                    run_channel_guide_check(
                        "adb",
                        "test-device",
                        "at.bernhardberger.tvhplayer",
                    )

        commands = [call.args[0] for call in run_mock.call_args_list]
        self.assertEqual(commands[-3][-2:], ["uninstall", "at.bernhardberger.tvhplayer.test"])
        self.assertEqual(commands[-2][-2:], ["force-stop", "at.bernhardberger.tvhplayer"])
        self.assertEqual(commands[-1][-3:], ["pm", "clear", "at.bernhardberger.tvhplayer"])

    def test_channel_guide_check_cleans_up_after_stale_state_cannot_be_verified(self) -> None:
        completed = subprocess.CompletedProcess(["adb"], 0, stdout="", stderr="")
        run_mock = Mock(
            side_effect=[
                completed,
                completed,
                completed,
                CLEAR_SUCCESS,
            ]
        )
        with tempfile.TemporaryDirectory() as directory:
            test_apk = Path(directory) / "app-debug-androidTest.apk"
            test_apk.touch()
            with patch.dict(
                DEVICE_GLOBALS,
                {
                    "DEFAULT_TEST_APK": test_apk,
                    "run": run_mock,
                    "package_installation_status": Mock(side_effect=[None, False]),
                },
            ):
                with self.assertRaisesRegex(SystemExit, "2"):
                    run_channel_guide_check(
                        "adb",
                        "test-device",
                        "at.bernhardberger.tvhplayer",
                    )

        commands = [call.args[0] for call in run_mock.call_args_list]
        self.assertEqual(commands[-3][-2:], ["uninstall", "at.bernhardberger.tvhplayer.test"])
        self.assertEqual(commands[-2][-2:], ["force-stop", "at.bernhardberger.tvhplayer"])
        self.assertEqual(commands[-1][-3:], ["pm", "clear", "at.bernhardberger.tvhplayer"])

    def test_channel_guide_check_fails_when_cleanup_does_not_complete(self) -> None:
        completed = subprocess.CompletedProcess(["adb"], 0, stdout="", stderr="")
        cleanup_failure = subprocess.CompletedProcess(
            ["adb"],
            1,
            stdout="",
            stderr="force-stop failed",
        )
        instrumentation = subprocess.CompletedProcess(
            ["adb"],
            0,
            stdout=(
                "instrumentation:at.bernhardberger.tvhplayer.test/"
                "androidx.test.runner.AndroidJUnitRunner "
                "(target=at.bernhardberger.tvhplayer)\n"
            ),
            stderr="",
        )
        run_mock = Mock(
            side_effect=[
                completed,
                completed,
                instrumentation,
                completed,
                CHANNEL_GUIDE_RUNNER_SUCCESS,
                completed,
                cleanup_failure,
                CLEAR_SUCCESS,
            ]
        )
        with tempfile.TemporaryDirectory() as directory:
            test_apk = Path(directory) / "app-debug-androidTest.apk"
            test_apk.touch()
            with patch.dict(
                DEVICE_GLOBALS,
                {
                    "DEFAULT_TEST_APK": test_apk,
                    "run": run_mock,
                    "package_installation_status": Mock(side_effect=[False, False]),
                },
            ):
                with self.assertRaisesRegex(SystemExit, "2"):
                    run_channel_guide_check(
                        "adb",
                        "test-device",
                        "at.bernhardberger.tvhplayer",
                    )

    def test_package_presence_query_distinguishes_absent_from_unverified(self) -> None:
        absent = subprocess.CompletedProcess(["adb"], 0, stdout="", stderr="")
        query_failure = subprocess.CompletedProcess(
            ["adb"],
            1,
            stdout="",
            stderr="device unavailable",
        )
        with patch.dict(
            DEVICE_GLOBALS,
            {"run": Mock(side_effect=[absent, query_failure])},
        ):
            self.assertFalse(
                package_installation_status("adb", "test-device", "example.test")
            )
            self.assertIsNone(
                package_installation_status("adb", "test-device", "example.test")
            )

    def test_channel_guide_check_rejects_unverified_or_remaining_cleanup_package(self) -> None:
        completed = subprocess.CompletedProcess(["adb"], 0, stdout="", stderr="")
        instrumentation = subprocess.CompletedProcess(
            ["adb"],
            0,
            stdout=(
                "instrumentation:at.bernhardberger.tvhplayer.test/"
                "androidx.test.runner.AndroidJUnitRunner "
                "(target=at.bernhardberger.tvhplayer)\n"
            ),
            stderr="",
        )
        for cleanup_status in (None, True):
            with self.subTest(cleanup_status=cleanup_status):
                run_mock = Mock(
                    side_effect=[
                        completed,
                        completed,
                        instrumentation,
                        completed,
                        CHANNEL_GUIDE_RUNNER_SUCCESS,
                        completed,
                        completed,
                        CLEAR_SUCCESS,
                    ]
                )
                with tempfile.TemporaryDirectory() as directory:
                    test_apk = Path(directory) / "app-debug-androidTest.apk"
                    test_apk.touch()
                    with patch.dict(
                        DEVICE_GLOBALS,
                        {
                            "DEFAULT_TEST_APK": test_apk,
                            "run": run_mock,
                            "package_installation_status": Mock(
                                side_effect=[False, cleanup_status]
                            ),
                        },
                    ):
                        with self.assertRaisesRegex(SystemExit, "2"):
                            run_channel_guide_check(
                                "adb",
                                "test-device",
                                "at.bernhardberger.tvhplayer",
                            )

                self.assertEqual(
                    run_mock.call_args_list[-2].args[0][-2:],
                    ["force-stop", "at.bernhardberger.tvhplayer"],
                )

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

    def test_credential_staging_is_cleaned_after_launch_failure(self) -> None:
        properties = {
            "manufacturer": "TCL",
            "model": "Smart TV Pro",
            "device": "G10",
            "product": "G10_4K_GB",
            "android": "12",
            "abis": "armeabi-v7a",
        }
        run_mock = Mock(side_effect=[None, None, SystemExit(2), None])

        with (
            patch.dict(
                DEVICE_GLOBALS,
                {
                    "load_local_config": Mock(
                        return_value=(
                            "g10",
                            {
                                "serial": "private-device",
                                "role": "test",
                                "expected_device": "G10",
                            },
                        )
                    ),
                    "require_device_ready": Mock(),
                    "read_device_properties": Mock(return_value=properties),
                    "verify_configured_identity": Mock(),
                    "load_credential_payload": Mock(
                        return_value='{"password":"super-secret"}'
                    ),
                    "run": run_mock,
                },
            ),
            patch.object(DEVICE["shutil"], "which", return_value="adb"),
            patch.object(
                DEVICE["sys"],
                "argv",
                ["device", "provision-test-credentials"],
            ),
        ):
            with self.assertRaisesRegex(SystemExit, "2"):
                DEVICE["main"]()

        self.assertEqual(run_mock.call_count, 4)
        cleanup = run_mock.call_args_list[-1]
        self.assertIn("files/tvhplayer_test_provisioning.json", cleanup.args[0])
        self.assertIn("files/tvhplayer_test_provisioning.result", cleanup.args[0])
        self.assertEqual(
            cleanup.kwargs,
            {"capture": True, "check": False, "sensitive": True},
        )
        self.assertNotIn("super-secret", str(cleanup))

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
