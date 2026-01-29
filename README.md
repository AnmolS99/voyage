# voyage 🌍

An interactive 3D globe iOS app to track the countries you've visited. Built with SwiftUI and SceneKit.

![iOS](https://img.shields.io/badge/iOS-17.0+-blue)
![Swift](https://img.shields.io/badge/Swift-5.0-orange)

<a href="https://www.buymeacoffee.com/anmols99" target="_blank"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" height="40"></a>

## Why voyage?

- **Free** - No ads, no subscriptions, no hidden costs
- **Privacy first** - Your data stays on your device and iCloud. Nothing is sent to third parties
- **Open source** - Inspect the code, contribute, or fork it

## Features

- Interactive 3D globe with drag and pinch-to-zoom
- 2D map view alternative
- Track visited countries with a single tap
- All 193 UN member states + 2 observer states (Vatican City, Palestine)
- Beautiful country boundaries from GeoJSON data
- Syncs across devices via iCloud

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

The globe (`globe.scn`) is pre-built for fast startup. To regenerate after adding/removing countries:

1. Modify `GlobeScene.swift` to build from scratch and save to Documents
2. Run the app to generate new `globe.scn`
3. Copy it back to the project bundle

Run `testGlobeAndMapCountryConsistency` to verify map and globe are in sync.

### Data Storage

Visited countries are stored as country name strings in UserDefaults + iCloud. Data is independent of `globe.scn`.

## Support

Found a bug or have a feature request? [Open an issue](https://github.com/AnmolS99/voyage/issues) on GitHub.

---

_Built with vibes and [Claude](https://claude.ai) 🤖_
