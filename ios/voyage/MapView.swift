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

    /// Where the 2:1 equirectangular map sits in the view at scale 1: it *fills* the
    /// view, spanning the full height in portrait (overhanging left and right) and the
    /// full width in landscape (overhanging top and bottom). Zoom never goes below 1,
    /// so no empty space ever shows beside or above the map. Mirrors Android's
    /// `MapProjection`.
    private struct MapFit {
        let mapWidth: CGFloat
        let mapHeight: CGFloat
        /// Centres the map in the view; zero or negative, as the map overhangs.
        let horizontalOffset: CGFloat
        let verticalOffset: CGFloat

        init(viewSize: CGSize) {
            mapHeight = max(viewSize.height, viewSize.width / 2)
            mapWidth = mapHeight * 2
            horizontalOffset = (viewSize.width - mapWidth) / 2
            verticalOffset = (viewSize.height - mapHeight) / 2
        }

        /// Lon/lat to view space at scale 1 (before pan/zoom).
        func point(lat: Double, lon: Double) -> CGPoint {
            CGPoint(x: (lon + 180) / 360 * mapWidth + horizontalOffset,
                    y: (90 - lat) / 180 * mapHeight + verticalOffset)
        }
    }

    var body: some View {
        GeometryReader { geometry in
            let fit = MapFit(viewSize: geometry.size)

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
                    let topLeft = CGPoint(x: fit.horizontalOffset, y: fit.verticalOffset).applying(transform)
                    let bottomRight = CGPoint(x: fit.horizontalOffset + fit.mapWidth,
                                              y: fit.verticalOffset + fit.mapHeight).applying(transform)
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
                pathCache.rebuildIfNeeded(countries: countries, mapWidth: fit.mapWidth)

                var mapContext = context
                mapContext.translateBy(x: size.width / 2 + offset.width, y: size.height / 2 + offset.height)
                mapContext.scaleBy(x: scale, y: scale)
                mapContext.translateBy(x: -size.width / 2, y: -size.height / 2)
                mapContext.translateBy(x: fit.horizontalOffset, y: fit.verticalOffset)

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
                    let center = fit.point(lat: coord.lat, lon: coord.lon).applying(transform)
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
                    let center = fit.point(lat: capital.lat, lon: capital.lon).applying(transform)

                    // Five-pointed star, matching the globe's capital marker
                    let starRadius: CGFloat = 6
                    let starPath = Path(CapitalMarker.starPath(outerRadius: starRadius, yUp: false))
                        .applying(CGAffineTransform(translationX: center.x, y: center.y))

                    context.fill(starPath, with: .color(AppColors.capitalMarker))
                    context.stroke(starPath, with: .color(AppColors.capitalMarkerOutline), lineWidth: 1)
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
            // A pan built against the old bounds is meaningless after a rotation, so
            // zoom resets with the view size — as on Android.
            .onChange(of: geometry.size) { _, size in resetZoom(viewSize: size) }
            // Coming from the globe, the map always opens filling the screen.
            .onChange(of: globeState.viewMode) { _, mode in
                if mode == .map { resetZoom(viewSize: geometry.size) }
            }
        }
        .onAppear {
            countries = CountryDataCache.shared.countries
        }
    }

    /// Back to minimum zoom, centred on the selected country as far as the clamp
    /// allows (in portrait: horizontally). Mirrors Android's `offsetCentring`.
    private func resetZoom(viewSize: CGSize) {
        var centring = CGSize.zero
        if let name = globeState.selectedCountry,
           let center = CountryHitTester.shared.center(of: name) {
            let point = MapFit(viewSize: viewSize).point(lat: center.lat, lon: center.lon)
            centring = CGSize(width: viewSize.width / 2 - point.x,
                              height: viewSize.height / 2 - point.y)
        }
        scale = 1
        lastScale = 1
        offset = clampOffset(centring, scale: 1, viewSize: viewSize)
        lastOffset = offset
    }

    private func handleTap(at location: CGPoint, in size: CGSize) {
        let fit = MapFit(viewSize: size)

        // Reverse the transformation to get map coordinates
        let centerX = size.width / 2 + offset.width
        let centerY = size.height / 2 + offset.height

        let mapX = (location.x - centerX) / scale + size.width / 2
        let mapY = (location.y - centerY) / scale + size.height / 2

        // Undo the centring to get lat/lon
        let lon = (mapX - fit.horizontalOffset) / fit.mapWidth * 360 - 180
        let lat = 90 - (mapY - fit.verticalOffset) / fit.mapHeight * 180

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

    // Clamp offset so no edge of the map can be dragged into view
    private func clampOffset(_ offset: CGSize, scale: CGFloat, viewSize: CGSize) -> CGSize {
        let fit = MapFit(viewSize: viewSize)

        // Calculate how much the scaled map extends beyond the view
        let scaledMapWidth = fit.mapWidth * scale
        let scaledMapHeight = fit.mapHeight * scale

        // Maximum offset is half the difference between scaled map and view
        // (never negative thanks to the fill; max() only absorbs float rounding)
        let maxOffsetX = max(0, (scaledMapWidth - viewSize.width) / 2)
        let maxOffsetY = max(0, (scaledMapHeight - viewSize.height) / 2)

        return CGSize(
            width: min(max(offset.width, -maxOffsetX), maxOffsetX),
            height: min(max(offset.height, -maxOffsetY), maxOffsetY)
        )
    }

}
