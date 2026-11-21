package com.anmol.voyage.tools

import com.anmol.voyage.data.EarthTextureFiles
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * Writes the Earth textures the app packages:
 * `EarthTextureGenerator <shared/data/textures> <output directory>`.
 *
 * Every image comes out under [EarthTextureFiles.PACKAGED_DIRECTORY] with its
 * own name. One within [EarthTextureFiles.MAX_WIDTH] is copied byte for byte;
 * a wider one is averaged down by the factor the app would otherwise decode it
 * at, so the app shows the same image without downloading the pixels it
 * dropped. Run by :app's `generate<Variant>EarthTextures` tasks — a build
 * product, never checked in.
 */
object EarthTextureGenerator {

    /** High enough that the second encoding adds nothing visible to the first. */
    private const val JPEG_QUALITY = 0.92f

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2) { "usage: EarthTextureGenerator <textures directory> <output directory>" }
        val output = File(args[1], EarthTextureFiles.PACKAGED_DIRECTORY).apply { mkdirs() }
        val images = File(args[0]).listFiles { file -> file.isFile && !file.isHidden }.orEmpty()
        require(images.isNotEmpty()) { "no textures in ${args[0]}" }
        for (image in images.sortedBy { it.name }) write(image, File(output, image.name))
    }

    private fun write(source: File, target: File) {
        val width = ImageIO.createImageInputStream(source).use { stream ->
            val reader = ImageIO.getImageReaders(stream).asSequence().firstOrNull()
                ?: error("${source.name} is not an image")
            reader.input = stream
            reader.getWidth(0).also { reader.dispose() }
        }
        val sample = EarthTextureFiles.sampleSizeFor(width)
        if (sample == 1) {
            source.copyTo(target, overwrite = true)
            return
        }
        // Only the photographic textures are this large; a lossless one would
        // need its own writer.
        require(source.extension.lowercase() in setOf("jpg", "jpeg")) {
            "${source.name} is ${width}px wide and not a JPEG — resize it, or teach EarthTextureGenerator its format"
        }
        writeJpeg(downsampled(ImageIO.read(source), sample), target)
    }

    /** [image] with each [sample] × [sample] block averaged into one pixel. */
    private fun downsampled(image: BufferedImage, sample: Int): BufferedImage {
        val width = image.width / sample
        val height = image.height / sample
        val result = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val block = IntArray(image.width * sample)
        val row = IntArray(width)
        val count = sample * sample
        for (y in 0 until height) {
            image.getRGB(0, y * sample, image.width, sample, block, 0, image.width)
            for (x in 0 until width) {
                var red = 0
                var green = 0
                var blue = 0
                for (dy in 0 until sample) {
                    val start = dy * image.width + x * sample
                    for (dx in 0 until sample) {
                        val pixel = block[start + dx]
                        red += pixel shr 16 and 0xFF
                        green += pixel shr 8 and 0xFF
                        blue += pixel and 0xFF
                    }
                }
                // Rounded, not truncated, so the average does not drift darker.
                row[x] = ((red + count / 2) / count shl 16) or
                    ((green + count / 2) / count shl 8) or
                    ((blue + count / 2) / count)
            }
            result.setRGB(0, y, width, 1, row, 0, width)
        }
        return result
    }

    private fun writeJpeg(image: BufferedImage, target: File) {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val quality = writer.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = JPEG_QUALITY
        }
        ImageIO.createImageOutputStream(target).use { stream ->
            writer.output = stream
            writer.write(null, IIOImage(image, null, null), quality)
        }
        writer.dispose()
    }
}
