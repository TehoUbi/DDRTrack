package com.tehoubi.ddrtracker.ocr

import com.tehoubi.ddrtracker.core.ocr.Box
import com.tehoubi.ddrtracker.core.ocr.CheckedScan
import com.tehoubi.ddrtracker.core.ocr.OcrPage
import com.tehoubi.ddrtracker.core.ocr.ParsedResult
import com.tehoubi.ddrtracker.core.ocr.ResultScreenParser
import com.tehoubi.ddrtracker.core.ocr.ScanValidator
import com.tehoubi.ddrtracker.core.vision.FieldConsensus
import com.tehoubi.ddrtracker.core.vision.OcrEnhance

/**
 * Reads one upright photo of a result screen.
 *
 * 1. A downscaled copy (long side [overviewSide]) is read to find where
 *    everything is.
 * 2. The stats panel is cropped from the full-resolution photo and read
 *    again. Its digits are the smallest text on screen; in a 12-24 MP photo
 *    of a whole cabinet they are a few dozen pixels tall after downscaling,
 *    which is where single digits go missing.
 * 3. Unless the title already matched a known song, the band under the
 *    difficulty banner, where the title and artist are, is read again at
 *    full resolution too: titles are small, and kanji need the detail more
 *    than digits do.
 * 4. If the style, difficulty or level is missing, the banner is read again
 *    at full resolution; "SINGLE" / "DOUBLE" is the smallest word on screen.
 * 5. If the result still doesn't add up, the panel is read once more in
 *    contrast-stretched grayscale and the readings are merged.
 *
 * Whatever is still unread is worked out by ScanValidator from the score,
 * EX score and step total.
 */
class ResultScreenReader(private val ocr: PaddleOcr, private val overviewSide: Int = 2048) {

    data class Reading(val page: OcrPage, val scan: CheckedScan, val passes: Int)

    fun read(photo: RgbImage, parser: ResultScreenParser, side: ResultScreenParser.Side = ResultScreenParser.Side.AUTO): Reading {
        val scale = OcrEnhance.scaleFor(photo.width, photo.height, overviewSide)
        var page = ocr.read(photo.scaled(scale))
        var passes = 1

        val panel = parser.statsRegion(page)
        if (panel != null) {
            page = page.withRegion(panel, ocr.read(crop(photo, panel, scale)))
            passes++
        }
        val band = parser.titleRegion(page)
        if (band != null && (parser.parse(page, side).title?.confidence ?: 0f) < SURE_TITLE) {
            page = page.withRegion(band, ocr.read(crop(photo, band, scale)))
            passes++
        }

        var parsed = parser.parse(page, side)
        val chart = parser.chartRegion(page)
        if (chart != null && (parsed.style == null || parsed.difficulty == null || parsed.level == null)) {
            // Up close the banner's words can run together, so this read only
            // fills in what the first one lacked (and the title, which the
            // catalog matches better knowing the chart).
            val again = parser.parse(page.withRegion(chart, ocr.read(crop(photo, chart, scale))), side)
            passes++
            if (completeness(again) > completeness(parsed)) {
                parsed = parsed.copy(
                    style = parsed.style ?: again.style,
                    difficulty = parsed.difficulty ?: again.difficulty,
                    level = parsed.level ?: again.level,
                    title = again.title ?: parsed.title,
                    artist = again.artist ?: parsed.artist,
                )
            }
        }
        var checked = ScanValidator.check(parsed)
        if (!checked.isConsistent && panel != null) {
            val enhanced = crop(photo, panel, scale).let { RgbImage(it.width, it.height, OcrEnhance.enhance(it.argb)) }
            val second = parser.parse(page.withRegion(panel, ocr.read(enhanced)), side)
            passes++
            checked = ScanValidator.check(merge(parsed, second))
        }
        return Reading(page, checked, passes)
    }

    private fun completeness(r: ParsedResult) = listOfNotNull(r.style, r.difficulty, r.level).size

    /** [region] is in overview coordinates; the crop is taken from the full-resolution [photo]. */
    private fun crop(photo: RgbImage, region: Box, scale: Float): RgbImage {
        val full = Box(
            (region.left / scale).toInt(), (region.top / scale).toInt(),
            (region.right / scale).toInt(), (region.bottom / scale).toInt(),
        )
        val cropped = photo.crop(full)
        // Very wide crops are scaled down a little; the detector gains nothing past this.
        return cropped.scaled(OcrEnhance.scaleFor(cropped.width, cropped.height, MAX_CROP_SIDE))
    }

    /** Field-wise vote between two readings, the original counting slightly more. */
    private fun merge(a: ParsedResult, b: ParsedResult): ParsedResult {
        if (!a.looksLikeResultScreen) return b
        if (!b.looksLikeResultScreen) return a
        val votes = FieldConsensus(window = 2, minVotes = 1)
        votes.add(a, weight = 1.1f)
        votes.add(b, weight = 1f)
        return votes.current()
    }

    private companion object {
        const val MAX_CROP_SIDE = 2560
        /** A catalog match this sure (a clean read of a known title) needs no second look. */
        const val SURE_TITLE = 0.9f
    }
}
