import sys
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import call, patch

sys.path.insert(0, str(Path(__file__).resolve().parent))

from gpx_simulator import (
    TrackPoint,
    NMEA_SETTLE_DELAY_SECONDS,
    _send_point,
    emulator_geo_commands,
    emulator_geo_fix_command,
    emulator_geo_nmea_command,
    playback_delay,
    point_elevations_meters,
    point_speeds_meters_per_second,
    read_track_points,
    simulate,
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
        # or 11.1 m/s over ten seconds. The emulator geo-fix command takes knots.
        velocities = point_speeds_meters_per_second(points)
        self.assertAlmostEqual(velocities[0], 11.12, delta=0.1)
        self.assertAlmostEqual(velocities[1], 11.12, delta=0.1)
        self.assertAlmostEqual(velocities[2], 11.12, delta=0.1)

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

        self.assertAlmostEqual(point_speeds_meters_per_second(points)[0], 1.11, delta=0.05)
        duplicate_timestamp = datetime(2026, 10, 11, 17, 54, 25, tzinfo=timezone.utc)
        duplicated_times = [
            TrackPoint(point.latitude, point.longitude, point.elevation, duplicate_timestamp)
            for point in points
        ]
        self.assertEqual(point_speeds_meters_per_second(duplicated_times)[0], 0)

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

        velocities = point_speeds_meters_per_second(points)
        self.assertEqual(velocities[1], 0)
        self.assertAlmostEqual(velocities[2] * 3.6, 0.04, delta=0.005)

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

        self.assertAlmostEqual(point_speeds_meters_per_second(points, playback_speed=4)[0], 44.48, delta=0.5)
        self.assertEqual(playback_delay(points[0], points[1], speed=4), 2.5)

    def test_emulator_fix_converts_meters_per_second_to_knots_with_missing_altitude(self):
        with tempfile.TemporaryDirectory() as directory:
            gpx = Path(directory) / "route.gpx"
            gpx.write_text("<gpx><trk><trkseg><trkpt lat='1' lon='2'/></trkseg></trk></gpx>", encoding="utf-8")
            point = read_track_points(gpx)[0]

        self.assertEqual(
            emulator_geo_fix_command("adb", "emulator-5564", point, 12.5, 540.5),
            ["adb", "-s", "emulator-5564", "emu", "geo", "fix", "2.0", "1.0", "540.5", "8", "24.298"],
        )

    def test_geo_fix_preserves_slow_movement_and_zero_speed(self):
        point = TrackPoint(1.0, 2.0, 540.5, None)

        slow = emulator_geo_fix_command("adb", "emulator-5564", point, 0.04)
        paused = emulator_geo_fix_command("adb", "emulator-5564", point, 0.0)

        self.assertEqual(slow[-1], "0.078")
        self.assertEqual(paused[-1], "0.0")

    def test_emulator_nmea_fix_encodes_altitude_coordinates_and_checksum(self):
        point = TrackPoint(
            latitude=-21.1,
            longitude=-47.8,
            elevation=540.5,
            timestamp=datetime(2026, 10, 11, 17, 54, 25, tzinfo=timezone.utc),
        )

        self.assertEqual(
            emulator_geo_nmea_command("adb", "emulator-5564", point),
            [
                "adb",
                "-s",
                "emulator-5564",
                "emu",
                "geo",
                "nmea",
                "$GPGGA,175425.00,2106.0000,S,04748.0000,W,1,08,0.9,540.5,M,0.0,M,,*5C",
            ],
        )

    def test_emulator_point_sends_altitude_before_velocity_fix(self):
        point = TrackPoint(
            latitude=-21.1,
            longitude=-47.8,
            elevation=540.5,
            timestamp=datetime(2026, 10, 11, 17, 54, 25, tzinfo=timezone.utc),
        )

        self.assertEqual(
            emulator_geo_commands("adb", "emulator-5564", point, 12.5),
            [
                [
                    "adb",
                    "-s",
                    "emulator-5564",
                    "emu",
                    "geo",
                    "nmea",
                    "$GPGGA,175425.00,2106.0000,S,04748.0000,W,1,08,0.9,540.5,M,0.0,M,,*5C",
                ],
                ["adb", "-s", "emulator-5564", "emu", "geo", "fix", "-47.8", "-21.1", "540.5", "8", "24.298"],
            ],
        )

    def test_send_point_allows_altitude_fix_to_reach_location_listener(self):
        point = TrackPoint(
            latitude=-21.1,
            longitude=-47.8,
            elevation=540.5,
            timestamp=datetime(2026, 10, 11, 17, 54, 25, tzinfo=timezone.utc),
        )
        expected_commands = emulator_geo_commands("adb", "emulator-5564", point, 12.5)

        with patch("gpx_simulator.subprocess.run") as run, patch("gpx_simulator.time.sleep") as sleep:
            _send_point("adb", "emulator-5564", point, 12.5, 0.0)

        self.assertEqual([item.args[0] for item in run.call_args_list], expected_commands)
        self.assertEqual(sleep.call_args_list, [call(NMEA_SETTLE_DELAY_SECONDS)])

    def test_one_x_schedule_does_not_accumulate_command_latency(self):
        start = datetime(2026, 10, 11, 17, 54, 25, tzinfo=timezone.utc)
        points = [
            TrackPoint(0.0, index * 0.001, 540.0, start.replace(second=start.second + index))
            for index in range(3)
        ]
        now = [100.0]
        sent_at = []

        def fake_send_point(*_args):
            sent_at.append(now[0])
            now[0] += 0.4

        def fake_sleep(delay):
            now[0] += delay

        with patch("gpx_simulator.time.monotonic", side_effect=lambda: now[0]), patch(
            "gpx_simulator.time.sleep", side_effect=fake_sleep
        ), patch("gpx_simulator._send_point", side_effect=fake_send_point):
            simulate(points, "adb", "emulator-5564", speed=1.0, loop=False, loop_pause=0)

        self.assertEqual(sent_at, [100.0, 101.0, 102.0])

    def test_rejects_gpx_without_valid_track_points(self):
        with tempfile.TemporaryDirectory() as directory:
            gpx = Path(directory) / "empty.gpx"
            gpx.write_text("<gpx><trk><trkseg><trkpt lat='91' lon='2'/></trkseg></trk></gpx>", encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "valid track points"):
                read_track_points(gpx)


if __name__ == "__main__":
    unittest.main()
