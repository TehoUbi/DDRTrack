package com.tehoubi.ddrtracker.ocr

import com.tehoubi.ddrtracker.core.ocr.ResultScreenParser
import com.tehoubi.ddrtracker.core.songs.SongCatalog
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test

/**
 * Times the photo reader on samples/sample1-5.jpg and writes a report with
 * everything it read: every line of text, every field with its confidence,
 * and the validator's verdict. A speed-up must leave the report unchanged
 * apart from the "time" lines:
 *
 *     OCR_BENCH=before.txt ./gradlew :ocr:test --tests '*OcrBench*' --rerun
 *     (change something)
 *     OCR_BENCH=after.txt ./gradlew :ocr:test --tests '*OcrBench*' --rerun
 *     diff <(grep -v '^time' before.txt) <(grep -v '^time' after.txt)
 *
 * OCR_RUNS sets the runs per photo (default 3). OCR_JVMARGS=-Xint turns the
 * JIT off, a rough stand-in for a slow phone runtime.
 */
class OcrBench {
    @Test
    fun bench() {
        val target = System.getenv("OCR_BENCH")
        assumeTrue("set OCR_BENCH to a report path to run the benchmark", target != null)
        val runs = (System.getenv("OCR_RUNS") ?: "3").toInt()
        val reader = ResultScreenReader(Samples.ocr)
        val photos = (1..5).map { "sample$it.jpg" }.associateWith { Samples.photo(it) }
        // Warm up: load the models and let the JIT see the code once.
        reader.read(photos.values.first(), ResultScreenParser(SongCatalog.bundled()))

        val report = StringBuilder()
        for ((name, photo) in photos) {
            var total = 0L
            lateinit var reading: ResultScreenReader.Reading
            repeat(runs) {
                // A fresh catalog each run, so no title match is remembered from the last.
                val parser = ResultScreenParser(SongCatalog.bundled())
                val start = System.nanoTime()
                reading = reader.read(photo, parser)
                total += System.nanoTime() - start
            }
            report.appendLine("== $name ${photo.width}x${photo.height}, ${reading.passes} passes")
            report.appendLine("time ${total / runs / 1_000_000} ms")
            reading.page.lines.forEach { report.appendLine("line ${it.text}") }
            report.appendLine("result ${reading.scan.result}")
            report.appendLine("consistent ${reading.scan.isConsistent} ${reading.scan.issues}")
        }
        File(target).writeText(report.toString())
    }
}
