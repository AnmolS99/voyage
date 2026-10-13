import SwiftUI
import SceneKit
import Metal

/// Full-screen overlay shown when the user taps an achievement's small medal.
/// The 3D coin starts at the small medal's on-screen frame (`sourceFrame`) and
/// animates its own frame to the center while the background blurs in, so the
/// small medal visibly enlarges rather than being swapped for another view.
/// It is spinnable around the Y axis only (matching the globe's horizontal drag).
///
/// With `celebrating` set it announces a medal just earned instead — a heading
/// over the coin and confetti in front of it — which is what
/// `AchievementUnlockCelebration` shows. There is no small medal to grow out
/// of then, so the coin springs up in place.
struct MedalOverlayView: View {
    let achievement: Achievement
    let isDarkMode: Bool
    /// Global frame of the tapped small medal, where the coin starts and
    /// returns on dismissal. Nil springs the coin up in place.
    let sourceFrame: CGRect?
    var celebrating: Bool = false
    /// Called once the collapse-back animation has finished.
    let onDismissed: () -> Void

    @State private var isExpanded = false
    /// Global frame of the expanded coin's slot, measured from the placeholder.
    @State private var targetFrame: CGRect = .zero

    var body: some View {
        GeometryReader { geo in
            let overlayOrigin = geo.frame(in: .global).origin
            let medalFrame = sourceFrame.map { isExpanded && targetFrame != .zero ? targetFrame : $0 } ?? targetFrame

            ZStack {
                Rectangle()
                    .fill(.ultraThinMaterial)
                    .ignoresSafeArea()
                    .opacity(isExpanded ? 1 : 0)
                    .onTapGesture { dismiss() }

                // Beams in the medal's color when it has just been earned —
                // not when it is only being looked at — behind everything else
                // so the text stays on top of them.
                if celebrating, let tier = achievement.tier {
                    MedalRays(color: MedalArtwork.headingColor(tier, isDarkMode: isDarkMode))
                        // The slot is larger than the coin in it, which fills ~76%.
                        .frame(
                            width: medalFrame.width * 0.76 * MedalRays.scale,
                            height: medalFrame.height * 0.76 * MedalRays.scale
                        )
                        .position(
                            x: medalFrame.midX - overlayOrigin.x,
                            y: medalFrame.midY - overlayOrigin.y
                        )
                        .scaleEffect(sourceFrame == nil && !isExpanded ? 0.01 : 1)
                        .opacity(isExpanded ? 1 : 0)
                        .allowsHitTesting(false)
                }

                if geo.size.width > geo.size.height {
                    // Landscape: there is no height under the coin for the
                    // text, so it moves beside the coin, which stays centered
                    // between two equal side columns.
                    let side = max(0, (geo.size.width - 320) / 2 - 32)
                    HStack(spacing: 32) {
                        Color.clear.frame(width: side, height: 1)
                        coinPlaceholder
                        details(alignment: .leading)
                            .frame(width: side, alignment: .leading)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    // The coin sits in the middle of the screen; the heading
                    // and the details hang off it as overlays, so they don't
                    // pull it off center.
                    coinPlaceholder
                        .overlay(alignment: .top) {
                            if celebrating {
                                unlockedHeading
                                    .fixedSize()
                                    .alignmentGuide(.top) { $0[.bottom] + 8 }
                            }
                        }
                        .overlay(alignment: .bottom) {
                            details(alignment: .center)
                                .fixedSize()
                                .alignmentGuide(.bottom) { $0[.top] - 8 }
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }

                MedalSceneView(achievement: achievement, isSettling: !isExpanded)
                    .frame(width: medalFrame.width, height: medalFrame.height)
                    .position(
                        x: medalFrame.midX - overlayOrigin.x,
                        y: medalFrame.midY - overlayOrigin.y
                    )
                    // Springing up in place, when there is no small medal to
                    // grow out of.
                    .scaleEffect(sourceFrame == nil && !isExpanded ? 0.01 : 1)

                if celebrating && isExpanded {
                    ConfettiView()
                }
            }
        }
        .task {
            // The unlock's haptic, as the games answer a correct guess.
            if celebrating { UINotificationFeedbackGenerator().notificationOccurred(.success) }
            withAnimation(.spring(response: 0.45, dampingFraction: 0.85)) {
                isExpanded = true
            }
        }
    }

    /// "Silver medal unlocked!", in that medal's color.
    private var unlockedHeading: some View {
        let tier = achievement.tier ?? .bronze
        return Text("\(tier.displayName) medal unlocked!")
            .font(.voyageDisplay(26))
            .foregroundColor(MedalArtwork.headingColor(tier, isDarkMode: isDarkMode))
            .accessibilityAddTraits(.isHeader)
            .opacity(isExpanded ? 1 : 0)
    }

    /// Invisible placeholder marking where the expanded coin belongs.
    private var coinPlaceholder: some View {
        Color.clear
            .frame(width: 320, height: 320)
            .onGeometryChange(for: CGRect.self) { proxy in
                proxy.frame(in: .global)
            } action: { frame in
                targetFrame = frame
            }
    }

    /// Name, progress, medal status, spin hint and close button: under the coin in
    /// portrait, beside it (leading-aligned, under the unlock heading) in
    /// landscape.
    private func details(alignment: HorizontalAlignment) -> some View {
        VStack(alignment: alignment, spacing: 8) {
            if celebrating && alignment == .leading {
                unlockedHeading
            }

            Text(achievement.name)
                .font(.voyageDisplay(22))
                .foregroundColor(AppColors.textPrimary(isDarkMode: isDarkMode))

            Text("\(achievement.current)/\(achievement.total) \(achievement.itemLabel)")
                .font(.voyageText(15, weight: .medium))
                .foregroundColor(AppColors.textTertiary(isDarkMode: isDarkMode))

            Text(achievement.medalStatus)
                .font(.voyageText(15, weight: .semibold))
                .foregroundColor(
                    achievement.tier.map { MedalArtwork.headingColor($0, isDarkMode: isDarkMode) }
                        ?? MedalArtwork.accent(nil)
                )

            Label("Drag to spin", systemImage: "hand.draw")
                .font(.voyageText(13, weight: .medium))
                .foregroundColor(AppColors.textTertiary(isDarkMode: isDarkMode))
                .padding(.top, 4)

            Button(action: dismiss) {
                Text(celebrating ? "Awesome!" : "Close")
                    .font(.voyageText(15, weight: .semibold))
                    .foregroundColor(isDarkMode ? .white : AppColors.closeButtonText)
                    .padding(.horizontal, 28)
                    .padding(.vertical, 10)
                    .background(
                        Capsule()
                            .fill(isDarkMode ? AppColors.closeButtonDark : AppColors.closeButtonLight)
                    )
            }
            .padding(.top, 12)
        }
        .multilineTextAlignment(alignment == .leading ? .leading : .center)
        .opacity(isExpanded ? 1 : 0)
    }

    private func dismiss() {
        withAnimation(.spring(response: 0.4, dampingFraction: 0.85), completionCriteria: .logicallyComplete) {
            isExpanded = false
        } completion: {
            onDismissed()
        }
    }
}

/// Static front-facing coin shown inside each achievement card. Displays a
/// cached offscreen snapshot instead of hosting a live `SCNView`, and never
/// renders on the main thread: on a cache miss (the tab opened before the
/// launch-time prewarm reached this medal) it shows a flat 2D coin face and
/// crossfades to the 3D snapshot when the background render finishes.
/// Rendering medal artwork synchronously in the first frame is what made the
/// tab take ~1s to open.
struct MedalCardView: View {
    let achievement: Achievement
    @State private var renderedSnapshot: UIImage?

    private var snapshot: UIImage? {
        MedalSceneView.cachedCardSnapshot(medal: achievement.medal, tier: achievement.tier)
            ?? renderedSnapshot
    }

    var body: some View {
        ZStack {
            if let snapshot {
                Image(uiImage: snapshot)
                    .resizable()
                    .scaledToFit()
                    .transition(.opacity)
            } else {
                FlatCoinFaceView(medal: achievement.medal, tier: achievement.tier)
                    .transition(.opacity)
            }
        }
        .task(id: achievement.tier) {
            let image = await MedalSceneView.cardSnapshot(
                medal: achievement.medal,
                tier: achievement.tier
            )
            withAnimation(.easeIn(duration: 0.2)) {
                renderedSnapshot = image
            }
        }
    }
}

/// Flat 2D stand-in for the coin, mirroring the 3D cap artwork: same radial
/// gradient, embossed ring, and emoji (a question mark while unearned),
/// sized to match the coin's footprint in the snapshot (~76% of the slot) so
/// the crossfade to the real coin barely registers.
private struct FlatCoinFaceView: View {
    let medal: String
    let tier: MedalTier?

    var body: some View {
        GeometryReader { geo in
            let side = min(geo.size.width, geo.size.height)
            let coin = side * 0.76
            let colors = MedalArtwork.colors(tier)

            ZStack {
                Circle()
                    .fill(
                        RadialGradient(
                            colors: [Color(colors.center), Color(colors.edge)],
                            center: .center,
                            startRadius: 0,
                            endRadius: coin / 2
                        )
                    )
                    .frame(width: coin, height: coin)

                Circle()
                    .stroke(Color(colors.rim), lineWidth: coin * 0.02)
                    .frame(width: coin * 0.9, height: coin * 0.9)

                if tier == nil {
                    Text("?")
                        .font(.system(size: coin * 0.5, weight: .bold))
                        .foregroundColor(Color(colors.rim))
                } else {
                    Text(medal)
                        .font(.system(size: coin * 0.5))
                }
            }
            .frame(width: geo.size.width, height: geo.size.height)
        }
    }
}

/// SceneKit view rendering a procedural coin medal with the achievement's emoji
/// on the face. Horizontal drags spin it around the Y axis with inertia and it
/// auto-spins slowly when idle, like the globe. Used only for the full-screen
/// overlay coin; the small coin inside each card is a `MedalCardView` snapshot.
struct MedalSceneView: UIViewRepresentable {
    let achievement: Achievement
    /// While true the coin stops spinning and eases back to front-facing,
    /// synchronized with the overlay's collapse so it lands matching the
    /// static small medal exactly.
    var isSettling: Bool = false

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> SCNView {
        let sceneView = SCNView()
        sceneView.backgroundColor = .clear
        sceneView.antialiasingMode = .multisampling4X
        sceneView.scene = Self.buildScene(medal: achievement.medal, tier: achievement.tier)
        context.coordinator.tier = achievement.tier
        context.coordinator.isSettling = isSettling

        context.coordinator.spinNode = sceneView.scene?.rootNode.childNode(withName: "spin", recursively: true)
        let pan = UIPanGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handlePan(_:)))
        sceneView.addGestureRecognizer(pan)
        if !isSettling {
            context.coordinator.startAutoSpin()
        }
        return sceneView
    }

    func updateUIView(_ uiView: SCNView, context: Context) {
        // Rebuild only when the medal changes tier — e.g. the user checks off
        // the country that earns silver
        if context.coordinator.tier != achievement.tier {
            context.coordinator.tier = achievement.tier
            uiView.scene = Self.buildScene(medal: achievement.medal, tier: achievement.tier)
            context.coordinator.spinNode = uiView.scene?.rootNode.childNode(withName: "spin", recursively: true)
            if !isSettling {
                context.coordinator.startAutoSpin()
            }
        }

        if context.coordinator.isSettling != isSettling {
            context.coordinator.isSettling = isSettling
            if isSettling {
                context.coordinator.settleToFront()
            } else {
                context.coordinator.startAutoSpin()
            }
        }
    }

    static func dismantleUIView(_ uiView: SCNView, coordinator: Coordinator) {
        coordinator.stopInertiaLoop()
    }

    // MARK: - Interaction

    final class Coordinator: NSObject {
        var spinNode: SCNNode?
        var tier: MedalTier?
        var isSettling = false

        private let inertia = GlobeInertia()
        private var displayLink: CADisplayLink?
        private var lastTimestamp: CFTimeInterval = 0
        private static let autoSpinKey = "autoSpin"
        private static let rotationSpeed: Float = 0.008

        func startAutoSpin() {
            let spin = SCNAction.repeatForever(SCNAction.rotateBy(x: 0, y: CGFloat.pi * 2, z: 0, duration: 14))
            spinNode?.runAction(spin, forKey: Self.autoSpinKey)
        }

        /// Stops any spin/inertia and eases the coin to the nearest
        /// front-facing rotation. Runs a touch longer than the overlay's
        /// collapse so the spin-to-front reads as a distinct, graceful settle.
        func settleToFront(duration: TimeInterval = 0.6) {
            guard let spinNode else { return }
            stopInertiaLoop()
            let currentY = spinNode.presentation.eulerAngles.y
            spinNode.removeAction(forKey: Self.autoSpinKey)
            spinNode.eulerAngles.y = currentY

            let fullTurn = 2 * Float.pi
            let target = (currentY / fullTurn).rounded() * fullTurn
            SCNTransaction.begin()
            SCNTransaction.animationDuration = duration
            SCNTransaction.animationTimingFunction = CAMediaTimingFunction(name: .easeOut)
            spinNode.eulerAngles.y = target
            SCNTransaction.commit()
        }

        @objc func handlePan(_ gesture: UIPanGestureRecognizer) {
            guard let spinNode else { return }

            switch gesture.state {
            case .began:
                stopInertiaLoop()
                // Sync rotation with the visual position before stopping auto-spin
                let actualRotationY = spinNode.presentation.eulerAngles.y
                spinNode.removeAction(forKey: Self.autoSpinKey)
                spinNode.eulerAngles.y = actualRotationY

            case .changed:
                let translation = gesture.translation(in: gesture.view)
                spinNode.eulerAngles.y += Float(translation.x) * Self.rotationSpeed
                gesture.setTranslation(.zero, in: gesture.view)

            case .ended, .cancelled:
                inertia.velocityY = Float(gesture.velocity(in: gesture.view).x) * Self.rotationSpeed
                startInertiaLoop()

            default:
                break
            }
        }

        private func startInertiaLoop() {
            displayLink?.invalidate()
            lastTimestamp = 0
            let link = CADisplayLink(target: self, selector: #selector(stepInertia(_:)))
            link.add(to: .main, forMode: .common)
            displayLink = link
        }

        @objc private func stepInertia(_ link: CADisplayLink) {
            defer { lastTimestamp = link.timestamp }
            guard lastTimestamp > 0 else { return }

            let dt = Float(link.timestamp - lastTimestamp)
            spinNode?.eulerAngles.y += inertia.step(dt: dt).dy
            if !inertia.isActive {
                stopInertiaLoop()
                startAutoSpin()
            }
        }

        func stopInertiaLoop() {
            displayLink?.invalidate()
            displayLink = nil
            inertia.reset()
        }

        deinit {
            displayLink?.invalidate()
        }
    }

    // MARK: - Scene construction

    private static func buildScene(medal: String, tier: MedalTier?) -> SCNScene {
        let scene = SCNScene()
        scene.background.contents = UIColor.clear

        let cameraNode = SCNNode()
        cameraNode.name = "camera"
        cameraNode.camera = SCNCamera()
        cameraNode.position = SCNVector3(0, 0, 2.5)
        scene.rootNode.addChildNode(cameraNode)

        let ambient = SCNNode()
        ambient.light = SCNLight()
        ambient.light!.type = .ambient
        ambient.light!.intensity = 450
        scene.rootNode.addChildNode(ambient)

        let keyLight = SCNNode()
        keyLight.light = SCNLight()
        keyLight.light!.type = .directional
        keyLight.light!.intensity = 900
        keyLight.position = SCNVector3(2, 3, 4)
        keyLight.look(at: SCNVector3Zero)
        scene.rootNode.addChildNode(keyLight)

        // Slight fixed tilt for depth; the spin node inside rotates around Y only
        let tiltNode = SCNNode()
        tiltNode.eulerAngles.x = -0.15
        scene.rootNode.addChildNode(tiltNode)

        let spinNode = SCNNode()
        spinNode.name = "spin"
        tiltNode.addChildNode(spinNode)

        let coin = SCNCylinder(radius: 1.1, height: 0.12)

        let rim = SCNMaterial()
        rim.diffuse.contents = MedalArtwork.colors(tier).rim
        rim.specular.contents = UIColor.white
        rim.shininess = 0.4
        rim.lightingModel = .blinn

        let face = SCNMaterial()
        face.diffuse.contents = capImage(tier: tier)
        face.specular.contents = UIColor.white
        face.shininess = 0.6
        face.lightingModel = .blinn

        if tier == nil {
            // A silhouette is flat: no light may pick out metal that has not
            // been earned yet.
            for material in [rim, face] {
                material.lightingModel = .constant
                material.specular.contents = UIColor.black
            }
        }

        // Cylinder material order: side, top cap, bottom cap. The caps carry
        // only the rotationally symmetric gradient+ring, so their unpredictable
        // UV orientation doesn't matter; the emoji/star sit on separate planes.
        coin.materials = [rim, face, face]

        let coinNode = SCNNode(geometry: coin)
        // Rotate so the top cap (the medal face) points at the camera
        coinNode.eulerAngles.x = .pi / 2
        spinNode.addChildNode(coinNode)

        // Emoji on the front, star on the back, on thin planes just above the
        // caps — SCNPlane UVs are upright and unmirrored, unlike cylinder caps.
        let frontNode = symbolPlaneNode(image: symbolImage(medal: medal, front: true, tier: tier))
        frontNode.position.z = 0.065
        spinNode.addChildNode(frontNode)

        let backNode = symbolPlaneNode(image: symbolImage(medal: medal, front: false, tier: tier))
        backNode.position.z = -0.065
        backNode.eulerAngles.y = .pi
        spinNode.addChildNode(backNode)

        return scene
    }

    private static func symbolPlaneNode(image: UIImage) -> SCNNode {
        let plane = SCNPlane(width: 1.3, height: 1.3)
        let material = SCNMaterial()
        material.diffuse.contents = image
        material.lightingModel = .constant
        material.isDoubleSided = false
        plane.materials = [material]
        return SCNNode(geometry: plane)
    }

    // MARK: - Card snapshots

    /// Shared offscreen renderer for card snapshots; use is serialized by
    /// `snapshotRendererLock`.
    private static let snapshotRenderer = SCNRenderer(device: MTLCreateSystemDefaultDevice(), options: nil)
    private static let snapshotRendererLock = NSLock()
    /// Card coins render into a 56pt slot; 224px covers 3x displays with headroom.
    private static let snapshotSide: CGFloat = 224

    private static func snapshotKey(medal: String, tier: MedalTier?) -> String {
        "coin|\(medal)|\(MedalArtwork.key(tier))"
    }

    /// Non-blocking cache lookup for a card snapshot — safe to call from the
    /// main thread during view body evaluation. Never renders.
    static func cachedCardSnapshot(medal: String, tier: MedalTier?) -> UIImage? {
        artworkLock.lock()
        defer { artworkLock.unlock() }
        return artworkCache[snapshotKey(medal: medal, tier: tier)]
    }

    /// The card's coin snapshot, rendering it off the calling thread on a
    /// cache miss.
    static func cardSnapshot(medal: String, tier: MedalTier?) async -> UIImage {
        if let hit = cachedCardSnapshot(medal: medal, tier: tier) {
            return hit
        }
        return await withCheckedContinuation { continuation in
            DispatchQueue.global(qos: .userInitiated).async {
                continuation.resume(returning: renderCardSnapshot(medal: medal, tier: tier))
            }
        }
    }

    /// A one-off offscreen render of the front-facing coin, cached per medal
    /// and tier. Cards display this instead of a live `SCNView`.
    /// Blocking — never call on the main thread.
    private static func renderCardSnapshot(medal: String, tier: MedalTier?) -> UIImage {
        cachedArtwork(key: snapshotKey(medal: medal, tier: tier)) {
            snapshotRendererLock.lock()
            defer { snapshotRendererLock.unlock() }
            let scene = buildScene(medal: medal, tier: tier)
            snapshotRenderer.scene = scene
            // SCNRenderer doesn't reliably adopt the scene's camera on its own
            // the way SCNView does
            snapshotRenderer.pointOfView = scene.rootNode.childNode(withName: "camera", recursively: false)
            let image = snapshotRenderer.snapshot(
                atTime: 0,
                with: CGSize(width: snapshotSide, height: snapshotSide),
                antialiasingMode: .multisampling4X
            )
            snapshotRenderer.scene = nil
            return image
        }
    }

    // MARK: - Artwork cache

    /// Rendering a coin snapshot is not free, and one card coin per achievement is needed when the Achievements tab
    /// first appears. All rendered artwork — symbol textures and finished coin
    /// snapshots — is cached here, and `prewarmArtwork` fills the cache off the
    /// main thread at app launch so the tab opens instantly.
    private static var artworkCache: [String: UIImage] = [:]
    private static let artworkLock = NSLock()
    private static var didPrewarm = false

    /// Renders and caches every medal's card snapshot in all four variants
    /// (unearned, bronze, silver, gold) on a background queue (each snapshot transitively
    /// renders and caches the cap and symbol textures the overlay coin needs
    /// too). Call once at app launch. Runs at .userInitiated so it usually
    /// finishes before the user can reach the Achievements tab — but the tab
    /// no longer depends on winning that race, since `MedalCardView` falls
    /// back to an async render on a cache miss.
    static func prewarmArtwork(medals: [String]) {
        artworkLock.lock()
        let alreadyPrewarmed = didPrewarm
        didPrewarm = true
        artworkLock.unlock()
        guard !alreadyPrewarmed else { return }

        DispatchQueue.global(qos: .userInitiated).async {
            for medal in medals {
                for tier in MedalArtwork.variants {
                    _ = renderCardSnapshot(medal: medal, tier: tier)
                }
            }
        }
    }

    private static func cachedArtwork(key: String, render: () -> UIImage) -> UIImage {
        artworkLock.lock()
        if let hit = artworkCache[key] {
            artworkLock.unlock()
            return hit
        }
        artworkLock.unlock()

        let image = render()

        artworkLock.lock()
        artworkCache[key] = image
        artworkLock.unlock()
        return image
    }

    /// Emoji for the front, star for the back; an unearned medal shows a
    /// question mark in place of its emoji. Transparent background so the coin
    /// face shows through.
    private static func symbolImage(medal: String, front: Bool, tier: MedalTier?) -> UIImage {
        cachedArtwork(key: "symbol|\(medal)|\(front)|\(MedalArtwork.key(tier))") {
            let ringColor = MedalArtwork.colors(tier).rim
            let side: CGFloat = 512
            let symbol = front && tier == nil
                ? renderedText("?", fontSize: 400, weight: .bold, color: ringColor)
                : renderedText(front ? medal : "★", fontSize: 400, color: ringColor)
            let renderer = UIGraphicsImageRenderer(size: CGSize(width: side, height: side))
            return renderer.image { _ in
                // Fit the symbol inside the square, preserving aspect ratio
                let scale = min(side / symbol.size.width, side / symbol.size.height)
                let size = CGSize(width: symbol.size.width * scale, height: symbol.size.height * scale)
                let origin = CGPoint(x: (side - size.width) / 2, y: (side - size.height) / 2)
                symbol.draw(in: CGRect(origin: origin, size: size))
            }
        }
    }

    /// Cached cap textures — every coin shares one of these four images.
    private static let capImages: [String: UIImage] = Dictionary(
        uniqueKeysWithValues: MedalArtwork.variants.map {
            (MedalArtwork.key($0), renderCapImage(tier: $0))
        }
    )

    private static func capImage(tier: MedalTier?) -> UIImage {
        capImages[MedalArtwork.key(tier)]!
    }

    /// Draws the coin cap: radial gradient with an embossed ring in the
    /// medal's metal, or flat dark while unearned. Rotationally symmetric by
    /// design.
    private static func renderCapImage(tier: MedalTier?) -> UIImage {
        let side: CGFloat = 1024
        let center = CGPoint(x: side / 2, y: side / 2)
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: side, height: side))

        return renderer.image { ctx in
            let cg = ctx.cgContext

            let colors = MedalArtwork.colors(tier)
            let centerColor = colors.center
            let edgeColor = colors.edge
            let gradient = CGGradient(
                colorsSpace: CGColorSpaceCreateDeviceRGB(),
                colors: [centerColor.cgColor, edgeColor.cgColor] as CFArray,
                locations: [0.0, 1.0]
            )!
            cg.drawRadialGradient(
                gradient,
                startCenter: center, startRadius: 0,
                endCenter: center, endRadius: side / 2,
                options: [.drawsAfterEndLocation]
            )

            cg.setStrokeColor(colors.rim.cgColor)
            cg.setLineWidth(20)
            cg.strokeEllipse(in: CGRect(x: 48, y: 48, width: side - 96, height: side - 96))
        }
    }

    private static func renderedText(
        _ text: String,
        fontSize: CGFloat,
        weight: UIFont.Weight = .regular,
        color: UIColor
    ) -> UIImage {
        let attributed = NSAttributedString(string: text, attributes: [
            .font: UIFont.systemFont(ofSize: fontSize, weight: weight),
            .foregroundColor: color,
        ])
        let size = attributed.size()
        let renderer = UIGraphicsImageRenderer(size: size)
        return renderer.image { _ in
            attributed.draw(at: .zero)
        }
    }
}

/// The colors each medal is struck in, shared by the 3D coin, its flat
/// stand-in and the card around it. Nil is a medal not yet earned.
enum MedalArtwork {
    /// Every look a coin can have: unearned, then each medal.
    static let variants: [MedalTier?] = [nil, .bronze, .silver, .gold]

    static func colors(_ tier: MedalTier?) -> (center: UIColor, edge: UIColor, rim: UIColor) {
        switch tier {
        case nil: return (AppColors.medalShadowFaceUI, AppColors.medalShadowFaceUI, AppColors.medalShadowSymbolUI)
        case .bronze: return (AppColors.medalBronzeCenterUI, AppColors.medalBronzeEdgeUI, AppColors.medalBronzeRimUI)
        case .silver: return (AppColors.medalSilverCenterUI, AppColors.medalSilverEdgeUI, AppColors.medalSilverRimUI)
        case .gold: return (AppColors.medalGoldCenterUI, AppColors.medalGoldEdgeUI, AppColors.medalGoldRimUI)
        }
    }

    /// The progress ring, tick and percentage color: the theme orange short
    /// of bronze, then the medal's own.
    static func accent(_ tier: MedalTier?) -> Color {
        switch tier {
        case nil: return AppColors.buttonColor
        case .bronze: return AppColors.medalBronzeAccent
        case .silver: return AppColors.medalSilverAccent
        case .gold: return AppColors.medalGoldAccent
        }
    }

    /// The overlay's text and beam color: the light end of the metal on the
    /// dark blur, the dark end on the light one — for silver and gold a darker
    /// ink than their coins' edges, which all but vanish into the light blur.
    static func headingColor(_ tier: MedalTier, isDarkMode: Bool) -> Color {
        switch tier {
        case .bronze: return isDarkMode ? AppColors.trophyBronzeLight : AppColors.trophyBronzeDark
        case .silver: return isDarkMode ? AppColors.trophySilverLight : AppColors.medalSilverInk
        case .gold: return isDarkMode ? AppColors.trophyGoldLight : AppColors.medalGoldInk
        }
    }

    /// A cache key for each of the four variants.
    static func key(_ tier: MedalTier?) -> String {
        tier.map { "\($0)" } ?? "shadow"
    }
}

/// Beams of light fanning out from behind an earned medal in its own color,
/// slowly turning. Android's `MedalRays` draws the same twelve beams, as wide,
/// fading the same way and turning at the same pace.
struct MedalRays: View {
    let color: Color

    /// How much larger than the coin the beams reach.
    static let scale: CGFloat = 1.8
    private static let count = 12
    private static let widthDegrees: Double = 10
    /// One turn of the beams, in seconds.
    private static let turnDuration: Double = 40

    var body: some View {
        TimelineView(.animation) { timeline in
            let seconds = timeline.date.timeIntervalSinceReferenceDate
            let turn = (seconds / Self.turnDuration).truncatingRemainder(dividingBy: 1) * 360

            Canvas { context, size in
                let center = CGPoint(x: size.width / 2, y: size.height / 2)
                let radius = min(size.width, size.height) / 2
                let disc = Path(ellipseIn: CGRect(
                    x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2
                ))

                // A soft halo first, so the beams rise out of light rather
                // than a hard rim.
                context.fill(disc, with: .radialGradient(
                    Gradient(stops: [
                        .init(color: color.opacity(0.35), location: 0.45),
                        .init(color: color.opacity(0), location: 1),
                    ]),
                    center: center, startRadius: 0, endRadius: radius
                ))

                var beams = Path()
                for index in 0..<Self.count {
                    let middle = turn + Double(index) * 360 / Double(Self.count) - 90
                    beams.move(to: center)
                    beams.addArc(
                        center: center,
                        radius: radius,
                        startAngle: .degrees(middle - Self.widthDegrees / 2),
                        endAngle: .degrees(middle + Self.widthDegrees / 2),
                        clockwise: false
                    )
                    beams.closeSubpath()
                }
                // Full strength out to the coin's edge, gone by the tips.
                context.fill(beams, with: .radialGradient(
                    Gradient(stops: [
                        .init(color: color.opacity(0.55), location: 1 / Self.scale),
                        .init(color: color.opacity(0), location: 1),
                    ]),
                    center: center, startRadius: 0, endRadius: radius
                ))
            }
        }
        .accessibilityHidden(true)
    }
}
