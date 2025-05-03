package com.tehoubi.ddrtracker.util

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tehoubi.ddrtracker.domain.model.Difficulty
import com.tehoubi.ddrtracker.domain.model.Grade
import com.tehoubi.ddrtracker.domain.model.Score
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.util.UUID

/**
 * Parses a DDR result screen bitmap into a [Score] using ML Kit OCR.
 *
 * The DDR score screen layout varies by game version. This parser targets
 * DDR A3 / World. Extend [parseRawText] for other versions.
 */
object OcrScoreParser {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Runs OCR on [bitmap] and attempts to extract a [Score].
     * Returns null if the image cannot be parsed confidently.
     */
    suspend fun parseScoreScreen(bitmap: Bitmap, userId: String): Score? {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(image).await()
        val rawText = result.text
        return parseRawText(rawText, userId)
    }

    /**
     * Extract score fields from raw OCR text.
     * TODO: Implement regex-based extraction for each field.
     */
    private fun parseRawText(text: String, userId: String): Score? {
        // TODO: Parse songTitle, score, grade, combo, perfects, etc.
        return null
    }

    // --- Helpers ---

    private fun parseGrade(raw: String): Grade? {
        // TODO: Map raw OCR string to Grade enum
        return null
    }

    private fun parseDifficulty(raw: String): Difficulty? {
        // TODO: Map raw OCR string to Difficulty enum
        return null
    }
}
