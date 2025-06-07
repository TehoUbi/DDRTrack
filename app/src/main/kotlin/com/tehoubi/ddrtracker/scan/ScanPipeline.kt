package com.tehoubi.ddrtracker.scan

import android.graphics.Bitmap
import com.tehoubi.ddrtracker.core.ocr.CheckedScan
import com.tehoubi.ddrtracker.core.ocr.OcrPage
import com.tehoubi.ddrtracker.core.ocr.ResultScreenParser
import com.tehoubi.ddrtracker.core.ocr.ScanValidator
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Recognise, parse and cross-check one image. */
@Singleton
class ScanPipeline @Inject constructor(
    private val recognizer: TextRecognizerEngine,
    private val catalogs: SongCatalogProvider,
) {
    data class Output(val page: OcrPage, val scan: CheckedScan)

    suspend fun run(bitmap: Bitmap, rotationDegrees: Int): Output {
        val page = recognizer.recognize(bitmap, rotationDegrees)
        val parser = ResultScreenParser(catalogs.catalog.first())
        return Output(page, ScanValidator.check(parser.parse(page)))
    }
}
