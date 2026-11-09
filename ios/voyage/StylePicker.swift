import ImageIO
import SceneKit
import SwiftUI
import UIKit

// MARK: - Shared framing

/// What a texture picker chooses for: Home's globe or its flat map.
enum StyleSurface {
    case globe
    case map

    var title: String {
        switch self {
        case .globe: return "Globe Style"
        case .map: return "Map Style"
        }
    }
}

/// Where every thumbnail and the preview look from: Home's camera at its default
/// distance, turned to Europe and Africa. Shared so the preview opens on exactly
/// the picture its thumbnail shows. Android `PREVIEW_CAMERA`.
enum PreviewCamera {
    static let latitude: Float = 20 * .pi / 180
    static let longitude: Float = 15 * .pi / 180
    /// `GlobeScene`'s camera distance.
    static let distance: Float = 4

    /// The globe node's y rotation that faces `longitude`, as `GlobeScene` faces 15°E.
    static var spin: Float { -.pi / 2 - longitude }
    /// The tilt that brings `latitude` to the centre.
    static var tilt: Float { latitude }

    /// The field of view whose cone just grazes the globe, so a snapshot's disc
    /// fills its square.
    static var grazingFieldOfView: CGFloat { CGFloat(2 * asin(1 / distance) * 180 / .pi) }

    /// The on-screen diameter of Home's globe at the default zoom in a view
    /// `height` points tall: the grazing cone's tangent over the vertical half
    /// field of view (45°, `GlobeState.cameraFieldOfView`), across the height.
    /// Android `SphereProjection.homeGlobeDiameter`.
    static func homeGlobeDiameter(viewHeight height: CGFloat) -> CGFloat {
        let d = Double(distance)
        let tanGrazing = 1 / sqrt(d * d - 1)
        let tanHalfFov = tan(GlobeState.cameraFieldOfView / 2 * .pi / 180)
        return height * CGFloat(tanGrazing / tanHalfFov)
    }
}

// MARK: - Textures

/// The Earth textures as the pickers and their preview draw them: decoded smaller
/// than Home's through ImageIO, so a thumbnail never decodes an 8192-pixel image
/// whole. Android `StyleTextures`.
enum StyleTextures {
    /// Thumbnail globes are SceneKit snapshots, which mipmap; this is plenty.
    static let globeThumbnailWidth = 1024
    static let mapThumbnailWidth = 512
    static let previewWidth = 4096

    /// `style`'s texture `width` pixels wide, or nil if it cannot be read. With
    /// `terrain`, the relief is hard-lit over it the way `MapView` draws it —
    /// for the map; the globe applies it in `TerrainRelief`'s shader, as Home does.
    static func image(for style: GlobeStyle, width: Int, terrain: Bool = false) -> CGImage? {
        guard let texture = downsampled(style.textureName, width: width) else { return nil }
        let height = width / 2
        guard let context = CGContext(
            data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
            space: CGColorSpace(name: CGColorSpace.sRGB)!,
            bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue
        ) else { return texture }
        let rect = CGRect(x: 0, y: 0, width: width, height: height)
        context.interpolationQuality = .high
        context.draw(texture, in: rect)
        if terrain, let relief = downsampled(TerrainRelief.imageName, width: width) {
            context.setBlendMode(.hardLight)
            context.draw(relief, in: rect)
        }
        return context.makeImage() ?? texture
    }

    private static func downsampled(_ name: String, width: Int) -> CGImage? {
        guard let url = Bundle.main.url(forResource: name, withExtension: nil),
              let source = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceThumbnailMaxPixelSize: width,
            kCGImageSourceShouldCacheImmediately: true,
        ]
        return CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary)
    }
}

// MARK: - The globe scene

/// Home's ocean sphere on its own: the same sphere, material, texture alignment,
/// relief and lights as `GlobeScene`, without countries or borders. The live
/// preview renders it at Home's field of view, and the thumbnails snapshot it
/// through the grazing one, so the two are the same picture at different sizes.
final class PreviewGlobeScene {
    let scene = SCNScene()
    let cameraNode = SCNNode()
    /// Tilts about the screen's x axis; holds `spinNode`.
    let tiltNode = SCNNode()
    /// Turns the globe about its own axis, as Home turns its `globe` node.
    let spinNode = SCNNode()
    private let material = SCNMaterial()

    init(fieldOfView: CGFloat) {
        scene.background.contents = UIColor.clear

        let sphere = SCNSphere(radius: 1.0)
        sphere.segmentCount = GlobeLayer.oceanSegmentCount
        material.specular.contents = UIColor.clear
        material.shininess = 0.3
        material.diffuse.mipFilter = .linear
        // As `GlobeView.Coordinator.updateGlobeStyle` aligns Home's ocean texture.
        material.diffuse.contentsTransform = SCNMatrix4MakeTranslation(-0.25, 0, 0)
        material.diffuse.wrapS = .repeat
        sphere.materials = [material]
        spinNode.addChildNode(SCNNode(geometry: sphere))
        tiltNode.addChildNode(spinNode)
        scene.rootNode.addChildNode(tiltNode)
        spinNode.eulerAngles.y = PreviewCamera.spin
        tiltNode.eulerAngles.x = PreviewCamera.tilt

        cameraNode.camera = SCNCamera()
        cameraNode.camera?.fieldOfView = fieldOfView
        cameraNode.camera?.zNear = 0.01
        cameraNode.camera?.zFar = 100
        cameraNode.position = SCNVector3(0, 0, PreviewCamera.distance)
        scene.rootNode.addChildNode(cameraNode)

        // `GlobeScene.createScene`'s lights, so the globe is shaded as Home's is.
        let lightNode = SCNNode()
        lightNode.light = SCNLight()
        lightNode.light?.type = .directional
        lightNode.light?.color = UIColor(white: 1.0, alpha: 1.0)
        lightNode.light?.intensity = 800
        lightNode.position = SCNVector3(1, 1, -2)
        cameraNode.addChildNode(lightNode)
        lightNode.look(at: SCNVector3(0, 0, 0))

        let ambientLightNode = SCNNode()
        ambientLightNode.light = SCNLight()
        ambientLightNode.light?.type = .ambient
        ambientLightNode.light?.color = UIColor(white: 0.4, alpha: 1.0)
        ambientLightNode.light?.intensity = 600
        scene.rootNode.addChildNode(ambientLightNode)
    }

    func setTexture(_ image: CGImage?) {
        material.diffuse.contents = image
    }

    func setTerrain(_ enabled: Bool, device: MTLDevice?) {
        let texture = enabled ? device.flatMap(TerrainRelief.texture(device:)) : nil
        TerrainRelief.apply(to: material, texture: texture, enabled: enabled)
    }
}

// MARK: - Thumbnails

/// Rendered thumbnails, kept for the process: three styles, with and without
/// terrain, for the globe and the map — a few hundred kilobytes in all.
@MainActor
enum StyleThumbnails {
    private struct Key: Hashable {
        let surface: StyleSurface
        let style: GlobeStyle
        let terrain: Bool
    }

    private static var cache: [Key: UIImage] = [:]
    private static var renderer: SCNRenderer? = MTLCreateSystemDefaultDevice().map { SCNRenderer(device: $0, options: nil) }

    static func cached(_ surface: StyleSurface, _ style: GlobeStyle, terrain: Bool) -> UIImage? {
        cache[Key(surface: surface, style: style, terrain: terrain)]
    }

    /// The thumbnail, made on first call: a snapshot of `PreviewGlobeScene` for
    /// the globe, the whole texture for the map.
    static func image(_ surface: StyleSurface, _ style: GlobeStyle, terrain: Bool, pixelSize: Int) async -> UIImage? {
        let key = Key(surface: surface, style: style, terrain: terrain)
        if let cached = cache[key] { return cached }
        let image: UIImage?
        switch surface {
        case .map:
            let texture = await Task.detached(priority: .userInitiated) {
                StyleTextures.image(for: style, width: StyleTextures.mapThumbnailWidth, terrain: terrain)
            }.value
            image = texture.map { UIImage(cgImage: $0) }
        case .globe:
            let texture = await Task.detached(priority: .userInitiated) {
                StyleTextures.image(for: style, width: StyleTextures.globeThumbnailWidth)
            }.value
            image = snapshotGlobe(texture: texture, terrain: terrain, pixelSize: pixelSize)
        }
        if let image { cache[key] = image }
        return image
    }

    private static func snapshotGlobe(texture: CGImage?, terrain: Bool, pixelSize: Int) -> UIImage? {
        guard let texture, let renderer else { return nil }
        let globe = PreviewGlobeScene(fieldOfView: PreviewCamera.grazingFieldOfView)
        globe.setTexture(texture)
        globe.setTerrain(terrain, device: renderer.device)
        renderer.scene = globe.scene
        renderer.pointOfView = globe.cameraNode
        let snapshot = renderer.snapshot(
            atTime: 0,
            with: CGSize(width: pixelSize, height: pixelSize),
            antialiasingMode: .multisampling4X
        )
        renderer.scene = nil
        guard let cgImage = snapshot.cgImage else { return nil }
        return UIImage(cgImage: cgImage, scale: UIScreen.main.scale, orientation: .up)
    }
}

// MARK: - The picker

/// One texture preference as a row of small globes — or small maps — each painted
/// with its texture: tap one to choose it, and Preview opens the chosen one full
/// screen, at the size Home draws it (see `StylePreview`).
///
/// The thumbnail open in the preview is hidden here, so the preview's globe seems
/// to lift out of its slot and settle back into it. Android `StylePicker`.
struct StylePicker: View {
    let surface: StyleSurface
    let systemImage: String
    @ObservedObject var globeState: GlobeState

    @State private var origins: [GlobeStyle: CGRect] = [:]
    @State private var previewing: GlobeStyle?
    @State private var isPresented = false

    private var selection: GlobeStyle {
        surface == .globe ? globeState.globeStyle : globeState.mapStyle
    }

    private func select(_ style: GlobeStyle) {
        switch surface {
        case .globe: globeState.setGlobeStyle(style)
        case .map: globeState.setMapStyle(style)
        }
    }

    /// The cover appears and goes without UIKit's slide: the preview animates itself.
    private func setPresented(_ presented: Bool) {
        var transaction = Transaction()
        transaction.disablesAnimations = true
        withTransaction(transaction) {
            isPresented = presented
            if !presented { previewing = nil }
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Image(systemName: systemImage)
                    .foregroundColor(AppColors.buttonColor)
                Text(surface.title)
                Spacer()
                Button {
                    previewing = selection
                    setPresented(true)
                } label: {
                    HStack(spacing: 5) {
                        Text("Preview")
                            .font(.voyageText(15, weight: .medium))
                        Image(systemName: "arrow.up.left.and.arrow.down.right")
                            .font(.system(size: 11, weight: .semibold))
                    }
                    .foregroundColor(AppColors.buttonColor)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Preview \(selection.displayName)")
            }

            HStack(spacing: 8) {
                ForEach(GlobeStyle.allCases, id: \.self) { style in
                    option(style)
                }
            }
        }
        .padding(.vertical, 6)
        .fullScreenCover(isPresented: $isPresented) {
            StylePreview(
                surface: surface,
                initialStyle: previewing ?? selection,
                globeState: globeState,
                origins: origins,
                onPreviewing: { previewing = $0 },
                onDismissed: { setPresented(false) }
            )
            .presentationBackground(.clear)
        }
    }

    private func option(_ style: GlobeStyle) -> some View {
        let isSelected = style == selection
        return Button {
            select(style)
        } label: {
            VStack(spacing: 8) {
                thumbnail(style)
                    .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { origins[style] = $0 }
                    .opacity(isPresented && previewing == style ? 0 : 1)
                    .padding(4)
                    .overlay(ring(isSelected))
                Text(style.displayName)
                    .font(.voyageText(13, weight: isSelected ? .semibold : .regular))
                    .foregroundColor(isSelected ? .primary : .secondary)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .animation(.easeInOut(duration: 0.22), value: isSelected)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    @ViewBuilder
    private func thumbnail(_ style: GlobeStyle) -> some View {
        switch surface {
        case .globe:
            StyleThumbnail(surface: .globe, style: style, terrain: globeState.showsTerrain)
                .frame(width: Self.globeThumbnailSize, height: Self.globeThumbnailSize)
                .clipShape(Circle())
        case .map:
            StyleThumbnail(surface: .map, style: style, terrain: globeState.showsTerrain)
                .aspectRatio(2, contentMode: .fit)
                .clipShape(RoundedRectangle(cornerRadius: Self.mapThumbnailCorner, style: .continuous))
        }
    }

    @ViewBuilder
    private func ring(_ isSelected: Bool) -> some View {
        let color = isSelected ? AppColors.buttonColor : Color.clear
        switch surface {
        case .globe:
            Circle().strokeBorder(color, lineWidth: 2)
        case .map:
            RoundedRectangle(cornerRadius: Self.mapThumbnailCorner + 4, style: .continuous)
                .strokeBorder(color, lineWidth: 2)
        }
    }

    static let globeThumbnailSize: CGFloat = 64
    static let mapThumbnailCorner: CGFloat = 8
}

/// A thumbnail that fades in over a quiet placeholder, so the row never jumps or
/// flashes while the textures decode.
struct StyleThumbnail: View {
    let surface: StyleSurface
    let style: GlobeStyle
    let terrain: Bool
    @State private var image: UIImage?
    @Environment(\.displayScale) private var displayScale

    var body: some View {
        ZStack {
            Color.primary.opacity(0.06)
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .interpolation(.high)
                    .transition(.opacity)
            }
        }
        .task(id: "\(style)-\(terrain)") {
            if let cached = StyleThumbnails.cached(surface, style, terrain: terrain) {
                image = cached
                return
            }
            let pixels = Int(StylePicker.globeThumbnailSize * displayScale)
            let rendered = await StyleThumbnails.image(surface, style, terrain: terrain, pixelSize: pixels)
            withAnimation(.easeOut(duration: 0.26)) { image = rendered }
        }
    }
}

// MARK: - The preview

/// A texture full screen at the size Home draws it, grown out of its thumbnail and
/// settling back into one on close. Android `StylePreview`.
///
/// The globe is Home's ocean sphere at the default zoom — same camera, field of
/// view, lights and relief, so the same size and shading for the same screen — over
/// the same backdrop, without countries or borders. It turns once a minute until
/// touched, and drags and coasts at Home's pan speed and decay (`GlobeInertia`).
/// The map fills the screen's height and pans, as Home's does at its widest.
///
/// The switcher at the bottom compares the three styles without leaving, and Use
/// chooses the one on screen. Closing — the button, a tap or a downward swipe off
/// the globe — flies it back into the thumbnail of the style on screen, turned
/// back to where that thumbnail looks.
struct StylePreview: View {
    let surface: StyleSurface
    let initialStyle: GlobeStyle
    @ObservedObject var globeState: GlobeState
    let origins: [GlobeStyle: CGRect]
    let onPreviewing: (GlobeStyle) -> Void
    let onDismissed: () -> Void

    @State private var style: GlobeStyle?
    @State private var progress: CGFloat = 0
    @State private var closing = false
    @StateObject private var globe = PreviewGlobeController()
    @State private var globeReady = false
    @State private var mapImages: [GlobeStyle: UIImage] = [:]
    @State private var pan: CGSize = .zero
    @State private var panStart: CGSize?
    @State private var dismissingDrag = false
    @Namespace private var switcher

    private var shown: GlobeStyle { style ?? initialStyle }

    private var currentStyle: GlobeStyle {
        surface == .globe ? globeState.globeStyle : globeState.mapStyle
    }

    private static let openSpring = Animation.spring(response: 0.5, dampingFraction: 0.86)
    private static let closeDuration = 0.42

    var body: some View {
        ZStack {
            GeometryReader { geometry in
                let frame = geometry.frame(in: .global)
                content(size: geometry.size, origin: origins[shown]?.offsetBy(dx: -frame.minX, dy: -frame.minY))
            }
            .ignoresSafeArea()

            chrome
                .opacity(Double(max(0, min(1, (progress - 0.35) / 0.65))))
                .offset(y: (1 - min(1, progress)) * 12)
                .allowsHitTesting(!closing)
        }
        .onAppear {
            withAnimation(Self.openSpring) { progress = 1 }
            if surface == .globe { configureGlobe() }
        }
        .task(id: shown) {
            switch surface {
            case .globe:
                await globe.show(shown, terrain: globeState.showsTerrain)
            case .map:
                if mapImages[shown] == nil {
                    let style = shown
                    let terrain = globeState.showsTerrain
                    let texture = await Task.detached(priority: .userInitiated) {
                        StyleTextures.image(for: style, width: StyleTextures.previewWidth, terrain: terrain)
                    }.value
                    if let texture {
                        withAnimation(.easeInOut(duration: 0.3)) { mapImages[style] = UIImage(cgImage: texture) }
                    }
                }
            }
        }
    }

    // MARK: Content

    @ViewBuilder
    private func content(size: CGSize, origin: CGRect?) -> some View {
        switch surface {
        case .globe: globeContent(size: size, origin: origin)
        case .map: mapContent(size: size, origin: origin)
        }
    }

    private func globeContent(size: CGSize, origin: CGRect?) -> some View {
        let diameter = PreviewCamera.homeGlobeDiameter(viewHeight: size.height)
        let center = CGPoint(x: size.width / 2, y: size.height / 2)
        let start = origin ?? CGRect(
            x: center.x - diameter * 0.43, y: center.y - diameter * 0.43,
            width: diameter * 0.86, height: diameter * 0.86
        )
        let p = progress
        let scale = lerp(start.width / diameter, 1, p)
        let offset = CGSize(
            width: lerp(start.midX - center.x, 0, p),
            height: lerp(start.midY - center.y, 0, p)
        )
        return ZStack {
            GlobeBackdrop(isDarkMode: globeState.isDarkMode)
                .opacity(Double(min(1, max(0, p))))

            ZStack {
                PreviewGlobeView(controller: globe)
                // The thumbnail stands in, enlarged, until the full texture is drawn.
                if let thumbnail = StyleThumbnails.cached(.globe, shown, terrain: globeState.showsTerrain) {
                    Image(uiImage: thumbnail)
                        .resizable()
                        .frame(width: diameter, height: diameter)
                        .clipShape(Circle())
                        .opacity(globeReady ? 0 : 1)
                        .allowsHitTesting(false)
                }
            }
            .frame(width: size.width, height: size.height)
            .scaleEffect(scale)
            .offset(offset)
        }
    }

    private func mapContent(size: CGSize, origin: CGRect?) -> some View {
        // Home's framing (`MapView.MapFit`): the 2:1 map fills the view and never
        // lets an edge into view.
        let mapHeight = max(size.height, size.width / 2)
        let mapSize = CGSize(width: mapHeight * 2, height: mapHeight)
        let slack = CGSize(width: (mapSize.width - size.width) / 2, height: (mapSize.height - size.height) / 2)
        let center = CGPoint(x: size.width / 2, y: size.height / 2)
        let target = CGRect(
            x: center.x - mapSize.width / 2 + pan.width, y: center.y - mapSize.height / 2 + pan.height,
            width: mapSize.width, height: mapSize.height
        )
        let start = origin ?? target.insetBy(dx: target.width * 0.07, dy: target.height * 0.07)
        let p = progress
        let scale = lerp(start.width / mapSize.width, 1, p)
        let offset = CGSize(
            width: lerp(start.midX - center.x, target.midX - center.x, p),
            height: lerp(start.midY - center.y, target.midY - center.y, p)
        )
        let corner = StylePicker.mapThumbnailCorner * max(0, 1 - p) / max(scale, 0.01)
        let image = mapImages[shown] ?? StyleThumbnails.cached(.map, shown, terrain: globeState.showsTerrain)

        return ZStack {
            AppColors.pageBackground(isDarkMode: globeState.isDarkMode)
                .opacity(Double(min(1, max(0, p))))

            Group {
                if let image {
                    Image(uiImage: image)
                        .resizable()
                        .interpolation(.high)
                } else {
                    Color.primary.opacity(0.06)
                }
            }
            .frame(width: mapSize.width, height: mapSize.height)
            .clipShape(RoundedRectangle(cornerRadius: corner, style: .continuous))
            .scaleEffect(scale)
            .offset(offset)
            .frame(width: size.width, height: size.height)
        }
        .contentShape(Rectangle())
        .gesture(
            DragGesture()
                .onChanged { value in
                    guard !closing else { return }
                    if panStart == nil {
                        // With no height to pan, a downward swipe closes, as it
                        // does off the globe.
                        dismissingDrag = slack.height < 1
                            && value.translation.height > abs(value.translation.width)
                        panStart = pan
                    }
                    if dismissingDrag {
                        dismissDrag(value.translation.height, height: size.height)
                    } else if let panStart {
                        pan = clamped(panStart + value.translation, slack)
                    }
                }
                .onEnded { value in
                    defer { panStart = nil }
                    guard !closing else { return }
                    if dismissingDrag {
                        dismissDragEnded(
                            value.translation.height, velocity: value.velocity.height, height: size.height
                        )
                    } else if let panStart {
                        withAnimation(.spring(response: 0.55, dampingFraction: 1)) {
                            pan = clamped(panStart + value.predictedEndTranslation, slack)
                        }
                    }
                }
        )
    }

    private func clamped(_ value: CGSize, _ slack: CGSize) -> CGSize {
        CGSize(
            width: min(slack.width, max(-slack.width, value.width)),
            height: min(slack.height, max(-slack.height, value.height))
        )
    }

    // MARK: Chrome

    private var chrome: some View {
        VStack {
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 1) {
                    Text(surface.title)
                        .font(.voyageText(12, weight: .medium))
                        .foregroundColor(.secondary)
                    Text(shown.displayName)
                        .font(.voyageDisplay(22))
                        .contentTransition(.opacity)
                }
                .padding(.horizontal, 18)
                .padding(.vertical, 8)
                .background(.ultraThinMaterial, in: Capsule())

                Spacer()

                Button(action: { close() }) {
                    Image(systemName: "xmark")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(.primary)
                        .frame(width: 44, height: 44)
                        .background(.ultraThinMaterial, in: Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Close preview")
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)

            Spacer()

            VStack(spacing: 12) {
                styleSwitcher
                useButton
            }
            .frame(maxWidth: 420)
            .padding(.horizontal, 16)
            .padding(.bottom, 12)
        }
    }

    private var styleSwitcher: some View {
        HStack(spacing: 2) {
            ForEach(GlobeStyle.allCases, id: \.self) { option in
                let isShown = option == shown
                Button {
                    withAnimation(.spring(response: 0.32, dampingFraction: 0.86)) { style = option }
                    onPreviewing(option)
                } label: {
                    Text(option.displayName)
                        .font(.voyageText(15, weight: .medium))
                        .foregroundColor(isShown ? .white : .primary)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background {
                            if isShown {
                                Capsule()
                                    .fill(AppColors.buttonColor)
                                    .matchedGeometryEffect(id: "shown", in: switcher)
                            }
                        }
                        .contentShape(Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(isShown ? .isSelected : [])
            }
        }
        .padding(4)
        .background(.ultraThinMaterial, in: Capsule())
        .sensoryFeedback(.selection, trigger: shown)
    }

    private var useButton: some View {
        let isCurrent = shown == currentStyle
        return Button {
            close(using: shown)
        } label: {
            HStack(spacing: 8) {
                if isCurrent {
                    Image(systemName: "checkmark")
                        .font(.system(size: 14, weight: .semibold))
                    Text("Current style")
                } else {
                    Text("Use \(shown.displayName)")
                }
            }
            .font(.voyageText(17, weight: .semibold))
            .foregroundColor(isCurrent ? .secondary : .white)
            .frame(maxWidth: .infinity)
            .frame(height: 52)
            .background {
                if isCurrent {
                    Capsule().fill(.ultraThinMaterial)
                } else {
                    Capsule().fill(AppColors.buttonColor)
                }
            }
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .disabled(isCurrent)
        .animation(.easeInOut(duration: 0.2), value: isCurrent)
    }

    // MARK: Opening and closing

    private func configureGlobe() {
        globe.onReady = {
            withAnimation(.easeOut(duration: 0.25)) { globeReady = true }
        }
        globe.onTapOutside = { close() }
        globe.onDismissDrag = { translation, height in dismissDrag(translation, height: height) }
        globe.onDismissEnd = { translation, velocity, height in
            dismissDragEnded(translation, velocity: velocity, height: height)
        }
    }

    /// The globe or map follows a downward swipe back toward its thumbnail.
    private func dismissDrag(_ translation: CGFloat, height: CGFloat) {
        guard !closing else { return }
        let fraction = min(0.85, max(0, translation / (height * Self.dismissDragFraction)))
        var transaction = Transaction()
        transaction.disablesAnimations = true
        withTransaction(transaction) { progress = 1 - fraction }
    }

    /// Lets go past a third of the way or on a downward flick; otherwise springs back.
    private func dismissDragEnded(_ translation: CGFloat, velocity: CGFloat, height: CGFloat) {
        guard !closing else { return }
        if translation > height * Self.dismissDragFraction / 3 || velocity > Self.dismissFlickVelocity {
            close()
        } else {
            withAnimation(Self.openSpring) { progress = 1 }
        }
    }

    private func close(using chosen: GlobeStyle? = nil) {
        guard !closing else { return }
        closing = true
        if let chosen {
            switch surface {
            case .globe: globeState.setGlobeStyle(chosen)
            case .map: globeState.setMapStyle(chosen)
            }
        }
        globe.returnToStart(duration: Self.closeDuration)
        withAnimation(.easeInOut(duration: Self.closeDuration)) {
            progress = 0
        } completion: {
            onDismissed()
        }
    }

    /// How far down, as a share of the screen, a swipe takes the preview all the way back.
    private static let dismissDragFraction: CGFloat = 0.5
    /// Points per second: a downward flick faster than this closes, however short.
    private static let dismissFlickVelocity: CGFloat = 1000
}

private func lerp(_ a: CGFloat, _ b: CGFloat, _ t: CGFloat) -> CGFloat { a + (b - a) * t }

private func + (lhs: CGSize, rhs: CGSize) -> CGSize {
    CGSize(width: lhs.width + rhs.width, height: lhs.height + rhs.height)
}

// MARK: - The live globe

/// The preview's SceneKit view.
private struct PreviewGlobeView: UIViewRepresentable {
    let controller: PreviewGlobeController

    func makeUIView(context: Context) -> SCNView { controller.view }
    func updateUIView(_ uiView: SCNView, context: Context) {}
}

/// Drives the preview globe: drags, coasting and the idle turn, integrated once per
/// rendered frame as `GlobeView.Coordinator` does, so a gesture never moves the
/// globe between frames.
final class PreviewGlobeController: NSObject, ObservableObject, SCNSceneRendererDelegate {
    let globe = PreviewGlobeScene(fieldOfView: CGFloat(GlobeState.cameraFieldOfView))
    private(set) lazy var view: SCNView = makeView()

    var onReady: (() -> Void)?
    var onTapOutside: (() -> Void)?
    /// Downward travel and the view's height, for a swipe that started off the globe.
    var onDismissDrag: ((CGFloat, CGFloat) -> Void)?
    var onDismissEnd: ((CGFloat, CGFloat, CGFloat) -> Void)?

    private let lock = NSLock()
    private var spin = PreviewCamera.spin
    private var tilt = PreviewCamera.tilt
    private var pendingSpin: Float = 0
    private var pendingTilt: Float = 0
    private let inertia = GlobeInertia()
    private var autoRotating = false
    private var dragging = false
    private var frozen = false
    private var lastTime: TimeInterval?
    private var awaitingFirstFrame = false
    private var dismissing = false
    private var textures: [GlobeStyle: CGImage] = [:]
    private var terrainApplied: Bool?

    /// `GlobeView.Coordinator.basePanRotationSpeed`: radians per point at the default zoom.
    private static let panSpeed: Float = 0.005
    /// `GlobeState.maxCameraLatitude`.
    private static let maxTilt = Float(GlobeState.maxCameraLatitude * .pi / 180)
    /// One turn a minute, as `GlobeScene`'s idle `SCNAction`.
    private static let idleSpeed: Float = 2 * .pi / 60

    private func makeView() -> SCNView {
        let view = SCNView(frame: .zero)
        view.scene = globe.scene
        view.pointOfView = globe.cameraNode
        view.backgroundColor = .clear
        view.isOpaque = false
        view.antialiasingMode = .multisampling4X
        view.rendersContinuously = true
        view.preferredFramesPerSecond = 0
        view.delegate = self

        let pan = UIPanGestureRecognizer(target: self, action: #selector(handlePan(_:)))
        pan.maximumNumberOfTouches = 1
        view.addGestureRecognizer(pan)
        let tap = UITapGestureRecognizer(target: self, action: #selector(handleTap(_:)))
        view.addGestureRecognizer(tap)
        return view
    }

    /// Shows `style`'s texture, decoding it on first use.
    @MainActor
    func show(_ style: GlobeStyle, terrain: Bool) async {
        if terrainApplied != terrain {
            globe.setTerrain(terrain, device: view.device)
            terrainApplied = terrain
        }
        let texture: CGImage?
        if let cached = textures[style] {
            texture = cached
        } else {
            texture = await Task.detached(priority: .userInitiated) {
                StyleTextures.image(for: style, width: StyleTextures.previewWidth)
            }.value
            textures[style] = texture
        }
        globe.setTexture(texture)
        lock.lock()
        awaitingFirstFrame = true
        lock.unlock()
    }

    /// Turns the globe back to the thumbnail's view over `duration`, for closing.
    func returnToStart(duration: TimeInterval) {
        lock.lock()
        frozen = true
        inertia.reset()
        let fullTurn = 2 * Float.pi
        let target = PreviewCamera.spin + ((spin - PreviewCamera.spin) / fullTurn).rounded() * fullTurn
        lock.unlock()
        SCNTransaction.begin()
        SCNTransaction.animationDuration = duration
        SCNTransaction.animationTimingFunction = CAMediaTimingFunction(name: .easeInEaseOut)
        globe.spinNode.eulerAngles.y = target
        globe.tiltNode.eulerAngles.x = PreviewCamera.tilt
        SCNTransaction.commit()
    }

    func renderer(_ renderer: SCNSceneRenderer, updateAtTime time: TimeInterval) {
        let dt = Float(min(max(time - (lastTime ?? time), 0), 1.0 / 20))
        lastTime = time
        lock.lock()
        defer { lock.unlock() }
        guard !frozen else { return }
        spin += pendingSpin
        tilt += pendingTilt
        pendingSpin = 0
        pendingTilt = 0
        if !dragging {
            if inertia.isActive {
                let step = inertia.step(dt: dt)
                tilt += step.dx
                spin += step.dy
            } else if autoRotating {
                spin += Self.idleSpeed * dt
            }
        }
        tilt = min(Self.maxTilt, max(-Self.maxTilt, tilt))
        globe.spinNode.eulerAngles.y = spin
        globe.tiltNode.eulerAngles.x = tilt
    }

    func renderer(_ renderer: SCNSceneRenderer, didRenderScene scene: SCNScene, atTime time: TimeInterval) {
        lock.lock()
        let first = awaitingFirstFrame
        if first {
            awaitingFirstFrame = false
            // Held still until now, so the thumbnail it replaces lines up.
            if !touched { autoRotating = true }
        }
        lock.unlock()
        if first {
            DispatchQueue.main.async { [weak self] in self?.onReady?() }
        }
    }

    /// Whether the globe has been touched: the idle turn stops for good, as on Home.
    private var touched = false

    private func isOnGlobe(_ point: CGPoint) -> Bool {
        let bounds = view.bounds
        let radius = PreviewCamera.homeGlobeDiameter(viewHeight: bounds.height) / 2
        return hypot(point.x - bounds.midX, point.y - bounds.midY) <= radius
    }

    @objc private func handleTap(_ gesture: UITapGestureRecognizer) {
        if !isOnGlobe(gesture.location(in: view)) { onTapOutside?() }
    }

    @objc private func handlePan(_ gesture: UIPanGestureRecognizer) {
        switch gesture.state {
        case .began:
            dismissing = !isOnGlobe(gesture.location(in: view))
            guard !dismissing else { return }
            lock.lock()
            touched = true
            autoRotating = false
            dragging = true
            inertia.reset()
            lock.unlock()

        case .changed:
            let translation = gesture.translation(in: view)
            if dismissing {
                onDismissDrag?(translation.y, view.bounds.height)
                return
            }
            lock.lock()
            pendingSpin += Float(translation.x) * Self.panSpeed
            pendingTilt += Float(translation.y) * Self.panSpeed
            lock.unlock()
            gesture.setTranslation(.zero, in: view)

        case .ended, .cancelled:
            let velocity = gesture.velocity(in: view)
            if dismissing {
                onDismissEnd?(gesture.translation(in: view).y, velocity.y, view.bounds.height)
                return
            }
            lock.lock()
            dragging = false
            inertia.velocityY = Float(velocity.x) * Self.panSpeed
            inertia.velocityX = Float(velocity.y) * Self.panSpeed
            lock.unlock()

        default:
            break
        }
    }
}
