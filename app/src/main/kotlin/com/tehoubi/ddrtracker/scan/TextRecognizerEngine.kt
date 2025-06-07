package com.tehoubi.ddrtracker.scan

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tehoubi.ddrtracker.core.ocr.Box
import com.tehoubi.ddrtracker.core.ocr.OcrElement
import com.tehoubi.ddrtracker.core.ocr.OcrLine
import com.tehoubi.ddrtracker.core.ocr.OcrPage
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Anything that can turn an image into positioned text. */
interface TextRecognizerEngine {
    /** [rotationDegrees] is how far the image must turn clockwise to be upright. */
    suspend fun recognize(bitmap: Bitmap, rotationDegrees: Int): OcrPage
}

/**
 * ML Kit's bundled Latin model: runs on-device, with no network or Play
 * Services download needed.
 */
@Singleton
class MlKitTextRecognizer @Inject constructor() : TextRecognizerEngine {

    private val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognize(bitmap: Bitmap, rotationDegrees: Int): OcrPage {
        val text = client.process(InputImage.fromBitmap(bitmap, rotationDegrees)).await()
        // Boxes come back in the upright frame.
        val sideways = rotationDegrees % 180 != 0
        val width = if (sideways) bitmap.height else bitmap.width
        val height = if (sideways) bitmap.width else bitmap.height
        return text.toOcrPage(width, height)
    }
}

internal fun Text.toOcrPage(width: Int, height: Int) = OcrPage(
    width = width,
    height = height,
    lines = textBlocks.flatMap { block ->
        block.lines.mapNotNull { line ->
            val box = line.boundingBox?.toBox() ?: return@mapNotNull null
            OcrLine(
                text = line.text,
                box = box,
                elements = line.elements.mapNotNull { el ->
                    el.boundingBox?.let { OcrElement(el.text, it.toBox(), el.confidence) }
                },
                confidence = line.confidence,
            )
        }
    },
)

private fun Rect.toBox() = Box(left, top, right, bottom)
