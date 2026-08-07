#!/usr/bin/env python3
"""Generate the cross-platform country fixture from shared/data/world.geojson.

`shared/fixtures/expected_countries.json` is the contract the iOS and Android
GeoJSON parsers are both tested against: same countries, same order, same
capitals, same ring/point counts, same bounding boxes. When `world.geojson` is
regenerated (`scripts/update_geometry.sh`), regenerate this too and review the
diff — a surprising change here is a real change to what both apps render.

The rules below intentionally restate the parsers' behaviour rather than
importing it: three independent implementations agreeing is what makes the
fixture a drift guard instead of a copy of one platform's output.

Usage:  python3 scripts/generate_country_fixture.py [--check]

  --check  exit non-zero if the fixture on disk is stale (for CI)
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
SOURCE = REPO_ROOT / "shared/data/world.geojson"
FIXTURE = REPO_ROOT / "shared/fixtures/expected_countries.json"


def rings_of(geometry: dict) -> tuple[list[list], list[list]]:
    """Split a Polygon/MultiPolygon into (outer rings, hole rings).

    The first ring of every polygon is its outer boundary; the rest are holes
    (e.g. the Lesotho enclave inside South Africa).
    """
    kind = geometry.get("type")
    if kind == "Polygon":
        polygons = [geometry.get("coordinates") or []]
    elif kind == "MultiPolygon":
        polygons = geometry.get("coordinates") or []
    else:
        return [], []

    outers = [polygon[0] for polygon in polygons if polygon]
    holes = [ring for polygon in polygons for ring in polygon[1:]]
    return outers, holes


def bbox_of(rings: list[list]) -> list[float] | None:
    """[minLon, minLat, maxLon, maxLat] over every coordinate in `rings`."""
    lons = [point[0] for ring in rings for point in ring]
    lats = [point[1] for ring in rings for point in ring]
    if not lons:
        return None
    return [min(lons), min(lats), max(lons), max(lats)]


def country_of(feature: dict) -> dict | None:
    """The fixture entry for one feature, or None if the parsers would skip it."""
    properties = feature.get("properties") or {}
    name = properties.get("name") or properties.get("NAME")
    geometry = feature.get("geometry") or {}
    if not name or not geometry.get("type"):
        return None

    capital = None
    if (
        properties.get("capital")
        and properties.get("capitalLat") is not None
        and properties.get("capitalLon") is not None
    ):
        capital = {
            "name": properties["capital"],
            "lat": properties["capitalLat"],
            "lon": properties["capitalLon"],
        }

    entry = {
        "iso": feature.get("id"),
        "name": name,
        "continent": properties.get("continent"),
        "capital": capital,
    }

    if geometry["type"] == "Point":
        coordinates = geometry.get("coordinates") or []
        if len(coordinates) < 2:
            return None
        entry.update(
            isPointCountry=True,
            point={"lat": coordinates[1], "lon": coordinates[0]},
            polygonPointCounts=[],
            holePointCounts=[],
            bbox=None,
        )
        return entry

    outers, holes = rings_of(geometry)
    if not outers:
        return None
    entry.update(
        isPointCountry=properties.get("renderAs") == "point",
        point=None,
        polygonPointCounts=[len(ring) for ring in outers],
        holePointCounts=[len(ring) for ring in holes],
        bbox=bbox_of(outers + holes),
    )
    return entry


def build_fixture() -> dict:
    with SOURCE.open() as handle:
        features = json.load(handle).get("features") or []
    countries = [entry for entry in (country_of(f) for f in features) if entry]
    return {
        "_generatedBy": "scripts/generate_country_fixture.py",
        "_source": "shared/data/world.geojson",
        "countryCount": len(countries),
        "totalCoordinateCount": sum(
            sum(c["polygonPointCounts"]) + sum(c["holePointCounts"]) for c in countries
        ),
        "countries": countries,
    }


def render(fixture: dict) -> str:
    """Serialize with one country per line, so diffs stay readable."""
    head = {key: value for key, value in fixture.items() if key != "countries"}
    lines = [json.dumps(head, indent=2)[:-2].rstrip() + ",", '  "countries": [']
    entries = [
        "    " + json.dumps(country, separators=(",", ":")) for country in fixture["countries"]
    ]
    lines.append(",\n".join(entries))
    lines.append("  ]")
    lines.append("}")
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        help="verify the fixture matches world.geojson instead of rewriting it",
    )
    args = parser.parse_args()

    content = render(build_fixture())

    if args.check:
        current = FIXTURE.read_text() if FIXTURE.exists() else ""
        if current != content:
            print(
                f"{FIXTURE.relative_to(REPO_ROOT)} is stale — "
                "run scripts/generate_country_fixture.py",
                file=sys.stderr,
            )
            return 1
        print(f"{FIXTURE.relative_to(REPO_ROOT)} is up to date")
        return 0

    FIXTURE.parent.mkdir(parents=True, exist_ok=True)
    FIXTURE.write_text(content)
    fixture = json.loads(content)
    print(
        f"wrote {FIXTURE.relative_to(REPO_ROOT)}: "
        f"{fixture['countryCount']} countries, "
        f"{fixture['totalCoordinateCount']} coordinates"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
