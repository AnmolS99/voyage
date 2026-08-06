#!/bin/bash
# Regenerates shared/data/world.geojson country geometry from Natural Earth 1:10m data.
#
# Downloads NE admin-0 countries, simplifies to the app's point budget with
# mapshaper, and merges the new geometry into the existing world.geojson
# (properties, ids, and point-country features are preserved by the merge script).
#
# Requires: node (npx), python3, curl.
# Usage: ./scripts/update_geometry.sh
#
# After running, regenerate the globe cache (see CLAUDE.md "Globe Cache Generation").

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

# Map units (not "countries") so overseas territories the app tracks separately
# (e.g. French Guiana) keep their own ISO-coded features instead of being folded
# into the parent country.
NE_URL="https://naciscdn.org/naturalearth/10m/cultural/ne_10m_admin_0_map_units.zip"

# Simplification budget: ~30% weighted retention of 1:10m ≈ 175k points world-wide
# (~17x the original 1:110m data). This is the main knob for detail vs. size.
SIMPLIFY_PCT="30%"
MIN_ISLAND_AREA="10km2"

echo "Downloading Natural Earth 1:10m admin-0 countries..."
curl -sL -o "$WORK_DIR/ne10m.zip" "$NE_URL"
unzip -o -q "$WORK_DIR/ne10m.zip" -d "$WORK_DIR"

echo "Simplifying with mapshaper (retention: $SIMPLIFY_PCT, min island: $MIN_ISLAND_AREA)..."
# Some NE units carry no valid ISO code (-99: disputed/special areas) or a code the app
# has no feature for (SJ). Left alone they'd vanish and leave holes that the previous
# dataset didn't have. Remap them (by ADM0_A3) to the country that rendered them before:
#   CYN/WSB/ESB/CNM -> CY  (N. Cyprus, UK bases, UN buffer: whole Cyprus island)
#   SOL -> SO  (Somaliland)          KOR/PRK -> KR/KP  (Korean DMZ halves)
#   USG -> CU  (Guantanamo Bay)      KAS -> PK  (Siachen Glacier)
#   SPI -> AR  (S. Patagonian Ice Field)   BRT -> SD  (Bir Tawil)
#   ISO 'SJ' (Svalbard) -> NO
REMAP='var m={CYN:"CY",WSB:"CY",ESB:"CY",CNM:"CY",SOL:"SO",KOR:"KR",PRK:"KP",USG:"CU",KAS:"PK",SPI:"AR",BRT:"SD"};
if (ISO_A2_EH=="SJ") ISO_A2_EH="NO"; else if (m[ADM0_A3]) ISO_A2_EH=m[ADM0_A3];'

# dissolve2 merges map units sharing an ISO code (England+Scotland+Wales+N.Ireland -> GB,
# Flanders+Wallonia+Brussels -> BE, ...) so no internal unit borders are drawn.
npx -y mapshaper "$WORK_DIR/ne_10m_admin_0_map_units.shp" \
    -clean \
    -filter-islands min-area="$MIN_ISLAND_AREA" \
    -each "$REMAP" \
    -dissolve2 ISO_A2_EH copy-fields=ISO_A2 \
    -simplify weighted keep-shapes "$SIMPLIFY_PCT" \
    -clean \
    -o format=geojson precision=0.0001 "$WORK_DIR/ne_simplified.json"

echo "Merging geometry into shared/data/world.geojson..."
python3 "$SCRIPT_DIR/merge_geometry.py" \
    "$WORK_DIR/ne_simplified.json" \
    "$PROJECT_DIR/shared/data/world.geojson"

echo "Done. Next: regenerate ios/voyage/globe.scn with GlobeCacheGenerator."
