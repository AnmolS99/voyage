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
# Natural Earth's default (de facto) view draws Crimea inside Russia's unit. Its
# disputed-areas layer carries Crimea on its own (BRK_A3 B89), which is used to
# cut it out of Russia and give it to Ukraine, as UN member states recognise it.
NE_DISPUTED_URL="https://naciscdn.org/naturalearth/10m/cultural/ne_10m_admin_0_disputed_areas.zip"

# Simplification budget: ~30% weighted retention of 1:10m ≈ 175k points world-wide
# (~17x the original 1:110m data). This is the main knob for detail vs. size.
SIMPLIFY_PCT="30%"
MIN_ISLAND_AREA="10km2"

# Pinned: mapshaper 0.7.87 skips simplification in this command chain (~535k points
# instead of ~171k, a 10 MB world.geojson). 0.7.83-0.7.86 reproduce the checked-in data.
MAPSHAPER="mapshaper@0.7.86"

echo "Downloading Natural Earth 1:10m admin-0 countries..."
curl -sL -o "$WORK_DIR/ne10m.zip" "$NE_URL"
unzip -o -q "$WORK_DIR/ne10m.zip" -d "$WORK_DIR"
curl -sL -o "$WORK_DIR/ne10m_disputed.zip" "$NE_DISPUTED_URL"
unzip -o -q "$WORK_DIR/ne10m_disputed.zip" -d "$WORK_DIR"

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
# Crimea is erased from the map units and merged back as a UA unit, so the dissolve
# joins it to mainland Ukraine without a border between them.
npx -y "$MAPSHAPER" -i "$WORK_DIR/ne_10m_admin_0_map_units.shp" \
        "$WORK_DIR/ne_10m_admin_0_disputed_areas.shp" combine-files \
    -filter 'BRK_A3=="B89"' target=ne_10m_admin_0_disputed_areas \
    -each 'ISO_A2_EH="UA"; ADM0_A3="UKR"' target=ne_10m_admin_0_disputed_areas \
    -erase ne_10m_admin_0_disputed_areas target=ne_10m_admin_0_map_units \
    -merge-layers target=ne_10m_admin_0_map_units,ne_10m_admin_0_disputed_areas force name=units \
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

echo "Regenerating the shared country fixture..."
python3 "$SCRIPT_DIR/generate_country_fixture.py"

echo "Done. Review the fixture diff, then regenerate ios/voyage/globe.scn with GlobeCacheGenerator."
