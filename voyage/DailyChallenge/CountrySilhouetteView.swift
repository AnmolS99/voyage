import SwiftUI

struct CountrySilhouetteView: View {
    let flagCode: String
    let isDarkMode: Bool

    private var country: GeoJSONCountry? {
        CountryDataCache.shared.countries.first { $0.flagCode == flagCode }
    }

    var body: some View {
        if let country = country, !country.polygons.isEmpty {
            Canvas { context, size in
                let allPoints = country.polygons.flatMap { $0 }
                guard !allPoints.isEmpty else { return }

                let lons = allPoints.compactMap { $0.count >= 2 ? $0[0] : nil }
                let lats = allPoints.compactMap { $0.count >= 2 ? $0[1] : nil }
                guard let minLon = lons.min(), let maxLon = lons.max(),
                      let minLat = lats.min(), let maxLat = lats.max() else { return }

                let lonRange = maxLon - minLon
                let latRange = maxLat - minLat
                guard lonRange > 0, latRange > 0 else { return }

                let padding: CGFloat = 20
                let drawWidth = size.width - padding * 2
                let drawHeight = size.height - padding * 2
                let scale = min(drawWidth / lonRange, drawHeight / latRange)

                let scaledWidth = lonRange * scale
                let scaledHeight = latRange * scale
                let offsetX = padding + (drawWidth - scaledWidth) / 2
                let offsetY = padding + (drawHeight - scaledHeight) / 2

                let fillColor = isDarkMode ? Color.white : Color(red: 0.2, green: 0.15, blue: 0.1)

                for polygon in country.polygons {
                    guard polygon.count >= 3 else { continue }
                    var path = Path()
                    for (i, coord) in polygon.enumerated() {
                        guard coord.count >= 2 else { continue }
                        let x = (coord[0] - minLon) * scale + offsetX
                        let y = (maxLat - coord[1]) * scale + offsetY
                        if i == 0 {
                            path.move(to: CGPoint(x: x, y: y))
                        } else {
                            path.addLine(to: CGPoint(x: x, y: y))
                        }
                    }
                    path.closeSubpath()
                    context.fill(path, with: .color(fillColor))
                }
            }
        } else {
            Image(systemName: "questionmark.square.dashed")
                .font(.system(size: 60))
                .foregroundColor(AppColors.textMuted(isDarkMode: isDarkMode))
        }
    }
}
