package com.tehoubi.ddrtracker.core.vision

/**
 * Cheap per-frame checks on the camera's luminance (Y) plane, run before
 * spending an OCR pass on a frame.
 *
 * Sharpness is the variance of a 4-neighbour Laplacian over a subsampled
 * grid, divided by the variance of the pixels themselves: edges of crisp
 * text produce large second derivatives, motion blur and missed focus
 * flatten them, and dividing by contrast keeps a dim cabinet from reading as
 * blurry. Glare is the share of blown-out pixels, which on a cabinet screen
 * means a reflection sitting on the digits.
 *
 * What counts as sharp depends on the scene, so blur is judged against the
 * sharpest recent frame by a [Tracker]; the absolute floor in [Thresholds]
 * only catches frames with no detail at all.
 *
 * These are hints, not gates: the scanner still reads a frame that fails
 * them, it just trusts it less.
 */
object FrameQuality {

    enum class Issue(val hint: String) {
        TOO_DARK("Too dark — get closer to the screen"),
        TOO_BRIGHT("Overexposed — tilt the phone a little"),
        GLARE("Glare on the screen — change your angle"),
        BLURRY("Hold steady"),
    }

    data class Thresholds(
        val minSharpness: Double = 0.02,
        val maxGlare: Double = 0.06,
        val minBrightness: Double = 35.0,
        val maxBrightness: Double = 225.0,
    )

    data class Report(val sharpness: Double, val brightness: Double, val glare: Double, val issue: Issue?) {
        val isUsable: Boolean get() = issue == null

        /** How much a parse of this frame should count in [FieldConsensus]. */
        val weight: Float get() = when (issue) {
            null -> 1f
            Issue.BLURRY, Issue.GLARE -> 0.5f
            Issue.TOO_DARK, Issue.TOO_BRIGHT -> 0.7f
        }
    }

    /**
     * Measures a frame. [step] subsamples the grid; 2 on a 1280x720 frame is
     * about 230k pixels and well under a millisecond on a phone.
     */
    fun measure(
        luma: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int = width,
        step: Int = 2,
        thresholds: Thresholds = Thresholds(),
    ): Report {
        require(width > 2 * step && height > 2 * step) { "frame too small: ${width}x$height" }
        fun y(x: Int, row: Int): Int = luma[row * rowStride + x].toInt() and 0xFF

        var sum = 0L
        var sumSq = 0L
        var count = 0
        var blown = 0
        var lapSum = 0.0
        var lapSq = 0.0
        var lapCount = 0

        var row = step
        while (row < height - step) {
            var x = step
            while (x < width - step) {
                val c = y(x, row)
                sum += c
                sumSq += c.toLong() * c
                count++
                if (c >= 250) blown++
                val lap = 4 * c - y(x - step, row) - y(x + step, row) - y(x, row - step) - y(x, row + step)
                lapSum += lap
                lapSq += lap.toDouble() * lap
                lapCount++
                x += step
            }
            row += step
        }

        val brightness = sum.toDouble() / count
        val glare = blown.toDouble() / count
        val lapMean = lapSum / lapCount
        val lapVariance = lapSq / lapCount - lapMean * lapMean
        val variance = sumSq.toDouble() / count - brightness * brightness
        val sharpness = lapVariance / variance.coerceAtLeast(1.0)

        val issue = when {
            brightness < thresholds.minBrightness -> Issue.TOO_DARK
            brightness > thresholds.maxBrightness -> Issue.TOO_BRIGHT
            glare > thresholds.maxGlare -> Issue.GLARE
            sharpness < thresholds.minSharpness -> Issue.BLURRY
            else -> null
        }
        return Report(sharpness, brightness, glare, issue)
    }

    /**
     * Judges blur relative to the sharpest frame seen recently. The peak
     * decays slowly so pointing the camera at a new scene re-calibrates
     * within a couple of seconds.
     */
    class Tracker(
        private val thresholds: Thresholds = Thresholds(),
        private val blurRatio: Double = 0.35,
        private val decay: Double = 0.97,
    ) {
        private var peak = 0.0

        fun measure(luma: ByteArray, width: Int, height: Int, rowStride: Int = width): Report {
            val report = measure(luma, width, height, rowStride, thresholds = thresholds)
            peak = maxOf(report.sharpness, peak * decay)
            return if (report.issue == null && report.sharpness < peak * blurRatio) {
                report.copy(issue = Issue.BLURRY)
            } else report
        }
    }
}
