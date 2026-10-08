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

/// Backdrop behind the 3D globe: starry sky in dark mode, warm gradient in light.
struct GlobeBackdrop: View {
    let isDarkMode: Bool

    var body: some View {
        if isDarkMode {
            StarryBackground()
        } else {
            LinearGradient(
                colors: [AppColors.backgroundLightTop, AppColors.backgroundLightBottom],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()
        }
    }
}

struct HomeView: View {
    @ObservedObject var globeState: GlobeState
    @State private var showingCountryList = false
    @State private var showingExplore = false

    var body: some View {
        ZStack {
            // Background - starry or warm gradient based on dark mode (only for globe)
            if globeState.viewMode == .globe {
                GlobeBackdrop(isDarkMode: globeState.isDarkMode)
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

                CountriesDock(
                    globeState: globeState,
                    onAddCountry: { showingCountryList = true },
                    onExplore: { showingExplore = true }
                )
            }
        }
        .animation(.easeInOut(duration: 0.3), value: globeState.viewMode)
        .onChange(of: globeState.viewMode) { _, newMode in
            if newMode == .map {
                OrientationManager.shared.lockToLandscape()
            } else {
                OrientationManager.shared.unlock()
                OrientationManager.shared.setNeedsOrientationUpdate()
            }
        }
        .sheet(isPresented: $showingCountryList) {
            CountryListView(globeState: globeState)
        }
        .sheet(isPresented: $showingExplore) {
            if let country = globeState.selectedCountry {
                CountryExploreView(globeState: globeState, countryName: country)
            }
        }
    }

    private var header: some View {
        HStack(alignment: .top) {
            if #available(iOS 26, *) {
                // Map/Globe toggle — standalone circle
                GlassEffectContainer {
                    Button(action: {
                        withAnimation(.spring(response: 0.5, dampingFraction: 0.8)) {
                            globeState.viewMode = globeState.viewMode == .globe ? .map : .globe
                        }
                    }) {
                        Image(systemName: globeState.viewMode == .globe ? "map" : "globe.europe.africa.fill")
                            .font(.system(size: 17, weight: .medium))
                            .frame(width: 44, height: 44)
                    }
                    .buttonStyle(.glass)
                    .buttonBorderShape(.circle)
                }
            } else {
                legacyToggleButton
            }

            Spacer()

            if #available(iOS 26, *) {
                GlassEffectContainer {
                    Button(action: {
                        withAnimation(.spring(response: 0.4, dampingFraction: 0.7)) {
                            globeState.toggleDarkMode()
                        }
                    }) {
                        Image(systemName: themeToggleSymbol)
                            .font(.system(size: 17, weight: .medium))
                            .frame(width: 44, height: 44)
                    }
                    .buttonStyle(.glass)
                    .buttonBorderShape(.circle)
                    .accessibilityLabel(globeState.isDarkMode ? "Light mode" : "Dark mode")
                }
            } else {
                legacyThemeToggle
            }
        }
        .padding(.horizontal, 24)
        .padding(.top, 16)
        .padding(.bottom, 12)
        .tint(nil)
    }

    // Fallbacks for iOS < 26
    private var legacyToggleButton: some View {
        Button(action: {
            withAnimation(.spring(response: 0.5, dampingFraction: 0.8)) {
                globeState.viewMode = globeState.viewMode == .globe ? .map : .globe
            }
        }) {
            Image(systemName: globeState.viewMode == .globe ? "map" : "globe.europe.africa.fill")
                .font(.system(size: 18, weight: .medium))
                .foregroundColor(.white)
                .frame(width: 44, height: 44)
                .background(Circle().fill(AppColors.buttonColor))
                .shadow(color: AppColors.buttonColor.opacity(0.4), radius: 8, y: 4)
        }
    }

    private var legacyThemeToggle: some View {
        Button(action: {
            withAnimation(.spring(response: 0.4, dampingFraction: 0.7)) {
                globeState.toggleDarkMode()
            }
        }) {
            Image(systemName: themeToggleSymbol)
                .font(.system(size: 18, weight: .medium))
                .foregroundColor(.white)
                .frame(width: 44, height: 44)
                .background(Circle().fill(AppColors.buttonColor))
                .shadow(color: AppColors.buttonColor.opacity(0.4), radius: 8, y: 4)
        }
    }

    private var themeToggleSymbol: String {
        globeState.isDarkMode ? "sun.max" : "moon"
    }
}
