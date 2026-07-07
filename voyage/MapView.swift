import SwiftUI

struct MapView: View {
    @ObservedObject var globeState: GlobeState
    @State private var countries: [GeoJSONCountry] = []
    @State private var scale: CGFloat = 1.0
    @State private var offset: CGSize = .zero
    @State private var lastScale: CGFloat = 1.0
    @State private var lastOffset: CGSize = .zero
    @State private var pathCache = PathCache()

    /// Per-country paths prebuilt in base map space (lon/lat projected at scale 1, no pan/zoom),
    /// so the per-frame Canvas pass only applies a transform instead of re-projecting ~170k
    /// boundary points. Reference type: lazily (re)built inside the Canvas closure without
    /// invalidating the view.
    private final class PathCache {
        struct Entry {
            let name: String
            let fillPath: Path      // outer rings + hole rings combined for even-odd fill
            let outerPaths: [Path]  // outer rings only (stroked borders)
            let bounds: CGRect      // base-space bounding box (gradient shading)
        }

        private(set) var mapWidth: CGFloat = 0
        private(set) var entries: [Entry] = []

        func rebuildIfNeeded(countries: [GeoJSONCountry], mapWidth: CGFloat) {
            let polygonCountries = countries.filter { !$0.isPointCountry }
            guard mapWidth != self.mapWidth || polygonCountries.count != entries.count else { return }
            self.mapWidth = mapWidth
            let mapHeight = mapWidth / 2

            func project(_ coord: [Double]) -> CGPoint {
                CGPoint(x: (coord[0] + 180) / 360 * mapWidth,
                        y: (90 - coord[1]) / 180 * mapHeight)
            }

            func ringPath(_ ring: [[Double]]) -> Path {
                var path = Path()
                var firstPoint = true
                for coord in ring where coord.count >= 2 {
                    let point = project(coord)
                    if firstPoint {
                        path.move(to: point)
                        firstPoint = false
                    } else {
                        path.addLine(to: point)
                    }
                }
                path.closeSubpath()
                return path
            }

            entries = polygonCountries.map { country in
                var fillPath = Path()
                var outerPaths: [Path] = []
                for polygon in country.polygons {
                    let path = ringPath(polygon)
                    outerPaths.append(path)
                    fillPath.addPath(path)
                }
                for hole in country.holes {
                    fillPath.addPath(ringPath(hole))
                }
                return Entry(name: country.name,
                             fillPath: fillPath,
                             outerPaths: outerPaths,
                             bounds: fillPath.boundingRect)
            }
        }
    }

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

                // Draw polygon countries from the path cache through a canvas-level
                // transform (pan/zoom), so paths are built once instead of per frame.
                pathCache.rebuildIfNeeded(countries: countries, mapWidth: mapWidth)

                var mapContext = context
                mapContext.translateBy(x: size.width / 2 + offset.width, y: size.height / 2 + offset.height)
                mapContext.scaleBy(x: scale, y: scale)
                mapContext.translateBy(x: -size.width / 2, y: -size.height / 2)
                mapContext.translateBy(x: 0, y: verticalOffset)

                for entry in pathCache.entries {
                    let isVisited = globeState.visitedCountries.contains(entry.name)
                    let isWishlist = globeState.wishlistCountries.contains(entry.name)
                    let isSelected = globeState.selectedCountry == entry.name
                    // Divide by scale: the canvas transform magnifies strokes, border width stays constant on screen
                    let borderWidth: CGFloat = (isSelected ? 1.5 : 0.5) / scale
                    let isBoth = isVisited && isWishlist

                    let fillShading: GraphicsContext.Shading
                    let borderShading: GraphicsContext.Shading

                    // Gradient shading from the cached base-space bounding box when both visited+wishlist
                    let gradientShading: GraphicsContext.Shading = .linearGradient(
                        Gradient(colors: [AppColors.visited, AppColors.wishlist]),
                        startPoint: CGPoint(x: entry.bounds.minX, y: entry.bounds.maxY),
                        endPoint: CGPoint(x: entry.bounds.maxX, y: entry.bounds.minY))

                    if isSelected {
                        fillShading = hasTexture ? .color(.clear) : .color(AppColors.land)
                        if isBoth { borderShading = gradientShading }
                        else if isVisited { borderShading = .color(AppColors.visited) }
                        else if isWishlist { borderShading = .color(AppColors.wishlist) }
                        else { borderShading = .color(.black) }
                    } else {
                        borderShading = .color(.black)
                        if isBoth { fillShading = gradientShading }
                        else if isVisited { fillShading = .color(AppColors.visited) }
                        else if isWishlist { fillShading = .color(AppColors.wishlist) }
                        else { fillShading = hasTexture ? .color(.clear) : .color(AppColors.land) }
                    }

                    // Even-odd fill: hole sub-paths toggle the winding count, leaving
                    // enclave areas (Lesotho) transparent so the underlying country shows through.
                    mapContext.fill(entry.fillPath, with: fillShading, style: FillStyle(eoFill: true))
                    // Only stroke outer country borders; enclave borders belong to the enclave country.
                    for path in entry.outerPaths {
                        mapContext.stroke(path, with: borderShading, lineWidth: borderWidth)
                    }
                }

                // Draw point countries (small island nations and microstates) as fixed-size
                // dots in screen space, so they stay visible and tappable at any zoom.
                for country in countries where country.isPointCountry {
                    let isVisited = globeState.visitedCountries.contains(country.name)
                    let isWishlist = globeState.wishlistCountries.contains(country.name)
                    let isSelected = globeState.selectedCountry == country.name
                    let borderWidth: CGFloat = isSelected ? 1.5 : 0.5
                    let isBoth = isVisited && isWishlist

                    // Determine fill and border shading
                    let fillShading: GraphicsContext.Shading
                    let borderShading: GraphicsContext.Shading

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
                        fillShading = hasTexture ? .color(.clear) : .color(AppColors.land)
                        if isBoth { borderShading = gradientShading }
                        else if isVisited { borderShading = .color(AppColors.visited) }
                        else if isWishlist { borderShading = .color(AppColors.wishlist) }
                        else { borderShading = .color(.black) }
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
        CountryHitTester.shared.findCountry(lat: lat, lon: lon)
    }

    private func getCountryCenter(name: String) -> (lat: Double, lon: Double)? {
        CountryHitTester.shared.center(of: name)
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
