import Foundation

/// Simple inertia model for globe spin-after-drag.
/// Tracks angular velocity and decays it over time using exponential damping.
final class GlobeInertia {
    var velocityX: Float = 0   // rad/s — camera orbit (vertical drag)
    var velocityY: Float = 0   // rad/s — globe spin (horizontal drag)

    /// Fraction of velocity remaining after 1 second (controls how long the spin coasts).
    private let damping: Float = 0.05

    var isActive: Bool {
        abs(velocityX) > 0.001 || abs(velocityY) > 0.001
    }

    /// Advance physics by `dt` seconds. Returns rotation deltas to apply.
    func step(dt: Float) -> (dx: Float, dy: Float) {
        let dx = velocityX * dt
        let dy = velocityY * dt
        let factor = pow(damping, dt)
        velocityX *= factor
        velocityY *= factor
        return (dx, dy)
    }

    func reset() {
        velocityX = 0
        velocityY = 0
    }
}
