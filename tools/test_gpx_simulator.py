import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from gpx_simulator import playback_delay, read_track_points


class GpxSimulatorTest(unittest.TestCase):
    def test_reads_track_points_from_namespaced_gpx(self):
        with tempfile.TemporaryDirectory() as directory:
            gpx = Path(directory) / "route.gpx"
            gpx.write_text(
                """<?xml version="1.0"?>
                <gpx xmlns="http://www.topografix.com/GPX/1/1">
                  <trk><trkseg>
                    <trkpt lat="-21.1" lon="-47.8"><ele>540.5</ele><time>2026-10-11T17:54:25Z</time></trkpt>
                    <trkpt lat="-21.2" lon="-47.9"><time>2026-10-11T17:54:28Z</time></trkpt>
                  </trkseg></trk>
                </gpx>""",
                encoding="utf-8",
            )

            points = read_track_points(gpx)

        self.assertEqual(len(points), 2)
        self.assertEqual(points[0].latitude, -21.1)
        self.assertEqual(points[0].longitude, -47.8)
        self.assertEqual(points[0].elevation, 540.5)
        self.assertIsNone(points[1].elevation)
        self.assertEqual(playback_delay(points[0], points[1], speed=1), 3)

    def test_accelerated_mode_scales_recorded_time(self):
        with tempfile.TemporaryDirectory() as directory:
            gpx = Path(directory) / "route.gpx"
            gpx.write_text(
                """<gpx><trk><trkseg>
                  <trkpt lat="1" lon="2"><time>2026-10-11T17:54:25Z</time></trkpt>
                  <trkpt lat="1.1" lon="2.1"><time>2026-10-11T17:54:31Z</time></trkpt>
                </trkseg></trk></gpx>""",
                encoding="utf-8",
            )
            first, second = read_track_points(gpx)

        self.assertEqual(playback_delay(first, second, speed=12), 0.5)

    def test_missing_timestamps_use_one_recorded_second_per_point(self):
        with tempfile.TemporaryDirectory() as directory:
            gpx = Path(directory) / "route.gpx"
            gpx.write_text(
                "<gpx><trk><trkseg><trkpt lat='1' lon='2'/><trkpt lat='3' lon='4'/></trkseg></trk></gpx>",
                encoding="utf-8",
            )
            first, second = read_track_points(gpx)

        self.assertEqual(playback_delay(first, second, speed=4), 0.25)

    def test_rejects_gpx_without_valid_track_points(self):
        with tempfile.TemporaryDirectory() as directory:
            gpx = Path(directory) / "empty.gpx"
            gpx.write_text("<gpx><trk><trkseg><trkpt lat='91' lon='2'/></trkseg></trk></gpx>", encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "valid track points"):
                read_track_points(gpx)


if __name__ == "__main__":
    unittest.main()
