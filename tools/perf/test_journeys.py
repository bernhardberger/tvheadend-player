"""Offline regression coverage for cumulative gfxinfo measurements (no adb)."""
import runpy
import unittest
from pathlib import Path

JOURNEYS = runpy.run_path(str(Path(__file__).with_name("journeys")))
delta = JOURNEYS["delta"]


class HistogramDeltaTest(unittest.TestCase):
    def test_delta_uses_samples_since_previous_snapshot(self):
        result = delta("HISTOGRAM: 5ms=100 10ms=5", "HISTOGRAM: 5ms=101 10ms=8", "HISTOGRAM")
        self.assertEqual(result["frame_count"], 4)
        self.assertEqual(result["p50_ms"], 10)
        self.assertEqual(result["p99_ms"], 10)

    def test_empty_delta_has_no_percentile(self):
        result = delta("GPU HISTOGRAM: 5ms=7", "GPU HISTOGRAM: 5ms=7", "GPU HISTOGRAM")
        self.assertEqual(result["frame_count"], 0)
        self.assertIsNone(result["p50_ms"])

    def test_counter_restart_is_not_reported_as_fast(self):
        with self.assertRaises(ValueError):
            delta("HISTOGRAM: 5ms=10", "HISTOGRAM: 5ms=1", "HISTOGRAM")

    def test_missing_histogram_is_not_zero_frames(self):
        with self.assertRaises(ValueError):
            delta("", "", "GPU HISTOGRAM")

    def test_cold_process_has_explicit_zero_baseline(self):
        result = delta(None, "HISTOGRAM: 5ms=2 10ms=1", "HISTOGRAM")
        self.assertEqual(result["frame_count"], 3)
        self.assertEqual(result["p50_ms"], 5)
        self.assertEqual(result["p90_ms"], 10)


if __name__ == "__main__":
    unittest.main()
