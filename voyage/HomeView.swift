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

struct HomeView: View {
    @ObservedObject var globeState: GlobeState
    @State private var showingCountryList = false
    @State private var showingExplore = false

    var body: some View {
        ZStack {
            // Background - starry or warm gradient based on dark mode (only for globe)
            if globeState.viewMode == .globe {
                if globeState.isDarkMode {
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

                // Bottom info panel
                bottomPanel
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
        HStack {
            Button(action: {
                withAnimation(.spring(response: 0.5, dampingFraction: 0.8)) {
                    globeState.viewMode = globeState.viewMode == .globe ? .map : .globe
                }
            }) {
                Text(globeState.viewMode == .globe ? "🗺️" : "🌍")
                    .font(.system(size: 32))
            }

            Spacer()

            // Dark mode toggle
            Button(action: {
                withAnimation(.spring(response: 0.4, dampingFraction: 0.7)) {
                    globeState.toggleDarkMode()
                }
            }) {
                Image(systemName: globeState.isDarkMode ? "sun.max.fill" : "moon.fill")
                    .font(.system(size: 18, weight: .medium))
                    .foregroundColor(.white)
                    .frame(width: 44, height: 44)
                    .background(
                        Circle()
                            .fill(AppColors.buttonColor(isDarkMode: globeState.isDarkMode))
                    )
                    .shadow(color: AppColors.buttonColor(isDarkMode: globeState.isDarkMode).opacity(0.4), radius: 8, y: 4)
            }

            // Add Country button (plus)
            Button(action: {
                showingCountryList = true
            }) {
                Image(systemName: "plus")
                    .font(.system(size: 18, weight: .medium))
                    .foregroundColor(.white)
                    .frame(width: 44, height: 44)
                    .background(
                        Circle()
                            .fill(AppColors.buttonColor(isDarkMode: globeState.isDarkMode))
                    )
                    .shadow(color: AppColors.buttonColor(isDarkMode: globeState.isDarkMode).opacity(0.4), radius: 8, y: 4)
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
                            .foregroundColor(AppColors.textPrimary(isDarkMode: globeState.isDarkMode))
                    }
                    .frame(maxWidth: .infinity)
                    .overlay(alignment: .trailing) {
                        // Close button
                        Button(action: {
                            withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                                globeState.deselectCountry()
                            }
                        }) {
                            Image(systemName: "xmark")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundColor(globeState.isDarkMode ? .white : AppColors.closeButtonText)
                                .frame(width: 36, height: 36)
                                .background(
                                    Circle()
                                        .fill(globeState.isDarkMode ? AppColors.closeButtonDark : AppColors.closeButtonLight)
                                )
                        }
                    }

                    HStack(spacing: 6) {
                        // Add/Remove Visit button
                        Button(action: {
                                if globeState.isVisited(country) {
                                    globeState.removeVisit(country)
                                } else {
                                    globeState.addVisit(country)
                                }
                        }) {
                            ZStack {
                                HStack(spacing: 4) {
                                    Image(systemName: "checkmark.circle.fill")
                                        .font(.system(size: 14, weight: .medium))
                                    Text("Visited")
                                        .font(.system(size: 13, weight: .semibold, design: .rounded))
                                }
                                .hidden()

                                HStack(spacing: 4) {
                                    Image(systemName: globeState.isVisited(country) ? "checkmark.circle.fill" : "plus.circle")
                                        .font(.system(size: 14, weight: .medium))
                                    Text(globeState.isVisited(country) ? "Visited" : "Visit")
                                        .font(.system(size: 13, weight: .semibold, design: .rounded))
                                }
                            }
                            .foregroundColor(.white)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(
                                Capsule()
                                    .fill(globeState.isVisited(country) ?
                                          AppColors.buttonVisited :
                                          AppColors.buttonColor(isDarkMode: globeState.isDarkMode))
                            )
                            .animation(nil, value: globeState.visitedCountries)
                        }

                        // Add/Remove Wishlist button
                        Button(action: {
                                if globeState.isInWishlist(country) {
                                    globeState.removeFromWishlist(country)
                                } else {
                                    globeState.addToWishlist(country)
                                }
                        }) {
                            ZStack {
                                HStack(spacing: 4) {
                                    Image(systemName: "heart.fill")
                                        .font(.system(size: 14, weight: .medium))
                                    Text("Wishlist")
                                        .font(.system(size: 13, weight: .semibold, design: .rounded))
                                }
                                .hidden()

                                HStack(spacing: 4) {
                                    Image(systemName: globeState.isInWishlist(country) ? "heart.fill" : "heart")
                                        .font(.system(size: 14, weight: .medium))
                                    Text(globeState.isInWishlist(country) ? "Wishlist" : "Wish")
                                        .font(.system(size: 13, weight: .semibold, design: .rounded))
                                }
                            }
                            .foregroundColor(.white)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(
                                Capsule()
                                    .fill(globeState.isInWishlist(country) ?
                                          AppColors.wishlist :
                                          AppColors.buttonColor(isDarkMode: globeState.isDarkMode))
                            )
                            .animation(nil, value: globeState.wishlistCountries)
                        }

                        // Explore button
                        Button(action: {
                            showingExplore = true
                        }) {
                            HStack(spacing: 4) {
                                Image(systemName: "binoculars.fill")
                                    .font(.system(size: 14, weight: .medium))
                                Text("Explore")
                                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                            }
                            .foregroundColor(.white)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(
                                Capsule()
                                    .fill(AppColors.buttonColor(isDarkMode: globeState.isDarkMode))
                            )
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 16)
                .background(
                    RoundedRectangle(cornerRadius: 20)
                        .fill(AppColors.cardBackground(isDarkMode: globeState.isDarkMode))
                        .shadow(color: .black.opacity(globeState.isDarkMode ? 0.3 : 0.08), radius: 12, y: 4)
                )
                .transition(.scale.combined(with: .opacity))
            }

            // Progress bar (hidden when a country is selected)
            if globeState.selectedCountry == nil {
            VStack(spacing: 8) {
                HStack {
                    Text("\(globeState.visitedUNCountries.count) of \(globeState.totalUNCountries) countries")
                        .font(.system(size: 14, weight: .medium, design: .rounded))
                        .foregroundColor(AppColors.textSecondary(isDarkMode: globeState.isDarkMode))

                    Spacer()

                    Text("\(Int(Double(globeState.visitedUNCountries.count) / Double(globeState.totalUNCountries) * 100))%")
                        .font(.system(size: 14, weight: .semibold, design: .rounded))
                        .foregroundColor(globeState.isDarkMode ? AppColors.progressDarkStart : AppColors.buttonLight)
                }

                GeometryReader { geometry in
                    ZStack(alignment: .leading) {
                        // Background track
                        RoundedRectangle(cornerRadius: 6)
                            .fill(AppColors.track(isDarkMode: globeState.isDarkMode))

                        // Progress fill
                        RoundedRectangle(cornerRadius: 6)
                            .fill(
                                LinearGradient(
                                    colors: globeState.isDarkMode ?
                                        [AppColors.progressDarkStart, AppColors.progressDarkEnd] :
                                        [AppColors.progressLightStart, AppColors.progressLightEnd],
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            )
                            .frame(width: max(0, geometry.size.width * CGFloat(globeState.visitedUNCountries.count) / CGFloat(globeState.totalUNCountries)))
                            .animation(.spring(response: 0.4, dampingFraction: 0.8), value: globeState.visitedUNCountries.count)
                    }
                }
                .frame(height: 12)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 14)
            .background(
                RoundedRectangle(cornerRadius: 20)
                    .fill(globeState.isDarkMode ? AppColors.cardDarkSecondary.opacity(0.8) : .white.opacity(0.7))
            )
            .transition(.opacity)
            }
        }
        .padding(.horizontal, 24)
        .padding(.bottom, 32)
        .animation(.spring(response: 0.4, dampingFraction: 0.7), value: globeState.selectedCountry != nil)
    }
}
