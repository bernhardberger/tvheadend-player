import importlib.util
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
            for package, key in (
                ("at.bernhardberger.tvhplayer", "4"),
                ("at.bernhardberger.tvhplayer", "172"),
                ("at.bernhardberger.tvhplayer", "23"),
                ("com.tcl.channelplus", "20"),
            ):
                with self.subTest(package=package, key=key):
                    output = Path(directory) / "capture"
                    result = subprocess.run(
                        ["/bin/bash", str(ROOT / "tools/profiling/capture"), "causal", "invalid-device",
                         package, str(output), "1", key],
                        env={"PATH": directory}, capture_output=True, text=True, timeout=5,
                    )
                    self.assertEqual(result.returncode, 2)
                    self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
