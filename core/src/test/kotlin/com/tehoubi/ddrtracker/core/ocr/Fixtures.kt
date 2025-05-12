package com.tehoubi.ddrtracker.core.ocr

/** Builds OCR pages the way ML Kit reports a 1280x720 A3 result screen. */
internal class PageBuilder(private val width: Int = 1280, private val height: Int = 720) {
    private val lines = mutableListOf<OcrLine>()

    fun line(text: String, left: Int, top: Int, right: Int, bottom: Int, confidence: Float = 0.95f) {
        lines += OcrLine.of(text, Box(left, top, right, bottom), confidence)
    }

    /** A stats-table row where label and value come back as separate blocks. */
    fun row(label: String, value: String, top: Int) {
        line(label, 420, top, 420 + label.length * 18, top + 26)
        line(value, 800, top, 800 + value.length * 18, top + 26)
    }

    fun build() = OcrPage(width, height, lines.toList())
}

internal fun page(block: PageBuilder.() -> Unit): OcrPage = PageBuilder().apply(block).build()

/** Butterfly ESP 12, PFC: 452 / 12 / 0 / 0 / 34 / 0 -> 999,880, EX 1482. */
internal fun butterflyPfc(
    marvelous: String = "452",
    perfect: String = "12",
    great: String = "0",
    good: String = "0",
    ok: String = "34",
    miss: String = "0",
    score: String = "999,880",
    exScore: String = "1482",
    labels: Map<String, String> = emptyMap(),
) = page {
    line("Butterfly", 420, 60, 640, 100)
    line("SMiLE.dk", 420, 106, 560, 128)
    line("SINGLE", 280, 140, 390, 168)
    line("EXPERT", 420, 140, 540, 168)
    line("12", 552, 140, 584, 168)
    line("AAA", 470, 240, 640, 330)
    line(score, 700, 250, 1010, 320)
    line("PERFECT FULL COMBO", 420, 342, 800, 370)
    fun l(name: String) = labels[name] ?: name
    row(l("MARVELOUS"), marvelous, 400)
    row(l("PERFECT"), perfect, 434)
    row(l("GREAT"), great, 468)
    row(l("GOOD"), good, 502)
    row(l("O.K."), ok, 536)
    row(l("MISS"), miss, 570)
    row(l("MAX COMBO"), "464", 604)
    row(l("EX SCORE"), exScore, 638)
    line("FAST", 940, 400, 1010, 426); line("5", 1040, 400, 1058, 426)
    line("SLOW", 940, 434, 1010, 460); line("7", 1040, 434, 1058, 460)
}
