import SwiftUI

struct MapView: View {
    let globeState: GlobeState
    @State private var countries: [GeoJSONCountry] = []
    @State private var scale: CGFloat = 1.0
    @State private var offset: CGSize = .zero
    @State private var lastScale: CGFloat = 1.0
    @State private var lastOffset: CGSize = .zero
    @State private var pathCache = PathCache()
    @State private var touches = TouchTracker()
    @State private var shownLook = ShownLook()
    /// Whether the map has been shown, and so is drawn — see `MapCanvas.isDrawn`.
    @State private var isDrawn = false
    @Environment(\.scenePhase) private var scenePhase

    /// What the touch in progress is doing. The map follows every touch through one
    /// zero-distance drag, which tells a tap, a pan and a zoom drag apart itself:
    /// a zero-distance drag recognises the moment a finger lands, so a separate
    /// `onTapGesture` beside it never fires.
    ///
    /// The zoom drag is tap, then press and drag vertically: the one-finger zoom
    /// the globe has (`GlobeView.Coordinator.handleDoubleTapDrag`), and Android's
    /// `detectZoomDrags`, which both of its renderers share. SwiftUI has no
    /// tap-then-press gesture before iOS 18, hence recognising it by hand.
    ///
    /// A reference type, like `PathCache`: bookkeeping a touch must not invalidate
    /// the view, as every invalidation redraws the whole Canvas.
    private final class TouchTracker {
        enum Mode { case undecided, panning, zooming }

        /// Where the touch in progress went down; nil between touches.
        var startLocation: CGPoint?
        var downTime = Date.distantPast
        var mode = Mode.undecided
        /// A pinch began during this touch, so its end is not a tap.
        var pinched = false
        /// Zoom and pan when the touch went down, which a pan and a zoom drag
        /// both measure from.
        var startScale: CGFloat = 1
        var startOffset: CGSize = .zero
        /// The last touch that was a quick tap, which a press soon after it turns
        /// into a zoom drag.
        var lastTap: (time: Date, location: CGPoint)?

        /// Movement a touch may have and still be a tap; past it, it pans.
        /// `DragGesture`'s default minimum distance.
        static let tapSlop: CGFloat = 10
        /// Longest touch that still counts as a zoom drag's first tap — UIKit's
        /// long-press default, as the globe's recognizer uses.
        static let maxTapDuration: TimeInterval = 0.4
        /// How soon, and how close to the tap, the second press must land.
        static let maxTapInterval: TimeInterval = 0.3
        static let maxTapDistance: CGFloat = 40
    }

    /// Finger travel, in points, that doubles (or halves) the zoom in a one-finger
    /// zoom drag — Android `MapProjection.ZOOM_DRAG_DOUBLING_DP`, as a point and a
    /// dp are the same physical size.
    static let zoomDragDoublingDistance: CGFloat = 100

    /// Applies a one-finger zoom drag: `travel` points of vertical finger travel
    /// (positive downwards) since a drag that began at `startScale`.
    ///
    /// Exponential, so every point is the same *ratio* of zoom across the whole
    /// 1…10 range, and measured from where the drag began, so dragging back puts
    /// the zoom back exactly. Dragging **up** zooms in — Apple's direction, as on
    /// the globe; Android's goes the other way (see `handleDoubleTapDrag`).
    static func zoomDragScale(from startScale: CGFloat, travel: CGFloat) -> CGFloat {
        min(max(startScale * pow(2, -travel / zoomDragDoublingDistance), 1.0), 10.0)
    }

    /// Per-country paths prebuilt in base map space (lon/lat projected at scale 1, no pan/zoom),
    /// so the per-frame Canvas pass only applies a transform instead of re-projecting ~170k
    /// boundary points. Reference type: lazily (re)built inside the Canvas closure without
    /// invalidating the view.
    private final class PathCache {
        struct Entry {
            let name: String
            let fillPath: Path      // outer rings + hole rings combined for even-odd fill
            let borderPath: Path    // outer rings only, as one path: one stroke per country
            let bounds: CGRect      // base-space bounding box (culling, gradient shading)
        }

        private(set) var mapWidth: CGFloat = 0
        private(set) var entries: [Entry] = []
        /// How many countries `entries` was built from.
        private var countryCount = 0

        func rebuildIfNeeded(countries: [GeoJSONCountry], mapWidth: CGFloat) {
            // Checked on every evaluation of the map's body, so nothing is
            // allocated until there is something to rebuild.
            guard mapWidth != self.mapWidth || countries.count != countryCount else { return }
            self.mapWidth = mapWidth
            countryCount = countries.count
            let polygonCountries = countries.filter { !$0.isPointCountry }
            let mapHeight = mapWidth / 2

            func project(_ coord: LonLat) -> CGPoint {
                CGPoint(x: (coord.lon + 180) / 360 * mapWidth,
                        y: (90 - coord.lat) / 180 * mapHeight)
            }

            func ringPath(_ ring: Ring) -> Path {
                var path = Path()
                var firstPoint = true
                for coord in ring {
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
                var borderPath = Path()
                for polygon in country.polygons {
                    borderPath.addPath(ringPath(polygon))
                }
                var fillPath = borderPath
                for hole in country.holes {
                    fillPath.addPath(ringPath(hole))
                }
                return Entry(name: country.name,
                             fillPath: fillPath,
                             borderPath: borderPath,
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
        let viewSize: CGSize
        let mapWidth: CGFloat
        let mapHeight: CGFloat
        /// Centres the map in the view; zero or negative, as the map overhangs.
        let horizontalOffset: CGFloat
        let verticalOffset: CGFloat

        init(viewSize: CGSize) {
            self.viewSize = viewSize
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

        /// The lat/lon under view point `location` once the map is zoomed by
        /// `scale` and panned by `offset`. Android `MapProjection.viewToLatLon`.
        func latLon(at location: CGPoint, scale: CGFloat, offset: CGSize) -> (lat: Double, lon: Double) {
            // Undo the pan and zoom, which act about the view's centre
            let x = (location.x - viewSize.width / 2 - offset.width) / scale + viewSize.width / 2
            let y = (location.y - viewSize.height / 2 - offset.height) / scale + viewSize.height / 2
            return (lat: 90 - (y - verticalOffset) / mapHeight * 180,
                    lon: (x - horizontalOffset) / mapWidth * 360 - 180)
        }

        /// `offset` held to where no edge of the map comes into view at `scale`.
        func clamped(_ offset: CGSize, scale: CGFloat) -> CGSize {
            // Half of what the zoomed map extends beyond the view (never negative
            // thanks to the fill; max() only absorbs float rounding)
            let maxOffsetX = max(0, (mapWidth * scale - viewSize.width) / 2)
            let maxOffsetY = max(0, (mapHeight * scale - viewSize.height) / 2)
            return CGSize(width: min(max(offset.width, -maxOffsetX), maxOffsetX),
                          height: min(max(offset.height, -maxOffsetY), maxOffsetY))
        }

        /// The part of the map in view, in the base map space `PathCache` builds
        /// its paths in (map at scale 1, origin at its top-left).
        func visibleMapRect(scale: CGFloat, offset: CGSize) -> CGRect {
            CGRect(x: (-viewSize.width / 2 - offset.width) / scale + viewSize.width / 2 - horizontalOffset,
                   y: (-viewSize.height / 2 - offset.height) / scale + viewSize.height / 2 - verticalOffset,
                   width: viewSize.width / scale,
                   height: viewSize.height / scale)
        }

        /// A microstate dot's on-screen radius at `scale`: the hit tester's 0.8° point
        /// radius on the map, so it grows with the land when zooming in and what is
        /// drawn is what is tappable — as on the globe — but never below 5 pt, so it
        /// stays visible zoomed out. Android `MapProjection.dotRadius`.
        func dotRadius(scale: CGFloat) -> CGFloat {
            max(5, CGFloat(CountryHitTester.pointHitRadius) / 360 * mapWidth * scale)
        }
    }

    /// The Earth texture and the relief as the Canvas draws them every frame:
    /// decoded once and kept. A `UIImage(named:)` of the 8192 px Earth texture
    /// was decoded again by every frame that drew it, which alone took most of
    /// a frame's time.
    ///
    /// The texture is kept at its full size — at minimum zoom the map is
    /// already wider in pixels than half of it — which is about 134 MB decoded
    /// (Core Graphics can't draw the globe's compressed copy, see
    /// `EarthTexture`). So only the style in use is held — choosing another
    /// lets the last one go — and none while the app is in the background,
    /// where memory held is what gets an app ended: the next frame drawn
    /// decodes it again.
    private enum MapImage {
        static let relief: UIImage? = decoded(TerrainRelief.imageName)

        private static var texture: (name: String, image: UIImage)?
        private static let lock = NSLock()

        private static let backgroundObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: nil
        ) { _ in
            lock.lock()
            texture = nil
            lock.unlock()
        }

        static func texture(named name: String) -> UIImage? {
            _ = backgroundObserver
            lock.lock()
            defer { lock.unlock() }
            if let texture, texture.name == name { return texture.image }
            texture = nil
            guard let image = decoded(name) else { return nil }
            texture = (name, image)
            return image
        }

        /// Read from the file rather than `UIImage(named:)`, whose system cache
        /// would keep the image after it is let go here.
        private static func decoded(_ name: String) -> UIImage? {
            guard let path = EarthTexture.url(named: name)?.path,
                  let source = UIImage(contentsOfFile: path) else { return nil }
            return source.preparingForDisplay() ?? source
        }
    }

    /// What the map draws of `GlobeState`.
    private struct Look {
        let isDarkMode: Bool
        let textureName: String
        let showsTerrain: Bool
        let visited: Set<String>
        let wishlist: Set<String>
        let selected: String?

        init(_ globeState: GlobeState) {
            isDarkMode = globeState.isDarkMode
            textureName = globeState.mapStyle.textureName
            showsTerrain = globeState.showsTerrain
            visited = globeState.visitedCountries
            wishlist = globeState.wishlistCountries
            selected = globeState.selectedCountry
        }
    }

    /// The `Look` the map last drew, kept while it is behind the globe. A
    /// reference type, like `PathCache`: keeping it must not invalidate the view.
    private final class ShownLook {
        var look: Look?
    }

    /// The look to draw. Behind the globe the map stays in the hierarchy, so
    /// switching to it is an instant crossfade, but it holds the look it last
    /// drew: `body` then reads nothing of `GlobeState` but the view mode, so a
    /// selection or a visit made on the globe neither re-evaluates the map nor
    /// redraws its Canvas unseen. It catches up in the update that shows it.
    private func lookToDraw() -> Look {
        if globeState.viewMode != .map, let look = shownLook.look { return look }
        let look = Look(globeState)
        shownLook.look = look
        return look
    }

    /// The map's drawing, as a view of its own holding plain values. Its Canvas
    /// renders off the main thread — hard-lighting the relief over the whole
    /// map takes several frames' worth of time, which on the main thread held
    /// up the touches of the very pan or zoom being drawn — so it may read
    /// nothing from `MapView`'s state or `GlobeState` while drawing: `MapView`
    /// reads those in its `body` and hands them in.
    private struct MapCanvas: View {
        let fit: MapFit
        let scale: CGFloat
        let offset: CGSize
        let countries: [GeoJSONCountry]
        let entries: [PathCache.Entry]
        let isDarkMode: Bool
        let textureName: String
        let showsTerrain: Bool
        let visited: Set<String>
        let wishlist: Set<String>
        let selected: String?
        /// False while the map has yet to be shown: it is mounted under the
        /// globe from launch, and drawing it there would decode the texture
        /// for a map nobody may ever open.
        let isDrawn: Bool

        /// What the canvas fills or strokes with for `paint` — the globe's
        /// colors for the same `CountryStyle`. `plain` is what an unpainted fill
        /// or border takes; the visited-and-wishlist gradient runs from the
        /// bottom-left of `rect` to its top-right.
        private static func shading(_ paint: CountryStyle.Paint,
                                    plain: GraphicsContext.Shading,
                                    gradientAcross rect: CGRect) -> GraphicsContext.Shading {
            switch paint {
            case .plain:
                return plain
            case .visited:
                return .color(AppColors.visited)
            case .wishlist:
                return .color(AppColors.wishlist)
            case .visitedAndWishlist:
                return .linearGradient(
                    Gradient(colors: [AppColors.visited, AppColors.wishlist]),
                    startPoint: CGPoint(x: rect.minX, y: rect.maxY),
                    endPoint: CGPoint(x: rect.maxX, y: rect.minY))
            case .custom(let color):
                return .color(Color(color))
            }
        }

        var body: some View {
            Canvas(rendersAsynchronously: true) { context, size in
                guard isDrawn else { return }

                // Draw ocean background (fallback under texture)
                let oceanColor = isDarkMode ? AppColors.oceanDark : AppColors.oceanMap
                context.fill(
                    Path(CGRect(origin: .zero, size: size)),
                    with: .color(oceanColor)
                )

                // Apply transformations with proper aspect ratio
                var transform = CGAffineTransform.identity
                transform = transform.translatedBy(x: size.width / 2 + offset.width, y: size.height / 2 + offset.height)
                transform = transform.scaledBy(x: scale, y: scale)
                transform = transform.translatedBy(x: -size.width / 2, y: -size.height / 2)

                // The whole map on screen, which the texture and the relief fill
                let topLeft = CGPoint(x: fit.horizontalOffset, y: fit.verticalOffset).applying(transform)
                let bottomRight = CGPoint(x: fit.horizontalOffset + fit.mapWidth,
                                          y: fit.verticalOffset + fit.mapHeight).applying(transform)
                let mapRect = CGRect(
                    x: topLeft.x, y: topLeft.y,
                    width: bottomRight.x - topLeft.x,
                    height: bottomRight.y - topLeft.y
                )

                // Draw map texture background
                var hasTexture = false
                if let textureImage = MapImage.texture(named: textureName) {
                    let resolved = context.resolve(Image(uiImage: textureImage))
                    context.draw(resolved, in: mapRect)
                    hasTexture = true
                }

                // Plain land is left unpainted over a texture, as on the globe
                let plainFill: GraphicsContext.Shading = hasTexture ? .color(.clear) : .color(AppColors.land)

                // Draw polygon countries from the path cache through a canvas-level
                // transform (pan/zoom), so paths are built once instead of per frame.

                var mapContext = context
                mapContext.translateBy(x: size.width / 2 + offset.width, y: size.height / 2 + offset.height)
                mapContext.scaleBy(x: scale, y: scale)
                mapContext.translateBy(x: -size.width / 2, y: -size.height / 2)
                mapContext.translateBy(x: fit.horizontalOffset, y: fit.verticalOffset)

                // Only what is in view: zoomed in on Europe, most of the world's
                // 171k points would be tessellated just to be clipped away. The
                // margin covers a border's width.
                let visibleRect = fit.visibleMapRect(scale: scale, offset: offset).insetBy(dx: -2 / scale, dy: -2 / scale)

                var selectedBorder: (path: Path, shading: GraphicsContext.Shading)?
                for entry in entries where entry.bounds.intersects(visibleRect) {
                    let style = CountryStyle(isVisited: visited.contains(entry.name),
                                             isWishlist: wishlist.contains(entry.name),
                                             isSelected: selected == entry.name)

                    // The gradient runs across the cached base-space bounding box
                    let fillShading = Self.shading(style.fill, plain: plainFill, gradientAcross: entry.bounds)
                    if style.isSelected {
                        selectedBorder = (entry.borderPath,
                                          Self.shading(style.border, plain: .color(.black), gradientAcross: entry.bounds))
                    }

                    // Even-odd fill: hole sub-paths toggle the winding count, leaving
                    // enclave areas (Lesotho) transparent so the underlying country shows through.
                    mapContext.fill(entry.fillPath, with: fillShading, style: FillStyle(eoFill: true))
                }

                // Hard light, the blend the globe's shader modifier applies: neutral
                // gray leaves a color as it is, so the ocean and flat land keep their
                // palette colors and only slopes shade. Over the fills, under the
                // borders and markers — which the globe leaves unshaded too.
                if showsTerrain, let relief = MapImage.relief {
                    var reliefContext = context
                    reliefContext.blendMode = .hardLight
                    reliefContext.draw(reliefContext.resolve(Image(uiImage: relief)), in: mapRect)
                }

                // Only stroke outer country borders; enclave borders belong to the enclave country.
                // Divide by scale: the canvas transform magnifies strokes, border width stays constant on screen.
                // Every border but the selected country's is the same thin black line, one stroke per country.
                for entry in entries where entry.name != selected && entry.bounds.intersects(visibleRect) {
                    mapContext.stroke(entry.borderPath, with: .color(.black), lineWidth: 0.5 / scale)
                }
                // The selected country's goes on last, over its neighbours' — as
                // the globe raises its selected outline above the others.
                if let selectedBorder {
                    mapContext.stroke(selectedBorder.path, with: selectedBorder.shading, lineWidth: 1.5 / scale)
                }

                // Draw point countries (small island nations and microstates) as dots sized
                // on the map with a screen-space floor, so they stay visible and tappable at any zoom.
                let dotRadius = fit.dotRadius(scale: scale)
                for country in countries where country.isPointCountry {
                    let style = CountryStyle(isVisited: visited.contains(country.name),
                                             isWishlist: wishlist.contains(country.name),
                                             isSelected: selected == country.name)
                    let borderWidth: CGFloat = style.isSelected ? 1.5 : 0.5

                    guard let coord = country.pointCoordinate else { continue }
                    let center = fit.point(lat: coord.lat, lon: coord.lon).applying(transform)
                    let dotRect = CGRect(x: center.x - dotRadius, y: center.y - dotRadius,
                                         width: dotRadius * 2, height: dotRadius * 2)

                    let fillShading = Self.shading(style.fill, plain: plainFill, gradientAcross: dotRect)
                    let borderShading = Self.shading(style.border, plain: .color(.black), gradientAcross: dotRect)

                    let dotPath = Path(ellipseIn: dotRect)
                    context.fill(dotPath, with: fillShading)
                    context.stroke(dotPath, with: borderShading, lineWidth: borderWidth)
                }

                // Draw a seal at each capital of the selected country
                if let selectedCountry = selected,
                   let country = CountryDataCache.shared.countryNamed(selectedCountry) {
                    for capital in country.capitals {
                        let center = fit.point(lat: capital.lat, lon: capital.lon).applying(transform)

                        // Star seal, drawn by the same routine as the globe's capital marker
                        context.withCGContext { cgContext in
                            CapitalMarker.draw(in: cgContext, center: center, radius: 6)
                        }
                    }
                }
            }
        }
    }

    var body: some View {
        GeometryReader { geometry in
            let fit = MapFit(viewSize: geometry.size)

            // Rebuilt here, not while drawing: the Canvas draws off the main thread.
            let _ = pathCache.rebuildIfNeeded(countries: countries, mapWidth: fit.mapWidth)

            let look = lookToDraw()

            MapCanvas(fit: fit, scale: scale, offset: offset,
                      countries: countries, entries: pathCache.entries,
                      isDarkMode: look.isDarkMode,
                      textureName: look.textureName,
                      showsTerrain: look.showsTerrain,
                      visited: look.visited,
                      wishlist: look.wishlist,
                      selected: look.selected,
                      isDrawn: isDrawn)
            .gesture(
                MagnifyGesture()
                    .onChanged { value in
                        touches.pinched = true
                        let newScale = min(max(lastScale * value.magnification, 1.0), 10.0)

                        // Keep the pinch's anchor point stationary
                        offset = zoomedOffset(about: value.startLocation, from: lastOffset,
                                              fromScale: lastScale, toScale: newScale,
                                              viewSize: geometry.size)
                        scale = newScale
                    }
                    .onEnded { _ in
                        lastScale = scale
                        lastOffset = offset
                    }
            )
            .simultaneousGesture(
                DragGesture(minimumDistance: 0, coordinateSpace: .local)
                    .onChanged { value in touchChanged(value, viewSize: geometry.size) }
                    .onEnded { value in touchEnded(value, viewSize: geometry.size) }
            )
            .contentShape(Rectangle())
            // A pan built against the old bounds is meaningless after a rotation, so
            // zoom resets with the view size — as on Android.
            .onChange(of: geometry.size) { _, size in resetZoom(viewSize: size) }
            // Coming from the globe, the map always opens filling the screen.
            .onChange(of: globeState.viewMode) { _, mode in
                if mode == .map {
                    isDrawn = true
                    resetZoom(viewSize: geometry.size)
                }
            }
            // `MapImage` lets the texture go in the background; behind the globe,
            // stop drawing too, or the next redraw would decode it again unseen.
            .onChange(of: scenePhase) { _, phase in
                if phase == .background, globeState.viewMode != .map { isDrawn = false }
            }
        }
        .onAppear {
            countries = CountryDataCache.shared.countries
            if globeState.viewMode == .map { isDrawn = true }
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

    /// Every change of every touch. A touch that presses soon after a tap, near
    /// it, is a zoom drag about where it pressed, as a pinch zooms about its
    /// anchor; any other pans once it has moved past the tap slop. Nothing changes
    /// until the finger moves, so a tap stays a tap.
    private func touchChanged(_ value: DragGesture.Value, viewSize: CGSize) {
        if touches.startLocation != value.startLocation {
            // A new touch — told by where it started rather than by the last one
            // having ended, as a cancelled drag never reports its end.
            touches.startLocation = value.startLocation
            touches.downTime = value.time
            touches.mode = .undecided
            touches.pinched = false
            touches.startScale = scale
            touches.startOffset = offset
            if let tap = touches.lastTap,
               value.time.timeIntervalSince(tap.time) <= TouchTracker.maxTapInterval,
               hypot(value.startLocation.x - tap.location.x,
                     value.startLocation.y - tap.location.y) <= TouchTracker.maxTapDistance {
                touches.mode = .zooming
            }
            touches.lastTap = nil
        }

        switch touches.mode {
        case .zooming:
            let newScale = Self.zoomDragScale(from: touches.startScale, travel: value.translation.height)
            guard newScale != scale else { return }
            offset = zoomedOffset(about: value.startLocation, from: touches.startOffset,
                                  fromScale: touches.startScale, toScale: newScale, viewSize: viewSize)
            scale = newScale
        case .undecided:
            guard Self.travel(of: value) > TouchTracker.tapSlop else { return }
            touches.mode = .panning
            fallthrough
        case .panning:
            let newOffset = CGSize(
                width: touches.startOffset.width + value.translation.width,
                height: touches.startOffset.height + value.translation.height
            )
            offset = clampOffset(newOffset, scale: scale, viewSize: viewSize)
        }
    }

    private func touchEnded(_ value: DragGesture.Value, viewSize: CGSize) {
        lastScale = scale
        lastOffset = offset

        // A touch that went nowhere is a tap, whatever it might have become.
        if touches.mode != .panning, !touches.pinched,
           Self.travel(of: value) <= TouchTracker.tapSlop {
            handleTap(at: value.location, in: viewSize)
            if value.time.timeIntervalSince(touches.downTime) <= TouchTracker.maxTapDuration {
                touches.lastTap = (value.time, value.location)
            }
        }
        touches.startLocation = nil
        touches.mode = .undecided
    }

    private static func travel(of value: DragGesture.Value) -> CGFloat {
        hypot(value.translation.width, value.translation.height)
    }

    /// The pan that keeps view point `anchor` still while the zoom goes from
    /// `fromScale` to `toScale`, starting from `startOffset` and clamped as any pan
    /// is. Shared by the pinch and the zoom drag; Android
    /// `MapProjection.offsetZoomingAbout`.
    private func zoomedOffset(about anchor: CGPoint, from startOffset: CGSize,
                        fromScale: CGFloat, toScale: CGFloat, viewSize: CGSize) -> CGSize {
        // The anchor relative to the map's centre before the zoom, which the zoom
        // moves by (1 - change) of itself, so compensate by that much.
        let anchorFromCenter = CGPoint(
            x: anchor.x - viewSize.width / 2 - startOffset.width,
            y: anchor.y - viewSize.height / 2 - startOffset.height
        )
        let change = toScale / fromScale
        let newOffset = CGSize(
            width: startOffset.width + anchorFromCenter.x * (1 - change),
            height: startOffset.height + anchorFromCenter.y * (1 - change)
        )
        return clampOffset(newOffset, scale: toScale, viewSize: viewSize)
    }

    private func handleTap(at location: CGPoint, in size: CGSize) {
        let (lat, lon) = MapFit(viewSize: size).latLon(at: location, scale: scale, offset: offset)

        // Find country at this location
        if let countryName = CountryHitTester.shared.findCountry(lat: lat, lon: lon) {
            globeState.selectCountry(countryName, center: CountryHitTester.shared.center(of: countryName))
        } else if globeState.selectedCountry != nil {
            // Ocean: dismiss the country card, as GlobeView does
            globeState.deselectCountry()
        }
    }

    // Clamp offset so no edge of the map can be dragged into view
    private func clampOffset(_ offset: CGSize, scale: CGFloat, viewSize: CGSize) -> CGSize {
        MapFit(viewSize: viewSize).clamped(offset, scale: scale)
    }

}
