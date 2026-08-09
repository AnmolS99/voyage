package com.anmol.voyage.state

import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

/**
 * Everything the app remembers between launches — the Android counterpart of the
 * keys iOS writes in `GlobeState.saveData()`.
 *
 * It is one document rather than a bag of loose preference keys so that a write
 * is always a complete, self-consistent snapshot, and so [version] can describe
 * the whole thing. That matters for the cross-platform sync deferred to after
 * launch: whatever ships it will have to read documents written by older
 * builds, and a version on the document is what makes that possible.
 *
 * **Compatibility rules.** Every field has a default, so a document written by
 * an older build (missing fields) reads back fine; unknown fields written by a
 * *newer* build are ignored rather than fatal. Renames and other shape changes
 * are applied by [migrated], which every read goes through.
 */
@Serializable
data class PersistedState(
    val version: Int = CURRENT_VERSION,
    val visitedCountries: Set<String> = emptySet(),
    val wishlistCountries: Set<String> = emptySet(),
    /** Checked top cities, keyed by country name. Countries with none are absent. */
    val checkedCities: Map<String, Set<String>> = emptyMap(),
    /** Checked top attractions, keyed by country name. */
    val checkedAttractions: Map<String, Set<String>> = emptyMap(),
    val viewMode: ViewMode = ViewMode.Map,
    val globeStyle: GlobeStyle = GlobeStyle.Realistic,
    val mapStyle: GlobeStyle = GlobeStyle.Realistic,
    val themeMode: ThemeMode = ThemeMode.System,
) {

    /**
     * This document brought up to [CURRENT_VERSION].
     *
     * Renaming is the only migration so far, and it is applied unconditionally
     * rather than per-version: the old names can also arrive from outside this
     * app's own storage — a backup restored from another device, or eventually a
     * synced document — where the version number says nothing useful.
     *
     * A document from a *newer* build is stamped back down to
     * [CURRENT_VERSION]: its extra fields are dropped on the next write anyway,
     * so claiming to still be that version would be a lie.
     */
    fun migrated(): PersistedState = copy(
        version = CURRENT_VERSION,
        visitedCountries = renameCountries(visitedCountries),
        wishlistCountries = renameCountries(wishlistCountries),
        checkedCities = renameCountryKeys(checkedCities),
        checkedAttractions = renameCountryKeys(checkedAttractions),
    )

    companion object {
        /**
         * On-disk schema version. Bump it when the shape changes, and teach
         * [migrated] how to bring older documents forward.
         */
        const val CURRENT_VERSION = 1

        /**
         * Countries renamed in the dataset (old name → current official name),
         * mirroring iOS `GlobeState.renamedCountries`. Saved data uses country
         * names as its keys, so a rename in `world.geojson` would otherwise
         * orphan everything the user had marked.
         */
        val RENAMED_COUNTRIES = mapOf(
            "Turkey" to "Türkiye",
            "Cape Verde" to "Cabo Verde",
        )

        private fun renameCountries(names: Set<String>): Set<String> =
            names.mapTo(LinkedHashSet(names.size)) { RENAMED_COUNTRIES[it] ?: it }

        /** Renames the keys, unioning values if both the old and new name are present. */
        private fun renameCountryKeys(
            byCountry: Map<String, Set<String>>,
        ): Map<String, Set<String>> {
            if (byCountry.keys.none { it in RENAMED_COUNTRIES }) return byCountry
            val result = LinkedHashMap<String, Set<String>>(byCountry.size)
            for ((country, items) in byCountry) {
                val name = RENAMED_COUNTRIES[country] ?: country
                result[name] = result[name]?.plus(items) ?: items
            }
            return result
        }
    }
}

/**
 * Reads and writes [PersistedState] as JSON.
 *
 * Kept separate from the DataStore plumbing so the format — and the migrations
 * every read applies — can be tested without a device or a real data store.
 */
object PersistedStateCodec {

    private val json = Json {
        // A newer build's fields must not make its documents unreadable to an
        // older one; the fields it does understand still load.
        ignoreUnknownKeys = true
        // Defaults are the whole document on a first save, so they have to be
        // written out rather than elided.
        encodeDefaults = true
    }

    /** Decodes [text], applying [PersistedState.migrated] to whatever it holds. */
    fun decode(text: String): PersistedState =
        json.decodeFromString(PersistedState.serializer(), text).migrated()

    @OptIn(ExperimentalSerializationApi::class)
    fun decode(input: InputStream): PersistedState =
        json.decodeFromStream(PersistedState.serializer(), input).migrated()

    fun encode(state: PersistedState): String =
        json.encodeToString(PersistedState.serializer(), state)

    @OptIn(ExperimentalSerializationApi::class)
    fun encode(state: PersistedState, output: OutputStream) {
        json.encodeToStream(PersistedState.serializer(), state, output)
    }
}
