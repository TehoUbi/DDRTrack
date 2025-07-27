package com.tehoubi.ddrtracker.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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
 * Recognise, parse and cross-check one image.
 *
 * If the first pass doesn't produce a consistent result, the image is run
 * again in contrast-stretched grayscale and the two readings are merged
 * field by field. A clean photo costs one pass; a murky one costs two.
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
        val image = withContext(Dispatchers.Default) { downscale(bitmap) }

        val page = recognizer.recognize(image, rotationDegrees)
        val first = parser.parse(page, side)
        val firstCheck = ScanValidator.check(first)
        if (firstCheck.isConsistent && first.title != null) return Output(page, firstCheck, passes = 1)

        val enhanced = withContext(Dispatchers.Default) { enhance(image) }
        val second = parser.parse(recognizer.recognize(enhanced, rotationDegrees), side)
        val merged = merge(first, second)
        return Output(page, ScanValidator.check(merged), passes = 2)
    }

    /** Decodes a picked image (screenshot or saved photo) upright and at a sensible size. */
    suspend fun decode(uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val scale = OcrEnhance.scaleFor(info.size.width, info.size.height)
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    }

    private fun downscale(bitmap: Bitmap): Bitmap {
        val scale = OcrEnhance.scaleFor(bitmap.width, bitmap.height)
        if (scale == 1f) return bitmap
        val m = Matrix().apply { postScale(scale, scale) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
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
}
