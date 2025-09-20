package com.tehoubi.ddrtracker.core.realscreens

import com.tehoubi.ddrtracker.core.ocr.Box
import com.tehoubi.ddrtracker.core.ocr.OcrLine
import com.tehoubi.ddrtracker.core.ocr.OcrPage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * OCR of real WORLD result-screen photos (see tools/ocr_fixture.py): the
 * whole photo, and a second full-resolution read of the stats panel, as the
 * app does.
 */
object RealScreens {

    @Serializable
    private data class Line(val text: String, val box: List<Int>, val angle: Float = 0f, val confidence: Float = 1f)

    @Serializable
    private data class Fixture(val width: Int, val height: Int, val lines: List<Line>, val region: List<Int>? = null)

    private val json = Json { ignoreUnknownKeys = true }

    private fun load(name: String): Fixture {
        val text = RealScreens::class.java.getResource("/realscreens/$name.json")?.readText()
            ?: error("missing fixture $name")
        return json.decodeFromString(Fixture.serializer(), text)
    }

    private fun Fixture.page() = OcrPage(width, height, lines.map {
        OcrLine.of(it.text, Box(it.box[0], it.box[1], it.box[2], it.box[3]), it.confidence, it.angle)
    })

    /** The single downscaled pass. */
    fun page(name: String): OcrPage = load(name).page()

    /** The whole-photo pass with the stats panel replaced by its detailed re-read. */
    fun twoPass(name: String): OcrPage {
        val panel = load("$name-panel")
        val r = panel.region!!
        return page(name).withRegion(Box(r[0], r[1], r[2], r[3]), panel.page())
    }
}
