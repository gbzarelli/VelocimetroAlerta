#!/usr/bin/env python3
"""Compare three private GPX exports with the current algorithm and a research probe.

Only aggregate counts/distances/elevation totals are emitted. The GPX lacks
per-fix accuracy and pause state, so constant thresholds and 1-km processing
are scenarios, not a faithful replay of the Android service.
"""

import argparse
import json
import xml.etree.ElementTree as ET
from datetime import datetime
from pathlib import Path

from tools.analyze_elevation_gpx import chunked_gain, current_gain, distance_km, proposed_probe, read_points

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = Path(__file__).with_name("activities.json")
THRESHOLDS = (3, 5, 8, 10, 12, 15)


def compare_activity(activity, points):
    elevations = [point[2] for point in points]
    distance = sum(distance_km(a, b) for a, b in zip(points, points[1:]))
    reference = activity["strava_gain_m"]
    scenarios = {}
    for threshold in THRESHOLDS:
        full_gain, full_loss = current_gain(elevations, threshold)
        chunk_gain, chunk_loss, calculations = chunked_gain(points, threshold)
        probe_gain, probe_loss = proposed_probe(elevations, threshold)
        scenarios[str(threshold)] = {
            "current_full_gain_m": round(full_gain, 1),
            "current_full_loss_m": round(full_loss, 1),
            "current_1km_gain_m": round(chunk_gain, 1),
            "current_1km_loss_m": round(chunk_loss, 1),
            "current_1km_calculations": calculations,
            "probe_gain_m": round(probe_gain, 1),
            "probe_loss_m": round(probe_loss, 1),
            "full_minus_strava_m": round(full_gain - reference, 1),
            "chunk_minus_full_m": round(chunk_gain - full_gain, 1),
            "probe_minus_strava_m": round(probe_gain - reference, 1),
        }
    raw_gain, raw_loss = current_gain(elevations, 0)
    return {
        "id": activity["id"],
        "title": activity["title"],
        "points": len(points),
        "strava_distance_km": activity["strava_distance_km"],
        "gpx_haversine_distance_km": round(distance, 3),
        "strava_gain_m": reference,
        "altitude_min_max_m": [round(min(elevations), 1), round(max(elevations), 1)],
        "raw_gain_loss_m": [round(raw_gain, 1), round(raw_loss, 1)],
        "scenarios_assumed_constant_horizontal_accuracy_m": scenarios,
    }


def gpx_diagnostics(path, points):
    """Report timing and sampling anomalies without retaining coordinates/times."""
    root = ET.parse(path).getroot()
    times = []
    for node in root.iter():
        if node.tag.rsplit("}", 1)[-1] != "trkpt":
            continue
        raw = next((child.text for child in node if child.tag.rsplit("}", 1)[-1] == "time"), None)
        times.append(datetime.fromisoformat(raw.replace("Z", "+00:00")) if raw else None)
    if len(times) != len(points):
        raise ValueError("Timestamp and elevation point counts differ")
    gaps = [(b - a).total_seconds() for a, b in zip(times, times[1:]) if a and b]
    return {
        "time_gaps_over_30s": sum(gap > 30 for gap in gaps),
        "max_time_gap_s": round(max(gaps, default=0), 1),
        "zero_distance_pairs": sum(distance_km(a, b) < 0.000001 for a, b in zip(points, points[1:])),
        "max_adjacent_altitude_step_m": round(max((abs(b[2] - a[2]) for a, b in zip(points, points[1:])), default=0), 1),
    }


def load_results(manifest=MANIFEST, gpx_dir=ROOT / "tools/gpx/issue-1"):
    activities = json.loads(manifest.read_text(encoding="utf-8"))
    if len(activities) != 3 or len({item["id"] for item in activities}) != 3:
        raise ValueError("Expected three distinct activities")
    results = []
    for activity in activities:
        activity_id = activity["id"]
        if not activity_id.isdecimal():
            raise ValueError("Activity id must be decimal")
        path = gpx_dir / f"{activity_id}.gpx"
        if not path.is_file():
            raise FileNotFoundError(f"Missing private GPX: {path}")
        points = read_points(path)
        result = compare_activity(activity, points)
        result["gpx_sampling_diagnostics"] = gpx_diagnostics(path, points)
        results.append(result)
    return results


def render_markdown(results):
    rows = [
        "| Atividade | Pontos | Distância Strava / GPX (km) | Ganho Strava (m) | Bruto GPX (m) | Atual integral 5 m | Atual blocos 5 m | Sonda 5 m |",
        "|---|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for result in results:
        five = result["scenarios_assumed_constant_horizontal_accuracy_m"]["5"]
        rows.append(
            f"| [{result['id']}](https://www.strava.com/activities/{result['id']}) | "
            f"{result['points']} | {result['strava_distance_km']:.2f} / "
            f"{result['gpx_haversine_distance_km']:.3f} | {result['strava_gain_m']} | "
            f"{result['raw_gain_loss_m'][0]:.1f} | {five['current_full_gain_m']:.1f} | "
            f"{five['current_1km_gain_m']:.1f} | {five['probe_gain_m']:.1f} |"
        )
    rows += ["", "Limiar de 5 m = precisão horizontal constante **hipotética**. Sonda = mediana causal de 5 pontos + excursão persistente; não implementada no Android. GPX Strava não contém precisão por fix, pausas nem referência independente de terreno."]
    return "\n".join(rows)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--format", choices=("json", "markdown"), default="markdown")
    parser.add_argument("--gpx-dir", type=Path, default=ROOT / "tools/gpx/issue-1")
    args = parser.parse_args()
    results = load_results(gpx_dir=args.gpx_dir)
    if args.format == "json":
        print(json.dumps(results, ensure_ascii=False, indent=2))
    else:
        print(render_markdown(results))


if __name__ == "__main__":
    main()
