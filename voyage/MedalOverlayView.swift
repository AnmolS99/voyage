import SwiftUI
import SceneKit
import CoreImage
import Metal

/// Full-screen overlay shown when the user taps an achievement's small medal.
/// The 3D coin starts at the small medal's on-screen frame (`sourceFrame`) and
/// animates its own frame to the center while the background blurs in, so the
/// small medal visibly enlarges rather than being swapped for another view.
/// It is spinnable around the Y axis only (matching the globe's horizontal drag).
struct MedalOverlayView: View {
    let achievement: Achievement
    let isDarkMode: Bool
    /// Global frame of the tapped small medal, where the coin starts and
    /// returns on dismissal.
    let sourceFrame: CGRect
    /// Called once the collapse-back animation has finished.
    let onDismissed: () -> Void

    @State private var isExpanded = false
    /// Global frame of the expanded coin's slot, measured from the placeholder.
    @State private var targetFrame: CGRect = .zero

    var body: some View {
        GeometryReader { geo in
            let overlayOrigin = geo.frame(in: .global).origin
            let medalFrame = isExpanded && targetFrame != .zero ? targetFrame : sourceFrame

            ZStack {
                Rectangle()
                    .fill(.ultraThinMaterial)
                    .ignoresSafeArea()
                    .opacity(isExpanded ? 1 : 0)
                    .onTapGesture { dismiss() }

                VStack(spacing: 8) {
                    // Invisible placeholder marking where the expanded coin belongs
                    Color.clear
                        .frame(width: 320, height: 320)
                        .onGeometryChange(for: CGRect.self) { proxy in
                            proxy.frame(in: .global)
                        } action: { frame in
                            targetFrame = frame
                        }

                    VStack(spacing: 8) {
                        Text(achievement.name)
                            .font(.system(size: 22, weight: .bold, design: .rounded))
                            .foregroundColor(AppColors.textPrimary(isDarkMode: isDarkMode))

                        Text("\(achievement.current)/\(achievement.total) \(achievement.itemLabel)")
                            .font(.system(size: 15, weight: .medium, design: .rounded))
                            .foregroundColor(AppColors.textTertiary(isDarkMode: isDarkMode))

                        Label("Drag to spin", systemImage: "hand.draw")
                            .font(.system(size: 13, weight: .medium, design: .rounded))
                            .foregroundColor(AppColors.textMuted(isDarkMode: isDarkMode))
                            .padding(.top, 4)

                        Button(action: dismiss) {
                            Text("Close")
                                .font(.system(size: 15, weight: .semibold, design: .rounded))
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
                    .opacity(isExpanded ? 1 : 0)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)

                MedalSceneView(achievement: achievement, isSettling: !isExpanded)
                    .frame(width: medalFrame.width, height: medalFrame.height)
                    .position(
                        x: medalFrame.midX - overlayOrigin.x,
                        y: medalFrame.midY - overlayOrigin.y
                    )
            }
        }
        .task {
            withAnimation(.spring(response: 0.45, dampingFraction: 0.85)) {
                isExpanded = true
            }
        }
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
/// cached offscreen snapshot instead of hosting a live `SCNView` — the tab
/// shows one coin per achievement, and creating that many SceneKit views at
/// once made the Achievements tab take ~1s to open.
struct MedalCardView: View {
    let achievement: Achievement

    var body: some View {
        Image(uiImage: MedalSceneView.cardSnapshot(medal: achievement.medal, completed: achievement.isCompleted))
            .resizable()
            .scaledToFit()
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
        sceneView.scene = Self.buildScene(medal: achievement.medal, completed: achievement.isCompleted)
        context.coordinator.isCompleted = achievement.isCompleted
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
        // Rebuild only when the medal flips between locked (silver) and
        // unlocked (gold) — e.g. the user checks off the final country
        if context.coordinator.isCompleted != achievement.isCompleted {
            context.coordinator.isCompleted = achievement.isCompleted
            uiView.scene = Self.buildScene(medal: achievement.medal, completed: achievement.isCompleted)
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
        var isCompleted = false
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

    private static func buildScene(medal: String, completed: Bool) -> SCNScene {
        let scene = SCNScene()
        scene.background.contents = UIColor.clear

        let cameraNode = SCNNode()
        cameraNode.name = "camera"
        cameraNode.camera = SCNCamera()
        cameraNode.position = SCNVector3(0, 0, 3.2)
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
        rim.diffuse.contents = completed ? AppColors.medalGoldRimUI : AppColors.medalSilverRimUI
        rim.specular.contents = UIColor.white
        rim.shininess = 0.4
        rim.lightingModel = .blinn

        let face = SCNMaterial()
        face.diffuse.contents = capImage(completed: completed)
        face.specular.contents = UIColor.white
        face.shininess = 0.6
        face.lightingModel = .blinn

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
        let frontNode = symbolPlaneNode(image: symbolImage(medal: medal, front: true, completed: completed))
        frontNode.position.z = 0.065
        spinNode.addChildNode(frontNode)

        let backNode = symbolPlaneNode(image: symbolImage(medal: medal, front: false, completed: completed))
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

    /// A one-off offscreen render of the front-facing coin, cached per medal
    /// and completion state. Cards display this instead of a live `SCNView`.
    static func cardSnapshot(medal: String, completed: Bool) -> UIImage {
        cachedArtwork(key: "coin|\(medal)|\(completed)") {
            snapshotRendererLock.lock()
            defer { snapshotRendererLock.unlock() }
            let scene = buildScene(medal: medal, completed: completed)
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

    /// Rendering a symbol image costs ~100ms (mostly Core Image desaturation),
    /// and one card coin per achievement is needed when the Achievements tab
    /// first appears. All rendered artwork — symbol textures and finished coin
    /// snapshots — is cached here, and `prewarmArtwork` fills the cache off the
    /// main thread at app launch so the tab opens instantly.
    private static var artworkCache: [String: UIImage] = [:]
    private static let artworkLock = NSLock()
    private static var didPrewarm = false

    /// Shared Core Image context — creating one per call is what made
    /// desaturation expensive.
    private static let ciContext = CIContext()

    /// Renders and caches every medal's textures and card snapshot in both
    /// locked and unlocked variants on a background queue. Call once at app
    /// launch.
    static func prewarmArtwork(medals: [String]) {
        artworkLock.lock()
        let alreadyPrewarmed = didPrewarm
        didPrewarm = true
        artworkLock.unlock()
        guard !alreadyPrewarmed else { return }

        DispatchQueue.global(qos: .utility).async {
            _ = goldCapImage
            _ = silverCapImage
            for medal in medals {
                for completed in [false, true] {
                    _ = symbolImage(medal: medal, front: true, completed: completed)
                    _ = symbolImage(medal: medal, front: false, completed: completed)
                    _ = cardSnapshot(medal: medal, completed: completed)
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

    /// Emoji for the front, star for the back; locked achievements get a
    /// desaturated, faded emoji. Transparent background so the coin face
    /// shows through.
    private static func symbolImage(medal: String, front: Bool, completed: Bool) -> UIImage {
        cachedArtwork(key: "symbol|\(medal)|\(front)|\(completed)") {
            let ringColor = completed ? AppColors.medalGoldRimUI : AppColors.medalSilverRimUI
            let side: CGFloat = 512
            var symbol = renderedText(front ? medal : "★", fontSize: 400, color: ringColor)
            if !completed && front {
                symbol = desaturated(symbol)
            }
            let renderer = UIGraphicsImageRenderer(size: CGSize(width: side, height: side))
            let alpha: CGFloat = completed ? 1.0 : 0.55
            return renderer.image { _ in
                // Fit the symbol inside the square, preserving aspect ratio
                let scale = min(side / symbol.size.width, side / symbol.size.height)
                let size = CGSize(width: symbol.size.width * scale, height: symbol.size.height * scale)
                let origin = CGPoint(x: (side - size.width) / 2, y: (side - size.height) / 2)
                symbol.draw(in: CGRect(origin: origin, size: size), blendMode: .normal, alpha: alpha)
            }
        }
    }

    /// Cached cap textures — every coin shares one of these two images.
    private static let goldCapImage = renderCapImage(completed: true)
    private static let silverCapImage = renderCapImage(completed: false)

    private static func capImage(completed: Bool) -> UIImage {
        completed ? goldCapImage : silverCapImage
    }

    /// Draws the coin cap: radial gradient with an embossed ring, gold when
    /// unlocked and silver when locked. Rotationally symmetric by design.
    private static func renderCapImage(completed: Bool) -> UIImage {
        let side: CGFloat = 1024
        let center = CGPoint(x: side / 2, y: side / 2)
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: side, height: side))

        return renderer.image { ctx in
            let cg = ctx.cgContext

            let centerColor = completed ? AppColors.medalGoldCenterUI : AppColors.medalSilverCenterUI
            let edgeColor = completed ? AppColors.medalGoldEdgeUI : AppColors.medalSilverEdgeUI
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

            let ringColor = completed ? AppColors.medalGoldRimUI : AppColors.medalSilverRimUI
            cg.setStrokeColor(ringColor.cgColor)
            cg.setLineWidth(20)
            cg.strokeEllipse(in: CGRect(x: 48, y: 48, width: side - 96, height: side - 96))
        }
    }

    private static func renderedText(_ text: String, fontSize: CGFloat, color: UIColor) -> UIImage {
        let attributed = NSAttributedString(string: text, attributes: [
            .font: UIFont.systemFont(ofSize: fontSize),
            .foregroundColor: color,
        ])
        let size = attributed.size()
        let renderer = UIGraphicsImageRenderer(size: size)
        return renderer.image { _ in
            attributed.draw(at: .zero)
        }
    }

    private static func desaturated(_ image: UIImage) -> UIImage {
        guard let ciImage = CIImage(image: image),
              let filter = CIFilter(name: "CIColorControls", parameters: [
                  kCIInputImageKey: ciImage,
                  kCIInputSaturationKey: 0.0,
              ]),
              let output = filter.outputImage,
              let cgImage = ciContext.createCGImage(output, from: output.extent) else {
            return image
        }
        return UIImage(cgImage: cgImage, scale: image.scale, orientation: image.imageOrientation)
    }
}
