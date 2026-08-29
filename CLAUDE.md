# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# voyage

An app that displays an interactive 3D globe where users can explore and track
countries they've visited. iOS ships today; a native Android version is being
built — see [docs/ANDROID_PLAN.md](docs/ANDROID_PLAN.md) for the phased plan and
current progress.

## Repository Layout

```
voyage/
├── ios/        # iOS app: voyage.xcodeproj, voyage/, voyageTests/,
│               # GlobeCacheGenerator/, fastlane/, Gemfile, Secrets.xcconfig
├── android/    # Android app: Kotlin/Compose Gradle project (app/, gradle/, tools/)
├── shared/
│   ├── data/       # world.geojson, country_highlights.json — consumed by BOTH apps
│   ├── fixtures/   # expected_countries.json — parser contract for BOTH apps
│   └── supabase/   # schemas/, migrations/, seed.sql
├── scripts/    # update_geometry.sh, merge_geometry.py, generate_country_fixture.py
└── docs/       # ANDROID_PLAN.md, ANDROID_DEVELOPMENT.md
```

**All iOS commands below run from the `ios/` directory.** The data files under
`shared/data/` are referenced by the Xcode project in place (not copied) — edit
them there, never duplicate them per platform.

**Android:** build/run/test instructions live in
[docs/ANDROID_DEVELOPMENT.md](docs/ANDROID_DEVELOPMENT.md); all Android commands
run from `android/`. The Android color palette
(`android/app/src/main/kotlin/com/anmol/voyage/ui/theme/ColorPalette.kt`) mirrors
`ios/voyage/ColorPalette.swift` — see [Color Palette](#color-palette) — so a
color change must land on both platforms in the same PR. Both apps parse the
same `shared/data/` files and both assert the same parser fixture — see
[Shared Country Fixture](#shared-country-fixture). `ui/map/WorldMap.kt` is a port
of `ios/voyage/MapView.swift`, so the consistency rule in
[Globe and Map Consistency](#globe-and-map-consistency) covers four renderers, not
two; on Android the shared decisions live in `ui/map/CountryStyle.kt`,
`ui/map/CapitalMarker.kt`, `ui/map/MapProjection.kt`, and
`globe/GlobeCamera.kt` rather than in the renderer, and
`ui/home/HomeScreen.kt` holds the chrome both Android renderers share.
Capital stars and microstate dots are **meshes in the globe's scene**
(`globe/MarkerMesh.kt`), not a Compose overlay above it: an overlay draws from
its own copy of the camera and visibly trails the globe by a frame while
dragging. The two renderers share the shape (`ui/map/CapitalMarker.kt`), the
colors (`ui/map/CountryStyle.kt`) and the sizes (`ui/map/CountryMarkers.kt`) —
but not the drawing. Marker size is specified in `dp` on both, so a dot is the
same size on the globe as on the map; the globe gets there with the same
displace-by-a-uniform trick the border outlines use.

The Android globe renders with **Filament** (`ui/globe/`), not SceneKit. Two
constraints there are easy to break: its materials are **unlit** and its view
has **post-processing disabled**, which together are what put exact palette
colors on screen — enabling either one shifts every country color, and
`GlobeCountryFill.kt` documents the coupling.

Border outlines work as they do on iOS — zero-width strips widened at render
time so they keep a constant on-screen width — but the pieces sit elsewhere:
the miter direction is a `CUSTOM0` vertex attribute widened by
`GlobeMaterials.outline` instead of a SceneKit shader modifier, and the zoom
scaling is `GlobeCamera.screenScale` rather than renderer code. The outline
sector grid is a decision Android made first and iOS adopted — see
[Globe Rendering](#globe-rendering) — so changing it is a two-platform change;
"Pinned invariants" in [docs/ANDROID_PLAN.md](docs/ANDROID_PLAN.md) has the
measurement.

## Git Conventions

Use conventional commits and conventional branch naming.

**Commit format:** `<type>: <description>`

Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

**Branch format:** `<type>/<short-description>`

Examples:

- `feat/dark-mode-toggle`
- `fix/globe-rotation-reset`
- `refactor/country-data-parsing`

## Build, Run & Test (iOS)

**Always build and run the simulator after making larger changes to verify the implementation works correctly.**

All commands in this section run from `ios/`:

```bash
cd ios

# Build
xcodebuild -scheme voyage -destination 'platform=iOS Simulator,name=iPhone 17 Pro,OS=26.2' -configuration Debug build

# Run in simulator
xcrun simctl install "iPhone 17 Pro" ~/Library/Developer/Xcode/DerivedData/voyage-*/Build/Products/Debug-iphonesimulator/voyage.app
xcrun simctl launch "iPhone 17 Pro" com.anmol.voyage

# Run the full test suite (voyageTests target)
xcodebuild -scheme voyage -destination 'platform=iOS Simulator,name=iPhone 17 Pro,OS=26.2' test

# Run a single test class or method
xcodebuild -scheme voyage -destination 'platform=iOS Simulator,name=iPhone 17 Pro,OS=26.2' test \
  -only-testing:voyageTests/AchievementCompletionTests
xcodebuild -scheme voyage -destination 'platform=iOS Simulator,name=iPhone 17 Pro,OS=26.2' test \
  -only-testing:voyageTests/voyageTests/testGlobeAndMapCountryConsistency
```

`voyageTests/testGlobeAndMapCountryConsistency` specifically guards the invariant described in [Globe and Map Consistency](#globe-and-map-consistency) — run it after touching either rendering path.

### TestFlight Builds

TestFlight builds are created with the existing GitHub Actions workflow (`.github/workflows/testflight.yml`) — never by archiving/uploading locally, since code signing (fastlane match) and `Secrets.xcconfig` live in CI secrets:

```bash
gh workflow run testflight.yml --ref <branch>   # branch is chosen via the dispatch ref
gh run watch                                    # monitor the build
```

## Setup

Supabase credentials are stored in `ios/Secrets.xcconfig` (gitignored). To set up:

```bash
cp ios/Secrets.xcconfig.example ios/Secrets.xcconfig
# Edit ios/Secrets.xcconfig with your Supabase URL and publishable key
```

## Architecture

- **SwiftUI** for UI
- **SceneKit** for 3D globe rendering
- **GeoJSON** for country boundary data
- **Supabase** for daily challenge backend

The app is a single `TabView` (`ContentView.swift`) with four tabs: Home (globe/map), Daily (challenge calendar), Achievements, Settings. All tabs share one `GlobeState` (`ContentView.swift`), an `ObservableObject` injected into every tab that holds visited/wishlist countries, checked cities/attractions, view mode (globe vs map), style preferences, and dark mode. `GlobeState` is the single source of truth — mutate it through its methods (`addVisit`, `toggleCheckedCity`, etc.) rather than duplicating state locally in views.

### Data persistence

`GlobeState` persists to both `UserDefaults` (local) and `NSUbiquitousKeyValueStore` (iCloud KV store), unioning the two on load and re-saving the merge so multi-device edits don't clobber each other. It observes `NSUbiquitousKeyValueStore.didChangeExternallyNotification` to pick up remote changes live. The Daily Challenge feature persists separately via `ChallengeStore` (UserDefaults only, keyed by date).

### Country data loading

`CountryDataCache` (singleton) parses `world.geojson` (via `GeoJSONParser`) once on first access of `.shared` (thread-safe) and is prewarmed on a background queue from `voyageApp.init` so parsing overlaps globe.scn loading; `country_highlights.json` stays lazy. Call sites should go through `CountryDataCache.shared` rather than re-parsing.

## Key Files

| File (under `ios/voyage/`)  | Purpose                                           |
| --------------------------- | ------------------------------------------------- |
| `ContentView.swift`         | Tab container + `GlobeState` (shared app state)   |
| `GlobeView.swift`           | Main 3D globe view with SceneKit integration      |
| `GlobeScene.swift`          | Creates the 3D scene (globe, countries, lighting) |
| `PolygonTriangulator.swift` | Converts GeoJSON polygons to 3D geometry          |
| `Earcut.swift`              | Ear-clipping triangulation (port of mapbox/earcut) |
| `GeoJSONParser.swift`       | Parses world.geojson into country data            |
| `CountryHitTester.swift`    | Shared tap-to-country lookup (globe + map)        |
| `CountryDataCache.swift`    | Singleton cache for parsed GeoJSON + highlights   |
| `MapView.swift`             | 2D flat map view alternative                      |
| `ColorPalette.swift`        | Centralized `AppColors` (see Color Palette below) |
| `Achievement.swift` / `ContinentData.swift` | Achievement progress model + continent groupings |
| `MedalOverlayView.swift`    | Tap a card's 3D medal → full-screen blur overlay with a Y-axis-spinnable coin |
| `DailyChallenge/`           | Daily geography quiz feature (see below)          |

## Globe Rendering

Countries are rendered by:

1. Parsing GeoJSON polygon coordinates (lon/lat)
2. Triangulating each polygon (with enclave holes) via earcut in lon/lat space
3. Subdividing triangles/border segments longer than ~2.5° so they follow sphere curvature
4. Converting to 3D sphere vertices via `latLonToSphere()`
5. Creating SceneKit geometry with materials

A legacy grid-based fill remains in `PolygonTriangulator` as an automatic fallback for
rings earcut cannot triangulate (the current dataset needs no fallbacks).

Border outlines keep a constant on-screen width across zoom levels: outline vertices sit
on the border centerline with their miter direction stored in the normal attribute, and a
geometry shader modifier (`PolygonTriangulator.outlineShaderModifier`) widens them by the
`outlineThickness` uniform. Zoom code (`GlobeView.Coordinator.updateOutlineThickness`)
scales that uniform with camera distance — no geometry is rebuilt when zooming or when
selection thickens/raises a country's outline.

All black borders are merged into lon x lat sector nodes (`outline_sector_N`)
sharing one material/uniform. The outline mesh dominates the scene's vertex count, so
`GlobeView.Coordinator.renderer(_:updateAtTime:)` hides sectors beyond the globe's
horizon each frame (frustum culling alone never removes the far side). The sector grid
is 12 longitude x 4 latitude on **both** platforms, and the latitude split is load-bearing:
longitude-only wedges run pole to pole and never fall entirely behind the horizon, so they
cull 0% — see `PolygonTriangulator.createSectoredOutlineGeometries` and
`OutlineSectorCullingTests`, which pin the measurement on each platform. Fills and
outlines are single-sided — winding faces outward so the GPU backface-culls the far
hemisphere. The selected country's outline is a separate overlay node
(`selected_outline`, managed by `GlobeView.Coordinator.updateSelectedOutline`) drawn
thicker, status-colored, and raised above the sector outlines.

The globe has layers: ocean sphere (base) → country polygons → border outlines → atmosphere glow

## Globe and Map Consistency

The globe view (`GlobeView.swift`) and map view (`MapView.swift`) must maintain identical appearance and behavior. The only difference should be the rendering perspective (3D sphere vs 2D flat projection). This includes:

- Country colors and selection highlighting
- Color priority logic (visited/wishlist status takes precedence over selection)
- Border/outline colors and styles
- Capital star markers

When modifying colors or selection logic, always update both files together.

## Daily Challenge

A daily geography quiz feature powered by Supabase. The `daily_challenges` table holds 365 pre-seeded questions.

### Challenge Types

| Type              | Clue shown                    | User guesses | Answer validated against         |
| ----------------- | ----------------------------- | ------------ | -------------------------------- |
| `is_guess_country` | Country silhouette (outline) | Country name | `GeoJSONCountry.name` via ISO code |
| `is_guess_capital` | Country name + flag          | Capital city | `GeoJSONCountry.capital.name`    |
| `is_guess_flag`    | Flag emoji                   | Country name | `GeoJSONCountry.name` via ISO code |

### Flow

1. User opens the **Daily** tab → `ChallengeCalendarView` shows a month grid.
2. Available challenge dates are fetched from Supabase on appear and cached.
3. Past and today's dates are tappable; future dates are locked (dimmed + lock icon).
4. Tapping a date opens `ChallengePlayView` as a sheet.
5. The view model fetches the challenge from Supabase by date, resolves the `answer` ISO code to a `GeoJSONCountry` via `CountryDataCache`.
6. User types guesses into `ChallengeSearchField` (filtered dropdown of all country names or capitals).
7. Each guess is validated case-insensitively. Wrong guesses show red; correct shows green.
8. Max 5 attempts. Game ends on correct guess or 5 failures → `ChallengeResultView`.
9. Progress is saved to `ChallengeStore` (UserDefaults) after every guess, so mid-game state persists if the user leaves.
10. Completed challenges show a green checkmark (solved) or red X (failed) on the calendar.

### Key Files

| File                              | Purpose                                      |
| --------------------------------- | --------------------------------------------- |
| `DailyChallenge.swift`            | Models: `DailyChallenge`, `QuestionType`, `ChallengeResult` |
| `SupabaseClient.swift`            | Network layer (reads credentials from `Secrets.xcconfig` via Info.plist) |
| `ChallengeStore.swift`            | Local persistence (UserDefaults)             |
| `DailyChallengeViewModel.swift`   | State management for the quiz flow           |
| `ChallengeCalendarView.swift`     | Month grid calendar (main tab view)          |
| `ChallengePlayView.swift`         | Quiz UI with clue, search, and guess list    |
| `ChallengeSearchField.swift`      | TextField with filtered dropdown suggestions |
| `CountrySilhouetteView.swift`     | Canvas-based country outline renderer        |
| `ConfettiView.swift`              | Success celebration animation                |
| `ChallengeResultView.swift`       | Post-completion result card                  |

### Supabase Schema

The `daily_challenges` table has columns: `id` (uuid), `date` (date), `is_guess_country` (bool), `is_guess_capital` (bool), `is_guess_flag` (bool), `answer` (text — ISO 3166-1 alpha-2 code), `created_at`, `updated_at`. Only one boolean is true per row.

Schema and migrations live in `shared/supabase/schemas/` and `shared/supabase/migrations/`; `shared/supabase/seed.sql` seeds the 365 daily challenges.

## Color Palette

| Element              | Hex     | RGB                   |
| -------------------- | ------- | --------------------- |
| Ocean                | #2F86A6 | (0.184, 0.525, 0.651) |
| Land (unvisited)     | #34BE82 | (0.204, 0.745, 0.510) |
| Selected (unvisited) | -       | (0.45, 0.85, 0.60)    |
| Visited              | #F2F013 | (0.949, 0.941, 0.075) |
| Visited + selected   | -       | (1.0, 1.0, 0.3)       |
| Wishlist             | -       | (0.6, 0.4, 0.8)       |
| Wishlist + selected  | -       | (0.75, 0.55, 0.95)    |
| Buttons (light mode) | #D98C59 | (0.85, 0.55, 0.35)    |

All colors are defined once per platform — `ios/voyage/ColorPalette.swift` (`AppColors`) and `android/app/src/main/kotlin/com/anmol/voyage/ui/theme/ColorPalette.kt` (`VoyagePalette`) — and referenced throughout; don't hardcode hex/RGB values elsewhere. The two files hold the same values, and `ColorPaletteTest` on Android pins the table above, so palette changes ship to both platforms together.

## Data Files

- `shared/data/world.geojson` - Country boundaries. Each feature's `id` is the ISO 3166-1 alpha-2 country code (e.g., `"US"`, `"AF"`), which doubles as the flag emoji code.
- `shared/data/country_highlights.json` - Top cities and attractions for each country, keyed by ISO code. See [Country Highlights Data](#country-highlights-data) for methodology.
- `shared/fixtures/expected_countries.json` - Parser contract asserted by both platforms. See [Shared Country Fixture](#shared-country-fixture).
- `ios/voyage/globe.scn` - Pre-built 3D globe cache, iOS-only (regenerate with GlobeCacheGenerator)

### Shared Country Fixture

`shared/fixtures/expected_countries.json` pins what parsing `world.geojson` must
produce: 206 countries in feature order, their ISO codes, names, continents,
capitals, per-ring point counts (170,955 coordinates total) and bounding boxes.
`voyageTests/GeoJSONFixtureTests` and the Android `GeoJsonParserTest` both assert
it, so neither hand-written parser can drift from the other or from the data.

It is derived from `world.geojson` by `scripts/generate_country_fixture.py`,
which `update_geometry.sh` runs automatically; Android CI fails if it is stale:

```bash
python3 scripts/generate_country_fixture.py          # rewrite after a data change
python3 scripts/generate_country_fixture.py --check  # what CI runs
```

Review the fixture's diff after regenerating it — an unexpected change there is
an unexpected change to what both apps render.

### Boundary Data Provenance

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

The merge (`scripts/merge_geometry.py`) replaces only feature geometry, matched by ISO
code; all custom properties (name, continent, capital, `renderAs`) are preserved. Larger
microstates (CY, LU, WS, CV, KM, MU, ST) are real polygons; the remaining 25 microstates
stay `Point` features rendered as dots. After regenerating world.geojson, always
regenerate `globe.scn` (see [Globe Cache Generation](#globe-cache-generation)).

## Country Highlights Data

`shared/data/country_highlights.json` contains 1-5 top cities and 1-5 top attractions for all 206 countries/territories. The data was compiled by cross-referencing at least 3 sources per country to ensure accuracy and reduce bias.

**Sources used:** Lonely Planet, TripAdvisor, Touropia, PlanetWare, Atlas Obscura, Culture Trip, Rough Guides, Wikipedia (tourism pages), official national tourism boards, and regional travel blogs.

**Selection criteria:**
- **Cities** were chosen by tourist relevance, not population size (e.g., Livingstone over Lusaka for Zambia, Siem Reap over Phnom Penh for Cambodia).
- **Attractions** prioritize landmarks, natural wonders, national parks, historical sites, and cultural sites that tourists actually visit.
- **Major destinations** (e.g., France, Japan, USA) have the full 5 cities + 5 attractions.
- **Small/less-visited countries** (e.g., Nauru, Tuvalu, Falkland Islands) have appropriately reduced entries (1-2 per list).
- **Conflict zones** (e.g., Syria, Yemen) include historically significant sites known pre-conflict.
- The data was reviewed continent by continent before finalizing.

## Globe Cache Generation

The `ios/voyage/globe.scn` file is a pre-built SceneKit scene for fast app startup. To regenerate after modifying `shared/data/world.geojson`:

```bash
# From Xcode: Select GlobeCacheGenerator scheme and Run (⌘R)
# Or from command line:
cd ios
xcodebuild -scheme GlobeCacheGenerator -destination 'platform=macOS' build
./DerivedData/voyage/Build/Products/Debug/GlobeCacheGenerator
```

The generator reads `shared/data/world.geojson` and outputs to `ios/voyage/globe.scn`.
