import SwiftUI

struct ConfettiView: View {
    private let particles: [ConfettiParticle] = (0..<100).map { _ in ConfettiParticle() }
    @State private var startTime = Date()

    var body: some View {
        TimelineView(.animation) { context in
            let elapsed = context.date.timeIntervalSince(startTime)
            Canvas { ctx, size in
                for particle in particles {
                    particle.draw(in: ctx, size: size, elapsed: elapsed)
                }
            }
            .ignoresSafeArea()
        }
        .allowsHitTesting(false)
        .onAppear { startTime = Date() }
    }
}

private struct ConfettiParticle {
    let xFraction: CGFloat
    let vy0: CGFloat
    let vx: CGFloat
    let color: Color
    let size: CGFloat
    let rotation0: CGFloat
    let rotSpeed: CGFloat
    let isRect: Bool

    private static let colors: [Color] = [.red, .orange, .yellow, .green, .blue, .purple, .pink, .cyan]
    private static let gravity: CGFloat = 600

    init() {
        xFraction = CGFloat.random(in: 0.1...0.9)
        vy0 = -CGFloat.random(in: 500...900)
        vx = CGFloat.random(in: -100...100)
        color = Self.colors.randomElement()!
        size = CGFloat.random(in: 7...13)
        rotation0 = CGFloat.random(in: 0...360)
        rotSpeed = CGFloat.random(in: -400...400)
        isRect = Bool.random()
    }

    func draw(in ctx: GraphicsContext, size canvasSize: CGSize, elapsed: Double) {
        let t = CGFloat(elapsed)
        let px = xFraction * canvasSize.width + vx * t
        let py = canvasSize.height + vy0 * t + 0.5 * Self.gravity * t * t

        guard py < canvasSize.height + 20 else { return }

        let opacity = elapsed < 2.5 ? 1.0 : max(0.0, 1.0 - (elapsed - 2.5) / 0.5)
        guard opacity > 0 else { return }

        var local = ctx
        local.opacity = opacity
        local.transform = CGAffineTransform(translationX: px, y: py)
            .rotated(by: (rotation0 + rotSpeed * t) * .pi / 180)

        let rect = CGRect(x: -size / 2, y: -size * 0.3, width: size, height: size * 0.55)
        let path = isRect ? Path(rect) : Path(ellipseIn: rect)
        local.fill(path, with: .color(color))
    }
}
