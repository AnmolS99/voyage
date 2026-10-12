import SwiftUI

/// Shared drawing for the capital marker — a "star seal": a terracotta disc with
/// an ink rim and a small cream star cut out of its centre.
///
/// The globe (`GlobeView`) and the map (`MapView`) must render capitals
/// identically, so the marker is drawn in one place: the map calls `draw` on its
/// canvas, the globe draws it once into a texture (`image`) for a billboarded
/// plane. Only the size differs: the globe measures in world units, the map in
/// points.
enum CapitalMarker {
    /// Ratio of the star's inner radius to its outer radius. Shallower than the
    /// classic 0.382 so the star stays legible as a cut-out at a few points across.
    private static let innerRadiusRatio: CGFloat = 0.45
    private static let pointCount = 5

    /// The star's outer radius as a fraction of the seal's.
    private static let starToSealRatio: CGFloat = 0.6
    /// The ink rim's width as a fraction of the seal's radius — 1pt on the map's 6pt seal.
    private static let rimToSealRatio: CGFloat = 1.0 / 6.0

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

    /// Draws the seal centered on `center` in a +Y-down context. `radius` is the
    /// outer edge of the ink rim, so the marker fits exactly in a `2 * radius` square.
    static func draw(in context: CGContext, center: CGPoint, radius: CGFloat) {
        let rimWidth = radius * rimToSealRatio
        let disc = CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)
            .insetBy(dx: rimWidth / 2, dy: rimWidth / 2)

        context.saveGState()
        defer { context.restoreGState() }

        context.setFillColor(AppColors.capitalMarkerUI.cgColor)
        context.fillEllipse(in: disc)
        context.setStrokeColor(AppColors.capitalMarkerOutlineUI.cgColor)
        context.setLineWidth(rimWidth)
        context.strokeEllipse(in: disc)

        // Filled only, no stroke: Android's globe builds the star as a flat mesh
        // from the same corners, which a stroke's rounded joins would outgrow.
        context.translateBy(x: center.x, y: center.y)
        context.addPath(starPath(outerRadius: radius * starToSealRatio, yUp: false))
        context.setFillColor(AppColors.capitalMarkerInsetUI.cgColor)
        context.fillPath()
    }

    /// The seal rendered once at texture resolution, for the globe's marker plane.
    static let image: UIImage = {
        let size: CGFloat = 128
        return UIGraphicsImageRenderer(size: CGSize(width: size, height: size)).image { ctx in
            draw(in: ctx.cgContext, center: CGPoint(x: size / 2, y: size / 2), radius: size / 2)
        }
    }()
}
