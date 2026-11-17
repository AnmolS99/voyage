import XCTest
import UIKit
@testable import voyage

/// Pins the priority rules the globe and the map both draw from.
final class CountryStyleTests: XCTestCase {

    func testAnUnselectedCountryPaintsItsFillAndKeepsABlackBorder() {
        let cases: [(visited: Bool, wishlist: Bool, fill: CountryStyle.Paint)] = [
            (false, false, .plain),
            (true, false, .visited),
            (false, true, .wishlist),
            (true, true, .visitedAndWishlist),
        ]
        for testCase in cases {
            let style = CountryStyle(isVisited: testCase.visited, isWishlist: testCase.wishlist, isSelected: false)
            XCTAssertEqual(style.fill, testCase.fill)
            XCTAssertEqual(style.border, .plain)
        }
    }

    func testASelectedCountryMovesItsPaintToTheBorder() {
        let cases: [(visited: Bool, wishlist: Bool, border: CountryStyle.Paint)] = [
            (false, false, .plain),
            (true, false, .visited),
            (false, true, .wishlist),
            (true, true, .visitedAndWishlist),
        ]
        for testCase in cases {
            let style = CountryStyle(isVisited: testCase.visited, isWishlist: testCase.wishlist, isSelected: true)
            XCTAssertEqual(style.fill, .plain)
            XCTAssertEqual(style.border, testCase.border)
        }
    }

    func testAnOverrideBeatsVisitedAndWishlist() {
        let unselected = CountryStyle(isVisited: true, isWishlist: true, isSelected: false, override: .red)
        XCTAssertEqual(unselected.fill, .custom(.red))
        XCTAssertEqual(unselected.border, .plain)

        let selected = CountryStyle(isVisited: true, isWishlist: false, isSelected: true, override: .red)
        XCTAssertEqual(selected.fill, .plain)
        XCTAssertEqual(selected.border, .custom(.red))
    }

    /// What lets a renderer skip a country: the same inputs give an equal style,
    /// and any change that shows gives a different one.
    func testStylesCompareByWhatIsDrawn() {
        let visited = CountryStyle(isVisited: true, isWishlist: false, isSelected: false)
        XCTAssertEqual(visited, CountryStyle(isVisited: true, isWishlist: false, isSelected: false))
        XCTAssertNotEqual(visited, CountryStyle(isVisited: true, isWishlist: false, isSelected: true))
        XCTAssertNotEqual(visited, CountryStyle(isVisited: true, isWishlist: true, isSelected: false))
        XCTAssertNotEqual(CountryStyle(isVisited: false, isWishlist: false, isSelected: false),
                          CountryStyle(isVisited: false, isWishlist: false, isSelected: true))
        XCTAssertNotEqual(CountryStyle(isVisited: false, isWishlist: false, isSelected: false, override: .red),
                          CountryStyle(isVisited: false, isWishlist: false, isSelected: false, override: .green))
    }
}
