import SwiftUI

struct StarryBackground: View {
    let starCount = 150

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                Color.black

                ForEach(0..<starCount, id: \.self) { i in
                    Circle()
                        .fill(Color.white.opacity(Double.random(in: 0.3...1.0)))
                        .frame(width: CGFloat.random(in: 1...3), height: CGFloat.random(in: 1...3))
                        .position(
                            x: CGFloat.random(in: 0...geometry.size.width),
                            y: CGFloat.random(in: 0...geometry.size.height)
                        )
                }
            }
        }
        .ignoresSafeArea()
    }
}

struct ContentView: View {
    @StateObject private var globeState = GlobeState()
    @State private var showingInfo = false

    var body: some View {
        ZStack {
            // Background - starry or warm gradient based on dark mode
            if globeState.isDarkMode {
                StarryBackground()
            } else {
                LinearGradient(
                    colors: [
                        Color(red: 0.98, green: 0.96, blue: 0.93),
                        Color(red: 0.95, green: 0.91, blue: 0.87)
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .ignoresSafeArea()
            }

            VStack(spacing: 0) {
                // Header
                header

                // Globe with zoom controls
                ZStack {
                    GlobeView(globeState: globeState)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)

                    // Zoom controls on the right
                    VStack(spacing: 12) {
                        Button(action: { globeState.zoomIn() }) {
                            Image(systemName: "plus")
                                .font(.system(size: 20, weight: .medium))
                                .foregroundColor(.white)
                                .frame(width: 44, height: 44)
                                .background(Circle().fill(globeState.isDarkMode ? Color(red: 0.4, green: 0.35, blue: 0.6) : Color(red: 0.85, green: 0.55, blue: 0.35)))
                                .shadow(color: Color.black.opacity(0.2), radius: 4, y: 2)
                        }

                        Button(action: { globeState.zoomOut() }) {
                            Image(systemName: "minus")
                                .font(.system(size: 20, weight: .medium))
                                .foregroundColor(.white)
                                .frame(width: 44, height: 44)
                                .background(Circle().fill(globeState.isDarkMode ? Color(red: 0.4, green: 0.35, blue: 0.6) : Color(red: 0.85, green: 0.55, blue: 0.35)))
                                .shadow(color: Color.black.opacity(0.2), radius: 4, y: 2)
                        }
                    }
                    .padding(.trailing, 16)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .trailing)
                }

                // Bottom info panel
                bottomPanel
            }
        }
        .preferredColorScheme(globeState.isDarkMode ? .dark : .light)
    }

    private var header: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text("Globe Explorer")
                    .font(.system(size: 28, weight: .semibold, design: .rounded))
                    .foregroundColor(globeState.isDarkMode ? .white : Color(red: 0.2, green: 0.15, blue: 0.1))

                Text("Tap any country to highlight it")
                    .font(.system(size: 14, weight: .medium, design: .rounded))
                    .foregroundColor(globeState.isDarkMode ? Color(red: 0.7, green: 0.7, blue: 0.75) : Color(red: 0.5, green: 0.45, blue: 0.4))
            }

            Spacer()

            // Dark mode toggle
            Button(action: {
                withAnimation(.spring(response: 0.4, dampingFraction: 0.7)) {
                    globeState.isDarkMode.toggle()
                }
            }) {
                Image(systemName: globeState.isDarkMode ? "sun.max.fill" : "moon.fill")
                    .font(.system(size: 18, weight: .medium))
                    .foregroundColor(.white)
                    .frame(width: 44, height: 44)
                    .background(
                        Circle()
                            .fill(globeState.isDarkMode ? Color(red: 0.4, green: 0.35, blue: 0.6) : Color(red: 0.85, green: 0.55, blue: 0.35))
                    )
                    .shadow(color: (globeState.isDarkMode ? Color(red: 0.4, green: 0.35, blue: 0.6) : Color(red: 0.85, green: 0.55, blue: 0.35)).opacity(0.4), radius: 8, y: 4)
            }

            // Reset button
            Button(action: {
                withAnimation(.spring(response: 0.4, dampingFraction: 0.7)) {
                    globeState.resetSelection()
                }
            }) {
                Image(systemName: "arrow.counterclockwise")
                    .font(.system(size: 18, weight: .medium))
                    .foregroundColor(.white)
                    .frame(width: 44, height: 44)
                    .background(
                        Circle()
                            .fill(globeState.isDarkMode ? Color(red: 0.4, green: 0.35, blue: 0.6) : Color(red: 0.85, green: 0.55, blue: 0.35))
                    )
                    .shadow(color: (globeState.isDarkMode ? Color(red: 0.4, green: 0.35, blue: 0.6) : Color(red: 0.85, green: 0.55, blue: 0.35)).opacity(0.4), radius: 8, y: 4)
            }
        }
        .padding(.horizontal, 24)
        .padding(.top, 16)
        .padding(.bottom, 12)
    }

    private var bottomPanel: some View {
        VStack(spacing: 12) {
            // Selected country display
            HStack {
                if let country = globeState.selectedCountry {
                    HStack(spacing: 10) {
                        Text(globeState.flagForCountry(country))
                            .font(.system(size: 24))

                        Text(country)
                            .font(.system(size: 18, weight: .semibold, design: .rounded))
                            .foregroundColor(globeState.isDarkMode ? .white : Color(red: 0.2, green: 0.15, blue: 0.1))
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 12)
                    .background(
                        Capsule()
                            .fill(globeState.isDarkMode ? Color(red: 0.2, green: 0.2, blue: 0.25) : .white)
                            .shadow(color: .black.opacity(globeState.isDarkMode ? 0.3 : 0.08), radius: 12, y: 4)
                    )
                    .transition(.scale.combined(with: .opacity))
                } else {
                    Text("No country selected")
                        .font(.system(size: 16, weight: .medium, design: .rounded))
                        .foregroundColor(globeState.isDarkMode ? Color(red: 0.6, green: 0.6, blue: 0.65) : Color(red: 0.5, green: 0.45, blue: 0.4))
                        .transition(.opacity)
                }
            }
            .animation(.spring(response: 0.4, dampingFraction: 0.7), value: globeState.selectedCountry)

            // Stats row
            HStack(spacing: 24) {
                statItem(count: globeState.selectedCountries.count, label: "Selected")

                Divider()
                    .frame(height: 24)

                statItem(count: globeState.totalCountries, label: "Countries")
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 14)
            .background(
                RoundedRectangle(cornerRadius: 20)
                    .fill(globeState.isDarkMode ? Color(red: 0.15, green: 0.15, blue: 0.2).opacity(0.8) : .white.opacity(0.7))
            )
        }
        .padding(.horizontal, 24)
        .padding(.bottom, 32)
    }

    private func statItem(count: Int, label: String) -> some View {
        VStack(spacing: 4) {
            Text("\(count)")
                .font(.system(size: 24, weight: .bold, design: .rounded))
                .foregroundColor(globeState.isDarkMode ? Color(red: 0.6, green: 0.5, blue: 0.8) : Color(red: 0.85, green: 0.55, blue: 0.35))

            Text(label)
                .font(.system(size: 12, weight: .medium, design: .rounded))
                .foregroundColor(globeState.isDarkMode ? Color(red: 0.6, green: 0.6, blue: 0.65) : Color(red: 0.5, green: 0.45, blue: 0.4))
        }
    }
}

class GlobeState: ObservableObject {
    @Published var selectedCountry: String?
    @Published var selectedCountries: Set<String> = []
    @Published var zoomLevel: Float = 4.0
    @Published var isDarkMode: Bool = false
    let totalCountries = 195

    func selectCountry(_ name: String) {
        selectedCountry = name
        selectedCountries.insert(name)
    }

    func resetSelection() {
        selectedCountry = nil
        selectedCountries.removeAll()
    }

    func zoomIn() {
        zoomLevel = max(2.0, zoomLevel - 0.5)
    }

    func zoomOut() {
        zoomLevel = min(10.0, zoomLevel + 0.5)
    }

    // Get flag emoji for a country
    func flagForCountry(_ name: String) -> String {
        let countryToCode: [String: String] = [
            "Afghanistan": "AF", "Albania": "AL", "Algeria": "DZ", "Argentina": "AR",
            "Armenia": "AM", "Australia": "AU", "Austria": "AT", "Azerbaijan": "AZ",
            "Bahrain": "BH", "Bangladesh": "BD", "Belarus": "BY", "Belgium": "BE",
            "Belize": "BZ", "Benin": "BJ", "Bhutan": "BT", "Bolivia": "BO",
            "Bosnia": "BA", "Bosnia and Herzegovina": "BA", "Botswana": "BW",
            "Brazil": "BR", "Brunei": "BN", "Bulgaria": "BG", "Burkina Faso": "BF",
            "Burundi": "BI", "Cambodia": "KH", "Cameroon": "CM", "Canada": "CA",
            "Cape Verde": "CV", "Central African Republic": "CF", "Chad": "TD",
            "Chile": "CL", "China": "CN", "Colombia": "CO", "Comoros": "KM",
            "Costa Rica": "CR", "Croatia": "HR", "Cuba": "CU", "Cyprus": "CY",
            "Czech Republic": "CZ", "Czechia": "CZ", "Denmark": "DK", "Djibouti": "DJ",
            "Dominican Republic": "DO", "DRC": "CD", "Democratic Republic of the Congo": "CD",
            "Ecuador": "EC", "Egypt": "EG", "El Salvador": "SV", "Equatorial Guinea": "GQ",
            "Eritrea": "ER", "Estonia": "EE", "Eswatini": "SZ", "Ethiopia": "ET",
            "Fiji": "FJ", "Finland": "FI", "France": "FR", "Gabon": "GA",
            "Gambia": "GM", "Georgia": "GE", "Germany": "DE", "Ghana": "GH",
            "Greece": "GR", "Greenland": "GL", "Guatemala": "GT", "Guinea": "GN",
            "Guinea-Bissau": "GW", "Guyana": "GY", "Haiti": "HT", "Honduras": "HN",
            "Hungary": "HU", "Iceland": "IS", "India": "IN", "Indonesia": "ID",
            "Iran": "IR", "Iraq": "IQ", "Ireland": "IE", "Israel": "IL",
            "Italy": "IT", "Ivory Coast": "CI", "Côte d'Ivoire": "CI",
            "Jamaica": "JM", "Japan": "JP", "Jordan": "JO", "Kazakhstan": "KZ",
            "Kenya": "KE", "Kiribati": "KI", "Kuwait": "KW", "Kyrgyzstan": "KG",
            "Laos": "LA", "Latvia": "LV", "Lebanon": "LB", "Lesotho": "LS",
            "Liberia": "LR", "Libya": "LY", "Lithuania": "LT", "Luxembourg": "LU",
            "Madagascar": "MG", "Malawi": "MW", "Malaysia": "MY", "Maldives": "MV",
            "Mali": "ML", "Malta": "MT", "Marshall Islands": "MH", "Mauritania": "MR",
            "Mauritius": "MU", "Mexico": "MX", "Micronesia": "FM", "Moldova": "MD",
            "Mongolia": "MN", "Montenegro": "ME", "Morocco": "MA", "Mozambique": "MZ",
            "Myanmar": "MM", "Namibia": "NA", "Nauru": "NR", "Nepal": "NP",
            "Netherlands": "NL", "New Zealand": "NZ", "Nicaragua": "NI", "Niger": "NE",
            "Nigeria": "NG", "North Korea": "KP", "North Macedonia": "MK", "Norway": "NO",
            "Oman": "OM", "Pakistan": "PK", "Palau": "PW", "Panama": "PA",
            "Papua New Guinea": "PG", "Paraguay": "PY", "Peru": "PE", "Philippines": "PH",
            "Poland": "PL", "Portugal": "PT", "Qatar": "QA", "Republic of Congo": "CG",
            "Romania": "RO", "Russia": "RU", "Rwanda": "RW", "Samoa": "WS",
            "Sao Tome and Principe": "ST", "Saudi Arabia": "SA", "Senegal": "SN",
            "Serbia": "RS", "Seychelles": "SC", "Sierra Leone": "SL", "Singapore": "SG",
            "Slovakia": "SK", "Slovenia": "SI", "Solomon Islands": "SB", "Somalia": "SO",
            "South Africa": "ZA", "South Korea": "KR", "South Sudan": "SS", "Spain": "ES",
            "Sri Lanka": "LK", "Sudan": "SD", "Suriname": "SR", "Sweden": "SE",
            "Switzerland": "CH", "Syria": "SY", "Taiwan": "TW", "Tajikistan": "TJ",
            "Tanzania": "TZ", "Thailand": "TH", "Togo": "TG", "Tonga": "TO",
            "Tunisia": "TN", "Turkey": "TR", "Turkmenistan": "TM", "Tuvalu": "TV",
            "UAE": "AE", "United Arab Emirates": "AE", "Uganda": "UG", "Ukraine": "UA",
            "United Kingdom": "GB", "United States": "US", "Uruguay": "UY",
            "Uzbekistan": "UZ", "Vanuatu": "VU", "Venezuela": "VE", "Vietnam": "VN",
            "Yemen": "YE", "Zambia": "ZM", "Zimbabwe": "ZW", "Antarctica": "AQ"
        ]

        if let code = countryToCode[name] {
            return flagEmoji(from: code)
        }
        return "🌍" // Generic globe emoji as fallback
    }

    private func flagEmoji(from countryCode: String) -> String {
        let base: UInt32 = 127397
        var emoji = ""
        for scalar in countryCode.uppercased().unicodeScalars {
            if let scalar = UnicodeScalar(base + scalar.value) {
                emoji.append(Character(scalar))
            }
        }
        return emoji
    }
}

#Preview {
    ContentView()
}
