#!/usr/bin/env python3
"""Generate the shared country neighbour graph and border distances.

Which countries border each other, and how far apart every pair of outlines
is, from shared/data/world.geojson plus a short, hand-reviewed list of links
(shared/data/neighbor_links.json). Both apps read the graph and the distances,
and Postgres reads the graph (Travel is scored on the server; Heat scores only
count taps), so the daily Travel route and the Heat colour come out the same
everywhere:

  shared/data/neighbors.json                  ISO -> sorted neighbour ISOs, and the graph version
  shared/data/border_distances.json           km between every pair, upper triangle
  shared/fixtures/daily/graph.json            test vectors for both apps and pgTAP
  shared/supabase/migrations/*_load_country_graph.sql
                                              the graph as Postgres rows
  shared/supabase/tests/country_graph_vectors_test.sql
                                              graph.json's graph vectors as a pgTAP test

Rules (restated in docs/DATA.md):

* Two polygon countries are **land neighbours** when their outlines share a
  point. Natural Earth borders are shared arcs, and mapshaper simplifies them
  as one topology, so neighbours share exact coordinates. A border the
  simplification pulled apart goes in neighbor_links.json (IL-SY).
* **Point countries** (the microstates drawn as dots) have no outline, so none
  of them borders anything by geometry. Their land borders are listed by hand
  in neighbor_links.json, like the bridges and tunnels.
* A **link** counts as a border: neighbours both ways, 0 km apart.
* The **graph version** is a hash of the borders alone (not the distances,
  which the server never sees). The apps read it from neighbors.json and
  Postgres returns it from country_graph_version(), so a Travel answer made
  on an older app's graph can be told apart from one on the server's.
* The **border distance** is the great-circle distance in km between the
  closest points of two outlines (a point country's outline is its point, and
  a point inside a polygon is 0 km from it), rounded to an integer. It is 0
  exactly for neighbours: a non-neighbour is never rounded down to 0.

Distances need numpy (`pip install numpy`); the rest is the standard library.

Usage:  python3 scripts/generate_neighbors.py [--check]

  --check  exit non-zero if any generated file on disk is stale (for CI)

When the graph changes, the newest load migration is rewritten while it exists
only on your branch, and a new timestamped one is written once it is on
origin/master: master's migrations may already be deployed, and `supabase db
push` never re-runs one, so an edit there would never reach production.
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import math
import subprocess
import sys
from collections import deque
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np

REPO_ROOT = Path(__file__).resolve().parents[1]
SOURCE = REPO_ROOT / "shared/data/world.geojson"
LINKS = REPO_ROOT / "shared/data/neighbor_links.json"
NEIGHBORS = REPO_ROOT / "shared/data/neighbors.json"
DISTANCES = REPO_ROOT / "shared/data/border_distances.json"
VECTORS = REPO_ROOT / "shared/fixtures/daily/graph.json"
MIGRATIONS = REPO_ROOT / "shared/supabase/migrations"
MIGRATION_SUFFIX = "_load_country_graph.sql"
PGTAP_VECTORS = REPO_ROOT / "shared/supabase/tests/country_graph_vectors_test.sql"

# Mean Earth radius (IUGG), the one both apps use for great-circle distances.
EARTH_RADIUS_KM = 6371.0088

# Segments per bounding sphere in the distance search. Only affects speed.
CHUNK = 16

# Outlines this close (as a unit-sphere chord, ~1 cm) touch, so the search stops.
TOUCHING_CHORD = 1e-9

# What graph.json pins. Neighbour sets for a spread of countries (enclaves,
# many neighbours, islands, curated links, point countries); shortest routes
# that are unique, tied, long, through a link, impossible and trivial; and
# distances across one border, one strait and half the world.
VECTOR_NEIGHBORS = ["FR", "DE", "CN", "LS", "VA", "GB", "AU", "SG"]
VECTOR_ROUTES = [
    ("FR", "DE"), ("FR", "FR"), ("PT", "DE"), ("ES", "PL"), ("NO", "IT"),
    ("GB", "IT"), ("DK", "AT"), ("FI", "TR"), ("MX", "AR"), ("ZA", "KE"),
    ("FR", "AU"), ("IS", "NO"),
]
VECTOR_DISTANCES = [
    ("FR", "DE"), ("GB", "FR"), ("IE", "IS"), ("ES", "MA"), ("NZ", "ES"),
    ("VA", "SM"), ("US", "RU"), ("AR", "AU"),
]


# ---------------------------------------------------------------------------
# Countries and geometry


@dataclass
class Country:
    iso: str
    name: str
    # Every ring (outer boundaries and holes) as an (n, 3) array of unit
    # vectors, for distances.
    rings: list[np.ndarray] = field(default_factory=list)
    # Outer rings and holes as [lon, lat] lists, for point-in-polygon.
    polygons: list[tuple[list, list[list]]] = field(default_factory=list)
    point: tuple[float, float] | None = None  # (lon, lat) for point countries

    @property
    def is_point(self) -> bool:
        return self.point is not None


def unit_vectors(lon_lat: np.ndarray) -> np.ndarray:
    lon = np.radians(lon_lat[:, 0])
    lat = np.radians(lon_lat[:, 1])
    return np.stack(
        [np.cos(lat) * np.cos(lon), np.cos(lat) * np.sin(lon), np.sin(lat)], axis=1
    )


def load_countries(path: Path) -> list[Country]:
    """Every feature with an ISO code, in ISO order."""
    with path.open() as handle:
        features = json.load(handle)["features"]
    countries = []
    for feature in features:
        iso = feature.get("id")
        geometry = feature.get("geometry") or {}
        if not iso or not geometry.get("type"):
            continue
        country = Country(iso=iso, name=feature["properties"]["name"])
        if geometry["type"] == "Point":
            lon, lat = geometry["coordinates"][:2]
            country.point = (lon, lat)
        else:
            polygons = (
                [geometry["coordinates"]]
                if geometry["type"] == "Polygon"
                else geometry["coordinates"]
            )
            for polygon in polygons:
                if not polygon:
                    continue
                country.polygons.append((polygon[0], polygon[1:]))
                for ring in polygon:
                    country.rings.append(unit_vectors(np.asarray(ring, dtype=float)))
        countries.append(country)
    countries.sort(key=lambda c: c.iso)
    return countries


def segments_of(country: Country) -> tuple[np.ndarray, np.ndarray]:
    """(starts, ends) of every outline segment; a point is one zero-length segment."""
    if country.is_point:
        p = unit_vectors(np.asarray([country.point], dtype=float))
        return p, p
    starts = np.concatenate([ring[:-1] for ring in country.rings])
    ends = np.concatenate([ring[1:] for ring in country.rings])
    return starts, ends


def point_in_ring(lon: float, lat: float, ring: list) -> bool:
    inside = False
    for (x1, y1), (x2, y2) in zip(ring, ring[1:]):
        if (y1 > lat) != (y2 > lat):
            if lon < x1 + (lat - y1) * (x2 - x1) / (y2 - y1):
                inside = not inside
    return inside


def contains(country: Country, lon: float, lat: float) -> bool:
    return any(
        point_in_ring(lon, lat, outer)
        and not any(point_in_ring(lon, lat, hole) for hole in holes)
        for outer, holes in country.polygons
    )


def segment_distances(p1, q1, p2, q2) -> np.ndarray:
    """Chord between the closest points of each pair of outline segments, broadcast.

    The closest points are found between the segments as chords of the unit
    sphere (Ericson, Real-Time Collision Detection, 5.1.9, with every branch as
    a `where`), so a crossing pair comes out as (nearly) 0, which a
    vertex-to-segment search would miss. They are then projected onto the
    sphere, which puts them on the segments' great-circle arcs: chords dip
    below the surface, and measuring between them would make far-apart
    outlines come out too close (about 5 km under a 500 km segment).
    """
    d1 = q1 - p1
    d2 = q2 - p2
    r = p1 - p2
    a = np.einsum("...i,...i", d1, d1)
    e = np.einsum("...i,...i", d2, d2)
    f = np.einsum("...i,...i", d2, r)
    c = np.einsum("...i,...i", d1, r)
    b = np.einsum("...i,...i", d1, d2)
    eps = 1e-18
    a_zero = a <= eps
    e_zero = e <= eps
    a_safe = np.where(a_zero, 1.0, a)
    e_safe = np.where(e_zero, 1.0, e)
    denom = a * e - b * b
    s = np.where(denom > eps, np.clip((b * f - c * e) / np.where(denom > eps, denom, 1.0), 0, 1), 0.0)
    t = (b * s + f) / e_safe
    s = np.where(t < 0, np.clip(-c / a_safe, 0, 1), np.where(t > 1, np.clip((b - c) / a_safe, 0, 1), s))
    t = np.clip(t, 0, 1)
    # Degenerate segments (points).
    s = np.where(a_zero, 0.0, np.where(e_zero, np.clip(-c / a_safe, 0, 1), s))
    t = np.where(e_zero, 0.0, np.where(a_zero, np.clip(f / e_safe, 0, 1), t))
    on_1 = p1 + d1 * s[..., None]
    on_2 = p2 + d2 * t[..., None]
    gap = on_1 / np.linalg.norm(on_1, axis=-1, keepdims=True) - on_2 / np.linalg.norm(on_2, axis=-1, keepdims=True)
    return np.sqrt(np.einsum("...i,...i", gap, gap))


def chord_to_km(chord: float) -> float:
    return 2 * math.asin(min(chord / 2, 1.0)) * EARTH_RADIUS_KM


@dataclass
class Chunks:
    """CHUNK consecutive segments of one country, with a sphere bounding their arcs."""

    starts: np.ndarray  # (k, CHUNK, 3), padded by repeating the last segment
    ends: np.ndarray
    centers: np.ndarray  # (k, 3)
    radii: np.ndarray  # (k,)


def chunks_of(country: Country) -> Chunks:
    starts, ends = segments_of(country)
    count = len(starts)
    padded = -(-count // CHUNK) * CHUNK
    index = np.minimum(np.arange(padded), count - 1)
    s = starts[index].reshape(-1, CHUNK, 3)
    e = ends[index].reshape(-1, CHUNK, 3)
    points = np.concatenate([s, e], axis=1)
    centers = points.mean(axis=1)
    # Grown by the most any arc bulges out past its chord (its sag, at the
    # middle), so each sphere holds the arcs that segment_distances measures on.
    half_chords = np.linalg.norm(e - s, axis=2) / 2
    sag = 1 - np.sqrt(1 - np.minimum(half_chords, 1.0) ** 2)
    radii = np.linalg.norm(points - centers[:, None, :], axis=2).max(axis=1) + sag.max(axis=1)
    return Chunks(s, e, centers, radii)


def outline_distance(a: Chunks, b: Chunks) -> float:
    """Closest chord distance between two outlines (branch and bound on chunks)."""
    gaps = np.linalg.norm(a.centers[:, None, :] - b.centers[None, :, :], axis=2)
    lower = np.maximum(gaps - a.radii[:, None] - b.radii[None, :], 0.0)
    # Any real pair of endpoints bounds the answer from above.
    best = float(np.min(np.linalg.norm(a.starts[:, None, 0, :] - b.starts[None, :, 0, :], axis=2)))
    candidates = np.argwhere(lower <= best)
    candidates = candidates[np.argsort(lower[candidates[:, 0], candidates[:, 1]])]
    batch = 64
    for begin in range(0, len(candidates), batch):
        pairs = candidates[begin:begin + batch]
        if lower[pairs[0, 0], pairs[0, 1]] > best:
            break
        d = segment_distances(
            a.starts[pairs[:, 0]][:, :, None, :],
            a.ends[pairs[:, 0]][:, :, None, :],
            b.starts[pairs[:, 1]][:, None, :, :],
            b.ends[pairs[:, 1]][:, None, :, :],
        )
        best = min(best, float(d.min()))
        if best < TOUCHING_CHORD:
            break  # touching: nothing can be closer
    return best


def border_distances(countries: list[Country]) -> list[list[float]]:
    """Unrounded km between every pair of outlines, as a full symmetric matrix."""
    chunks = [chunks_of(c) for c in countries]
    n = len(countries)
    km = [[0.0] * n for _ in range(n)]
    for i in range(n):
        for j in range(i + 1, n):
            a, b = countries[i], countries[j]
            if a.is_point and not b.is_point and contains(b, *a.point):
                d = 0.0
            elif b.is_point and not a.is_point and contains(a, *b.point):
                d = 0.0
            else:
                d = chord_to_km(outline_distance(chunks[i], chunks[j]))
            km[i][j] = km[j][i] = d
    return km


# ---------------------------------------------------------------------------
# The graph


def load_links(path: Path, codes: set[str]) -> list[dict]:
    with path.open() as handle:
        links = json.load(handle)["links"]
    for link in links:
        a, b = link["between"]
        for code in (a, b):
            if code not in codes:
                raise SystemExit(f"neighbor_links.json: {code} is not a country in world.geojson")
        if a == b:
            raise SystemExit(f"neighbor_links.json: {a} is linked to itself")
        if not link.get("note"):
            raise SystemExit(f"neighbor_links.json: {a}-{b} has no note")
    return links


def land_neighbors(countries: list[Country]) -> dict[str, set[str]]:
    """Polygon countries that share an outline coordinate (point countries never do)."""
    owners: dict[tuple[float, float], set[str]] = {}
    for country in countries:
        for outer, holes in country.polygons:
            for ring in [outer, *holes]:
                for lon, lat in ring:
                    owners.setdefault((lon, lat), set()).add(country.iso)
    neighbors: dict[str, set[str]] = {c.iso: set() for c in countries}
    for isos in owners.values():
        for iso in isos:
            neighbors[iso] |= isos - {iso}
    return neighbors


def build_graph(countries: list[Country], raw_km: list[list[float]], links: list[dict]):
    codes = [c.iso for c in countries]
    index = {code: i for i, code in enumerate(codes)}
    neighbors = land_neighbors(countries)
    for link in links:
        a, b = link["between"]
        if b in neighbors[a]:
            raise SystemExit(f"neighbor_links.json: {a}-{b} already border by land")
        neighbors[a].add(b)
        neighbors[b].add(a)

    km: list[list[int]] = []
    for i, a in enumerate(codes):
        row = []
        for j, b in enumerate(codes):
            if i == j or b in neighbors[a]:
                row.append(0)
            else:
                # Never 0 for non-neighbours: 0 km means "borders".
                row.append(max(1, round(raw_km[i][j])))
        km.append(row)
    sorted_neighbors = {code: sorted(neighbors[code]) for code in codes}
    return codes, index, sorted_neighbors, km


def shortest_routes(neighbors: dict[str, list[str]], start: str, goal: str) -> list[list[str]]:
    """Every shortest route from start to goal, both ends included, sorted.

    [start] alone when they are the same country; empty when no route exists
    or a code is unknown.

    Breadth-first from start, then the routes walk back through each
    country's predecessors one step closer. The apps and Postgres implement
    the same thing; graph.json pins them to this one.
    """
    if start not in neighbors or goal not in neighbors:
        return []
    if start == goal:
        return [[start]]
    depth = {start: 0}
    queue = deque([start])
    while queue:
        current = queue.popleft()
        if current == goal:
            break
        for next_code in neighbors[current]:
            if next_code not in depth:
                depth[next_code] = depth[current] + 1
                queue.append(next_code)
    if goal not in depth:
        return []

    def back(code: str) -> list[list[str]]:
        if code == start:
            return [[start]]
        return [
            route + [code]
            for previous in neighbors[code]
            if depth.get(previous) == depth[code] - 1
            for route in back(previous)
        ]

    return sorted(back(goal))


def graph_version(neighbors: dict[str, list[str]]) -> str:
    """12 hex digits of SHA-256 over the canonical border lists: changes exactly when a border does."""
    canonical = json.dumps(neighbors, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(canonical.encode()).hexdigest()[:12]


def check_graph(codes, neighbors, km, links) -> None:
    """The invariants the apps rely on. Fails loudly rather than writing bad data."""
    for a in codes:
        for b in neighbors[a]:
            assert a != b, f"{a} borders itself"
            assert a in neighbors[b], f"{a}->{b} has no {b}->{a}"
    n = len(codes)
    for i in range(n):
        for j in range(n):
            assert km[i][j] == km[j][i], f"{codes[i]}-{codes[j]} distance is not symmetric"
            if i != j:
                assert (km[i][j] == 0) == (codes[j] in neighbors[codes[i]]), (
                    f"{codes[i]}-{codes[j]}: 0 km must mean neighbours"
                )
    for link in links:
        a, b = link["between"]
        assert b in neighbors[a]


# ---------------------------------------------------------------------------
# Output


def render_neighbors(codes, neighbors, version) -> str:
    lines = [f'    "{code}": {json.dumps(neighbors[code])}' for code in codes]
    return (
        "{\n"
        '  "_generatedBy": "scripts/generate_neighbors.py",\n'
        f'  "version": {json.dumps(version)},\n'
        '  "neighbors": {\n' + ",\n".join(lines) + "\n  }\n}\n"
    )


def render_distances(codes, km) -> str:
    """Upper triangle: row i holds the km from codes[i] to codes[i+1:], one row per line."""
    rows = [
        "    " + json.dumps(km[i][i + 1:], separators=(",", ":"))
        for i in range(len(codes))
    ]
    return (
        "{\n"
        '  "_generatedBy": "scripts/generate_neighbors.py",\n'
        '  "_format": "rows[i][k] is the km between codes[i] and codes[i + 1 + k]",\n'
        f'  "codes": {json.dumps(codes)},\n'
        '  "rows": [\n' + ",\n".join(rows) + "\n  ]\n}\n"
    )


def build_vectors(codes, index, neighbors, km, version) -> dict:
    routes = []
    for start, goal in VECTOR_ROUTES:
        found = shortest_routes(neighbors, start, goal)
        routes.append({
            "from": start,
            "to": goal,
            # Countries in between: 0 for neighbours, -1 when no route exists.
            "between": len(found[0]) - 2 if found and start != goal else (-1 if not found else 0),
            "routes": found,
        })
    return {
        "_generatedBy": "scripts/generate_neighbors.py",
        "_about": (
            "Test vectors pinning the iOS, Android and Postgres graph lookups to the "
            "generator. 'between' counts the countries strictly between from and to."
        ),
        "version": version,
        "neighbors": {code: neighbors[code] for code in VECTOR_NEIGHBORS},
        "routes": routes,
        "distances": [
            {"a": a, "b": b, "km": km[index[a]][index[b]]} for a, b in VECTOR_DISTANCES
        ],
    }


def render_vectors(vectors: dict) -> str:
    lines = [
        "{",
        f'  "_generatedBy": {json.dumps(vectors["_generatedBy"])},',
        f'  "_about": {json.dumps(vectors["_about"])},',
        f'  "version": {json.dumps(vectors["version"])},',
        '  "neighbors": {',
        ",\n".join(f"    {json.dumps(k)}: {json.dumps(v)}" for k, v in vectors["neighbors"].items()),
        "  },",
        '  "routes": [',
        ",\n".join("    " + json.dumps(r, separators=(",", ":")) for r in vectors["routes"]),
        "  ],",
        '  "distances": [',
        ",\n".join("    " + json.dumps(d, separators=(",", ":")) for d in vectors["distances"]),
        "  ]",
        "}",
    ]
    return "\n".join(lines) + "\n"


def render_migration(codes, neighbors, version) -> str:
    edges = [(a, b) for a in codes for b in neighbors[a]]
    out = [
        "-- Generated by scripts/generate_neighbors.py from shared/data/neighbors.json.",
        "-- Do not edit: regenerate (see docs/DATA.md).",
        "--",
        "-- Replaces the whole graph, so the newest of these migrations is the graph.",
        "",
        "truncate public.country_neighbors;",
        "",
        "insert into public.country_neighbors (a, b) values",
        ",\n".join(f"  ('{a}', '{b}')" for a, b in edges) + ";",
        "",
        "-- The graph's version, as neighbors.json has it: what an app's Travel answer",
        "-- was made against.",
        "create or replace function public.country_graph_version()",
        "returns text",
        "language sql",
        "stable",
        "set search_path = ''",
        f"as $$ select '{version}' $$;",
        "",
        "grant execute on function public.country_graph_version() to anon, authenticated;",
    ]
    return "\n".join(out) + "\n"


def sql_text_array(values: list[str]) -> str:
    return "array[" + ", ".join(f"'{v}'" for v in values) + "]::text[]"


def render_pgtap(vectors: dict) -> str:
    tests = []
    tests.append(
        f"select is(public.country_graph_version(), '{vectors['version']}', 'the graph version');"
    )
    for code, expected in vectors["neighbors"].items():
        tests.append(
            "select is(\n"
            f"  (select coalesce(array_agg(b order by b), '{{}}') from public.country_neighbors where a = '{code}'),\n"
            f"  {sql_text_array(expected)},\n"
            f"  'neighbours of {code}'\n);"
        )
    for route in vectors["routes"]:
        expected = ", ".join(f"'{json.dumps(r)}'::jsonb" for r in route["routes"])
        expected_array = f"array[{expected}]::jsonb[]" if route["routes"] else "'{}'::jsonb[]"
        tests.append(
            "select is(\n"
            f"  (select coalesce(array_agg(to_jsonb(r.route) order by r.route), '{{}}')\n"
            f"     from public.shortest_routes('{route['from']}', '{route['to']}') r),\n"
            f"  {expected_array},\n"
            f"  'shortest routes {route['from']} to {route['to']}'\n);"
        )
    return (
        "-- Generated by scripts/generate_neighbors.py from shared/fixtures/daily/graph.json,\n"
        "-- the vectors the iOS and Android graph tests read too (Postgres has no distances,\n"
        "-- so only the graph ones). Do not edit: regenerate.\n"
        "begin;\n"
        f"select plan({len(tests)});\n\n"
        + "\n\n".join(tests)
        + "\n\nselect * from finish();\nrollback;\n"
    )


def newest_migration() -> Path | None:
    found = sorted(MIGRATIONS.glob(f"*{MIGRATION_SUFFIX}"))
    return found[-1] if found else None


def on_master(path: Path) -> bool:
    """Whether origin/master has this file, so it may be deployed and must not change.

    True when there is no origin/master to ask, so the caller errs towards a new
    migration.
    """
    def git(*args: str) -> bool:
        return subprocess.run(["git", *args], cwd=REPO_ROOT, capture_output=True).returncode == 0

    if not git("rev-parse", "--verify", "--quiet", "origin/master"):
        print("no origin/master to compare with, so writing a new migration", file=sys.stderr)
        return True
    return git("cat-file", "-e", f"origin/master:{path.relative_to(REPO_ROOT).as_posix()}")


def migration_path(content: str, check: bool) -> Path:
    """Where the graph's load migration goes: the newest one, or a new one.

    The newest one is kept when it already says this, or when it is not on
    origin/master yet. --check never picks a new one, so a stale newest
    migration fails it.
    """
    newest = newest_migration()
    if newest is not None and (check or newest.read_text() == content or not on_master(newest)):
        return newest
    stamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%d%H%M%S")
    return MIGRATIONS / f"{stamp}{MIGRATION_SUFFIX}"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="verify the generated files instead of writing them")
    args = parser.parse_args()

    countries = load_countries(SOURCE)
    links = load_links(LINKS, {c.iso for c in countries})
    codes, index, neighbors, km = build_graph(countries, border_distances(countries), links)
    check_graph(codes, neighbors, km, links)
    version = graph_version(neighbors)
    vectors = build_vectors(codes, index, neighbors, km, version)

    migration = render_migration(codes, neighbors, version)
    outputs = {
        NEIGHBORS: render_neighbors(codes, neighbors, version),
        DISTANCES: render_distances(codes, km),
        VECTORS: render_vectors(vectors),
        migration_path(migration, args.check): migration,
        PGTAP_VECTORS: render_pgtap(vectors),
    }

    if args.check:
        stale = [p for p, content in outputs.items() if not p.exists() or p.read_text() != content]
        for path in stale:
            print(f"{path.relative_to(REPO_ROOT)} is stale — run scripts/generate_neighbors.py", file=sys.stderr)
        if stale:
            return 1
        print("country graph files are up to date")
        return 0

    for path, content in outputs.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
        print(f"wrote {path.relative_to(REPO_ROOT)}")
    edges = sum(len(v) for v in neighbors.values()) // 2
    isolated = sum(1 for v in neighbors.values() if not v)
    print(f"{len(codes)} countries, {edges} borders ({len(links)} curated), {isolated} with no neighbour, version {version}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
