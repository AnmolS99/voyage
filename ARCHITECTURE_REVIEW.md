# Voyage Architecture Review

An analysis of the project structure and performance with actionable recommendations.

## Project Overview

| Metric | Value |
|--------|-------|
| Total Swift Files | 16 (main app) + 4 (tests) |
| Total Lines of Code | ~2,943 |
| Data Files | world.geojson (920KB), globe.scn (21MB) |
| Architecture | SwiftUI + SceneKit + MVVM-ish |

---

## Current Structure

```
voyage/
├── voyage/                           # Main app target
│   ├── Views
│   │   ├── ContentView.swift         # Main container + GlobeState (538 lines)
│   │   ├── GlobeView.swift           # 3D globe with SceneKit (583 lines)
│   │   ├── MapView.swift             # 2D flat map alternative (~150 lines)
│   │   ├── AchievementsView.swift    # Achievement cards (~80 lines)
│   │   └── SettingsView.swift        # Settings screen (~100 lines)
│   ├── Scene
│   │   ├── GlobeScene.swift          # 3D scene setup (~200 lines)
│   │   ├── GlobeCache.swift          # Pre-built scene loader (26 lines)
│   │   └── PolygonTriangulator.swift # Geometry generation (345 lines)
│   ├── Data
│   │   ├── GeoJSONParser.swift       # Country data parser (112 lines)
│   │   ├── ContinentData.swift       # Continent mappings (89 lines)
│   │   └── Achievement.swift         # Achievement model (16 lines)
│   ├── Managers
│   │   ├── OrientationManager.swift  # Device orientation (45 lines)
│   │   └── TipJarManager.swift       # In-app purchases (~60 lines)
│   ├── App
│   │   ├── voyageApp.swift           # App entry point
│   │   └── AppDelegate.swift         # App delegate
│   └── Resources
│       ├── world.geojson             # Country boundaries (920KB)
│       ├── globe.scn                 # Pre-cached 3D scene (21MB)
│       ├── Assets.xcassets/          # App icons and colors
│       └── TipJar.storekit           # IAP configuration
├── GlobeCacheGenerator/              # Utility to regenerate globe.scn
├── voyageTests/                      # Unit tests
└── voyage.xcodeproj/
```

---

## Structure Recommendations

### 1. Extract GlobeState from ContentView

**Priority:** Medium
**File:** `ContentView.swift:307-533`
**Effort:** Low

`GlobeState` is a 226-line `ObservableObject` class embedded inside `ContentView.swift`. This violates single-responsibility principle.

**Current:**
```swift
// ContentView.swift (538 lines total)
struct ContentView: View { ... }
class GlobeState: ObservableObject { ... }  // 226 lines buried here
```

**Recommended:**
```swift
// GlobeState.swift (new file)
class GlobeState: ObservableObject { ... }

// ContentView.swift (now ~310 lines)
struct ContentView: View { ... }
```

---

### 2. Remove Duplicate Flag Data

**Priority:** Medium
**File:** `ContentView.swift:462-514`
**Effort:** Low

The `flagForCountry()` method contains a hardcoded 120-entry dictionary mapping country names to flag emojis. However, `world.geojson` already has `flagCode` properties for each country.

**Current:**
```swift
func flagForCountry(_ name: String) -> String {
    let flags: [String: String] = [
        "Afghanistan": "🇦🇫",
        "Albania": "🇦🇱",
        // ... 118 more entries
    ]
    return flags[name] ?? "🏳️"
}
```

**Recommended:**
- Use `GeoJSONCountry.flagCode` from parsed data
- Remove the redundant dictionary
- Saves ~50 lines and eliminates data duplication

---

### 3. Remove Dead Code in PolygonTriangulator

**Priority:** Low
**File:** `PolygonTriangulator.swift:154-250`
**Effort:** Low

The following functions are never called (the code uses grid-based fill instead of ear-clipping):
- `earClipTriangulate()`
- `isEar()`
- `pointInTriangle()`
- `signedArea()`
- `sign()`

**Impact:** ~95 lines of dead code that adds maintenance burden.

---

### 4. Consolidate Color Definitions

**Priority:** Medium
**Files:** Multiple
**Effort:** Medium

Colors are scattered across multiple files with duplicated RGB values:

| Location | Example |
|----------|---------|
| `GeoJSONCountry.landColor()` | RGB values inline |
| `GlobeView.updateHighlights()` | RGB values inline |
| `MapView` body | RGB values inline |
| `ContentView` | RGB values inline |

**Recommended:** Create a centralized color palette:

```swift
// ColorPalette.swift (new file)
enum AppColors {
    static let ocean = Color(red: 0.184, green: 0.525, blue: 0.651)
    static let land = Color(red: 0.204, green: 0.745, blue: 0.510)
    static let landSelected = Color(red: 0.45, green: 0.85, blue: 0.60)
    static let visited = Color(red: 0.949, green: 0.941, blue: 0.075)
    static let visitedSelected = Color(red: 1.0, green: 1.0, blue: 0.3)
    static let wishlist = Color(red: 0.6, green: 0.4, blue: 0.8)
    static let wishlistSelected = Color(red: 0.75, green: 0.55, blue: 0.95)
    static let buttonLight = Color(red: 0.85, green: 0.55, blue: 0.35)
}
```

---

### 5. Add Protocol for Globe/Map Consistency

**Priority:** Low
**Files:** `GlobeView.swift`, `MapView.swift`
**Effort:** Medium

Per CLAUDE.md, both views must maintain identical appearance and behavior. Currently this is enforced manually. A shared protocol or utility could ensure consistency:

```swift
protocol CountryColorProvider {
    func color(for country: String, visited: Set<String>, wishlist: Set<String>, selected: String?) -> Color
}
```

---

## Performance Recommendations

### 1. Cache Parsed GeoJSON Data

**Priority:** Critical
**Impact:** App startup time
**Effort:** Low

**Issue:** `GeoJSONParser.loadCountries()` re-parses the 920KB JSON file every time it's called. Multiple components call it independently:
- `GlobeView.Coordinator`
- `GlobeScene.rebuildCoordinatorData()`
- `MapView`

**Symptoms:**
- Noticeable lag on app launch
- Redundant work parsing the same file 3+ times

**Recommended:**
```swift
// CountryDataCache.swift (new file)
final class CountryDataCache {
    static let shared = CountryDataCache()

    private(set) lazy var countries: [GeoJSONCountry] = {
        GeoJSONParser.loadCountries()
    }()

    private init() {}
}

// Usage everywhere:
let countries = CountryDataCache.shared.countries
```

---

### 2. Implement Spatial Indexing for Hit Detection

**Priority:** High
**Impact:** Tap responsiveness
**Effort:** High

**Issue:** Country tap detection uses linear search through ALL countries, performing expensive point-in-polygon tests for each.

**Current complexity:** O(n * m) where n = countries, m = polygon vertices

**Symptoms:**
- 50-150ms tap delay on complex countries
- Worse on older devices

**Recommended approaches:**

1. **Grid-based spatial hash** (simpler):
```swift
class SpatialIndex {
    private var grid: [[String]] // 360x180 grid of country names

    func countriesAt(lon: Double, lat: Double) -> [String] {
        let x = Int(lon + 180)
        let y = Int(lat + 90)
        return grid[x][y]
    }
}
```

2. **Quadtree** (more sophisticated):
- Better for varying country sizes
- More complex to implement

---

### 3. Reduce globe.scn File Size

**Priority:** Medium
**Impact:** App bundle size (21MB currently)
**Effort:** Medium

**Possible causes:**
- Over-tessellation of large countries
- Uncompressed geometry data
- Redundant material instances

**Investigation steps:**
1. Profile which countries contribute most geometry
2. Review adaptive cell sizes in `PolygonTriangulator`
3. Check if SceneKit compression is enabled

**Potential optimizations:**
- Increase cell size for very large countries (Russia, Canada, etc.)
- Use indexed geometry to reduce vertex duplication
- Enable SceneKit geometry compression
- Consider LOD (level-of-detail) for distant countries

---

### 4. Optimize Color Updates

**Priority:** Medium
**Impact:** Selection responsiveness
**Effort:** Medium

**Issue:** `updateHighlights()` in `GlobeView.swift` iterates through ALL 200+ country nodes on every selection change, updating materials even for unchanged countries.

**Current:**
```swift
func updateHighlights() {
    for country in allCountries {
        let newColor = computeColor(for: country)
        country.geometry?.firstMaterial?.diffuse.contents = newColor
    }
}
```

**Recommended:**
```swift
func updateHighlights(changedCountries: Set<String>) {
    for countryName in changedCountries {
        guard let node = countryNodes[countryName] else { continue }
        let newColor = computeColor(for: countryName)
        node.geometry?.firstMaterial?.diffuse.contents = newColor
    }
}
```

---

### 5. Fix StarryBackground Re-generation

**Priority:** Low
**Impact:** Minor CPU usage
**Effort:** Low

**Issue:** `StarryBackground` uses `Double.random()` inside the view body, causing star positions to potentially change on SwiftUI re-renders.

**Current:**
```swift
struct StarryBackground: View {
    var body: some View {
        Canvas { context, size in
            for _ in 0..<150 {
                let x = Double.random(in: 0...size.width)  // Regenerated each render
                // ...
            }
        }
    }
}
```

**Recommended:**
```swift
struct StarryBackground: View {
    let stars: [(x: CGFloat, y: CGFloat, size: CGFloat, opacity: Double)]

    init() {
        stars = (0..<150).map { _ in
            (CGFloat.random(in: 0...1),
             CGFloat.random(in: 0...1),
             CGFloat.random(in: 1...3),
             Double.random(in: 0.3...1.0))
        }
    }

    var body: some View {
        Canvas { context, size in
            for star in stars {
                let x = star.x * size.width
                // ...
            }
        }
    }
}
```

---

## Code Quality Recommendations

### 1. Use Codable for GeoJSON Parsing

**Priority:** Low
**File:** `GeoJSONParser.swift`
**Effort:** Medium

Currently uses manual `JSONSerialization` parsing. Swift's `Codable` would be cleaner:

```swift
struct GeoJSONFeatureCollection: Codable {
    let type: String
    let features: [GeoJSONFeature]
}

struct GeoJSONFeature: Codable {
    let type: String
    let properties: Properties
    let geometry: Geometry

    struct Properties: Codable {
        let name: String
        let continent: String?
        let capital: String?
        // ...
    }
}
```

---

### 2. Add Missing Test Coverage

**Current tests:**
- `ContinentDataTests.swift` - Continent mapping tests
- `AchievementTests.swift` - Achievement progress tests
- `AchievementCompletionTests.swift` - Achievement completion tests

**Missing coverage:**
- GeoJSON parsing edge cases
- Color computation logic
- Hit detection accuracy
- State persistence (UserDefaults/iCloud sync)

---

## Summary Table

| Recommendation | Priority | Effort | Impact |
|----------------|----------|--------|--------|
| Cache GeoJSON parsing | Critical | Low | Startup time |
| Spatial indexing for hits | High | High | Tap responsiveness |
| Extract GlobeState | Medium | Low | Code organization |
| Remove duplicate flag data | Medium | Low | Maintenance |
| Consolidate colors | Medium | Medium | Consistency |
| Reduce globe.scn size | Medium | Medium | Bundle size |
| Optimize color updates | Medium | Medium | Selection speed |
| Remove dead code | Low | Low | Maintenance |
| Fix StarryBackground | Low | Low | Minor CPU |
| Globe/Map protocol | Low | Medium | Consistency |
| Codable for GeoJSON | Low | Medium | Code quality |

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                        voyageApp                            │
│                            │                                │
│                      ContentView                            │
│                            │                                │
│         ┌──────────────────┼──────────────────┐            │
│         │                  │                  │            │
│         ▼                  ▼                  ▼            │
│    GlobeView           MapView        AchievementsView     │
│         │                  │                               │
│         ▼                  │                               │
│    GlobeScene              │                               │
│         │                  │                               │
│         ▼                  ▼                               │
│  PolygonTriangulator  ────────────► GeoJSONParser          │
│         │                               │                  │
│         ▼                               ▼                  │
│    globe.scn ◄────────────────── world.geojson             │
│                                                            │
│  ┌─────────────────────────────────────────────────────┐  │
│  │                    GlobeState                        │  │
│  │  • selectedCountry    • visitedCountries            │  │
│  │  • wishlistCountries  • isDarkMode                  │  │
│  │  • viewMode           • zoomLevel                   │  │
│  │                                                      │  │
│  │  Persistence: UserDefaults + iCloud                 │  │
│  └─────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

*Generated: 2026-02-03*
