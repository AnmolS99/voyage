import Foundation

/// The globe camera's limits and the maths that places it — static, and apart
/// from `GlobeState`, which holds what changes. Android's `GlobeCamera` has the
/// same names and the same values.
enum GlobeCamera {
    /// Closest the camera may get to the globe's center: just above the surface,
    /// with the camera's zNear clearing it at `minCameraDistance - 1`.
    static let minCameraDistance: Float = 1.1
    /// Furthest the camera may get from the globe's center.
    ///
    /// Far enough out that the whole world is in frame — the globe still spans
    /// about 40% of the screen's height — and no further: past this it is a small
    /// ball in a lot of empty space, with nothing to see that the last step out
    /// did not already show. Android clamps to the same 6.0, and its
    /// `GlobeCameraTest` pins it as this does.
    ///
    /// Read by `handlePinch` and `handleDoubleTapDrag`, which are the only two
    /// ways to zoom: they used to undercut it with their own `min(8.0, ...)`,
    /// which left the constant describing nothing.
    static let maxCameraDistance: Float = 6.0

    /// How far from the globe's center the camera must sit for a country to fill
    /// the view: a cap of `radiusDegrees` around `latitude`, fitted to the narrower
    /// axis of a view `aspect` (width ÷ height) wide.
    ///
    /// A point θ from the view's center is seen at α off-axis, where
    /// tan α = sin θ / (d − cos θ); solving that for d with tan α at `fitMargin` of
    /// the narrower half-field of view gives the distance. The camera can't tilt
    /// past `maxCameraLatitude`, so a cap centered beyond it is widened by the
    /// shortfall — the camera looks at it from the side.
    ///
    /// Clamped to `minFitDistance...maxFitDistance`: a microstate still shows its
    /// neighbors, and nothing pulls back to the zoom-out limit. Android's
    /// `GlobeCamera.distanceToFit` is the same formula with the same constants;
    /// `CountryFocusTests` and its `GlobeCameraTest` pin the same distances.
    static func distanceToFit(radiusDegrees: Double, latitude: Double, aspect: Double) -> Float {
        let shortfall = max(0, abs(latitude) - maxCameraLatitude)
        let theta = (radiusDegrees + shortfall) * .pi / 180
        let tanHalf = tan(cameraFieldOfView / 2 * .pi / 180) * min(1, aspect) * fitMargin
        let distance = Float(cos(theta) + sin(theta) / tanHalf)
        return max(minFitDistance, min(maxFitDistance, distance))
    }
    /// `SCNCamera.fieldOfView` in `GlobeScene` — vertical, as on Android.
    static let cameraFieldOfView = 45.0
    /// The `±.pi / 2.5` tilt clamp on `currentRotationX`, in degrees.
    static let maxCameraLatitude = 72.0
    /// Share of the narrower half-field of view a selected country may span.
    static let fitMargin = 0.85
    /// Closest a selection flies in: Luxembourg, Vatican City, every microstate dot.
    static let minFitDistance: Float = 1.25
    /// Furthest a selection pulls back: Russia in portrait lands just inside it.
    static let maxFitDistance: Float = 4.8

    /// How long the globe takes to turn back home — longer than a selection's
    /// 0.8 s flight, since it can be half a turn and a full zoom out away.
    /// Android `GlobeFlight.RETURN_HOME_SECONDS`.
    static let returnHomeDuration: TimeInterval = 1.2
}
