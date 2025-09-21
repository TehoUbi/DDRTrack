package com.tehoubi.ddrtracker.scan

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tehoubi.ddrtracker.core.ocr.OcrPage
import com.tehoubi.ddrtracker.core.vision.FrameQuality

/** One analysed preview frame. */
data class LiveFrame(val page: OcrPage?, val quality: FrameQuality.Report)

/**
 * Runs on CameraX's analysis stream: measures each frame's quality from the
 * Y plane, and at most every [intervalMs] runs OCR on it.
 *
 * Uses STRATEGY_KEEP_ONLY_LATEST upstream, so a slow OCR pass drops frames
 * rather than queueing them; the frame is closed only once ML Kit is done.
 */
class LiveTextAnalyzer(
    private val intervalMs: Long = 350,
    private val onFrame: (LiveFrame) -> Unit,
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val tracker = FrameQuality.Tracker()
    private var lastRun = 0L
    private var luma = ByteArray(0)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        val media = image.image
        if (media == null || now - lastRun < intervalMs) {
            image.close()
            return
        }
        lastRun = now

        val plane = image.planes[0]
        val buffer = plane.buffer
        if (luma.size != buffer.remaining()) luma = ByteArray(buffer.remaining())
        buffer.get(luma)
        buffer.rewind()
        val quality = tracker.measure(luma, image.width, image.height, plane.rowStride)

        val rotation = image.imageInfo.rotationDegrees
        val sideways = rotation % 180 != 0
        val width = if (sideways) image.height else image.width
        val height = if (sideways) image.width else image.height

        recognizer.process(InputImage.fromMediaImage(media, rotation))
            .addOnSuccessListener { text -> onFrame(LiveFrame(text.toOcrPage(width, height), quality)) }
            .addOnFailureListener { onFrame(LiveFrame(null, quality)) }
            .addOnCompleteListener { image.close() }
    }

    fun close() = recognizer.close()
}
