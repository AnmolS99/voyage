import XCTest
@testable import voyage

/// The country list's All · Visited · Wishlist filter. Android's
/// `CountryListFilterTest` pins the same cases.
final class CountryListFilterTests: XCTestCase {
    private let visited: Set<String> = ["France", "Japan"]
    private let wishlist: Set<String> = ["Peru", "Japan"]

    private func listed(_ filter: CountryListFilter, kept: Set<String> = []) -> Set<String> {
        let names = ["France", "Japan", "Peru", "Chad"]
        return Set(names.filter { filter.includes($0, visited: visited, wishlist: wishlist, kept: kept) })
    }

    func testAllListsEveryCountry() {
        XCTAssertEqual(listed(.all), ["France", "Japan", "Peru", "Chad"])
        XCTAssertNil(CountryListFilter.all.members(visited: visited, wishlist: wishlist))
    }

    func testVisitedAndWishlistListTheirOwnCountries() {
        XCTAssertEqual(listed(.visited), ["France", "Japan"])
        XCTAssertEqual(listed(.wishlist), ["Peru", "Japan"])
    }

    func testACountryUnmarkedFromTheFilteredListStaysInIt() {
        // France was visited when Visited was chosen, then un-visited from the list.
        let kept = CountryListFilter.visited.members(visited: visited, wishlist: wishlist)!
        let afterUnvisit = visited.subtracting(["France"])
        XCTAssertTrue(CountryListFilter.visited.includes("France", visited: afterUnvisit, wishlist: wishlist, kept: kept))
        // Choosing the filter afresh lets it go.
        let rechosen = CountryListFilter.visited.members(visited: afterUnvisit, wishlist: wishlist)!
        XCTAssertFalse(CountryListFilter.visited.includes("France", visited: afterUnvisit, wishlist: wishlist, kept: rechosen))
    }

    func testAnEmptyFilterListsNothing() {
        XCTAssertTrue(["France", "Chad"].allSatisfy {
            !CountryListFilter.wishlist.includes($0, visited: visited, wishlist: [])
        })
    }
}
