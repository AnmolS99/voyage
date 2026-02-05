import SwiftUI
import UIKit

/// Centralized color palette for the app
/// All colors are defined once here and referenced throughout the codebase
enum AppColors {
    // MARK: - Map/Globe Colors

    /// Ocean blue - #2F86A6
    static let ocean = Color(red: 0.184, green: 0.525, blue: 0.651)
    static let oceanUI = UIColor(red: 0.184, green: 0.525, blue: 0.651, alpha: 1.0)

    /// Land green - #34BE82
    static let land = Color(red: 0.204, green: 0.745, blue: 0.510)
    static let landUI = UIColor(red: 0.204, green: 0.745, blue: 0.510, alpha: 1.0)

    /// Land when selected (brighter green)
    static let landSelected = Color(red: 0.45, green: 0.85, blue: 0.60)
    static let landSelectedUI = UIColor(red: 0.45, green: 0.85, blue: 0.60, alpha: 1.0)

    /// Visited yellow - #F2F013
    static let visited = Color(red: 0.949, green: 0.941, blue: 0.075)
    static let visitedUI = UIColor(red: 0.949, green: 0.941, blue: 0.075, alpha: 1.0)

    /// Visited when selected (brighter yellow)
    static let visitedSelected = Color(red: 1.0, green: 1.0, blue: 0.3)
    static let visitedSelectedUI = UIColor(red: 1.0, green: 1.0, blue: 0.3, alpha: 1.0)

    /// Wishlist purple
    static let wishlist = Color(red: 0.6, green: 0.4, blue: 0.8)
    static let wishlistUI = UIColor(red: 0.6, green: 0.4, blue: 0.8, alpha: 1.0)

    /// Wishlist when selected (brighter purple)
    static let wishlistSelected = Color(red: 0.75, green: 0.55, blue: 0.95)
    static let wishlistSelectedUI = UIColor(red: 0.75, green: 0.55, blue: 0.95, alpha: 1.0)

    /// Atmosphere glow
    static let atmosphere = UIColor(red: 0.6, green: 0.8, blue: 1.0, alpha: 0.15)

    /// Dark mode ocean
    static let oceanDark = Color(red: 0.1, green: 0.15, blue: 0.25)

    // MARK: - UI Button Colors

    /// Primary button color (light mode) - warm orange
    static let buttonLight = Color(red: 0.85, green: 0.55, blue: 0.35)

    /// Primary button color (dark mode) - muted purple
    static let buttonDark = Color(red: 0.4, green: 0.35, blue: 0.6)

    /// Visited/success button green
    static let buttonVisited = Color(red: 0.3, green: 0.7, blue: 0.4)

    // MARK: - UI Background Colors

    /// Light mode warm gradient top
    static let backgroundLightTop = Color(red: 0.98, green: 0.96, blue: 0.93)

    /// Light mode warm gradient bottom
    static let backgroundLightBottom = Color(red: 0.95, green: 0.91, blue: 0.87)

    /// Dark mode card/panel background
    static let cardDark = Color(red: 0.2, green: 0.2, blue: 0.25)

    /// Dark mode secondary background
    static let cardDarkSecondary = Color(red: 0.15, green: 0.15, blue: 0.2)

    /// Light mode track/divider
    static let trackLight = Color(red: 0.9, green: 0.88, blue: 0.85)

    /// Dark mode track/divider
    static let trackDark = Color(red: 0.25, green: 0.25, blue: 0.3)

    /// Close button background (dark mode)
    static let closeButtonDark = Color(red: 0.3, green: 0.3, blue: 0.35)

    /// Close button background (light mode)
    static let closeButtonLight = Color(red: 0.9, green: 0.9, blue: 0.9)

    // MARK: - UI Text Colors

    /// Primary text (light mode) - warm brown
    static let textPrimaryLight = Color(red: 0.2, green: 0.15, blue: 0.1)

    /// Secondary text (light mode)
    static let textSecondaryLight = Color(red: 0.4, green: 0.35, blue: 0.3)

    /// Tertiary text (light mode)
    static let textTertiaryLight = Color(red: 0.5, green: 0.45, blue: 0.4)

    /// Muted text (light mode)
    static let textMutedLight = Color(red: 0.6, green: 0.55, blue: 0.5)

    /// Secondary text (dark mode)
    static let textSecondaryDark = Color(red: 0.7, green: 0.7, blue: 0.75)

    /// Tertiary text (dark mode)
    static let textTertiaryDark = Color(red: 0.6, green: 0.6, blue: 0.65)

    /// Muted text (dark mode)
    static let textMutedDark = Color(red: 0.5, green: 0.5, blue: 0.55)

    /// Close button text (light mode)
    static let closeButtonText = Color(red: 0.3, green: 0.3, blue: 0.3)

    // MARK: - Progress Bar Colors

    /// Progress gradient (dark mode)
    static let progressDarkStart = Color(red: 0.5, green: 0.4, blue: 0.8)
    static let progressDarkEnd = Color(red: 0.6, green: 0.5, blue: 0.9)

    /// Progress gradient (light mode)
    static let progressLightStart = Color(red: 0.85, green: 0.5, blue: 0.3)
    static let progressLightEnd = Color(red: 0.95, green: 0.6, blue: 0.4)

    // MARK: - Page Background Colors

    /// Page background (dark mode)
    static let pageBgDark = Color(red: 0.1, green: 0.1, blue: 0.12)

    /// Page background (light mode)
    static let pageBgLight = Color(red: 0.96, green: 0.95, blue: 0.93)

    /// Badge text (dark mode)
    static let badgeTextDark = Color(red: 0.8, green: 0.8, blue: 0.85)

    /// Badge text (light mode)
    static let badgeTextLight = Color(red: 0.3, green: 0.25, blue: 0.2)

    // MARK: - Helper Functions

    /// Returns the appropriate button color based on dark mode
    static func buttonColor(isDarkMode: Bool) -> Color {
        isDarkMode ? buttonDark : buttonLight
    }

    /// Returns the appropriate text color based on dark mode
    static func textPrimary(isDarkMode: Bool) -> Color {
        isDarkMode ? .white : textPrimaryLight
    }

    /// Returns the appropriate secondary text color based on dark mode
    static func textSecondary(isDarkMode: Bool) -> Color {
        isDarkMode ? textSecondaryDark : textSecondaryLight
    }

    /// Returns the appropriate tertiary text color based on dark mode
    static func textTertiary(isDarkMode: Bool) -> Color {
        isDarkMode ? textTertiaryDark : textTertiaryLight
    }

    /// Returns the appropriate muted text color based on dark mode
    static func textMuted(isDarkMode: Bool) -> Color {
        isDarkMode ? textMutedDark : textMutedLight
    }

    /// Returns the appropriate track color based on dark mode
    static func track(isDarkMode: Bool) -> Color {
        isDarkMode ? trackDark : trackLight
    }

    /// Returns the appropriate card background based on dark mode
    static func cardBackground(isDarkMode: Bool) -> Color {
        isDarkMode ? cardDark : .white
    }

    /// Returns the appropriate page background based on dark mode
    static func pageBackground(isDarkMode: Bool) -> Color {
        isDarkMode ? pageBgDark : pageBgLight
    }
}
