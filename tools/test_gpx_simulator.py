import sys
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from gpx_simulator import (
    TrackPoint,
    emulator_geo_fix_command,
    playback_delay,
    point_elevations_meters,
    point_speeds_knots,
    read_track_points,
)


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

    def test_derives_velocity_from_distance_and_recorded_time(self):
        with tempfile.TemporaryDirectory() as directory:
            gpx = Path(directory) / "route.gpx"
            gpx.write_text(
                """<gpx><trk><trkseg>
                  <trkpt lat="0" lon="0"><time>2026-10-11T17:54:25Z</time></trkpt>
                  <trkpt lat="0" lon="0.001"><time>2026-10-11T17:54:35Z</time></trkpt>
                  <trkpt lat="0" lon="0.003"><time>2026-10-11T17:54:55Z</time></trkpt>
                </trkseg></trk></gpx>""",
                encoding="utf-8",
            )
            points = read_track_points(gpx)

        # At the equator, 0.001 degrees of longitude is about 111.2 m,
        # or 21.6 knots over ten seconds.
        velocities = point_speeds_knots(points)
        self.assertAlmostEqual(velocities[0], 21.6, delta=0.1)
        self.assertAlmostEqual(velocities[1], 21.6, delta=0.1)
        self.assertAlmostEqual(velocities[2], 21.6, delta=0.1)

    def test_missing_timestamps_use_one_second_and_duplicate_timestamps_report_zero(self):
        with tempfile.TemporaryDirectory() as directory:
            gpx = Path(directory) / "route.gpx"
            gpx.write_text(
                """<gpx><trk><trkseg>
                  <trkpt lat="0" lon="0"/>
                  <trkpt lat="0" lon="0.00001"/>
                </trkseg></trk></gpx>""",
                encoding="utf-8",
            )
            points = read_track_points(gpx)

        self.assertAlmostEqual(point_speeds_knots(points)[0], 2.16, delta=0.05)
        duplicate_timestamp = datetime(2026, 10, 11, 17, 54, 25, tzinfo=timezone.utc)
        duplicated_times = [
            TrackPoint(point.latitude, point.longitude, point.elevation, duplicate_timestamp)
            for point in points
        ]
        self.assertEqual(point_speeds_knots(duplicated_times)[0], 0)

    def test_pause_and_very_slow_movement_keep_low_or_zero_velocity(self):
        with tempfile.TemporaryDirectory() as directory:
            gpx = Path(directory) / "route.gpx"
            gpx.write_text(
                """<gpx><trk><trkseg>
                  <trkpt lat="0" lon="0"><time>2026-10-11T17:54:25Z</time></trkpt>
                  <trkpt lat="0" lon="0"><time>2026-10-11T17:55:25Z</time></trkpt>
                  <trkpt lat="0" lon="0.000001"><time>2026-10-11T17:55:35Z</time></trkpt>
                </trkseg></trk></gpx>""",
                encoding="utf-8",
            )
            points = read_track_points(gpx)

        velocities = point_speeds_knots(points)
        self.assertEqual(velocities[1], 0)
        self.assertAlmostEqual(velocities[2] * 1.852, 0.04, delta=0.005)

    def test_missing_altitude_keeps_a_known_elevation_instead_of_using_zero(self):
        points = [
            TrackPoint(0.0, 0.0, None, None),
            TrackPoint(0.0, 0.001, 123.4, None),
            TrackPoint(0.0, 0.002, None, None),
            TrackPoint(0.0, 0.003, 124.0, None),
        ]

        self.assertEqual(point_elevations_meters(points), [123.4, 123.4, 123.4, 124.0])

    def test_playback_factor_scales_velocity_but_not_recorded_timing(self):
        with tempfile.TemporaryDirectory() as directory:
            gpx = Path(directory) / "route.gpx"
            gpx.write_text(
                """<gpx><trk><trkseg>
                  <trkpt lat="0" lon="0"><time>2026-10-11T17:54:25Z</time></trkpt>
                  <trkpt lat="0" lon="0.001"><time>2026-10-11T17:54:35Z</time></trkpt>
                </trkseg></trk></gpx>""",
                encoding="utf-8",
            )
            points = read_track_points(gpx)

        self.assertAlmostEqual(point_speeds_knots(points, playback_speed=4)[0], 86.4, delta=0.5)
        self.assertEqual(playback_delay(points[0], points[1], speed=4), 2.5)

    def test_emulator_fix_includes_satellites_and_velocity_with_missing_altitude(self):
        with tempfile.TemporaryDirectory() as directory:
            gpx = Path(directory) / "route.gpx"
            gpx.write_text("<gpx><trk><trkseg><trkpt lat='1' lon='2'/></trkseg></trk></gpx>", encoding="utf-8")
            point = read_track_points(gpx)[0]

        self.assertEqual(
            emulator_geo_fix_command("adb", "emulator-5564", point, 12.5, 540.5),
            ["adb", "-s", "emulator-5564", "emu", "geo", "fix", "2.0", "1.0", "540.5", "8", "12.5"],
        )

    def test_rejects_gpx_without_valid_track_points(self):
        with tempfile.TemporaryDirectory() as directory:
            gpx = Path(directory) / "empty.gpx"
            gpx.write_text("<gpx><trk><trkseg><trkpt lat='91' lon='2'/></trkseg></trk></gpx>", encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "valid track points"):
                read_track_points(gpx)


if __name__ == "__main__":
    unittest.main()
