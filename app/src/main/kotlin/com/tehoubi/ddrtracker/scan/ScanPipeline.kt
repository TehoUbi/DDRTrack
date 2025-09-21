package com.tehoubi.ddrtracker.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.tehoubi.ddrtracker.core.ocr.Box
import com.tehoubi.ddrtracker.core.ocr.CheckedScan
import com.tehoubi.ddrtracker.core.ocr.OcrPage
import com.tehoubi.ddrtracker.core.ocr.ParsedResult
import com.tehoubi.ddrtracker.core.ocr.ResultScreenParser
import com.tehoubi.ddrtracker.core.ocr.ScanValidator
import com.tehoubi.ddrtracker.core.vision.FieldConsensus
import com.tehoubi.ddrtracker.core.vision.OcrEnhance
import com.tehoubi.ddrtracker.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads one photo of a result screen.
 *
 * 1. The photo is turned upright and a downscaled copy (long side 2560) is
 *    read to find where everything is.
 * 2. The stats panel is cropped from the full-resolution photo and read
 *    again. Its digits are the smallest text on screen; in a 12-24 MP photo
 *    of a whole cabinet they are a few dozen pixels tall after downscaling,
 *    which is where single digits go missing.
 * 3. If the title looks unreliable, the band under the difficulty banner is
 *    read with the Japanese model (many DDR titles are Japanese).
 * 4. If the result still doesn't add up, the panel is read once more in
 *    contrast-stretched grayscale and the readings are merged.
 *
 * Whatever is still unread is worked out by ScanValidator from the score,
 * EX score and step total.
 */
@Singleton
class ScanPipeline @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recognizer: TextRecognizerEngine,
    private val catalogs: SongCatalogProvider,
    private val settings: SettingsRepository,
) {
    data class Output(val page: OcrPage, val scan: CheckedScan, val passes: Int)

    suspend fun run(bitmap: Bitmap, rotationDegrees: Int): Output {
        val parser = ResultScreenParser(catalogs.catalog.first())
        val side = settings.current().playerSide
        val upright = withContext(Dispatchers.Default) { rotate(bitmap, rotationDegrees) }
        val scale = OcrEnhance.scaleFor(upright.width, upright.height)
        val overview = withContext(Dispatchers.Default) { scaled(upright, scale) }
        var passes = 1

        var page = recognizer.recognize(overview, 0)
        val panel = parser.statsRegion(page)
        if (panel != null) {
            page = page.withRegion(panel, recognizer.recognize(crop(upright, panel, scale), 0))
            passes++
        }
        var parsed = parser.parse(page, side)

        if ((parsed.title?.confidence ?: 0f) < TITLE_CONFIDENCE) {
            parser.titleRegion(page)?.let { band ->
                val jp = page.withRegion(band, recognizer.recognize(crop(upright, band, scale), 0, Script.JAPANESE))
                passes++
                val title = parser.parse(jp, side)
                if ((title.title?.confidence ?: 0f) > (parsed.title?.confidence ?: 0f)) {
                    parsed = parsed.copy(title = title.title, artist = title.artist ?: parsed.artist)
                }
            }
        }

        var checked = ScanValidator.check(parsed)
        if (!checked.isConsistent && panel != null) {
            val enhanced = withContext(Dispatchers.Default) { enhance(crop(upright, panel, scale)) }
            val second = parser.parse(page.withRegion(panel, recognizer.recognize(enhanced, 0)), side)
            passes++
            checked = ScanValidator.check(merge(parsed, second))
        }
        return Output(page, checked, passes)
    }

    /** Decodes a picked image (screenshot or saved photo) upright, at full resolution. */
    suspend fun decode(uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                // Keep detail for the panel crop, but stay well inside memory limits.
                val scale = OcrEnhance.scaleFor(info.size.width, info.size.height, maxSide = MAX_DECODE_SIDE)
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bitmap
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }

    private fun scaled(bitmap: Bitmap, scale: Float): Bitmap =
        if (scale == 1f) bitmap
        else Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)

    /** [region] is in overview coordinates; the crop is taken from the full-resolution [upright]. */
    private fun crop(upright: Bitmap, region: Box, scale: Float): Bitmap {
        val left = (region.left / scale).toInt().coerceIn(0, upright.width - 1)
        val top = (region.top / scale).toInt().coerceIn(0, upright.height - 1)
        val right = (region.right / scale).toInt().coerceIn(left + 1, upright.width)
        val bottom = (region.bottom / scale).toInt().coerceIn(top + 1, upright.height)
        val cropped = Bitmap.createBitmap(upright, left, top, right - left, bottom - top)
        // Very wide crops are scaled down a little; ML Kit gains nothing past this.
        return scaled(cropped, OcrEnhance.scaleFor(cropped.width, cropped.height, maxSide = MAX_CROP_SIDE))
    }

    private fun enhance(bitmap: Bitmap): Bitmap {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val out = OcrEnhance.enhance(pixels)
        return Bitmap.createBitmap(out, bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
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
        const val TITLE_CONFIDENCE = 0.7f
        const val MAX_DECODE_SIDE = 4096
        const val MAX_CROP_SIDE = 3200
    }
}
