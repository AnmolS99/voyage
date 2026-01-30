# voyage

An iOS app that displays an interactive 3D globe where users can explore and track countries they've visited. Built with SwiftUI and SceneKit.

## Build & Run

```bash
# Build
xcodebuild -scheme voyage -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.6' -configuration Debug build

# Run in simulator
xcrun simctl install "iPhone 16 Pro" ~/Library/Developer/Xcode/DerivedData/voyage-*/Build/Products/Debug-iphonesimulator/voyage.app
xcrun simctl launch "iPhone 16 Pro" com.voyage.voyage

# Run tests
xcodebuild test -scheme voyage -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.6'
```

## Architecture

- **SwiftUI** for UI layer
- **SceneKit** for 3D globe rendering
- **GeoJSON** for country boundary data
- **iCloud + UserDefaults** for data persistence

### Key Files

| File | Purpose |
|------|---------|
| `voyageApp.swift` | App entry point |
| `ContentView.swift` | Main container with header, bottom panel, and `GlobeState` |
| `GlobeView.swift` | 3D globe with SceneKit + gesture handling (pan, pinch, tap) |
| `GlobeScene.swift` | Scene setup (globe node, camera, lighting) |
| `MapView.swift` | 2D flat map alternative (equirectangular projection) |
| `GeoJSONParser.swift` | Parses `world.geojson` into `GeoJSONCountry` objects |
| `PolygonTriangulator.swift` | Converts GeoJSON polygons to 3D geometry |
| `GlobeCache.swift` | Loads pre-built `globe.scn` from bundle |
| `ContinentData.swift` | Country-to-continent mapping (loaded from GeoJSON) |
| `AchievementsView.swift` | Achievement tracking UI per continent |
| `SettingsView.swift` | Settings sheet with reset data option |
| `CountryData.swift` | Country metadata and flag emojis |
| `CapitalData.swift` | Capital city coordinates for markers |
| `OrientationManager.swift` | Device orientation handling (locks to landscape for map) |

## State Management

`GlobeState` (in `ContentView.swift`) is the central `ObservableObject`:

```swift
class GlobeState: ObservableObject {
    @Published var selectedCountry: String?
    @Published var visitedCountries: Set<String>
    @Published var wishlistCountries: Set<String>
    @Published var viewMode: ViewMode  // .globe or .map
    @Published var isDarkMode: Bool
    @Published var isAutoRotating: Bool
    @Published var targetCountryCenter: (lat: Double, lon: Double)?
}
```

Data is persisted to both `UserDefaults` (local) and `NSUbiquitousKeyValueStore` (iCloud), with automatic sync/merge.

## Globe Rendering Pipeline

Countries are rendered through this pipeline:

1. **Parse GeoJSON** (`GeoJSONParser.loadCountries()`)
   - Returns `[GeoJSONCountry]` with polygons or point coordinates
   - Point countries: small islands/microstates rendered as dots
   - Polygon countries: full boundary geometry

2. **Convert to 3D** (`PolygonTriangulator`)
   - `latLonToSphere(lat:lon:radius:)` converts coordinates to 3D
   - Grid-based fill for polygon triangulation
   - Adaptive cell size based on country size

3. **Create SceneKit Geometry** (`GlobeScene`)
   - Ocean sphere (radius 1.0)
   - Country polygons (radius 1.003)
   - Border outlines (radius 1.005)
   - Capital markers (radius 1.007)
   - Atmosphere glow (radius 1.08)

4. **Load from Cache** (`GlobeCache`)
   - Pre-built `globe.scn` loaded for fast startup
   - Coordinator rebuilds `countryNodes` dictionary for hit testing

### Layer Stack (innermost to outermost)

| Layer | Radius | Purpose |
|-------|--------|---------|
| Ocean | 1.000 | Blue base sphere |
| Countries | 1.003 | Filled polygon geometry |
| Borders | 1.005 | Black outline strips |
| Capital markers | 1.007 | Pulsing dots |
| Atmosphere | 1.08 | Transparent glow |

## Color Palette

| Element | Light Mode | Dark Mode |
|---------|------------|-----------|
| Ocean | `#2F86A6` | `#1A2640` |
| Land (unvisited) | `#34BE82` | `#34BE82` |
| Visited | `#F2F013` | `#F2F013` |
| Wishlist | `#9966CC` | `#9966CC` |
| Selected | `#D98C59` | `#D98C59` |
| Buttons | `#D98C59` | `#665899` |

RGB values for code:
- Ocean: `(0.184, 0.525, 0.651)`
- Land: `(0.204, 0.745, 0.510)`
- Visited: `(0.949, 0.941, 0.075)`
- Selected/Buttons: `(0.85, 0.55, 0.35)`

## Data Files

| File | Format | Purpose |
|------|--------|---------|
| `world.geojson` | GeoJSON | Country boundaries (polygons + point markers) |
| `globe.scn` | SceneKit | Pre-built 3D globe for fast loading |
| `countries.json` | JSON | Legacy metadata (unused) |

### GeoJSON Properties

Each feature in `world.geojson` has:
- `name`: Country display name
- `continent`: Africa, Asia, Europe, North America, South America, Oceania, Antarctica
- `renderAs`: "point" for small islands/microstates (optional)
- `flagCode`: ISO country code for flag emoji (optional)

## Testing

Tests are in `voyageTests/`:

| Test File | Coverage |
|-----------|----------|
| `voyageTests.swift` | GeoJSON loading, point-in-polygon, coordinate conversion, globe/map consistency |
| `AchievementTests.swift` | Achievement progress/completion logic |
| `AchievementCompletionTests.swift` | Achievement completion edge cases |
| `ContinentDataTests.swift` | Continent mapping validation |

Key test to run after modifying country data:
```bash
# Verifies globe.scn and map have same countries
xcodebuild test -scheme voyage -only-testing:voyageTests/voyageTests/testGlobeAndMapCountryConsistency
```

## Globe Bundling

The `globe.scn` is pre-built for fast app startup. To regenerate:

1. Modify `GlobeScene.swift` to save the generated globe to Documents:
   ```swift
   // In createScene(), after generating globe:
   let documentsPath = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
   let sceneURL = documentsPath.appendingPathComponent("globe.scn")
   scene.write(to: sceneURL, options: nil, delegate: nil, progressHandler: nil)
   ```

2. Run the app in simulator

3. Copy the generated `globe.scn` from simulator Documents to the project:
   ```bash
   cp ~/Library/Developer/CoreSimulator/Devices/*/data/Containers/Data/Application/*/Documents/globe.scn voyage/globe.scn
   ```

4. Run `testGlobeAndMapCountryConsistency` to verify

## Code Conventions

### Swift Style
- Use `@StateObject` for owned state, `@ObservedObject` for passed state
- Prefer computed properties over stored state when possible
- Use `SCNTransaction` for animated SceneKit changes

### Coordinate Systems
- GeoJSON: `[longitude, latitude]` (lon first)
- Internal: `(lat: Double, lon: Double)` tuples
- SceneKit: Y-up, camera at +Z looking at origin

### Country Selection Flow
1. User taps globe/map
2. Hit test converts screen point to lat/lon
3. `findCountryAt()` searches polygons + nearby for small countries
4. `GlobeState.selectCountry()` updates state
5. `updateHighlights()` changes material colors
6. `centerOnSelectedCountry()` animates camera

### Adding New Countries

1. Add to `world.geojson` with proper geometry type:
   - Large countries: `Polygon` or `MultiPolygon`
   - Small islands: `Point` with `"renderAs": "point"`

2. Include required properties:
   ```json
   {
     "type": "Feature",
     "properties": {
       "name": "Country Name",
       "continent": "Continent Name"
     },
     "geometry": { ... }
   }
   ```

3. Regenerate `globe.scn` (see Globe Bundling section)

4. Run tests to verify consistency

## Achievements System

Achievements track progress per continent:
- `Continent` enum defines 7 continents with medal emojis
- `ContinentData` loads country-continent mapping from GeoJSON
- `AchievementsView` displays progress cards
- Antarctica is excluded from achievements (only 1 "country")

## Data Persistence

- **Local**: `UserDefaults` with keys `visitedCountries`, `wishlistCountries`
- **Cloud**: `NSUbiquitousKeyValueStore` (same keys)
- **Merge strategy**: Union of local and cloud on load
- **Sync**: Automatic via `didChangeExternallyNotification`

Data is independent of `globe.scn` - stored as country name strings.
