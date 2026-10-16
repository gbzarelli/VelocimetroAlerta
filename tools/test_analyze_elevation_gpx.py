"""Check the offline analysis probe against known GpxMath behaviors."""

import unittest

from tools.analyze_elevation_gpx import current_gain, proposed_probe


class ElevationAnalysisTest(unittest.TestCase):
    def test_current_gain_matches_existing_kotlin_test(self):
        self.assertEqual((5.0, 2.0), current_gain([100, 102, 105, 103], 2))

    def test_current_gain_counts_short_symmetric_bump(self):
        self.assertEqual((6.0, 6.0), current_gain([100, 106, 100], 5))

    def test_probe_rejects_isolated_spike(self):
        self.assertEqual((0.0, 0.0), proposed_probe([100] * 5 + [106] + [100] * 5, 5))

    def test_probe_counts_gradual_climb_after_filter_lag(self):
        # The causal median lags the final two rising samples.
        self.assertEqual((10.0, 0.0), proposed_probe(list(range(100, 113)), 10))


if __name__ == "__main__":
    unittest.main()
