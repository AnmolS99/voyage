# Data provenance

How the shared data in `shared/data/` was produced. Day-to-day rules for these
files are in [CLAUDE.md](../CLAUDE.md).

## Boundary Data Provenance

Country geometry comes from **Natural Earth 1:10m admin-0 map units**, simplified with
mapshaper (weighted Visvalingam, ~30% retention, islands < 10 km² dropped, 4-decimal
coordinates) to ~170k boundary points world-wide. To regenerate or change the detail
budget, run:

```bash
./scripts/update_geometry.sh   # downloads NE data, simplifies, merges into shared/data/world.geojson
```

Map units splitting one country into several features (GB = England + Scotland + Wales +
N. Ireland, BE = Flanders + Wallonia + Brussels, PT = mainland + Madeira + Azores, ...)
are dissolved by ISO code in mapshaper (`-dissolve2`) so countries render as single
shapes without internal unit borders.

Crimea belongs to Ukraine (UA), not Russia: Natural Earth's default view folds it into
Russia's unit, so `update_geometry.sh` erases it from the map units using NE's
disputed-areas layer (`BRK_A3 == "B89"`) and merges it back as a UA unit before the
dissolve.

The merge (`scripts/merge_geometry.py`) replaces only feature geometry, matched by ISO
code; all custom properties (name, continent, capital, `renderAs`) are preserved. Larger
microstates (CY, LU, WS, CV, KM, MU, ST) are real polygons; the remaining 25 microstates
stay `Point` features rendered as dots. After regenerating world.geojson, always
regenerate `globe.scn` (see CLAUDE.md, "Data changes").

## Country Highlights Data

`shared/data/country_highlights.json` contains 1-5 top cities and 1-5 top attractions for all 207 countries/territories. The data was compiled by cross-referencing at least 3 sources per country to ensure accuracy and reduce bias.

**Sources used:** Lonely Planet, TripAdvisor, Touropia, PlanetWare, Atlas Obscura, Culture Trip, Rough Guides, Wikipedia (tourism pages), official national tourism boards, and regional travel blogs.

**Selection criteria:**
- **Cities** were chosen by tourist relevance, not population size (e.g., Livingstone over Lusaka for Zambia, Siem Reap over Phnom Penh for Cambodia).
- **Attractions** prioritize landmarks, natural wonders, national parks, historical sites, and cultural sites that tourists actually visit.
- **Major destinations** (e.g., France, Japan, USA) have the full 5 cities + 5 attractions.
- **Small/less-visited countries** (e.g., Nauru, Tuvalu, Falkland Islands) have appropriately reduced entries (1-2 per list).
- **Conflict zones** (e.g., Syria, Yemen) include historically significant sites known pre-conflict.
- The data was reviewed continent by continent before finalizing.

