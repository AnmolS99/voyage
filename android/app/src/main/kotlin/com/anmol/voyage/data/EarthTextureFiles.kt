package com.anmol.voyage.data

/** Where the build packages the Earth textures, and how wide it lets them be. */
object EarthTextureFiles {

    /** At 8192 px a bitmap is 128 MB, more than a `Canvas` will draw. */
    const val MAX_WIDTH = 4096

    const val SHARED_DIRECTORY = "textures"

    /** Not [SHARED_DIRECTORY]: `ignoreAssetsPatterns`, which leaves the originals out, matches by name. */
    const val PACKAGED_DIRECTORY = "earth"

    fun packagedAsset(sharedPath: String): String {
        require(sharedPath.startsWith("$SHARED_DIRECTORY/")) { "$sharedPath is not an Earth texture" }
        return PACKAGED_DIRECTORY + sharedPath.removePrefix(SHARED_DIRECTORY)
    }

    fun sampleSizeFor(width: Int): Int {
        var sample = 1
        while (width / sample > MAX_WIDTH) sample *= 2
        return sample
    }
}
