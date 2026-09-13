import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("profiling_analysis", ROOT / "tools/profiling/analyze.py")
assert SPEC is not None and SPEC.loader is not None
ANALYSIS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(ANALYSIS)


class ProfilingEvidenceTest(unittest.TestCase):
    def test_capture_identity_cannot_be_missing_or_silently_overwritten(self):
        text = "mode=atrace\npackage=at.bernhardberger.tvhplayer.profile\npid=42\nruns=1\nkeycodes=20"
        self.assertEqual(ANALYSIS.parse_metadata(text)["pid"], "42")
        for invalid in ("", text + "\npid=99", text + "\nmode=frames"):
            with self.subTest(text=invalid), self.assertRaises(ValueError):
                ANALYSIS.parse_metadata(invalid)

    def test_atrace_rejects_missing_accounting_overwrite_and_lost_events(self):
        for text in ("", "# entries-in-buffer/entries-written: 4/9", "# entries-in-buffer/entries-written: 4/4\nCPU 0: LOST 1 EVENTS"):
            with self.subTest(text=text), self.assertRaises(ValueError):
                ANALYSIS.validate_atrace_header(text)
        ANALYSIS.validate_atrace_header("# entries-in-buffer/entries-written: 42/42   #P:4")

    def test_empty_foreign_and_incomplete_frames_cannot_pass(self):
        for rows in (
            [],
            [{"pid": "99", "frames": "12", "incomplete": "0"}],
            [{"pid": "42", "frames": "0", "incomplete": "0"}],
            [{"pid": "42", "frames": "12", "incomplete": "1"}],
        ):
            with self.subTest(rows=rows), self.assertRaises(ValueError):
                ANALYSIS.validate_frames(rows, 42)
        ANALYSIS.validate_frames([{"pid": "42", "frames": "12", "incomplete": "0"}], 42)

    def test_sampling_requires_only_the_recorded_process(self):
        with self.assertRaises(ValueError):
            ANALYSIS.validate_owned_rows([], 42)
        with self.assertRaises(ValueError):
            ANALYSIS.validate_owned_rows([{"pid": "42"}, {"pid": "99"}], 42)
        ANALYSIS.validate_owned_rows([{"pid": "42"}, {"pid": "42"}], 42)

    def test_input_delivery_without_expected_focus_is_not_a_successful_journey(self):
        valid = {"callbacks": "1", "destination_kind": "P44:focus:guide", "callback_ms": "12.5"}
        for row in (
            {**valid, "callbacks": "0"},
            {**valid, "callbacks": "2"},
            {**valid, "destination_kind": "P44:focus:channel"},
            {**valid, "callback_ms": "[NULL]"},
        ):
            with self.subTest(row=row), self.assertRaises(ValueError):
                ANALYSIS.validate_focus([row], ["guide"], 1)
        ANALYSIS.validate_focus([valid], ["guide"], 1)
        with self.assertRaises(ValueError):
            ANALYSIS.validate_focus([valid], ["guide"], 2)

    def test_capture_rejects_exit_guide_activation_and_foreign_package_before_adb(self):
        with tempfile.TemporaryDirectory() as directory:
            for package, key, delay in (
                ("at.bernhardberger.tvhplayer", "4", "0.4"),
                ("at.bernhardberger.tvhplayer", "172", "0.4"),
                ("at.bernhardberger.tvhplayer", "23", "0.4"),
                ("com.tcl.channelplus", "20", "0.4"),
                ("at.bernhardberger.tvhplayer", "20", "0.4; exit 0"),
            ):
                with self.subTest(package=package, key=key):
                    output = Path(directory) / "capture"
                    result = subprocess.run(
                        ["/bin/bash", str(ROOT / "tools/profiling/capture"), "causal", "invalid-device",
                         package, str(output), "1", key],
                        env={"PATH": directory, "TVHPLAYER_PROFILE_KEY_DELAY_SECONDS": delay},
                        capture_output=True, text=True, timeout=5,
                    )
                    self.assertEqual(result.returncode, 2)
                    self.assertFalse(output.exists())

    def test_capture_rejects_transport_injection_and_repeated_back_before_adb(self):
        with tempfile.TemporaryDirectory() as directory:
            for extra, keys in (
                ({"TVHPLAYER_PROFILE_INPUT_TRANSPORT": "cmd; exit 0"}, ["20"]),
                ({"TVHPLAYER_PROFILE_ALLOW_BACK": "true"}, ["4", "4"]),
                ({"TVHPLAYER_PROFILE_ALLOW_BACK": "yes"}, ["4"]),
                ({"TVHPLAYER_PROFILE_VIDEO": "yes"}, ["20"]),
            ):
                result = subprocess.run(
                    ["/bin/bash", str(ROOT / "tools/profiling/capture"), "causal", "invalid-device",
                     "at.bernhardberger.tvhplayer", str(Path(directory) / "capture"), "1", *keys],
                    env={"PATH": directory, **extra}, capture_output=True, text=True, timeout=5,
                )
                self.assertEqual(result.returncode, 2)
                self.assertFalse((Path(directory) / "capture").exists())

    def test_long_capture_requires_a_bounded_sufficient_duration_before_adb(self):
        with tempfile.TemporaryDirectory() as directory:
            for count, duration in ((81, "60"), (40, "8"), (80, "30"), (40, "61"), (40, "60; exit 0")):
                with self.subTest(count=count, duration=duration):
                    result = subprocess.run(
                        ["/bin/bash", str(ROOT / "tools/profiling/capture"), "causal", "invalid-device",
                         "at.bernhardberger.tvhplayer", str(Path(directory) / "capture"), "1", *(["20"] * count)],
                        env={"PATH": directory, "TVHPLAYER_PROFILE_DURATION_SECONDS": duration},
                        capture_output=True, text=True, timeout=5,
                    )
                    self.assertEqual(result.returncode, 2)
                    self.assertFalse((Path(directory) / "capture").exists())

    def test_video_startup_failure_stops_keys_and_preserves_failure_through_cleanup(self):
        # Execute the actual generated remote shell in a filesystem sandbox. This
        # checks process ordering/cleanup, not trace or video validity.
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            def executable(name, text):
                path = root / name
                path.write_text(text)
                path.chmod(0o700)

            executable("pidof", "#!/bin/sh\nprintf '42\\n'\n")
            executable("dumpsys", "#!/bin/sh\nprintf 'mCurrentFocus=at.bernhardberger.tvhplayer/MainActivity\\n'\n")
            executable("perfetto", "#!/bin/sh\nsleep 3\n")
            executable("screenrecord", "#!/bin/sh\nexit 7\n")
            executable("cmd", '#!/bin/sh\nprintf "%s\\n" "$*" >> "$SANDBOX/keys"\n')
            executable("adb", '''#!/usr/bin/python3
import os, pathlib, subprocess, sys
root = pathlib.Path(os.environ["SANDBOX"])
args = sys.argv[3:]
if args[0] == "push":
    sys.exit(0)
if args[0] == "pull":
    pathlib.Path(args[-1]).write_bytes(b"fixture only")
    sys.exit(0)
command = " ".join(args[1:])
if command == "pidof at.bernhardberger.tvhplayer":
    print(42)
    sys.exit(0)
if command.startswith("pm path "):
    print("package:/data/app/fixture/base.apk")
    sys.exit(0)
if command.startswith("sha256sum "):
    print("a" * 64 + "  /data/app/fixture/base.apk")
    sys.exit(0)
if "am force-stop" in command:
    (root / "cleanup").write_text(command)
    sys.exit(0)
if "getprop" in command:
    sys.exit(0)
for prefix in ("/data/local/tmp/", "/data/misc/perfetto-configs/", "/data/misc/perfetto-traces/"):
    command = command.replace(prefix, str(root) + "/")
sys.exit(subprocess.run(["/bin/bash", "-c", command]).returncode)
''')
            output = root / "capture"
            result = subprocess.run(
                ["/bin/bash", str(ROOT / "tools/profiling/capture"), "causal", "fixture-device",
                 "at.bernhardberger.tvhplayer", str(output), "1", "20", "19"],
                env={**os.environ, "PATH": f"{root}:{os.defpath}", "SANDBOX": str(root),
                     "TVHPLAYER_PROFILE_VIDEO": "true", "TVHPLAYER_PROFILE_INPUT_TRANSPORT": "cmd"},
                cwd=ROOT, capture_output=True, text=True, timeout=10,
            )
            self.assertEqual(result.returncode, 45, result.stderr)
            self.assertFalse((root / "keys").exists())
            self.assertIn("am force-stop at.bernhardberger.tvhplayer", (root / "cleanup").read_text())
            self.assertFalse((output / "run-1.mp4").exists())


if __name__ == "__main__":
    unittest.main()
