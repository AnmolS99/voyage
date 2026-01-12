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

struct LoadingView: View {
    @State private var rotationAngle: Double = 0
    @State private var pulseScale: CGFloat = 1.0
    let isDarkMode: Bool

    var body: some View {
        ZStack {
            // Background
            if isDarkMode {
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

            VStack(spacing: 24) {
                // Rotating globe icon with pulse effect
                ZStack {
                    // Outer glow ring
                    Circle()
                        .stroke(
                            LinearGradient(
                                colors: isDarkMode ?
                                    [Color(red: 0.5, green: 0.4, blue: 0.8).opacity(0.3),
                                     Color(red: 0.6, green: 0.5, blue: 0.9).opacity(0.3)] :
                                    [Color(red: 0.85, green: 0.5, blue: 0.3).opacity(0.3),
                                     Color(red: 0.95, green: 0.6, blue: 0.4).opacity(0.3)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            ),
                            lineWidth: 3
                        )
                        .frame(width: 120, height: 120)
                        .scaleEffect(pulseScale)
                        .opacity(2 - pulseScale)

                    // Main globe icon
                    Image(systemName: "globe.americas.fill")
                        .font(.system(size: 70, weight: .light))
                        .foregroundStyle(
                            LinearGradient(
                                colors: isDarkMode ?
                                    [Color(red: 0.5, green: 0.4, blue: 0.8),
                                     Color(red: 0.6, green: 0.5, blue: 0.9)] :
                                    [Color(red: 0.85, green: 0.5, blue: 0.3),
                                     Color(red: 0.95, green: 0.6, blue: 0.4)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .rotationEffect(.degrees(rotationAngle))
                        .shadow(
                            color: (isDarkMode ?
                                    Color(red: 0.5, green: 0.4, blue: 0.8) :
                                    Color(red: 0.85, green: 0.5, blue: 0.3)).opacity(0.5),
                            radius: 20
                        )
                }

                VStack(spacing: 8) {
                    Text("Loading Globe")
                        .font(.system(size: 24, weight: .semibold, design: .rounded))
                        .foregroundColor(isDarkMode ? .white : Color(red: 0.2, green: 0.15, blue: 0.1))

                    Text("Preparing your world exploration...")
                        .font(.system(size: 14, weight: .medium, design: .rounded))
                        .foregroundColor(isDarkMode ? Color(red: 0.7, green: 0.7, blue: 0.75) : Color(red: 0.5, green: 0.45, blue: 0.4))
                        .multilineTextAlignment(.center)
                }
            }
        }
        .onAppear {
            // Continuous rotation animation
            withAnimation(.linear(duration: 3).repeatForever(autoreverses: false)) {
                rotationAngle = 360
            }

            // Pulse animation
            withAnimation(.easeInOut(duration: 1.5).repeatForever(autoreverses: true)) {
                pulseScale = 1.3
            }
        }
    }
}

struct ContentView: View {
    @StateObject private var globeState = GlobeState()
    @State private var showingSettings = false

    var body: some View {
        ZStack {
            // Background - starry or warm gradient based on dark mode (only for globe)
            if globeState.viewMode == .globe {
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
            }

            // Globe or Map view - fullscreen
            GlobeView(globeState: globeState)
                .ignoresSafeArea()
                .opacity(globeState.viewMode == .globe ? 1 : 0)
                .allowsHitTesting(globeState.viewMode == .globe)

            MapView(globeState: globeState)
                .ignoresSafeArea()
                .opacity(globeState.viewMode == .map ? 1 : 0)
                .allowsHitTesting(globeState.viewMode == .map)

            // UI Overlay
            VStack {
                // Header at top
                header

                Spacer()

                // Zoom controls on the right (only for globe view)
                if globeState.viewMode == .globe {
                    HStack {
                        Spacer()
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
                    }
                }

                Spacer()

                // Bottom info panel
                bottomPanel
            }

            // Loading overlay
            if globeState.isLoading {
                LoadingView(isDarkMode: globeState.isDarkMode)
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.3), value: globeState.viewMode)
        .animation(.easeInOut(duration: 0.5), value: globeState.isLoading)
        .preferredColorScheme(globeState.isDarkMode ? .dark : .light)
        .onChange(of: globeState.viewMode) { _, newMode in
            if newMode == .map {
                OrientationManager.shared.lockToLandscape()
            } else {
                OrientationManager.shared.unlock()
                OrientationManager.shared.setNeedsOrientationUpdate()
            }
        }
        .sheet(isPresented: $showingSettings) {
            SettingsView(globeState: globeState)
        }
    }

    private var header: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(globeState.viewMode == .globe ? "Globe Explorer" : "World Map")
                    .font(.system(size: 28, weight: .semibold, design: .rounded))
                    .foregroundColor(globeState.isDarkMode ? .white : Color(red: 0.2, green: 0.15, blue: 0.1))

                Text("Tap any country to explore")
                    .font(.system(size: 14, weight: .medium, design: .rounded))
                    .foregroundColor(globeState.isDarkMode ? Color(red: 0.7, green: 0.7, blue: 0.75) : Color(red: 0.5, green: 0.45, blue: 0.4))
            }

            Spacer()

            // View mode toggle (Globe/Map)
            Button(action: {
                withAnimation(.spring(response: 0.5, dampingFraction: 0.8)) {
                    globeState.viewMode = globeState.viewMode == .globe ? .map : .globe
                }
            }) {
                Image(systemName: globeState.viewMode == .globe ? "map" : "globe.americas")
                    .font(.system(size: 18, weight: .medium))
                    .foregroundColor(.white)
                    .frame(width: 44, height: 44)
                    .background(
                        Circle()
                            .fill(globeState.isDarkMode ? Color(red: 0.4, green: 0.35, blue: 0.6) : Color(red: 0.85, green: 0.55, blue: 0.35))
                    )
                    .shadow(color: (globeState.isDarkMode ? Color(red: 0.4, green: 0.35, blue: 0.6) : Color(red: 0.85, green: 0.55, blue: 0.35)).opacity(0.4), radius: 8, y: 4)
            }

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

            // Settings button
            Button(action: {
                showingSettings = true
            }) {
                Image(systemName: "gearshape.fill")
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
            // Selected country display with Add Visit button
            if let country = globeState.selectedCountry {
                VStack(spacing: 12) {
                    HStack(spacing: 10) {
                        Text(globeState.flagForCountry(country))
                            .font(.system(size: 24))

                        Text(country)
                            .font(.system(size: 18, weight: .semibold, design: .rounded))
                            .foregroundColor(globeState.isDarkMode ? .white : Color(red: 0.2, green: 0.15, blue: 0.1))
                    }

                    HStack(spacing: 12) {
                        // Add/Remove Visit button
                        Button(action: {
                            withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                                if globeState.isVisited(country) {
                                    globeState.removeVisit(country)
                                } else {
                                    globeState.addVisit(country)
                                }
                            }
                        }) {
                            HStack(spacing: 6) {
                                Image(systemName: globeState.isVisited(country) ? "checkmark.circle.fill" : "plus.circle")
                                    .font(.system(size: 16, weight: .medium))
                                Text(globeState.isVisited(country) ? "Visited" : "Add Visit")
                                    .font(.system(size: 14, weight: .semibold, design: .rounded))
                            }
                            .foregroundColor(.white)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(
                                Capsule()
                                    .fill(globeState.isVisited(country) ?
                                          Color(red: 0.3, green: 0.7, blue: 0.4) :
                                          (globeState.isDarkMode ? Color(red: 0.4, green: 0.35, blue: 0.6) : Color(red: 0.85, green: 0.55, blue: 0.35)))
                            )
                        }

                        // Close button
                        Button(action: {
                            withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                                globeState.deselectCountry()
                            }
                        }) {
                            Image(systemName: "xmark")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundColor(globeState.isDarkMode ? .white : Color(red: 0.3, green: 0.3, blue: 0.3))
                                .frame(width: 36, height: 36)
                                .background(
                                    Circle()
                                        .fill(globeState.isDarkMode ? Color(red: 0.3, green: 0.3, blue: 0.35) : Color(red: 0.9, green: 0.9, blue: 0.9))
                                )
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 16)
                .background(
                    RoundedRectangle(cornerRadius: 20)
                        .fill(globeState.isDarkMode ? Color(red: 0.2, green: 0.2, blue: 0.25) : .white)
                        .shadow(color: .black.opacity(globeState.isDarkMode ? 0.3 : 0.08), radius: 12, y: 4)
                )
                .transition(.scale.combined(with: .opacity))
            }

            // Progress bar
            VStack(spacing: 8) {
                HStack {
                    Text("\(globeState.visitedCountries.count) of \(globeState.totalCountries) countries")
                        .font(.system(size: 14, weight: .medium, design: .rounded))
                        .foregroundColor(globeState.isDarkMode ? Color(red: 0.7, green: 0.7, blue: 0.75) : Color(red: 0.4, green: 0.35, blue: 0.3))

                    Spacer()

                    Text("\(Int(Double(globeState.visitedCountries.count) / Double(globeState.totalCountries) * 100))%")
                        .font(.system(size: 14, weight: .semibold, design: .rounded))
                        .foregroundColor(globeState.isDarkMode ? Color(red: 0.6, green: 0.5, blue: 0.8) : Color(red: 0.85, green: 0.55, blue: 0.35))
                }

                GeometryReader { geometry in
                    ZStack(alignment: .leading) {
                        // Background track
                        RoundedRectangle(cornerRadius: 6)
                            .fill(globeState.isDarkMode ? Color(red: 0.25, green: 0.25, blue: 0.3) : Color(red: 0.9, green: 0.88, blue: 0.85))

                        // Progress fill
                        RoundedRectangle(cornerRadius: 6)
                            .fill(
                                LinearGradient(
                                    colors: globeState.isDarkMode ?
                                        [Color(red: 0.5, green: 0.4, blue: 0.8), Color(red: 0.6, green: 0.5, blue: 0.9)] :
                                        [Color(red: 0.85, green: 0.5, blue: 0.3), Color(red: 0.95, green: 0.6, blue: 0.4)],
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            )
                            .frame(width: max(0, geometry.size.width * CGFloat(globeState.visitedCountries.count) / CGFloat(globeState.totalCountries)))
                            .animation(.spring(response: 0.4, dampingFraction: 0.8), value: globeState.visitedCountries.count)
                    }
                }
                .frame(height: 12)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 14)
            .background(
                RoundedRectangle(cornerRadius: 20)
                    .fill(globeState.isDarkMode ? Color(red: 0.15, green: 0.15, blue: 0.2).opacity(0.8) : .white.opacity(0.7))
            )
        }
        .padding(.horizontal, 24)
        .padding(.bottom, 32)
        .animation(.spring(response: 0.4, dampingFraction: 0.7), value: globeState.selectedCountry)
    }

}

enum ViewMode {
    case globe
    case map
}

class GlobeState: ObservableObject {
    @Published var selectedCountry: String?
    @Published var selectedCountries: Set<String> = []
    @Published var visitedCountries: Set<String> = []
    @Published var zoomLevel: Float = 4.0
    @Published var isDarkMode: Bool = false
    @Published var isAutoRotating: Bool = true
    @Published var targetCountryCenter: (lat: Double, lon: Double)?
    @Published var viewMode: ViewMode = .globe
    @Published var isLoading: Bool = true
    let totalCountries = 195

    func selectCountry(_ name: String, center: (lat: Double, lon: Double)? = nil) {
        selectedCountry = name
        selectedCountries.insert(name)
        isAutoRotating = false
        targetCountryCenter = center
    }

    func addVisit(_ name: String) {
        visitedCountries.insert(name)
    }

    func removeVisit(_ name: String) {
        visitedCountries.remove(name)
    }

    func isVisited(_ name: String) -> Bool {
        visitedCountries.contains(name)
    }

    func deselectCountry() {
        selectedCountry = nil
        targetCountryCenter = nil
        isAutoRotating = true
    }

    func resetSelection() {
        selectedCountry = nil
        selectedCountries.removeAll()
        targetCountryCenter = nil
        isAutoRotating = true
    }

    func resetAllData() {
        selectedCountry = nil
        selectedCountries.removeAll()
        visitedCountries.removeAll()
        targetCountryCenter = nil
        isAutoRotating = true
    }

    func zoomIn() {
        zoomLevel = max(1.2, zoomLevel - 0.5)
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
            "United Kingdom": "GB", "United States": "US", "United States of America": "US", "Uruguay": "UY",
            "Uzbekistan": "UZ", "Vanuatu": "VU", "Venezuela": "VE", "Vietnam": "VN",
            "Palestine": "PS",
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
