"""Contract tests for the aggregate comparator; no private GPX is required."""

import json
import tempfile
import unittest
from pathlib import Path

from tools.elevation_validation.compare import compare_activity, gpx_diagnostics, load_results, render_markdown


class CompareTest(unittest.TestCase):
    def test_aggregate_comparison_and_private_output(self):
        activity = {"id": "123", "title": "Synthetic", "strava_distance_km": 0, "strava_gain_m": 6}
        points = [(0, 0, 100), (0, 0, 106), (0, 0, 100)]
        result = compare_activity(activity, points)
        self.assertEqual(6, result["scenarios_assumed_constant_horizontal_accuracy_m"]["5"]["current_full_gain_m"])
        self.assertEqual(0, result["scenarios_assumed_constant_horizontal_accuracy_m"]["5"]["current_1km_gain_m"])
        self.assertNotIn("coordinates", json.dumps(result))
        self.assertNotIn("timestamps", json.dumps(result))
        self.assertIn("hipotética", render_markdown([result]))

    def test_requires_all_three_private_files(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(FileNotFoundError, "19463623301.gpx"):
                load_results(gpx_dir=Path(directory))

    def test_sampling_diagnostics_do_not_emit_point_data(self):
        gpx = '<gpx><trk><trkseg><trkpt lat="0" lon="0"><ele>100</ele><time>2026-01-01T00:00:00Z</time></trkpt><trkpt lat="0" lon="0"><ele>106</ele><time>2026-01-01T00:01:00Z</time></trkpt></trkseg></trk></gpx>'
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "synthetic.gpx"
            path.write_text(gpx, encoding="utf-8")
            diagnostics = gpx_diagnostics(path, [(0, 0, 100), (0, 0, 106)])
        self.assertEqual(1, diagnostics["time_gaps_over_30s"])
        self.assertEqual(60, diagnostics["max_time_gap_s"])
        self.assertEqual(1, diagnostics["zero_distance_pairs"])
        self.assertNotIn("2026", json.dumps(diagnostics))


if __name__ == "__main__":
    unittest.main()
