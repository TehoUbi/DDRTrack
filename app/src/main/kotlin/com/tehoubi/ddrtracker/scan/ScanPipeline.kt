package com.tehoubi.ddrtracker.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.tehoubi.ddrtracker.core.ocr.CheckedScan
import com.tehoubi.ddrtracker.core.ocr.OcrPage
import com.tehoubi.ddrtracker.core.ocr.ResultScreenParser
import com.tehoubi.ddrtracker.core.vision.OcrEnhance
import com.tehoubi.ddrtracker.data.settings.SettingsRepository
import com.tehoubi.ddrtracker.ocr.PaddleOcr
import com.tehoubi.ddrtracker.ocr.ResultScreenReader
import com.tehoubi.ddrtracker.ocr.RgbImage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads one photo of a result screen with PP-OCRv6 (see ResultScreenReader
 * for the passes it makes). The same code runs in the :ocr tests against
 * photos of real screens.
 */
@Singleton
class ScanPipeline @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalogs: SongCatalogProvider,
    private val settings: SettingsRepository,
) {
    data class Output(val page: OcrPage, val scan: CheckedScan, val passes: Int)

    // The models are about 30 MB and take a moment to load, so they load once,
    // and one photo is read at a time.
    private val lock = Mutex()
    private var reader: ResultScreenReader? = null

    private fun reader(): ResultScreenReader = reader ?: ResultScreenReader(
        PaddleOcr(asset(PaddleOcr.DET_MODEL), asset(PaddleOcr.REC_MODEL)),
    ).also { reader = it }

    private fun asset(name: String): ByteArray = context.assets.open(name).use { it.readBytes() }

    /** Loads the models ahead of the first photo, while the player lines up the shot. */
    suspend fun warmUp() {
        withContext(Dispatchers.Default) { lock.withLock { reader() } }
    }

    suspend fun run(bitmap: Bitmap, rotationDegrees: Int): Output {
        val parser = ResultScreenParser(catalogs.catalog.first())
        val side = settings.current().playerSide
        return withContext(Dispatchers.Default) {
            val photo = bitmap.toRgbImage().rotated(rotationDegrees)
            val reading = lock.withLock { reader().read(photo, parser, side) }
            Output(reading.page, reading.scan, reading.passes)
        }
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

    private fun Bitmap.toRgbImage(): RgbImage {
        // A 50 MP still would need several 200 MB copies; 4096 px keeps the detail that matters.
        val scale = OcrEnhance.scaleFor(width, height, maxSide = MAX_DECODE_SIDE)
        val sized = if (scale == 1f) this else Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
        val src = if (sized.config == Bitmap.Config.ARGB_8888) sized else sized.copy(Bitmap.Config.ARGB_8888, false)
        val pixels = IntArray(src.width * src.height)
        src.getPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
        return RgbImage(src.width, src.height, pixels)
    }

    private companion object {
        const val MAX_DECODE_SIDE = 4096
    }
}
