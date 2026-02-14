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
                // Draw ocean background (fallback under texture)
                let oceanColor = globeState.isDarkMode ? AppColors.oceanDark : AppColors.oceanMap
                context.fill(
                    Path(CGRect(origin: .zero, size: size)),
                    with: .color(oceanColor)
                )

                // Apply transformations with proper aspect ratio
                var transform = CGAffineTransform.identity
                transform = transform.translatedBy(x: size.width / 2 + offset.width, y: size.height / 2 + offset.height)
                transform = transform.scaledBy(x: scale, y: scale)
                transform = transform.translatedBy(x: -size.width / 2, y: -size.height / 2)

                // Draw map texture background
                var hasTexture = false
                if let textureImage = UIImage(named: globeState.mapStyle.textureName) {
                    let topLeft = CGPoint(x: 0, y: verticalOffset).applying(transform)
                    let bottomRight = CGPoint(x: mapWidth, y: verticalOffset + mapHeight).applying(transform)
                    let textureRect = CGRect(
                        x: topLeft.x, y: topLeft.y,
                        width: bottomRight.x - topLeft.x,
                        height: bottomRight.y - topLeft.y
                    )
                    let resolved = context.resolve(Image(uiImage: textureImage))
                    context.draw(resolved, in: textureRect)
                    hasTexture = true
                }

                // Draw countries
                for country in countries {
                    let isVisited = globeState.visitedCountries.contains(country.name)
                    let isWishlist = globeState.wishlistCountries.contains(country.name)
                    let isSelected = globeState.selectedCountry == country.name
                    let borderWidth: CGFloat = isSelected ? 1.5 : 0.5
                    let isBoth = isVisited && isWishlist

                    // Determine fill and border shading
                    let fillShading: GraphicsContext.Shading
                    let borderShading: GraphicsContext.Shading

                    if country.isPointCountry {
                        // Point country: compute bounding box from dot position
                        guard let coord = country.pointCoordinate else { continue }
                        let x = (coord.lon + 180) / 360 * mapWidth
                        let y = (90 - coord.lat) / 180 * mapHeight + verticalOffset
                        let center = CGPoint(x: x, y: y).applying(transform)
                        let dotRadius: CGFloat = 5
                        let dotRect = CGRect(x: center.x - dotRadius, y: center.y - dotRadius,
                                             width: dotRadius * 2, height: dotRadius * 2)

                        let gradientShading: GraphicsContext.Shading = .linearGradient(
                            Gradient(colors: [AppColors.visited, AppColors.wishlist]),
                            startPoint: CGPoint(x: dotRect.minX, y: dotRect.maxY),
                            endPoint: CGPoint(x: dotRect.maxX, y: dotRect.minY))

                        if isSelected {
                            if isBoth {
                                fillShading = gradientShading
                                borderShading = .color(.white)
                            } else if isVisited {
                                fillShading = .color(AppColors.visited)
                                borderShading = .color(.white)
                            } else if isWishlist {
                                fillShading = .color(AppColors.wishlist)
                                borderShading = .color(.white)
                            } else {
                                fillShading = hasTexture ? .color(Color.white.opacity(0.3)) : .color(AppColors.land)
                                borderShading = .color(.black)
                            }
                        } else {
                            borderShading = .color(.black)
                            if isBoth { fillShading = gradientShading }
                            else if isVisited { fillShading = .color(AppColors.visited) }
                            else if isWishlist { fillShading = .color(AppColors.wishlist) }
                            else { fillShading = hasTexture ? .color(.clear) : .color(AppColors.land) }
                        }

                        let dotPath = Path(ellipseIn: dotRect)
                        context.fill(dotPath, with: fillShading)
                        context.stroke(dotPath, with: borderShading, lineWidth: borderWidth)
                    } else {
                        // Polygon country: compute gradient shading from bounding box when both visited+wishlist
                        func countryGradient() -> GraphicsContext.Shading {
                            var minX = CGFloat.infinity, maxX = -CGFloat.infinity
                            var minY = CGFloat.infinity, maxY = -CGFloat.infinity
                            for polygon in country.polygons {
                                for coord in polygon where coord.count >= 2 {
                                    let pt = CGPoint(
                                        x: (coord[0] + 180) / 360 * mapWidth,
                                        y: (90 - coord[1]) / 180 * mapHeight + verticalOffset
                                    ).applying(transform)
                                    minX = min(minX, pt.x); maxX = max(maxX, pt.x)
                                    minY = min(minY, pt.y); maxY = max(maxY, pt.y)
                                }
                            }
                            return .linearGradient(
                                Gradient(colors: [AppColors.visited, AppColors.wishlist]),
                                startPoint: CGPoint(x: minX, y: maxY),
                                endPoint: CGPoint(x: maxX, y: minY))
                        }

                        if isSelected {
                            if isBoth {
                                fillShading = countryGradient()
                                borderShading = .color(.white)
                            } else if isVisited {
                                fillShading = .color(AppColors.visited)
                                borderShading = .color(.white)
                            } else if isWishlist {
                                fillShading = .color(AppColors.wishlist)
                                borderShading = .color(.white)
                            } else {
                                fillShading = hasTexture ? .color(Color.white.opacity(0.3)) : .color(AppColors.land)
                                borderShading = .color(.black)
                            }
                        } else {
                            borderShading = .color(.black)
                            if isBoth { fillShading = countryGradient() }
                            else if isVisited { fillShading = .color(AppColors.visited) }
                            else if isWishlist { fillShading = .color(AppColors.wishlist) }
                            else { fillShading = hasTexture ? .color(.clear) : .color(AppColors.land) }
                        }

                        // Draw all polygons with the determined shading
                        for polygon in country.polygons {
                            var path = Path()
                            var firstPoint = true

                            for coord in polygon {
                                guard coord.count >= 2 else { continue }
                                let point = CGPoint(
                                    x: (coord[0] + 180) / 360 * mapWidth,
                                    y: (90 - coord[1]) / 180 * mapHeight + verticalOffset
                                ).applying(transform)

                                if firstPoint {
                                    path.move(to: point)
                                    firstPoint = false
                                } else {
                                    path.addLine(to: point)
                                }
                            }
                            path.closeSubpath()

                            context.fill(path, with: fillShading)
                            context.stroke(path, with: borderShading, lineWidth: borderWidth)
                        }
                    }
                }

                // Draw capital dot for selected country
                if let selectedCountry = globeState.selectedCountry,
                   let country = countries.first(where: { $0.name == selectedCountry }),
                   let capital = country.capital {
                    let x = (capital.lon + 180) / 360 * mapWidth
                    let y = (90 - capital.lat) / 180 * mapHeight + verticalOffset
                    let center = CGPoint(x: x, y: y).applying(transform)

                    // Draw small black dot
                    let dotRadius: CGFloat = 4
                    let dotPath = Path(ellipseIn: CGRect(
                        x: center.x - dotRadius,
                        y: center.y - dotRadius,
                        width: dotRadius * 2,
                        height: dotRadius * 2
                    ))

                    context.fill(dotPath, with: .color(.black))
                    context.stroke(dotPath, with: .color(Color(white: 0.3)), lineWidth: 1)
                }
            }
            .gesture(
                MagnifyGesture()
                    .onChanged { value in
                        let newScale = min(max(lastScale * value.magnification, 1.0), 10.0)

                        // Get the pinch anchor point in view coordinates
                        let anchor = value.startLocation

                        // Calculate offset adjustment to keep anchor point stationary
                        // The anchor point relative to center before zoom
                        let anchorFromCenter = CGPoint(
                            x: anchor.x - geometry.size.width / 2 - lastOffset.width,
                            y: anchor.y - geometry.size.height / 2 - lastOffset.height
                        )

                        // Scale factor change
                        let scaleChange = newScale / lastScale

                        // After zoom, the anchor would move by this much, so compensate
                        let newOffset = CGSize(
                            width: lastOffset.width + anchorFromCenter.x * (1 - scaleChange),
                            height: lastOffset.height + anchorFromCenter.y * (1 - scaleChange)
                        )

                        scale = newScale
                        offset = clampOffset(newOffset, scale: scale, viewSize: geometry.size)
                    }
                    .onEnded { _ in
                        lastScale = scale
                        lastOffset = offset
                    }
            )
            .simultaneousGesture(
                DragGesture()
                    .onChanged { value in
                        let newOffset = CGSize(
                            width: lastOffset.width + value.translation.width,
                            height: lastOffset.height + value.translation.height
                        )
                        offset = clampOffset(newOffset, scale: scale, viewSize: geometry.size)
                    }
                    .onEnded { _ in
                        lastOffset = offset
                    }
            )
            .contentShape(Rectangle())
            .onTapGesture(coordinateSpace: .local) { location in
                handleTap(at: location, in: geometry.size)
            }
        }
        .onAppear {
            countries = CountryDataCache.shared.countries
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
        // First check point countries (small island nations and microstates)
        let pointHitRadius: Double = 0.8
        for country in countries where country.isPointCountry {
            guard let coord = country.pointCoordinate else { continue }
            let distance = sqrt(pow(lat - coord.lat, 2) + pow(lon - coord.lon, 2))
            if distance < pointHitRadius {
                return country.name
            }
        }

        // Then check polygon countries
        for country in countries where !country.isPointCountry {
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

                for country in countries where !country.isPointCountry {
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

        // Point countries have their center stored directly
        if country.isPointCountry, let coord = country.pointCoordinate {
            return coord
        }

        // Calculate center for polygon countries
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

    // Clamp offset to prevent dragging outside map bounds
    private func clampOffset(_ offset: CGSize, scale: CGFloat, viewSize: CGSize) -> CGSize {
        let mapWidth = viewSize.width
        let mapHeight = mapWidth / 2

        // Calculate how much the scaled map extends beyond the view
        let scaledMapWidth = mapWidth * scale
        let scaledMapHeight = mapHeight * scale

        // Maximum offset is half the difference between scaled map and view
        let maxOffsetX = max(0, (scaledMapWidth - viewSize.width) / 2)
        let maxOffsetY = max(0, (scaledMapHeight - viewSize.height) / 2)

        return CGSize(
            width: min(max(offset.width, -maxOffsetX), maxOffsetX),
            height: min(max(offset.height, -maxOffsetY), maxOffsetY)
        )
    }

}
