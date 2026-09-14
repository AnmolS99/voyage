package com.anmol.voyage.state

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Which rendering of the world the Home tab shows — the iOS `ViewMode`.
 *
 * The default is [Map] until the Filament globe lands in Phase 7, at which point
 * it becomes [Globe] to match iOS. Unlike iOS, which starts every launch on the
 * globe, the choice is persisted here: on Android the process can be killed and
 * restored at any moment, and coming back to a different view than the one left
 * behind reads as a bug rather than a reset.
 */
@Serializable
enum class ViewMode {
    @SerialName("globe")
    Globe,

    @SerialName("map")
    Map,
}

/**
 * Earth texture style, shared by the globe and the flat map — the iOS
 * `GlobeStyle`, including its name, since iOS uses the one enum for both.
 *
 * Serialized names match the iOS raw values so a future sync feature reads the
 * same documents.
 *
 * @property textureAsset the style's image under `shared/data/` — the same file
 *   iOS bundles as `GlobeStyle.textureName`.
 */
@Serializable
enum class GlobeStyle(val textureAsset: String) {
    @SerialName("stylized")
    Stylized("textures/stylized_earth_texture.jpg"),

    @SerialName("natural")
    Natural("textures/natural_earth_texture.jpg"),

    @SerialName("realistic")
    Realistic("textures/earth_texture.jpg"),
}

/**
 * Appearance preference.
 *
 * iOS stores a plain `isDarkMode` boolean because it has no "follow the system"
 * option; Android users expect one and it is the default, so this is a
 * three-state enum. [Light] and [Dark] map onto the iOS boolean exactly.
 */
@Serializable
enum class ThemeMode {
    @SerialName("system")
    System,

    @SerialName("light")
    Light,

    @SerialName("dark")
    Dark;

    /** Whether to use the dark scheme, given what the system is currently set to. */
    fun isDark(systemInDarkTheme: Boolean): Boolean = when (this) {
        System -> systemInDarkTheme
        Light -> false
        Dark -> true
    }
}
