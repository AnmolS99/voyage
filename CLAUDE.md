# voyage

An iOS app that displays an interactive 3D globe where users can explore and track countries they've visited.

## Git Conventions

Use conventional commits and conventional branch naming.

**Commit format:** `<type>: <description>`

Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

**Branch format:** `<type>/<short-description>`

Examples:

- `feat/dark-mode-toggle`
- `fix/globe-rotation-reset`
- `refactor/country-data-parsing`

## Build & Run

**Always build and run the simulator after making larger changes to verify the implementation works correctly.**

```bash
# Build
xcodebuild -scheme voyage -destination 'platform=iOS Simulator,name=iPhone 17 Pro,OS=26.2' -configuration Debug build

# Run in simulator
xcrun simctl install "iPhone 17 Pro" ~/Library/Developer/Xcode/DerivedData/voyage-*/Build/Products/Debug-iphonesimulator/voyage.app
xcrun simctl launch "iPhone 17 Pro" com.anmol.voyage
```

## Setup

Supabase credentials are stored in `Secrets.xcconfig` (gitignored). To set up:

```bash
cp Secrets.xcconfig.example Secrets.xcconfig
# Edit Secrets.xcconfig with your Supabase URL and publishable key
```

## Architecture

- **SwiftUI** for UI
- **SceneKit** for 3D globe rendering
- **GeoJSON** for country boundary data
- **Supabase** for daily challenge backend

## Key Files

| File                        | Purpose                                           |
| --------------------------- | ------------------------------------------------- |
| `GlobeView.swift`           | Main 3D globe view with SceneKit integration      |
| `GlobeScene.swift`          | Creates the 3D scene (globe, countries, lighting) |
| `PolygonTriangulator.swift` | Converts GeoJSON polygons to 3D geometry          |
| `GeoJSONParser.swift`       | Parses world.geojson into country data            |
| `MapView.swift`             | 2D flat map view alternative                      |
| `ContentView.swift`         | Main app container with UI controls               |
| `DailyChallenge/`           | Daily geography quiz feature (see below)          |

## Globe Rendering

Countries are rendered by:

1. Parsing GeoJSON polygon coordinates (lon/lat)
2. Converting to 3D sphere vertices via `latLonToSphere()`
3. Triangulating polygons using grid-based fill
4. Creating SceneKit geometry with materials

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
| --------------------------------- | -------------------------------------------- |
| `DailyChallenge.swift`            | Models: `DailyChallenge`, `QuestionType`, `ChallengeResult` |
| `SupabaseClient.swift`            | Network layer (reads credentials from `Secrets.xcconfig` via Info.plist) |
| `ChallengeStore.swift`            | Local persistence (UserDefaults)             |
| `DailyChallengeViewModel.swift`   | State management for the quiz flow           |
| `ChallengeCalendarView.swift`     | Month grid calendar (main tab view)          |
| `ChallengePlayView.swift`         | Quiz UI with clue, search, and guess list    |
| `ChallengeSearchField.swift`      | TextField with filtered dropdown suggestions |
| `CountrySilhouetteView.swift`     | Canvas-based country outline renderer        |
| `ChallengeResultView.swift`       | Post-completion result card                  |

### Supabase Schema

The `daily_challenges` table has columns: `id` (uuid), `date` (date), `is_guess_country` (bool), `is_guess_capital` (bool), `is_guess_flag` (bool), `answer` (text — ISO 3166-1 alpha-2 code), `created_at`, `updated_at`. Only one boolean is true per row.

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

## Data Files

- `world.geojson` - Country boundaries. Each feature's `id` is the ISO 3166-1 alpha-2 country code (e.g., `"US"`, `"AF"`), which doubles as the flag emoji code.
- `country_highlights.json` - Top cities and attractions for each country, keyed by ISO code. See [Country Highlights Data](#country-highlights-data) for methodology.
- `globe.scn` - Pre-built 3D globe cache (regenerate with GlobeCacheGenerator)

## Country Highlights Data

`country_highlights.json` contains 1-5 top cities and 1-5 top attractions for all 206 countries/territories. The data was compiled by cross-referencing at least 3 sources per country to ensure accuracy and reduce bias.

**Sources used:** Lonely Planet, TripAdvisor, Touropia, PlanetWare, Atlas Obscura, Culture Trip, Rough Guides, Wikipedia (tourism pages), official national tourism boards, and regional travel blogs.

**Selection criteria:**
- **Cities** were chosen by tourist relevance, not population size (e.g., Livingstone over Lusaka for Zambia, Siem Reap over Phnom Penh for Cambodia).
- **Attractions** prioritize landmarks, natural wonders, national parks, historical sites, and cultural sites that tourists actually visit.
- **Major destinations** (e.g., France, Japan, USA) have the full 5 cities + 5 attractions.
- **Small/less-visited countries** (e.g., Nauru, Tuvalu, Falkland Islands) have appropriately reduced entries (1-2 per list).
- **Conflict zones** (e.g., Syria, Yemen) include historically significant sites known pre-conflict.
- The data was reviewed continent by continent before finalizing.

## Globe Cache Generation

The `globe.scn` file is a pre-built SceneKit scene for fast app startup. To regenerate after modifying `world.geojson`:

```bash
# From Xcode: Select GlobeCacheGenerator scheme and Run (⌘R)
# Or from command line:
xcodebuild -scheme GlobeCacheGenerator -destination 'platform=macOS' build
./DerivedData/voyage/Build/Products/Debug/GlobeCacheGenerator
```

The generator reads `voyage/world.geojson` and outputs to `voyage/globe.scn`.
