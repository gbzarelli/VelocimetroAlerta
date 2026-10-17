#!/usr/bin/env python3
"""Replay a GPX track through the Android Emulator's GPS console."""

import argparse
import math
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional


EARTH_RADIUS_METERS = 6_371_000.0
NMEA_SETTLE_DELAY_SECONDS = 0.4
METERS_PER_SECOND_PER_KNOT = 1_852.0 / 3_600.0


@dataclass(frozen=True)
class TrackPoint:
    latitude: float
    longitude: float
    elevation: Optional[float]
    timestamp: Optional[datetime]


def _local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def _child_text(element: ET.Element, name: str) -> Optional[str]:
    child = next((item for item in element if _local_name(item.tag) == name), None)
    return child.text.strip() if child is not None and child.text else None


def _parse_timestamp(value: Optional[str]) -> Optional[datetime]:
    if not value:
        return None
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    return parsed.replace(tzinfo=timezone.utc) if parsed.tzinfo is None else parsed


def read_track_points(gpx_path: Path) -> list[TrackPoint]:
    """Read valid track points in file order, including GPX 1.0/1.1 namespaces."""
    try:
        root = ET.parse(gpx_path).getroot()
    except (ET.ParseError, OSError) as error:
        raise ValueError("Unable to read GPX file: {}".format(error)) from error

    points = []
    for element in root.iter():
        if _local_name(element.tag) != "trkpt":
            continue
        try:
            latitude = float(element.attrib["lat"])
            longitude = float(element.attrib["lon"])
            if not -90 <= latitude <= 90 or not -180 <= longitude <= 180:
                continue
            elevation_text = _child_text(element, "ele")
            elevation = float(elevation_text) if elevation_text is not None else None
            timestamp = _parse_timestamp(_child_text(element, "time"))
        except (KeyError, TypeError, ValueError):
            continue
        points.append(TrackPoint(latitude, longitude, elevation, timestamp))

    if not points:
        raise ValueError("GPX does not contain valid track points")
    return points


def playback_delay(previous: TrackPoint, current: TrackPoint, speed: float) -> float:
    """Return wall-clock seconds between fixes at the requested playback factor."""
    if speed <= 0:
        raise ValueError("Playback speed must be greater than zero")
    if previous.timestamp is None or current.timestamp is None:
        recorded_seconds = 1.0
    else:
        recorded_seconds = (current.timestamp - previous.timestamp).total_seconds()
    return max(0.0, recorded_seconds / speed)


def _distance_meters(first: TrackPoint, second: TrackPoint) -> float:
    """Return great-circle distance between two track points."""
    first_latitude = math.radians(first.latitude)
    second_latitude = math.radians(second.latitude)
    latitude_delta = second_latitude - first_latitude
    longitude_delta = math.radians(second.longitude - first.longitude)
    haversine = (
        math.sin(latitude_delta / 2) ** 2
        + math.cos(first_latitude) * math.cos(second_latitude) * math.sin(longitude_delta / 2) ** 2
    )
    return 2 * EARTH_RADIUS_METERS * math.asin(math.sqrt(min(1.0, haversine)))


def _segment_speed_meters_per_second(previous: TrackPoint, current: TrackPoint, playback_speed: float) -> float:
    if previous.timestamp is None or current.timestamp is None:
        recorded_seconds = 1.0
    else:
        recorded_seconds = (current.timestamp - previous.timestamp).total_seconds()
    if recorded_seconds <= 0:
        return 0.0
    return _distance_meters(previous, current) / recorded_seconds * playback_speed


def point_speeds_meters_per_second(points: list[TrackPoint], playback_speed: float = 1.0) -> list[float]:
    """Derive emulator velocities in m/s while preserving GPX playback timing."""
    if playback_speed <= 0:
        raise ValueError("Playback speed must be greater than zero")
    if not points:
        return []
    if len(points) == 1:
        return [0.0]

    velocities = [_segment_speed_meters_per_second(points[0], points[1], playback_speed)]
    velocities.extend(
        _segment_speed_meters_per_second(points[index - 1], points[index], playback_speed)
        for index in range(1, len(points))
    )
    return velocities


def point_elevations_meters(points: list[TrackPoint]) -> list[float]:
    """Fill missing altitude samples with the nearest known route elevation."""
    first_known = next(
        (point.elevation for point in points if point.elevation is not None and math.isfinite(point.elevation)),
        0.0,
    )
    previous = first_known
    elevations = []
    for point in points:
        if point.elevation is not None and math.isfinite(point.elevation):
            previous = point.elevation
        elevations.append(previous)
    return elevations


def emulator_geo_fix_command(
    adb: str,
    device: str,
    point: TrackPoint,
    velocity_meters_per_second: float,
    fallback_elevation_meters: float = 0.0,
) -> list[str]:
    """Build a geo-fix command, converting meters per second to the console's knots."""
    elevation = (
        point.elevation
        if point.elevation is not None and math.isfinite(point.elevation)
        else fallback_elevation_meters
    )
    return [
        adb,
        "-s",
        device,
        "emu",
        "geo",
        "fix",
        str(point.longitude),
        str(point.latitude),
        str(elevation),
        "8",
        str(round(velocity_meters_per_second / METERS_PER_SECOND_PER_KNOT, 3)),
    ]


def _nmea_coordinate(value: float, degree_digits: int) -> tuple[str, str]:
    absolute = abs(value)
    degrees = int(absolute)
    minutes = round((absolute - degrees) * 60.0, 4)
    if minutes >= 60.0:
        degrees += 1
        minutes = 0.0
    hemisphere = ("N" if value >= 0 else "S") if degree_digits == 2 else ("E" if value >= 0 else "W")
    return f"{degrees:0{degree_digits}d}{minutes:07.4f}", hemisphere


def emulator_geo_nmea_command(
    adb: str,
    device: str,
    point: TrackPoint,
    fallback_elevation_meters: float = 0.0,
) -> list[str]:
    """Build a GGA fix because API 24 emulator `geo fix` omits altitude from Location."""
    elevation = (
        point.elevation
        if point.elevation is not None and math.isfinite(point.elevation)
        else fallback_elevation_meters
    )
    timestamp = point.timestamp or datetime.now(timezone.utc)
    if timestamp.tzinfo is None:
        timestamp = timestamp.replace(tzinfo=timezone.utc)
    utc_time = timestamp.astimezone(timezone.utc).strftime("%H%M%S.%f")[:-4]
    latitude, north_south = _nmea_coordinate(point.latitude, 2)
    longitude, east_west = _nmea_coordinate(point.longitude, 3)
    fields = [
        "GPGGA",
        utc_time,
        latitude,
        north_south,
        longitude,
        east_west,
        "1",
        "08",
        "0.9",
        f"{elevation:.1f}",
        "M",
        "0.0",
        "M",
        "",
        "",
    ]
    body = ",".join(fields)
    checksum = 0
    for character in body:
        checksum ^= ord(character)
    sentence = f"${body}*{checksum:02X}"
    return [adb, "-s", device, "emu", "geo", "nmea", sentence]


def emulator_geo_commands(
    adb: str,
    device: str,
    point: TrackPoint,
    velocity_meters_per_second: float,
    fallback_elevation_meters: float = 0.0,
) -> list[list[str]]:
    """Send altitude via GGA, then refresh speed and position with the emulator fix."""
    return [
        emulator_geo_nmea_command(adb, device, point, fallback_elevation_meters),
        emulator_geo_fix_command(adb, device, point, velocity_meters_per_second, fallback_elevation_meters),
    ]


def _send_point(
    adb: str,
    device: str,
    point: TrackPoint,
    velocity_meters_per_second: float,
    fallback_elevation_meters: float,
) -> None:
    try:
        for index, command in enumerate(
            emulator_geo_commands(adb, device, point, velocity_meters_per_second, fallback_elevation_meters)
        ):
            if index > 0:
                time.sleep(NMEA_SETTLE_DELAY_SECONDS)
            subprocess.run(command, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True)
    except FileNotFoundError as error:
        raise RuntimeError("adb was not found; install Android platform-tools or set ADB") from error
    except subprocess.CalledProcessError as error:
        raise RuntimeError(error.stderr.strip() or "adb could not send a location to {}".format(device)) from error


def simulate(points: list[TrackPoint], adb: str, device: str, speed: float, loop: bool, loop_pause: float) -> None:
    velocities = point_speeds_meters_per_second(points, playback_speed=speed)
    elevations = point_elevations_meters(points)
    cycle = 0
    while True:
        cycle += 1
        print("Cycle {}: sending {} GPX points to {} at {}x".format(cycle, len(points), device, speed), flush=True)
        next_due = time.monotonic()
        for index, (point, velocity_meters_per_second, elevation) in enumerate(zip(points, velocities, elevations)):
            remaining = next_due - time.monotonic()
            if remaining > 0:
                time.sleep(remaining)
            _send_point(adb, device, point, velocity_meters_per_second, elevation)
            if index % 100 == 0 or index == len(points) - 1:
                print("  {}/{} points ({:.6f}, {:.6f})".format(index + 1, len(points), point.latitude, point.longitude), flush=True)
            if index < len(points) - 1:
                next_due += playback_delay(point, points[index + 1], speed)
        if not loop:
            return
        print("Route complete; restarting in {:.1f}s (Ctrl+C to stop).".format(loop_pause), flush=True)
        time.sleep(loop_pause)


def main(argv: Optional[list[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gpx", type=Path, default=Path("tools/gpx/Pedalada_vespertina.gpx"))
    parser.add_argument("--device", default="emulator-5564", help="Emulator serial (default: emulator-5564)")
    parser.add_argument("--adb", default="adb", help="adb executable path")
    parser.add_argument("--speed", type=float, default=1.0, help="Playback factor: 1=realtime, 12=12x faster")
    parser.add_argument("--loop", action="store_true", help="Restart the route after it reaches its final point")
    parser.add_argument("--loop-pause", type=float, default=2.0, help="Seconds to pause before restarting a loop")
    args = parser.parse_args(argv)

    if args.speed <= 0:
        parser.error("--speed must be greater than zero")
    if args.loop_pause < 0:
        parser.error("--loop-pause cannot be negative")
    if not args.gpx.is_file():
        parser.error("GPX file not found: {} (set GPX=... or copy the file into tools/gpx/)".format(args.gpx))
    try:
        points = read_track_points(args.gpx)
        state = subprocess.run(
            [args.adb, "-s", args.device, "get-state"], check=True, capture_output=True, text=True
        ).stdout.strip()
        if state != "device":
            raise RuntimeError("{} is not online (adb state: {})".format(args.device, state or "unknown"))
        simulate(points, args.adb, args.device, args.speed, args.loop, args.loop_pause)
    except (RuntimeError, ValueError, FileNotFoundError, KeyboardInterrupt) as error:
        if isinstance(error, KeyboardInterrupt):
            print("\nGPX simulation stopped.", file=sys.stderr)
            return 130
        print("GPX simulator: {}".format(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
