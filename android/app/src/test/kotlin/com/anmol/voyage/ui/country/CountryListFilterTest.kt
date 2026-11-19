package com.anmol.voyage.ui.country

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The country list's All · Visited · Wishlist filter. iOS's `CountryListFilterTests` pins the same cases. */
class CountryListFilterTest {

    private val visited = setOf("France", "Japan")
    private val wishlist = setOf("Peru", "Japan")

    private fun listed(filter: CountryListFilter, kept: Set<String> = emptySet()): Set<String> =
        listOf("France", "Japan", "Peru", "Chad")
            .filter { filter.includes(it, visited, wishlist, kept) }
            .toSet()

    @Test
    fun `all lists every country`() {
        assertEquals(setOf("France", "Japan", "Peru", "Chad"), listed(CountryListFilter.ALL))
        assertNull(CountryListFilter.ALL.members(visited, wishlist))
    }

    @Test
    fun `visited and wishlist list their own countries`() {
        assertEquals(setOf("France", "Japan"), listed(CountryListFilter.VISITED))
        assertEquals(setOf("Peru", "Japan"), listed(CountryListFilter.WISHLIST))
    }

    @Test
    fun `a country unmarked from the filtered list stays in it`() {
        // France was visited when Visited was chosen, then un-visited from the list.
        val kept = CountryListFilter.VISITED.members(visited, wishlist)!!
        val afterUnvisit = visited - "France"
        assertTrue(CountryListFilter.VISITED.includes("France", afterUnvisit, wishlist, kept))
        // Choosing the filter afresh lets it go.
        val rechosen = CountryListFilter.VISITED.members(afterUnvisit, wishlist)!!
        assertFalse(CountryListFilter.VISITED.includes("France", afterUnvisit, wishlist, rechosen))
    }

    @Test
    fun `an empty filter lists nothing`() {
        assertTrue(
            listOf("France", "Chad").none { CountryListFilter.WISHLIST.includes(it, visited, emptySet()) },
        )
    }
}
