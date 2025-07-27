package com.tehoubi.ddrtracker.core.vision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OcrEnhanceTest {

    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    private fun value(p: Int) = p and 0xFF

    @Test
    fun `a dim low-contrast image is stretched to full range`() {
        // Text at 110 on a background of 80: a washed-out cabinet photo.
        val pixels = IntArray(1000) { if (it % 10 == 0) gray(110) else gray(80) }
        val out = OcrEnhance.enhance(pixels)
        assertEquals(0, value(out[1]))
        assertEquals(255, value(out[0]))
    }

    @Test
    fun `colour becomes luminance`() {
        val red = (0xFF shl 24) or (255 shl 16)
        val blue = (0xFF shl 24) or 255
        val out = OcrEnhance.enhance(IntArray(100) { if (it < 50) red else blue })
        assertTrue(value(out[0]) > value(out[99]))
        // Output is gray: all channels equal, fully opaque.
        assertEquals(out[0] and 0xFF, (out[0] shr 16) and 0xFF)
        assertEquals(0xFF, out[0] ushr 24)
    }

    @Test
    fun `flat images don't divide by zero`() {
        val out = OcrEnhance.enhance(IntArray(10) { gray(128) })
        assertEquals(10, out.size)
    }

    @Test
    fun `scale keeps the long side bounded`() {
        assertEquals(1f, OcrEnhance.scaleFor(1920, 1080))
        assertEquals(0.625f, OcrEnhance.scaleFor(4096, 3072))
    }
}
