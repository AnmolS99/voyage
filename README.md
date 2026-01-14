# Globe Explorer

An interactive 3D globe iOS app built with SwiftUI and SceneKit. Tap on any country to highlight it and see its flag.

![iOS](https://img.shields.io/badge/iOS-17.0+-blue)
![Swift](https://img.shields.io/badge/Swift-5.0-orange)
![License](https://img.shields.io/badge/License-Private-red)

<a href="https://www.buymeacoffee.com/anmols99" target="_blank"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" height="40"></a>

## Features

- **Interactive 3D Globe** - Rotate the globe by dragging, zoom with pinch or +/- buttons
- **Country Selection** - Tap any country to highlight it in yellow
- **Country Flags** - Displays the flag emoji for selected countries
- **Real Country Boundaries** - Uses GeoJSON data for accurate country outlines
- **180+ Countries** - Includes all major countries and territories

## Screenshots

The app displays a beautiful 3D globe with:
- Ocean rendered in blue
- Countries in various shades of green
- Selected countries highlighted in yellow
- Country name and flag displayed at the bottom

## Technical Details

### Architecture

- **SwiftUI** - Main UI framework
- **SceneKit** - 3D rendering of the globe
- **GeoJSON** - Country boundary data from [world.geo.json](https://github.com/johan/world.geo.json)

### Key Files

| File | Description |
|------|-------------|
| `ContentView.swift` | Main UI with header, globe view, and bottom panel |
| `GlobeView.swift` | SceneKit view wrapper with gesture handling |
| `GlobeScene.swift` | 3D scene setup (globe, lighting, camera) |
| `GeoJSONParser.swift` | Parses country boundary data |
| `PolygonTriangulator.swift` | Converts polygons to 3D geometry |

### Country Detection

The app uses a ray-casting point-in-polygon algorithm to detect which country was tapped. It includes a search radius feature to make selecting smaller countries easier.

## Requirements

- iOS 17.0+
- Xcode 15.0+

## Installation

1. Clone the repository
2. Open `GlobeExplorer.xcodeproj` in Xcode
3. Build and run on simulator or device

## License

Private repository - All rights reserved.
