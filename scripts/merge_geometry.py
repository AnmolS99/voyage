#!/usr/bin/env python3
"""Merge Natural Earth geometry into voyage/world.geojson.

Replaces only the `geometry` of each feature, matched by ISO 3166-1 alpha-2
code (feature.id in world.geojson <-> ISO_A2_EH / ISO_A2 in Natural Earth).
All properties (name, continent, capital, ...) and feature order are preserved.

Microstates listed in PROMOTE are upgraded from Point features (rendered as
dots) to their real Natural Earth polygons. All other Point features stay dots.

Usage: merge_geometry.py <ne_simplified.json> <world.geojson>
"""

import json
import sys

# Point-countries large enough (roughly >= 1000 km^2) to render as real polygons.
PROMOTE = {"CY", "LU", "CV", "WS", "KM", "MU", "ST"}


def point_count(geometry):
    if geometry is None:
        return 0
    if geometry["type"] == "Polygon":
        return sum(len(ring) for ring in geometry["coordinates"])
    if geometry["type"] == "MultiPolygon":
        return sum(len(ring) for poly in geometry["coordinates"] for ring in poly)
    return 0


def polygons_of(geometry):
    """All polygons of a geometry as MultiPolygon-style coordinates."""
    if geometry["type"] == "Polygon":
        return [geometry["coordinates"]]
    if geometry["type"] == "MultiPolygon":
        return geometry["coordinates"]
    return []


def build_ne_index(ne_features):
    """Index NE geometry by ISO alpha-2 code. Map units split several countries
    into constituent units sharing one code (e.g. GB = England + Scotland +
    Wales + N. Ireland, BE = Flanders + Wallonia + Brussels, PT = mainland +
    Madeira + Azores), so all units for a code are unioned into one
    MultiPolygon."""
    index = {}
    for feature in ne_features:
        props = feature["properties"]
        code = props.get("ISO_A2_EH") or ""
        if len(code) != 2 or not code.isalpha():
            code = props.get("ISO_A2") or ""
        if len(code) != 2 or not code.isalpha():
            continue
        if feature["geometry"] is None:
            continue
        polygons = polygons_of(feature["geometry"])
        if not polygons:
            continue
        if code in index:
            index[code]["coordinates"].extend(polygons)
        else:
            index[code] = {"type": "MultiPolygon", "coordinates": list(polygons)}
    return index


def main():
    ne_path, world_path = sys.argv[1], sys.argv[2]
    with open(ne_path) as f:
        ne_index = build_ne_index(json.load(f)["features"])
    with open(world_path) as f:
        world = json.load(f)

    old_total = new_total = 0
    unmatched, promoted, kept_points = [], [], []

    for feature in world["features"]:
        iso = feature.get("id")
        geometry = feature["geometry"]
        old_total += point_count(geometry)

        if geometry["type"] == "Point":
            if iso in PROMOTE and iso in ne_index:
                feature["geometry"] = ne_index[iso]
                feature["properties"].pop("renderAs", None)
                promoted.append(iso)
            else:
                kept_points.append(iso)
        elif iso in ne_index:
            feature["geometry"] = ne_index[iso]
        else:
            unmatched.append(iso)

        new_total += point_count(feature["geometry"])

    with open(world_path, "w") as f:
        json.dump(world, f, separators=(",", ":"), ensure_ascii=False)

    print(f"  boundary points: {old_total:,} -> {new_total:,}")
    print(f"  promoted to polygons: {', '.join(promoted) or 'none'}")
    print(f"  still point-rendered: {len(kept_points)} countries")
    if unmatched:
        print(f"  WARNING - no NE match, kept old geometry: {', '.join(unmatched)}")


if __name__ == "__main__":
    main()
