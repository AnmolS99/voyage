import SwiftUI

/// A country's outline as the Daily Challenge shows it: seen from straight above
/// its own center, as the globe would show it once selected.
///
/// The rings are projected orthographically around the country's `CountryFocus`,
/// so a country across the antimeridian (Russia, Fiji, New Zealand, the US) stays
/// in one piece and a northern one (Norway, Iceland) is not stretched sideways the
/// way plotting raw longitude against latitude stretches it. Only the rings that
/// reach the focus cap, or come within `neighborMargin` of it, are drawn — the US
/// without Hawaii and the far Aleutians, Japan without the Ryukyus — so the main
/// landmass is not shrunk to make room for distant islands, while near islands
/// (Shikoku, Jeju, Gaza, Bioko, Tobago) stay.
///
/// Built once per country and normalised to its own `bounds`; the view only scales it.
struct CountrySilhouette {
    /// How far outside the focus cap, in degrees of arc, a ring may start and still be drawn.
    static let neighborMargin = 2.0

    /// Outer rings and holes in projected units (x east, y down), filled even-odd
    /// so enclaves such as Lesotho in South Africa stay cut out.
    let path: Path
    let bounds: CGRect

    init?(_ country: GeoJSONCountry) {
        guard !country.isPointCountry,
              let focus = CountryHitTester.shared.focus(of: country.name) else { return nil }
        self.init(rings: country.polygons + country.holes, focus: focus)
    }

    init?(rings: [Ring], focus: CountryFocus) {
        let toRadians = Double.pi / 180
        let lat0 = focus.lat * toRadians
        let sinLat0 = sin(lat0)
        let cosLat0 = cos(lat0)
        let nearest = cos(min(focus.radiusDegrees + Self.neighborMargin, 89) * toRadians)

        var path = Path()
        for ring in rings {
            let projected = ring.map { coord -> (point: CGPoint, cosDistance: Double) in
                let lat = coord.lat * toRadians
                let dLon = (coord.lon - focus.lon) * toRadians
                let x = cos(lat) * sin(dLon)
                let y = cosLat0 * sin(lat) - sinLat0 * cos(lat) * cos(dLon)
                let cosDistance = sinLat0 * sin(lat) + cosLat0 * cos(lat) * cos(dLon)
                return (CGPoint(x: x, y: -y), cosDistance)
            }
            guard projected.count >= 3,
                  projected.contains(where: { $0.cosDistance >= nearest }) else { continue }
            path.addLines(projected.map(\.point))
            path.closeSubpath()
        }

        let bounds = path.boundingRect
        guard !path.isEmpty, bounds.width > 0, bounds.height > 0 else { return nil }
        self.path = path
        self.bounds = bounds
    }

    /// The silhouette scaled to fit `size` less `padding` on every side, centered.
    func fitted(in size: CGSize, padding: CGFloat) -> Path {
        let scale = min((size.width - padding * 2) / bounds.width,
                        (size.height - padding * 2) / bounds.height)
        guard scale > 0 else { return Path() }
        let transform = CGAffineTransform(translationX: size.width / 2, y: size.height / 2)
            .scaledBy(x: scale, y: scale)
            .translatedBy(x: -bounds.midX, y: -bounds.midY)
        return path.applying(transform)
    }

    /// Built silhouettes by flag code: the view is re-created on every change to
    /// the challenge sheet, but a country's outline never changes.
    @MainActor private static var cache: [String: CountrySilhouette] = [:]

    @MainActor static func of(flagCode: String) -> CountrySilhouette? {
        if let cached = cache[flagCode] { return cached }
        guard let country = CountryDataCache.shared.countryWithIsoCode(flagCode),
              let silhouette = CountrySilhouette(country) else { return nil }
        cache[flagCode] = silhouette
        return silhouette
    }
}

struct CountrySilhouetteView: View {
    let flagCode: String
    let isDarkMode: Bool

    var body: some View {
        if let silhouette = CountrySilhouette.of(flagCode: flagCode) {
            let fillColor = isDarkMode ? Color.white : Color(red: 0.2, green: 0.15, blue: 0.1)
            Canvas { context, size in
                context.fill(silhouette.fitted(in: size, padding: 20),
                             with: .color(fillColor),
                             style: FillStyle(eoFill: true))
            }
        } else {
            Image(systemName: "questionmark.square.dashed")
                .font(.system(size: 60))
                .foregroundColor(AppColors.textMuted(isDarkMode: isDarkMode))
        }
    }
}
