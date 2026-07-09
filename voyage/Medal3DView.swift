import SwiftUI
import SceneKit
import UIKit

/// An interactive 3D medal rendered with SceneKit.
///
/// The medal is a thin metallic coin standing on edge so its face points at the
/// camera. Completed achievements mint a shiny gold medal; locked ones show a
/// dimmed steel disc — mirroring the grayscale/opacity treatment the flat medal
/// in `AchievementCard` already uses.
///
/// The coin idly turns like a spinning coin and can be grabbed and spun with a
/// drag. Only one of these is ever alive at a time (it lives inside the expanded
/// `AchievementDetailSection`, and expanding one card collapses the others), so
/// the SceneKit view stays cheap.
struct Medal3DView: UIViewRepresentable {
    let emoji: String
    let isCompleted: Bool

    func makeUIView(context: Context) -> SCNView {
        let view = SCNView()
        view.scene = context.coordinator.makeScene()
        view.backgroundColor = .clear
        view.antialiasingMode = .multisampling4X
        view.autoenablesDefaultLighting = false
        view.rendersContinuously = true

        let pan = UIPanGestureRecognizer(target: context.coordinator,
                                         action: #selector(Coordinator.handlePan(_:)))
        view.addGestureRecognizer(pan)

        context.coordinator.scnView = view
        context.coordinator.startIdleSpin()
        return view
    }

    func updateUIView(_ uiView: SCNView, context: Context) {
        context.coordinator.update(emoji: emoji, isCompleted: isCompleted)
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(emoji: emoji, isCompleted: isCompleted)
    }

    // MARK: - Coordinator

    final class Coordinator: NSObject {
        weak var scnView: SCNView?

        /// Rotated for the idle/drag animation; the coin geometry is a child so it
        /// stays face-on while this node turns about the vertical screen axis.
        private let container = SCNNode()
        private let coinNode = SCNNode()

        private var emoji: String
        private var isCompleted: Bool

        private var panStartEuler = SCNVector3Zero
        private static let idleSpinKey = "idleSpin"

        init(emoji: String, isCompleted: Bool) {
            self.emoji = emoji
            self.isCompleted = isCompleted
        }

        // MARK: Scene

        func makeScene() -> SCNScene {
            let scene = SCNScene()

            let coin = SCNCylinder(radius: 1.0, height: 0.14)
            coin.radialSegmentCount = 96
            coinNode.geometry = coin
            coinNode.eulerAngles.x = -.pi / 2   // stand the coin on edge, face toward +Z
            applyMaterials()
            container.addChildNode(coinNode)
            scene.rootNode.addChildNode(container)

            let cameraNode = SCNNode()
            cameraNode.camera = SCNCamera()
            cameraNode.camera?.fieldOfView = 30
            cameraNode.position = SCNVector3(0, 0, 5.5)
            scene.rootNode.addChildNode(cameraNode)

            // Key light (upper-right) whose highlight sweeps across the metal as it turns.
            let key = SCNNode()
            key.light = SCNLight()
            key.light?.type = .omni
            key.light?.intensity = 1100
            key.position = SCNVector3(3, 4, 6)
            scene.rootNode.addChildNode(key)

            // Ambient fill so the shadowed side never goes pure black.
            let ambient = SCNNode()
            ambient.light = SCNLight()
            ambient.light?.type = .ambient
            ambient.light?.intensity = 350
            scene.rootNode.addChildNode(ambient)

            return scene
        }

        private func applyMaterials() {
            let tier: MedalTier = isCompleted ? .gold : .locked

            let rim = SCNMaterial()
            rim.lightingModel = .phong
            rim.diffuse.contents = tier.rimColor
            rim.specular.contents = UIColor.white
            rim.shininess = 0.6

            let face = SCNMaterial()
            face.lightingModel = .phong
            face.diffuse.contents = Coordinator.faceTexture(emoji: emoji, tier: tier)
            face.diffuse.wrapS = .clamp
            face.diffuse.wrapT = .clamp
            face.specular.contents = UIColor.white
            face.shininess = 0.5

            // SCNCylinder material order is [side, top, bottom].
            coinNode.geometry?.materials = [rim, face, face]
        }

        func update(emoji: String, isCompleted: Bool) {
            guard emoji != self.emoji || isCompleted != self.isCompleted else { return }
            self.emoji = emoji
            self.isCompleted = isCompleted
            applyMaterials()
        }

        // MARK: Animation

        func startIdleSpin() {
            guard container.action(forKey: Coordinator.idleSpinKey) == nil else { return }
            let spin = SCNAction.repeatForever(
                SCNAction.rotate(by: .pi * 2, around: SCNVector3(0, 1, 0), duration: 9)
            )
            container.runAction(spin, forKey: Coordinator.idleSpinKey)
        }

        @objc func handlePan(_ gesture: UIPanGestureRecognizer) {
            guard let view = scnView else { return }
            switch gesture.state {
            case .began:
                container.removeAction(forKey: Coordinator.idleSpinKey)
                panStartEuler = container.eulerAngles
            case .changed:
                let t = gesture.translation(in: view)
                container.eulerAngles = SCNVector3(
                    panStartEuler.x + Float(-t.y) * 0.01,
                    panStartEuler.y + Float(t.x) * 0.01,
                    panStartEuler.z
                )
            case .ended, .cancelled, .failed:
                // rotate(by:) is relative, so the idle spin resumes from wherever
                // the drag left the coin — no snap back to a fixed orientation.
                startIdleSpin()
            default:
                break
            }
        }

        // MARK: Face texture

        /// Renders the medal face: a radial metallic disc, an engraved rim ring, and
        /// the achievement emoji centered on top.
        private static func faceTexture(emoji: String, tier: MedalTier) -> UIImage {
            let size = CGSize(width: 512, height: 512)
            let renderer = UIGraphicsImageRenderer(size: size)
            return renderer.image { ctx in
                let cg = ctx.cgContext
                let rect = CGRect(origin: .zero, size: size)

                let colors = [tier.faceLight.cgColor, tier.faceDark.cgColor] as CFArray
                let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(),
                                          colors: colors, locations: [0.0, 1.0])!
                cg.drawRadialGradient(
                    gradient,
                    startCenter: CGPoint(x: size.width * 0.4, y: size.height * 0.32),
                    startRadius: 0,
                    endCenter: CGPoint(x: size.width / 2, y: size.height / 2),
                    endRadius: size.width * 0.62,
                    options: []
                )

                let ring = UIBezierPath(ovalIn: rect.insetBy(dx: 36, dy: 36))
                ring.lineWidth = 18
                tier.ringColor.setStroke()
                ring.stroke()

                let font = UIFont.systemFont(ofSize: 260)
                let attributes: [NSAttributedString.Key: Any] = [.font: font]
                let glyph = emoji as NSString
                let glyphSize = glyph.size(withAttributes: attributes)
                let origin = CGPoint(x: (size.width - glyphSize.width) / 2,
                                     y: (size.height - glyphSize.height) / 2)
                // Locked medals dim the emoji so the steel disc reads as "not earned yet",
                // matching the grayscale/opacity treatment of the flat card medal.
                cg.setAlpha(tier.emojiAlpha)
                glyph.draw(at: origin, withAttributes: attributes)
            }
        }
    }
}

/// Visual treatment for a medal, keyed off whether the achievement is earned.
private enum MedalTier {
    case gold
    case locked

    var rimColor: UIColor {
        switch self {
        case .gold:   return UIColor(red: 0.78, green: 0.60, blue: 0.16, alpha: 1.0)
        case .locked: return UIColor(white: 0.34, alpha: 1.0)
        }
    }

    var faceLight: UIColor {
        switch self {
        case .gold:   return UIColor(red: 1.00, green: 0.87, blue: 0.42, alpha: 1.0)
        case .locked: return UIColor(white: 0.62, alpha: 1.0)
        }
    }

    var faceDark: UIColor {
        switch self {
        case .gold:   return UIColor(red: 0.80, green: 0.60, blue: 0.16, alpha: 1.0)
        case .locked: return UIColor(white: 0.38, alpha: 1.0)
        }
    }

    var ringColor: UIColor {
        switch self {
        case .gold:   return UIColor(red: 0.62, green: 0.46, blue: 0.10, alpha: 1.0)
        case .locked: return UIColor(white: 0.26, alpha: 1.0)
        }
    }

    var emojiAlpha: CGFloat {
        switch self {
        case .gold:   return 1.0
        case .locked: return 0.55
        }
    }
}
