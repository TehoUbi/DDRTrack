package com.tehoubi.ddrtracker.ocr

import com.tehoubi.ddrtracker.core.ocr.Box
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class RgbImageTest {

    // 3 x 2:  a b c
    //         d e f
    private val img = RgbImage(3, 2, intArrayOf(1, 2, 3, 4, 5, 6).map { it or (0xFF shl 24) }.toIntArray())
    private fun RgbImage.values() = argb.map { it and 0xFFFFFF }

    @Test
    fun `rotates clockwise and back`() {
        val cw = img.rotated(90)
        assertEquals(2, cw.width); assertEquals(3, cw.height)
        assertEquals(listOf(4, 1, 5, 2, 6, 3), cw.values())
        assertEquals(listOf(6, 5, 4, 3, 2, 1), img.rotated(180).values())
        assertEquals(listOf(3, 6, 2, 5, 1, 4), img.rotated(270).values())
        assertContentEquals(img.argb, cw.rotated(-90).argb)
    }

    @Test
    fun `crops clamp to the image`() {
        val c = img.crop(Box(1, -5, 10, 1))
        assertEquals(listOf(2, 3), c.values())
    }

    @Test
    fun `shrinking averages the whole footprint`() {
        val grey = { v: Int -> (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        val stripes = RgbImage(4, 1, intArrayOf(grey(0), grey(200), grey(0), grey(200)))
        // Point sampling would give 0 or 200; a thin stroke must survive as grey.
        assertEquals(listOf(100, 100), stripes.resize(2, 1).argb.map { it and 0xFF })
    }
}
