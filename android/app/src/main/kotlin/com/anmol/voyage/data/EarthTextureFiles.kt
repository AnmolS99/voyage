package com.anmol.voyage.data

/**
 * Where the Earth textures are packaged and how wide they may be — the part of
 * [EarthTextureCache] the build shares with the app.
 *
 * The images in `shared/data/textures/` are the ones iOS bundles, and two of
 * them are 8192 × 4096. Android shows no more than [MAX_WIDTH] of that, so the
 * build packages copies already brought down to it (`EarthTextureGenerator` in
 * tools/world-cache) instead of the originals: about 9 MB less to download,
 * and a quarter of the JPEG to read on each decode.
 */
object EarthTextureFiles {

    /**
     * The widest texture a GPU and a `Canvas` both take. At 8192 × 4096 a bitmap
     * is 128 MB — over the 100 MB a `Canvas` will draw, and 171 MB on the GPU
     * once it has mipmaps. Halved, it is 32 MB.
     */
    const val MAX_WIDTH = 4096

    /** The textures' directory under `shared/data/`, which is not packaged. */
    const val SHARED_DIRECTORY = "textures"

    /**
     * The asset directory the build writes its copies to. Not [SHARED_DIRECTORY]:
     * `ignoreAssetsPatterns` goes by name, so leaving the originals out of the
     * app would leave out a generated directory of the same name with them.
     */
    const val PACKAGED_DIRECTORY = "earth"

    /** The packaged asset for an image at [sharedPath], relative to `shared/data/`. */
    fun packagedAsset(sharedPath: String): String {
        require(sharedPath.startsWith("$SHARED_DIRECTORY/")) { "$sharedPath is not an Earth texture" }
        return PACKAGED_DIRECTORY + sharedPath.removePrefix(SHARED_DIRECTORY)
    }

    /** The power-of-two downsampling that brings [width] within [MAX_WIDTH]. */
    fun sampleSizeFor(width: Int): Int {
        var sample = 1
        while (width / sample > MAX_WIDTH) sample *= 2
        return sample
    }
}
