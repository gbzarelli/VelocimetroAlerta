#!/usr/bin/env python3
"""Aggregate-only, offline elevation analysis for a private GPX track.

No route coordinates or timestamps are printed or stored by this script.
The baseline function mirrors GpxMath.calculateGain on the selected revision.
"""

import argparse
import json
import math
import statistics
import xml.etree.ElementTree as ET
from pathlib import Path


def read_points(path):
    root = ET.parse(path).getroot()
    points = []
    for node in root.iter():
        if node.tag.rsplit("}", 1)[-1] != "trkpt":
            continue
        elevation = next((child.text for child in node if child.tag.rsplit("}", 1)[-1] == "ele"), None)
        if elevation is None:
            raise ValueError("Track point without elevation")
        points.append((float(node.attrib["lat"]), float(node.attrib["lon"]), float(elevation)))
    if not points:
        raise ValueError("No track points")
    return points


def distance_km(a, b):
    lat1, lon1 = map(math.radians, a[:2])
    lat2, lon2 = map(math.radians, b[:2])
    dlat, dlon = lat2 - lat1, lon2 - lon1
    x = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    return 6371.0 * 2 * math.atan2(math.sqrt(x), math.sqrt(1 - x))


def current_gain(elevations, threshold, start=0):
    """Literal translation of GpxMath.calculateGain for constant accuracy."""
    if not elevations:
        return 0.0, 0.0
    anchor = elevations[start]
    anchor_index = start
    climb = False
    gain = loss = 0.0
    for index in range(start, len(elevations)):
        difference = elevations[index] - anchor
        if difference > 0 and difference >= threshold:
            if not climb:
                loss += max(0.0, max(anchor - x for x in elevations[anchor_index + 1:index + 1]))
            gain += difference
            climb = True
        elif difference < 0 and -difference >= threshold:
            if climb:
                gain += max(0.0, max(x - anchor for x in elevations[anchor_index + 1:index + 1]))
            loss -= difference
            climb = False
        else:
            continue
        anchor_index = index
        anchor = elevations[index]
    return gain, loss


def chunked_gain(points, threshold):
    """Sequential 1-km service path; calculate before appending current point."""
    elevations = [points[0][2]]
    distance = 0.0
    last_calculation_distance = 0.0
    start = 0
    gain = loss = 0.0
    calculations = 0
    for previous, current in zip(points, points[1:]):
        distance += distance_km(previous, current)
        if distance > last_calculation_distance + 1.0:
            a, b = current_gain(elevations, threshold, start)
            gain += a
            loss += b
            start = len(elevations) - 1
            last_calculation_distance = distance
            calculations += 1
        elevations.append(current[2])
    return round(gain, 3), round(loss, 3), calculations


def proposed_probe(elevations, threshold, window=5):
    """Research probe only: causal median + persistent excursion/hysteresis state."""
    recent = []
    direction = None
    trough = peak = None
    gain = loss = 0.0
    for raw in elevations:
        recent.append(raw)
        if len(recent) > window:
            recent.pop(0)
        altitude = statistics.median(recent)
        if trough is None:
            trough = peak = altitude
            continue
        if direction is None:
            trough = min(trough, altitude)
            peak = max(peak, altitude)
            if altitude - trough >= threshold:
                gain += altitude - trough
                peak = altitude
                direction = "up"
            elif peak - altitude >= threshold:
                loss += peak - altitude
                trough = altitude
                direction = "down"
        elif direction == "up":
            if altitude > peak:
                gain += altitude - peak
                peak = altitude
            elif peak - altitude >= threshold:
                loss += peak - altitude
                trough = altitude
                direction = "down"
        elif altitude < trough:
            loss += trough - altitude
            trough = altitude
        elif altitude - trough >= threshold:
            gain += altitude - trough
            peak = altitude
            direction = "up"
    return round(gain, 3), round(loss, 3)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("gpx", type=Path)
    parser.add_argument("--reference-gain", type=float)
    args = parser.parse_args()
    points = read_points(args.gpx)
    elevations = [point[2] for point in points]
    total_distance = sum(distance_km(a, b) for a, b in zip(points, points[1:]))
    results = {
        "points": len(points),
        "distance_km_haversine": round(total_distance, 3),
        "elevation_start_end_min_max_m": [elevations[0], elevations[-1], min(elevations), max(elevations)],
        "reference_gain_m": args.reference_gain,
        "current_no_accuracy_in_gpx_m": tuple(round(x, 3) for x in current_gain(elevations, 0)),
        "current_assumed_constant_horizontal_accuracy_m": {},
        "probe_median5_persistent_excursion_m": {},
    }
    for accuracy in (3, 5, 8, 10, 12, 15):
        full = tuple(round(x, 3) for x in current_gain(elevations, accuracy))
        chunked = chunked_gain(points, accuracy)
        results["current_assumed_constant_horizontal_accuracy_m"][accuracy] = {
            "full": full, "chunked": chunked,
        }
    for threshold in (5, 8, 10, 12, 15):
        results["probe_median5_persistent_excursion_m"][threshold] = proposed_probe(elevations, threshold)
    print(json.dumps(results, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
