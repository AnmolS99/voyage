package com.anmol.voyage.ui.country

/**
 * Which countries the country list shows: every one, the visited ones or the
 * wishlisted ones — iOS `CountryListFilter`, same cases and rules.
 */
enum class CountryListFilter {
    ALL,
    VISITED,
    WISHLIST,
    ;

    /** The countries this filter lists right now; null for [ALL], which lists every one. */
    fun members(visited: Set<String>, wishlist: Set<String>): Set<String>? = when (this) {
        ALL -> null
        VISITED -> visited
        WISHLIST -> wishlist
    }

    /**
     * Whether the list shows [name]. [kept] is what the filter listed when it
     * was chosen: a country un-visited (or un-wished) from the filtered list
     * stays in it, unmarked, so a mis-tap is one tap to undo rather than a row
     * that vanishes. It leaves when another filter is chosen or the list closes.
     */
    fun includes(
        name: String,
        visited: Set<String>,
        wishlist: Set<String>,
        kept: Set<String> = emptySet(),
    ): Boolean {
        val members = members(visited, wishlist) ?: return true
        return name in members || name in kept
    }
}
