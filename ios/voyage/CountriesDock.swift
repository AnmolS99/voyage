import SwiftUI

/// Home's bottom chrome: a compact progress dock (count · bar · % · +) that
/// morphs into the selected country's card.
///
/// It is one glass surface, not two views swapping: the surface persists and
/// its padding, width and corner radius animate, so it grows upward from a
/// fixed bottom edge. The progress row persists too, shrinking into the
/// card's slim bottom row and growing back into the dock.
struct CountriesDock: View {
    @ObservedObject var globeState: GlobeState
    let onAddCountry: () -> Void
    let onExplore: () -> Void

    @Environment(\.verticalSizeClass) private var verticalSizeClass
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @GestureState(resetTransaction: Transaction(animation: .spring(response: 0.3, dampingFraction: 0.8)))
    private var dragOffset: CGFloat = 0

    private var country: String? { globeState.selectedCountry }
    /// The last selected country, kept so the card's content is still there
    /// to fold away while the card closes.
    @State private var lastCountry: String?
    private var isOpen: Bool { country != nil }
    private var isLandscape: Bool { verticalSizeClass == .compact }
    private var metrics: Metrics { isLandscape ? .landscape : .portrait }

    private var visitedCount: Int { globeState.visitedUNCountries.count }
    private var total: Int { globeState.totalUNCountries }
    private var percent: Int { Int((Double(visitedCount) / Double(total) * 100).rounded()) }

    var body: some View {
        let m = metrics
        VStack(alignment: .leading, spacing: 0) {
            // Pinned on top of the progress row: the content stays in place
            // while this container's height animates, so it unfolds upward out
            // of the progress row's top edge and folds back down into it.
            ZStack(alignment: .bottom) {
                if let shown = country ?? lastCountry {
                    cardContent(shown, m)
                        .padding(.bottom, m.cardRowSpacing)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            // Full width even while empty, so only the height animates.
            .frame(maxWidth: .infinity)
            .frame(height: isOpen ? nil : 0, alignment: .bottom)
            .clipped()
            .opacity(isOpen ? 1 : 0)
            .allowsHitTesting(isOpen)
            .accessibilityHidden(!isOpen)

            HStack(spacing: 12) {
                progress
                if !isOpen {
                    addButton(m)
                        .transition(.scale(scale: 0.6).combined(with: .opacity))
                }
            }
        }
        .padding(isOpen ? m.cardPadding : m.dockPadding)
        .frame(maxWidth: .infinity, alignment: .leading)
        .dockGlass(in: surfaceShape)
        // The whole surface owns its touches: gaps between rows must neither
        // pan the globe nor select the country beneath, and must start a swipe.
        .contentShape(surfaceShape)
        .frame(maxWidth: isOpen ? m.cardMaxWidth : m.dockMaxWidth)
        .offset(y: dragOffset)
        .gesture(swipeDownToClose, isEnabled: isOpen)
        .padding(.horizontal, 21)
        .padding(.bottom, m.bottomGap)
        // Keyed on the country, not just open/closed: switching between
        // countries whose header takes one line vs two (Chad → Central
        // African Republic) resizes the card with the same spring.
        .animation(morphAnimation, value: country)
        .onChange(of: country) { _, new in
            if let new { lastCountry = new }
        }
        .sensoryFeedback(.selection, trigger: country) { _, new in new != nil }
        .sensoryFeedback(.success, trigger: visitedCount) { old, new in new > old }
        .sensoryFeedback(.impact(weight: .light), trigger: globeState.wishlistCountries)
    }

    private var surfaceShape: RoundedRectangle {
        RoundedRectangle(cornerRadius: isOpen ? metrics.cardRadius : metrics.dockHeight / 2, style: .continuous)
    }

    // MARK: - Animation

    private var morphAnimation: Animation {
        if reduceMotion { return .easeInOut(duration: 0.25) }
        return isOpen
            ? .spring(response: 0.42, dampingFraction: 0.82)
            : .spring(response: 0.36, dampingFraction: 0.82)
    }

    private var swipeDownToClose: some Gesture {
        DragGesture(minimumDistance: 12)
            .updating($dragOffset) { value, state, _ in
                state = max(0, value.translation.height) * 0.6
            }
            .onEnded { value in
                if value.translation.height > 50 || value.predictedEndTranslation.height > 140 {
                    globeState.deselectCountry()
                }
            }
    }

    // MARK: - Progress (dock row ↔ card bottom row)

    /// One persistent row in both states: it slides to the card's bottom edge
    /// as the card grows above it, while its font sizes, bar height and
    /// spacing interpolate — so it shrinks and grows rather than crossfading
    /// between a large and a small copy.
    private var progress: some View {
        HStack(spacing: isOpen ? 9 : 12) {
            (Text("\(visitedCount)")
                .fontWeight(.semibold)
                .foregroundColor(.primary)
             + Text("/\(total)")
                .foregroundColor(.secondary))
                .animatableFont(size: isOpen ? 12 : 15)
                .monospacedDigit()
                .contentTransition(.numericText(value: Double(visitedCount)))
                .fixedSize()

            DockProgressBar(
                fraction: Double(visitedCount) / Double(total),
                ghostFraction: showsGhost ? 1 / Double(total) : 0,
                height: isOpen ? 4 : 6
            )

            Text("\(percent)%")
                .animatableFont(size: isOpen ? 12 : 14, weight: .bold)
                .foregroundColor(AppColors.buttonColor)
                .monospacedDigit()
                .contentTransition(.numericText(value: Double(percent)))
                .fixedSize()
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(visitedCount) of \(total) countries visited, \(percent) percent")
    }

    /// The card previews what Visit would add: a +1 ghost segment after the fill.
    private var showsGhost: Bool {
        guard let country else { return false }
        return !globeState.isVisited(country) && globeState.countsTowardProgress(country)
    }

    private func addButton(_ m: Metrics) -> some View {
        Button(action: onAddCountry) {
            Image(systemName: "plus")
                .font(.system(size: m.plusSize * 0.42, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: m.plusSize, height: m.plusSize)
                .background(Circle().fill(AppColors.buttonColor))
        }
        .accessibilityLabel("Add country")
    }

    // MARK: - Card

    @ViewBuilder
    private func cardContent(_ country: String, _ m: Metrics) -> some View {
        if isLandscape {
            HStack(spacing: 9) {
                countryHeader(country)
                actionButtons(country, m)
                closeButton
            }
        } else {
            VStack(spacing: m.cardRowSpacing) {
                HStack(spacing: 9) {
                    countryHeader(country)
                    closeButton
                }
                actionButtons(country, m)
            }
        }
    }

    /// Flag, name and capital. When another country is picked while the card
    /// is open, the old header fades out before the new one fades in, so the
    /// two never overlap.
    private func countryHeader(_ country: String) -> some View {
        ZStack(alignment: .leading) {
            HStack(spacing: 9) {
                Text(globeState.flagForCountry(country))
                    .font(.system(size: 24))
                    // Never squeezed out by a long name
                    .fixedSize()
                NameAndCapitalLayout {
                    countryName(country)
                        // Names too long even for their own line
                        // ("Democratic Republic of the Congo") shrink
                        // rather than truncate
                        .minimumScaleFactor(0.5)
                    capitalName(country)
                }
            }
            .id(country)
            .transition(headerSwapTransition)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }

    /// Out first, then in: the insertion waits for the removal to finish.
    private var headerSwapTransition: AnyTransition {
        let fadeOut = 0.1
        if reduceMotion {
            return .asymmetric(
                insertion: .opacity.animation(.easeOut(duration: 0.15).delay(fadeOut)),
                removal: .opacity.animation(.easeIn(duration: fadeOut))
            )
        }
        return .asymmetric(
            insertion: .opacity.combined(with: .offset(y: -4))
                .animation(.easeOut(duration: 0.2).delay(fadeOut)),
            removal: .opacity.animation(.easeIn(duration: fadeOut))
        )
    }

    private func countryName(_ country: String) -> some View {
        Text(country)
            .font(.system(size: 18, weight: .bold))
            .foregroundStyle(.primary)
            .lineLimit(1)
    }

    @ViewBuilder
    private func capitalName(_ country: String) -> some View {
        if let capital = globeState.capitalForCountry(country) {
            Text(capital)
                .font(.system(size: 15))
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
    }

    private var closeButton: some View {
        Button {
            globeState.deselectCountry()
        } label: {
            Image(systemName: "xmark")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(.red)
                .frame(width: 30, height: 30)
                .background(Circle().fill(Self.subtleFill))
        }
        .accessibilityLabel("Close")
    }

    private func actionButtons(_ country: String, _ m: Metrics) -> some View {
        let isVisited = globeState.isVisited(country)
        let isWished = globeState.isInWishlist(country)
        return HStack(spacing: m.buttonSpacing) {
            // Visit and Wish are solid orange; once toggled they take the
            // country's globe color — visited yellow, wishlist purple.
            CardActionButton(
                title: isVisited ? "Visited" : "Visit",
                symbol: isVisited ? "checkmark.circle.fill" : "plus.circle",
                tint: isVisited ? AppColors.visited : AppColors.buttonColor,
                // Dark text keeps contrast on the bright yellow
                onTint: isVisited ? .black : .white,
                metrics: m
            ) {
                withAnimation(.spring(response: 0.4, dampingFraction: 0.8)) {
                    if isVisited { globeState.removeVisit(country) } else { globeState.addVisit(country) }
                }
            }

            CardActionButton(
                title: isWished ? "Wished" : "Wish",
                symbol: isWished ? "heart.fill" : "heart",
                tint: isWished ? AppColors.wishlist : AppColors.buttonColor,
                bounceTrigger: isWished,
                metrics: m
            ) {
                withAnimation(.easeInOut(duration: 0.2)) {
                    if isWished { globeState.removeFromWishlist(country) } else { globeState.addToWishlist(country) }
                }
            }

            CardActionButton(title: "Explore", symbol: "binoculars", metrics: m, action: onExplore)
        }
    }

    /// Fill for the card's secondary buttons and close button.
    static let subtleFill = Color.primary.opacity(0.1)

    // MARK: - Metrics

    fileprivate struct Metrics {
        let dockHeight: CGFloat
        let plusSize: CGFloat
        let dockMaxWidth: CGFloat
        let cardMaxWidth: CGFloat
        let cardRadius: CGFloat
        let cardPadding: EdgeInsets
        let cardRowSpacing: CGFloat
        let buttonHeight: CGFloat
        let buttonFontSize: CGFloat
        let buttonSpacing: CGFloat
        /// Equal-width buttons in portrait; sized to fit in the landscape row.
        let buttonsFillWidth: Bool
        let bottomGap: CGFloat

        /// The + button sits this far from the dock's top, bottom and trailing edges.
        var dockPadding: EdgeInsets {
            let inset = (dockHeight - plusSize) / 2
            return EdgeInsets(top: inset, leading: 18, bottom: inset, trailing: inset)
        }

        static let portrait = Metrics(
            dockHeight: 56, plusSize: 42, dockMaxWidth: 480, cardMaxWidth: 480,
            cardRadius: 26, cardPadding: EdgeInsets(top: 11, leading: 16, bottom: 13, trailing: 11),
            cardRowSpacing: 11, buttonHeight: 36, buttonFontSize: 14, buttonSpacing: 7,
            buttonsFillWidth: true, bottomGap: 10
        )

        static let landscape = Metrics(
            dockHeight: 46, plusSize: 36, dockMaxWidth: 514, cardMaxWidth: 640,
            cardRadius: 24, cardPadding: EdgeInsets(top: 9, leading: 16, bottom: 11, trailing: 9),
            cardRowSpacing: 9, buttonHeight: 34, buttonFontSize: 13.5, buttonSpacing: 6,
            buttonsFillWidth: false, bottomGap: 8
        )
    }
}

/// Visit / Wish / Explore. With a `tint` the button is a solid capsule of
/// that color; without one it is a subtle fill with an accent symbol.
private struct CardActionButton: View {
    let title: String
    let symbol: String
    var tint: Color? = nil
    /// Symbol and text color on a solid `tint`
    var onTint: Color = .white
    var bounceTrigger = false
    let metrics: CountriesDock.Metrics
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 5) {
                Image(systemName: symbol)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(tint == nil ? AppColors.buttonColor : onTint)
                    .symbolEffect(.bounce, value: bounceTrigger)
                Text(title)
                    .font(.system(size: metrics.buttonFontSize, weight: .semibold))
                    .foregroundStyle(tint == nil ? Color.primary : onTint)
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
            }
            // Equal-width portrait buttons get just enough inset to fit "Explore"
            .padding(.horizontal, metrics.buttonsFillWidth ? 6 : 14)
            .frame(maxWidth: metrics.buttonsFillWidth ? .infinity : nil)
            .frame(height: metrics.buttonHeight)
            .background(
                Capsule().fill(tint ?? CountriesDock.subtleFill)
            )
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

/// Name and capital on one line when both fit at full size; otherwise the
/// capital drops below the name, and the name may shrink to fit its line.
private struct NameAndCapitalLayout: Layout {
    var spacing: CGFloat = 9
    var lineSpacing: CGFloat = 1

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        frames(in: proposal.width ?? .infinity, subviews).reduce(CGRect.null) { $0.union($1) }.size
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        // Decide from the same width sizeThatFits saw: bounds are the size it
        // returned, which pixel rounding can make a hair too narrow for one line.
        let width = max(bounds.width, proposal.width ?? bounds.width)
        for (subview, frame) in zip(subviews, frames(in: width, subviews)) {
            subview.place(
                at: CGPoint(x: bounds.minX + frame.minX, y: bounds.minY + frame.minY),
                proposal: ProposedViewSize(frame.size)
            )
        }
    }

    /// Frames for [name, capital?] relative to the layout's origin.
    private func frames(in width: CGFloat, _ subviews: Subviews) -> [CGRect] {
        guard let name = subviews.first else { return [] }
        let nameIdeal = name.sizeThatFits(.unspecified)
        guard subviews.count > 1 else {
            let size = name.sizeThatFits(ProposedViewSize(width: min(width, nameIdeal.width), height: nil))
            return [CGRect(origin: .zero, size: size)]
        }
        let capital = subviews[1]
        let capitalIdeal = capital.sizeThatFits(.unspecified)

        if nameIdeal.width + spacing + capitalIdeal.width <= width + 0.5 {
            // One line, baselines roughly aligned by centering vertically
            let height = max(nameIdeal.height, capitalIdeal.height)
            return [
                CGRect(x: 0, y: (height - nameIdeal.height) / 2, width: nameIdeal.width, height: nameIdeal.height),
                CGRect(x: nameIdeal.width + spacing, y: (height - capitalIdeal.height) / 2,
                       width: capitalIdeal.width, height: capitalIdeal.height),
            ]
        }
        let nameSize = name.sizeThatFits(ProposedViewSize(width: min(width, nameIdeal.width), height: nil))
        let capitalSize = capital.sizeThatFits(ProposedViewSize(width: min(width, capitalIdeal.width), height: nil))
        return [
            CGRect(origin: .zero, size: nameSize),
            CGRect(origin: CGPoint(x: 0, y: nameSize.height + lineSpacing), size: capitalSize),
        ]
    }
}

/// Capsule progress bar with an optional ghost segment previewing a +1 visit.
private struct DockProgressBar: View {
    let fraction: Double
    let ghostFraction: Double
    let height: CGFloat

    var body: some View {
        GeometryReader { geometry in
            let width = geometry.size.width
            // Never narrower than the bar is tall, so 0 visits still shows a dot.
            let fillWidth = max(height, width * fraction)
            ZStack(alignment: .leading) {
                Capsule().fill(Color.primary.opacity(0.15))
                // Always present: with no ghost it sits hidden under the fill,
                // so it grows out of and shrinks back into the fill's end
                // instead of being inserted and faded in on its own.
                Capsule()
                    .fill(AppColors.buttonColor.opacity(0.4))
                    .frame(width: min(width, fillWidth + width * ghostFraction))
                Capsule()
                    .fill(AppColors.buttonColor)
                    .frame(width: fillWidth)
            }
        }
        .frame(height: height)
    }
}

private extension View {
    /// Liquid Glass on iOS 26, a regular material before it.
    @ViewBuilder
    func dockGlass<S: Shape>(in shape: S) -> some View {
        if #available(iOS 26, *) {
            glassEffect(.regular, in: shape)
        } else {
            background(.regularMaterial, in: shape)
        }
    }

    /// A system font whose size animates frame by frame. A plain `.font`
    /// change snaps to the new size.
    func animatableFont(size: CGFloat, weight: Font.Weight = .regular) -> some View {
        modifier(AnimatableFont(size: size, weight: weight))
    }
}

private struct AnimatableFont: ViewModifier, Animatable {
    var size: CGFloat
    let weight: Font.Weight

    var animatableData: CGFloat {
        get { size }
        set { size = newValue }
    }

    func body(content: Content) -> some View {
        content.font(.system(size: size, weight: weight))
    }
}
