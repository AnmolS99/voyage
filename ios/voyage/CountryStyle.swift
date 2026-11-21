import UIKit

/// How one country is drawn, on the globe and on the map alike: which paint its
/// fill takes and which its border.
///
/// This is the one place the priority rules live (see "Globe and Map
/// Consistency" in CLAUDE.md): an override beats visited and wishlist, and a
/// selected country gives its paint up to its border and shows plain land
/// inside. Android's `ui/map/CountryStyle.kt` holds the same rules.
///
/// It is `Equatable` so a renderer can remember the style it last applied to a
/// country and skip the ones that did not change.
struct CountryStyle: Equatable {
    enum Paint: Equatable {
        /// Plain land for a fill (nothing at all over an Earth texture), black
        /// for a border.
        case plain
        case visited
        case wishlist
        /// Visited and on the wishlist: the yellow-to-purple gradient.
        case visitedAndWishlist
        /// A color a challenge game gives a country (`GlobeState.countryHighlightColors`).
        case custom(UIColor)
    }

    let fill: Paint
    let border: Paint
    let isSelected: Bool

    init(isVisited: Bool, isWishlist: Bool, isSelected: Bool, override: UIColor? = nil) {
        let status: Paint
        if let override {
            status = .custom(override)
        } else if isVisited && isWishlist {
            status = .visitedAndWishlist
        } else if isVisited {
            status = .visited
        } else if isWishlist {
            status = .wishlist
        } else {
            status = .plain
        }

        self.fill = isSelected ? .plain : status
        self.border = isSelected ? status : .plain
        self.isSelected = isSelected
    }
}
