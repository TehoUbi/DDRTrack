package com.tehoubi.ddrtracker.ocr

import com.tehoubi.ddrtracker.core.vision.OcrEnhance
import org.junit.Assume.assumeTrue
import java.io.File
import javax.imageio.ImageIO

/** The models and the sample photos, as the Gradle test task points at them. */
object Samples {
    val ocr: PaddleOcr by lazy { PaddleOcr.fromDirectory(File(System.getProperty("ocr.models") ?: "models")) }

    private val dir = File(System.getProperty("samples.dir") ?: "../samples")

    /**
     * samples/[name], at most 4096 px on its long side as the app decodes
     * photos, or the test is skipped when the photo isn't there.
     */
    fun photo(name: String): RgbImage {
        val file = File(dir, name)
        assumeTrue("$file not present", file.exists())
        val img = ImageIO.read(file)
        val px = img.getRGB(0, 0, img.width, img.height, null, 0, img.width)
        return RgbImage(img.width, img.height, px).scaled(OcrEnhance.scaleFor(img.width, img.height, maxSide = 4096))
    }
}
