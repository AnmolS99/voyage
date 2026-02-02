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

```bash
# Build
xcodebuild -scheme voyage -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.6' -configuration Debug build

# Run in simulator
xcrun simctl install "iPhone 16 Pro" ~/Library/Developer/Xcode/DerivedData/voyage-*/Build/Products/Debug-iphonesimulator/voyage.app
xcrun simctl launch "iPhone 16 Pro" com.anmol.voyage
```

## Architecture

- **SwiftUI** for UI
- **SceneKit** for 3D globe rendering
- **GeoJSON** for country boundary data

## Key Files

| File                        | Purpose                                           |
| --------------------------- | ------------------------------------------------- |
| `GlobeView.swift`           | Main 3D globe view with SceneKit integration      |
| `GlobeScene.swift`          | Creates the 3D scene (globe, countries, lighting) |
| `PolygonTriangulator.swift` | Converts GeoJSON polygons to 3D geometry          |
| `GeoJSONParser.swift`       | Parses world.geojson into country data            |
| `MapView.swift`             | 2D flat map view alternative                      |
| `ContentView.swift`         | Main app container with UI controls               |

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

- `world.geojson` - Country boundaries
- `globe.scn` - Pre-built 3D globe cache (regenerate with GlobeCacheGenerator)

## Globe Cache Generation

The `globe.scn` file is a pre-built SceneKit scene for fast app startup. To regenerate after modifying `world.geojson`:

```bash
# From Xcode: Select GlobeCacheGenerator scheme and Run (⌘R)
# Or from command line:
xcodebuild -scheme GlobeCacheGenerator -destination 'platform=macOS' build
./DerivedData/voyage/Build/Products/Debug/GlobeCacheGenerator
```

The generator reads `voyage/world.geojson` and outputs to `voyage/globe.scn`.
