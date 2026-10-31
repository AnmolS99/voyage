import SwiftUI
import UIKit

/// Voyage's two typefaces, bundled from `shared/data/fonts/` (SIL OFL) and
/// mirrored by Android's `VoyageFonts`:
/// - **Newsreader** (display) — country names, titles, prompts, celebrations.
/// - **Schibsted Grotesk** (text) — buttons, labels, numbers and body copy.
///
/// Sizes are fixed, like the `.system(size:)` fonts they replace. SF Symbols
/// and emoji stay on the system font.
enum AppFonts {
    /// Newsreader at a static weight. It ships Medium and SemiBold only —
    /// bolder weights resolve to SemiBold, as the design system never sets
    /// Newsreader at 700.
    static func displayName(for weight: Font.Weight) -> String {
        switch weight {
        case .semibold, .bold, .heavy, .black: return "Newsreader-SemiBold"
        default: return "Newsreader-Medium"
        }
    }

    /// Schibsted Grotesk at a static weight: Regular, Medium, SemiBold, Bold.
    static func textName(for weight: Font.Weight) -> String {
        switch weight {
        case .medium: return "SchibstedGrotesk-Medium"
        case .semibold: return "SchibstedGrotesk-SemiBold"
        case .bold, .heavy, .black: return "SchibstedGrotesk-Bold"
        default: return "SchibstedGrotesk-Regular"
        }
    }

    /// Newsreader's cap height as a share of its line height — the same at any
    /// size, so it holds for text scaled down to fit.
    static let displayCapHeightOfLine: CGFloat = {
        guard let font = UIFont(name: displayName(for: .medium), size: 100) else { return 0.67 }
        return font.capHeight / font.lineHeight
    }()

    /// Navigation-bar titles are display type, set once at launch.
    static func configureAppearance() {
        let appearance = UINavigationBar.appearance()
        if let large = UIFont(name: displayName(for: .semibold), size: 34) {
            appearance.largeTitleTextAttributes = [.font: large]
        }
        if let inline = UIFont(name: displayName(for: .semibold), size: 18) {
            appearance.titleTextAttributes = [.font: inline]
        }
    }
}

extension Font {
    /// Newsreader — titles, country names, prompts, celebrations.
    static func voyageDisplay(_ size: CGFloat, weight: Font.Weight = .semibold) -> Font {
        .custom(AppFonts.displayName(for: weight), fixedSize: size)
    }

    /// Schibsted Grotesk — everything you tap or read that isn't a title.
    static func voyageText(_ size: CGFloat, weight: Font.Weight = .regular) -> Font {
        .custom(AppFonts.textName(for: weight), fixedSize: size)
    }
}

extension View {
    /// Centres this Newsreader text in an `HStack` on the middle of its
    /// capitals rather than its line box, whose room below the baseline
    /// otherwise lifts it above a flag beside it — Android `DisplayCapitalsCentre`.
    func centredOnDisplayCapitals() -> some View {
        alignmentGuide(VerticalAlignment.center) { d in
            d[.firstTextBaseline] - d.height * AppFonts.displayCapHeightOfLine / 2
        }
    }
}
