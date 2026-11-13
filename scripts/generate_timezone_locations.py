#!/usr/bin/env python3
"""Writes shared/data/timezone_locations.json: where each time zone's city is.

The Daylight theme is light while the sun is up and dark while it is down.
Neither app asks for the user's location, so both take it to be the reference
city of the device's time zone, which the IANA time zone database lists in
zone.tab. Older names for a zone (Asia/Calcutta for Asia/Kolkata), which
devices still report, get the coordinates of the zone they now link to.

    python3 scripts/generate_timezone_locations.py [/usr/share/zoneinfo]
"""
import json
import re
import sys
from pathlib import Path

OUTPUT = Path(__file__).resolve().parent.parent / "shared/data/timezone_locations.json"


def degrees(iso6709: str) -> float:
    """'+5955' or '-0694612' (±DDMM[SS] or ±DDDMM[SS]) as decimal degrees."""
    sign = -1 if iso6709[0] == "-" else 1
    digits = iso6709[1:]
    whole = len(digits) - (4 if len(digits) in (6, 7) else 2)
    d, rest = int(digits[:whole]), digits[whole:]
    return sign * (d + int(rest[:2]) / 60 + int(rest[2:] or 0) / 3600)


def main() -> None:
    zoneinfo = Path(sys.argv[1] if len(sys.argv) > 1 else "/usr/share/zoneinfo")
    locations = {}
    for line in (zoneinfo / "zone.tab").read_text().splitlines():
        if line.startswith("#") or not line.strip():
            continue
        _, coordinates, zone = line.split("\t")[:3]
        lat, lon = re.fullmatch(r"([+-]\d+)([+-]\d+)", coordinates).groups()
        locations[zone] = [round(degrees(lat), 2), round(degrees(lon), 2)]

    # "L <target> <alias>" lines link an old name to its current zone.
    for line in (zoneinfo / "tzdata.zi").read_text().splitlines():
        parts = line.split()
        if len(parts) == 3 and parts[0] == "L" and parts[1] in locations:
            locations.setdefault(parts[2], locations[parts[1]])

    rows = [f'  {json.dumps(zone)}: {json.dumps(locations[zone])}' for zone in sorted(locations)]
    OUTPUT.write_text("{\n" + ",\n".join(rows) + "\n}\n")
    print(f"{len(locations)} time zones -> {OUTPUT}")


if __name__ == "__main__":
    main()
