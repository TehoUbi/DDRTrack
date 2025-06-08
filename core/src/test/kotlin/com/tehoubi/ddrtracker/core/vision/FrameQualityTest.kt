package com.tehoubi.ddrtracker.core.vision

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FrameQualityTest {

    private val w = 320
    private val h = 180

    /**
     * Light glyph-sized marks on a dark screen. Random rather than periodic:
     * real text has detail at many scales, which is what blur takes away.
     */
    private fun textFrame(): ByteArray {
        val rnd = Random(7)
        val cells = Array(h / 3 + 1) { BooleanArray(w / 3 + 1) { rnd.nextInt(3) == 0 } }
        return ByteArray(w * h) { i ->
            val x = i % w; val y = i / w
            (if (cells[y / 3][x / 3]) 200 else 30).toByte()
        }
    }

    /** Box blur, a stand-in for a shaky hand. */
    private fun blur(src: ByteArray, r: Int = 4): ByteArray = ByteArray(w * h) { i ->
        val x = i % w; val y = i / w
        var s = 0; var n = 0
        for (dy in -r..r) for (dx in -r..r) {
            val xx = (x + dx).coerceIn(0, w - 1); val yy = (y + dy).coerceIn(0, h - 1)
            s += src[yy * w + xx].toInt() and 0xFF; n++
        }
        (s / n).toByte()
    }

    @Test
    fun `sharp text passes`() {
        val r = FrameQuality.measure(textFrame(), w, h)
        assertNull(r.issue, r.toString())
    }

    @Test
    fun `blur is detected`() {
        val sharp = FrameQuality.measure(textFrame(), w, h)
        val blurred = FrameQuality.measure(blur(textFrame(), r = 4), w, h)
        assertTrue(blurred.sharpness < sharp.sharpness / 10, "$sharp vs $blurred")
        // On its own a blurred frame still has some detail...
        assertNull(blurred.issue)
        // ...but next to a sharp frame of the same scene it is blurry.
        val tracker = FrameQuality.Tracker()
        assertNull(tracker.measure(textFrame(), w, h).issue)
        assertEquals(FrameQuality.Issue.BLURRY, tracker.measure(blur(textFrame(), r = 4), w, h).issue)
        assertNull(tracker.measure(textFrame(), w, h).issue)
    }

    @Test
    fun `sharpness does not depend on contrast`() {
        val dim = textFrame().map { ((it.toInt() and 0xFF) / 4 + 20).toByte() }.toByteArray()
        val a = FrameQuality.measure(textFrame(), w, h).sharpness
        val b = FrameQuality.measure(dim, w, h).sharpness
        assertEquals(a, b, a * 0.1)
    }

    @Test
    fun `a flat frame is blurry even without a reference`() {
        val flat = ByteArray(w * h) { (100 + (it % 2)).toByte() }
        assertEquals(FrameQuality.Issue.BLURRY, FrameQuality.measure(blur(flat, 2), w, h).issue)
    }

    @Test
    fun `glare is detected`() {
        val frame = textFrame()
        for (y in 40 until 110) for (x in 100 until 220) frame[y * w + x] = 255.toByte()
        assertEquals(FrameQuality.Issue.GLARE, FrameQuality.measure(frame, w, h).issue)
    }

    @Test
    fun `darkness is detected`() {
        val frame = ByteArray(w * h) { (Random(it).nextInt(0, 20)).toByte() }
        assertEquals(FrameQuality.Issue.TOO_DARK, FrameQuality.measure(frame, w, h).issue)
    }

    @Test
    fun `row stride padding is skipped`() {
        val stride = w + 64
        val padded = ByteArray(stride * h)
        val src = textFrame()
        for (y in 0 until h) System.arraycopy(src, y * w, padded, y * stride, w)
        val a = FrameQuality.measure(src, w, h)
        val b = FrameQuality.measure(padded, w, h, rowStride = stride)
        assertEquals(a.sharpness, b.sharpness, 1e-9)
    }
}
