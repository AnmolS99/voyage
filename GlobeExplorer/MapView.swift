import SwiftUI

struct MapView: View {
    @ObservedObject var globeState: GlobeState
    @State private var countries: [GeoJSONCountry] = []
    @State private var scale: CGFloat = 1.0
    @State private var offset: CGSize = .zero
    @State private var lastScale: CGFloat = 1.0
    @State private var lastOffset: CGSize = .zero

    var body: some View {
        GeometryReader { geometry in
            // Use 2:1 aspect ratio for equirectangular projection
            let mapWidth = geometry.size.width
            let mapHeight = mapWidth / 2
            let verticalOffset = (geometry.size.height - mapHeight) / 2

            Canvas { context, size in
                // Draw ocean background
                let oceanColor = globeState.isDarkMode ?
                    Color(red: 0.1, green: 0.15, blue: 0.25) :
                    Color(red: 0.2, green: 0.4, blue: 0.65)
                context.fill(
                    Path(CGRect(origin: .zero, size: size)),
                    with: .color(oceanColor)
                )

                // Apply transformations with proper aspect ratio
                var transform = CGAffineTransform.identity
                transform = transform.translatedBy(x: size.width / 2 + offset.width, y: size.height / 2 + offset.height)
                transform = transform.scaledBy(x: scale, y: scale)
                transform = transform.translatedBy(x: -size.width / 2, y: -size.height / 2)

                // Draw countries
                for country in countries {
                    let isCurrentlySelected = globeState.selectedCountry == country.name
                    let isVisited = globeState.visitedCountries.contains(country.name)

                    let fillColor: Color
                    if isCurrentlySelected {
                        fillColor = Color(red: 0.7, green: 0.9, blue: 0.4)
                    } else if isVisited {
                        fillColor = Color(red: 1.0, green: 0.85, blue: 0.2)
                    } else {
                        fillColor = Color(red: 0.3, green: 0.6, blue: 0.35)
                    }

                    for polygon in country.polygons {
                        var path = Path()
                        var firstPoint = true

                        for coord in polygon {
                            guard coord.count >= 2 else { continue }
                            let lon = coord[0]
                            let lat = coord[1]

                            // Equirectangular projection with proper aspect ratio
                            let x = (lon + 180) / 360 * mapWidth
                            let y = (90 - lat) / 180 * mapHeight + verticalOffset

                            let point = CGPoint(x: x, y: y).applying(transform)

                            if firstPoint {
                                path.move(to: point)
                                firstPoint = false
                            } else {
                                path.addLine(to: point)
                            }
                        }
                        path.closeSubpath()

                        context.fill(path, with: .color(fillColor))

                        // Draw border
                        let borderColor = globeState.isDarkMode ?
                            Color(white: 0.3) : Color(white: 0.2)
                        context.stroke(path, with: .color(borderColor), lineWidth: 0.5)
                    }
                }
            }
            .gesture(
                MagnificationGesture()
                    .onChanged { value in
                        let newScale = lastScale * value
                        scale = min(max(newScale, 1.0), 5.0)
                    }
                    .onEnded { _ in
                        lastScale = scale
                    }
            )
            .simultaneousGesture(
                DragGesture()
                    .onChanged { value in
                        offset = CGSize(
                            width: lastOffset.width + value.translation.width,
                            height: lastOffset.height + value.translation.height
                        )
                    }
                    .onEnded { _ in
                        lastOffset = offset
                    }
            )
            .simultaneousGesture(
                TapGesture()
                    .onEnded { _ in
                        // Tap handling is done via overlay
                    }
            )
            .contentShape(Rectangle())
            .onTapGesture { location in
                handleTap(at: location, in: geometry.size)
            }
        }
        .onAppear {
            countries = GeoJSONParser.loadCountries()
        }
    }

    private func handleTap(at location: CGPoint, in size: CGSize) {
        // Use same aspect ratio as rendering
        let mapWidth = size.width
        let mapHeight = mapWidth / 2
        let verticalOffset = (size.height - mapHeight) / 2

        // Reverse the transformation to get map coordinates
        let centerX = size.width / 2 + offset.width
        let centerY = size.height / 2 + offset.height

        let mapX = (location.x - centerX) / scale + size.width / 2
        let mapY = (location.y - centerY) / scale + size.height / 2

        // Convert to lat/lon with proper aspect ratio
        let lon = mapX / mapWidth * 360 - 180
        let lat = 90 - (mapY - verticalOffset) / mapHeight * 180

        // Find country at this location
        if let countryName = findCountryAt(lat: lat, lon: lon) {
            let center = getCountryCenter(name: countryName)
            globeState.selectCountry(countryName, center: center)
        }
    }

    private func findCountryAt(lat: Double, lon: Double) -> String? {
        for country in countries {
            for polygon in country.polygons {
                if isPointInPolygon(lon: lon, lat: lat, polygon: polygon) {
                    return country.name
                }
            }
        }

        // Search nearby for small countries
        let searchRadii: [Double] = [1.0, 2.0, 3.0]
        let pointsPerRadius = 8

        for radius in searchRadii {
            for i in 0..<pointsPerRadius {
                let angle = Double(i) * (2.0 * .pi / Double(pointsPerRadius))
                let searchLat = lat + radius * sin(angle)
                let searchLon = lon + radius * cos(angle)

                for country in countries {
                    for polygon in country.polygons {
                        if isPointInPolygon(lon: searchLon, lat: searchLat, polygon: polygon) {
                            return country.name
                        }
                    }
                }
            }
        }

        return nil
    }

    private func isPointInPolygon(lon: Double, lat: Double, polygon: [[Double]]) -> Bool {
        var inside = false
        var j = polygon.count - 1

        for i in 0..<polygon.count {
            guard polygon[i].count >= 2 && polygon[j].count >= 2 else {
                j = i
                continue
            }
            let xi = polygon[i][0], yi = polygon[i][1]
            let xj = polygon[j][0], yj = polygon[j][1]

            if ((yi > lat) != (yj > lat)) &&
                (lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) {
                inside = !inside
            }
            j = i
        }

        return inside
    }

    private func getCountryCenter(name: String) -> (lat: Double, lon: Double)? {
        guard let country = countries.first(where: { $0.name == name }) else { return nil }

        var totalLat = 0.0
        var totalLon = 0.0
        var count = 0

        for polygon in country.polygons {
            for coord in polygon {
                if coord.count >= 2 {
                    totalLon += coord[0]
                    totalLat += coord[1]
                    count += 1
                }
            }
        }

        guard count > 0 else { return nil }
        return (lat: totalLat / Double(count), lon: totalLon / Double(count))
    }
}
