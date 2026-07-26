import SwiftUI

/// Shared geometry for the capital marker — the five-pointed star used on printed
/// maps to mark a capital city.
///
/// The globe (`GlobeView`) and the map (`MapView`) must render capitals
/// identically, so the shape lives here instead of being rebuilt in each view.
/// Only the size differs: the globe measures in world units, the map in points.
enum CapitalMarker {
    /// Ratio of the star's inner radius to its outer radius. 0.382 is the classic
    /// five-pointed star — the inner vertices land where the arms would cross if
    /// the star were drawn as a pentagram.
    private static let innerRadiusRatio: CGFloat = 0.382
    private static let pointCount = 5

    /// A five-pointed star centered on the origin with one point facing up.
    ///
    /// - Parameter yUp: `true` for coordinate spaces where +Y points up (SceneKit),
    ///   `false` where +Y points down (Core Graphics, SwiftUI `Canvas`). Getting
    ///   this wrong renders the star upside down rather than failing loudly.
    static func starPath(outerRadius: CGFloat, yUp: Bool) -> CGPath {
        let path = CGMutablePath()
        let innerRadius = outerRadius * innerRadiusRatio
        let ySign: CGFloat = yUp ? 1 : -1

        for vertex in 0..<(pointCount * 2) {
            // Start at straight up, then alternate outer/inner every half segment.
            let angle = CGFloat.pi / 2 + CGFloat(vertex) * .pi / CGFloat(pointCount)
            let radius = vertex.isMultiple(of: 2) ? outerRadius : innerRadius
            let point = CGPoint(x: cos(angle) * radius, y: sin(angle) * radius * ySign)
            if vertex == 0 {
                path.move(to: point)
            } else {
                path.addLine(to: point)
            }
        }

        path.closeSubpath()
        return path
    }
}
