# voyage 🌍

An interactive iOS app to track your adventures across Earth.

![iOS](https://img.shields.io/badge/iOS-17.0+-blue)
![Swift](https://img.shields.io/badge/Swift-5.0-orange)

<a href="https://apps.apple.com/no/app/voyage-track-your-journey/id6758411779?l=nb" target="_blank"><img src="https://developer.apple.com/assets/elements/badges/download-on-the-app-store.svg" alt="Download on the App Store" height="40"></a>&nbsp;&nbsp;<a href="https://www.buymeacoffee.com/anmols99" target="_blank"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" height="40"></a>

## Why voyage?

- **Free** - Like completely. No ads, no subscriptions, no hidden costs.
- **Privacy first** - Your data stays on your device and iCloud. Nothing is sent to third parties.
- **Open source** - Inspect the code, contribute, or fork it.

## Features

- Interactive 3D globe with multiple selectable textures,
- 2D map view alternative.
- Track visited/wishlist countries with a single tap.
- All 193 UN member states + 2 observer states (Vatican City, Palestine).
- Dark mode (of course)
- Data syncs across devices via iCloud

## Contributing

### Getting Started

```bash
git clone https://github.com/AnmolS99/voyage.git
cd voyage
open voyage.xcodeproj
```

Build and run on iOS 17.0+ simulator or device.

### Architecture

| File                   | Purpose                                        |
| ---------------------- | ---------------------------------------------- |
| `GlobeView.swift`      | 3D globe with SceneKit + gesture handling      |
| `MapView.swift`        | 2D flat map alternative                        |
| `GlobeScene.swift`     | Scene setup (globe, lighting, camera)          |
| `GeoJSONParser.swift`  | Parses country data from `world.geojson` (polygons + point markers) |

### Globe Bundling

The globe (`globe.scn`) is pre-built for fast startup. To regenerate after modifying `world.geojson`:

```bash
# Build and run the generator
xcodebuild -scheme GlobeCacheGenerator -destination 'platform=macOS' build
# Run it (output goes directly to voyage/globe.scn)
./DerivedData/voyage/Build/Products/Debug/GlobeCacheGenerator
```

Or from Xcode: Select the `GlobeCacheGenerator` scheme, build and run (⌘R).

Run `testGlobeAndMapCountryConsistency` to verify map and globe are in sync.

### Data Storage

Visited/wishlist countries and checked cities/attractions are stored in UserDefaults + iCloud. Data is independent of `globe.scn`.

### Country Highlights Data

`country_highlights.json` contains 1-5 top cities and 1-5 top attractions for all 206 countries/territories. The data was compiled by cross-referencing at least 3 sources per country (Lonely Planet, TripAdvisor, Touropia, PlanetWare, Atlas Obscura, Culture Trip, Wikipedia tourism pages, official tourism boards, and regional travel blogs).

Cities were selected by tourist relevance rather than population, and the number of entries scales with the country's tourism profile (5 for major destinations, 1-2 for microstates and remote territories). The data was reviewed continent by continent before finalizing.

## Support

Found a bug or have a feature request? [Open an issue](https://github.com/AnmolS99/voyage/issues) on GitHub.

---

_Built with vibes and [Claude](https://claude.ai) 🤖_
