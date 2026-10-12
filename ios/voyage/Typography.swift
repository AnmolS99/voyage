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
