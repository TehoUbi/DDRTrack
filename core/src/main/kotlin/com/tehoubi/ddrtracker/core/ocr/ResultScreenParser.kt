package com.tehoubi.ddrtracker.core.ocr

import com.tehoubi.ddrtracker.core.model.ClearLamp
import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.Grade
import com.tehoubi.ddrtracker.core.model.LEVEL_RANGE
import com.tehoubi.ddrtracker.core.model.PlayStyle
import com.tehoubi.ddrtracker.core.ocr.TextMatching.labelKey
import com.tehoubi.ddrtracker.core.ocr.TextMatching.levenshtein
import com.tehoubi.ddrtracker.core.ocr.TextMatching.tolerance
import com.tehoubi.ddrtracker.core.songs.SongCatalog
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Turns OCR output of a DDR result screen into structured fields.
 *
 * Works on geometry rather than plain text: ML Kit usually reports the stats
 * table's labels and values as separate blocks, so a value is found by
 * looking for a number on the same row to the right of its label. Targets the
 * A20 PLUS / A3 / WORLD layout but only relies on the labels, not on fixed
 * pixel positions, so photos taken at an angle or cropped still parse.
 *
 * With a [catalog], title candidates are matched against known songs and the
 * catalog's spelling wins; otherwise the raw OCR line is used.
 */
class ResultScreenParser(private val catalog: SongCatalog? = null) {

    /** Which player's half of a two-player result screen to read. */
    enum class Side { AUTO, LEFT, RIGHT }

    internal data class LabelHit(
        val label: StatLabel,
        val box: Box,
        val lineIndex: Int,
        /** Index of the first element after the label in its line. */
        val nextElement: Int,
        val confidence: Float,
    )

    private companion object {
        val RIGHT_COLUMN = setOf(StatLabel.SCORE, StatLabel.BEST_SCORE, StatLabel.EX_SCORE, StatLabel.MAX_COMBO)
        /** Labels whose left edge bounds the judgment value column. */
        val TAB_WORDS = listOf("SIMPLERESULT", "DETAILS", "PLAYGRAPH")
        val COLUMN_FENCES = setOf(StatLabel.FAST, StatLabel.SLOW, StatLabel.SCORE, StatLabel.BEST_SCORE, StatLabel.EX_SCORE, StatLabel.MAX_COMBO)
    }

    private val labelKeys: List<Pair<StatLabel, String>> = StatLabel.entries
        .flatMap { label -> label.spellings.map { label to labelKey(it) } }
        // Longest first so "EX SCORE" wins over "SCORE".
        .sortedByDescending { it.second.length }

    /**
     * Parses [page]. When both players' tables are on screen (a 2-player
     * session), the page is split between them and [side] picks one; AUTO
     * takes whichever side read more completely, preferring 1P on a tie.
     */
    fun parse(page: OcrPage, side: Side = Side.AUTO): ParsedResult {
        val (left, right) = halves(page) ?: return parseRegion(page)
        return when (side) {
            Side.LEFT -> parseRegion(left)
            Side.RIGHT -> parseRegion(right)
            Side.AUTO -> {
                val l = parseRegion(left)
                val r = parseRegion(right)
                if (completeness(r) > completeness(l)) r else l
            }
        }
    }

    /**
     * Where the stats panel is on [page], padded by a row, for a second,
     * higher-resolution read. Its digits are the smallest text on screen and
     * the first thing a downscaled pass loses. Null if no table labels were
     * found.
     */
    fun statsRegion(page: OcrPage): Box? {
        val straight = deskew(page)
        val hits = findLabels(straight.lines).filter { it.label.isJudgment || it.label in RIGHT_COLUMN }
        if (hits.size < 2) return null
        val top = hits.minOf { it.box.top }
        val bottom = hits.maxOf { it.box.bottom }
        val row = hits.map { it.box.height }.median() ?: return null
        val rowLines = page.lines.filter { it.box.centerY in (top - row).toFloat()..(bottom + row).toFloat() }
        val left = hits.minOf { it.box.left } - row
        val right = rowLines.maxOf { it.box.right } + row
        // Deskewing moved boxes; pad by the drift so the tilted panel still fits.
        val drift = (abs(page.lines.map { it.angle }.median() ?: 0f) / 57.3f * page.width).toInt()
        return Box(left, top - row, right, bottom + row).expand(drift / 2, drift).clampTo(page)
    }

    /**
     * The band between the difficulty banner and the stats panel where WORLD
     * prints the song title, for a read with a recogniser that handles
     * Japanese. Null if the banner wasn't found.
     */
    fun titleRegion(page: OcrPage): Box? {
        val straight = deskew(page)
        val banner = findChart(straight.lines).bannerBottom ?: return null
        val hits = findLabels(straight.lines)
        val tabs = straight.lines.filter { l -> labelKey(l.text).let { k -> TAB_WORDS.any { w -> k.contains(w) } } }.minOfOrNull { it.box.top }
        val bottom = listOfNotNull(tabs, hits.filter { it.label.isJudgment }.minOfOrNull { it.box.top }).minOrNull() ?: return null
        if (bottom <= banner) return null
        return Box(0, banner, page.width, bottom).clampTo(page)
    }

    private fun Box.clampTo(page: OcrPage) =
        Box(left.coerceAtLeast(0), top.coerceAtLeast(0), right.coerceAtMost(page.width), bottom.coerceAtMost(page.height))

    /** Both halves, for a side picker. Null when the screen has one table. */
    fun parseBothSides(page: OcrPage): Pair<ParsedResult, ParsedResult>? =
        halves(page)?.let { (l, r) -> parseRegion(l) to parseRegion(r) }

    /**
     * Splits a two-table page. Song and chart text above the tables is
     * shared by both players (it is centred and may sit on either side of
     * the split), so it goes to both halves; scores and table rows go to the
     * half they're in.
     */
    private fun halves(page: OcrPage): Pair<OcrPage, OcrPage>? {
        val hits = findLabels(page.lines)
        val split = splitX(hits) ?: return null
        val tableTop = hits.filter { it.label.isJudgment }.minOf { it.box.top }
        fun shared(line: OcrLine) =
            (line.box.left < split && line.box.right > split) ||
                (line.box.bottom <= tableTop && line.text.count { it.isDigit() } < 4)
        val left = page.lines.filter { shared(it) || it.box.right <= split }
        val right = page.lines.filter { shared(it) || it.box.left >= split }
        return page.copy(lines = left) to page.copy(lines = right)
    }

    private fun completeness(r: ParsedResult): Int =
        StatLabel.JUDGMENTS.count { it in r.stats } + (if (r.score != null) 2 else 0) + (if (r.exScore != null) 1 else 0)

    /**
     * If the same judgment label shows up twice far apart horizontally, the
     * x coordinate to split the page at: just left of the right-hand table's
     * labels, so the left table's values stay on the left.
     */
    internal fun splitX(hits: List<LabelHit>): Int? {
        val judgments = hits.filter { it.label.isJudgment }
        val splits = judgments.groupBy { it.label }.values.mapNotNull { same ->
            if (same.size < 2) return@mapNotNull null
            val sorted = same.sortedBy { it.box.left }
            val l = sorted.first(); val r = sorted.last()
            // Same row height and well apart: two tables, not one label read twice.
            if (r.box.left - l.box.right < l.box.width * 2) null else r.box.left - r.box.height / 2
        }
        // Need agreement from at least two rows before believing it.
        return if (splits.size >= 2) splits.sorted()[splits.size / 2] else null
    }

    private fun parseRegion(page: OcrPage): ParsedResult {
        val lines = deskew(page).lines
        val hits = findLabels(lines)
        val tokens = numberTokens(lines, hits)
        val used = mutableSetOf<NumberToken>()
        val stats = linkedMapOf<StatLabel, Field<Int>>()
        val rowHeight = hits.filter { it.label.isJudgment }.map { it.box.height }.median()
            ?: hits.map { it.box.height }.median() ?: 24

        // FAST / SLOW first: on WORLD their values sit under the labels, inside
        // the judgment rows, and must not be taken for judgment counts.
        readFastSlow(hits, tokens, used, rowHeight, stats)

        // The right-hand column (SCORE, BEST SCORE, EX SCORE, MAX COMBO).
        var comboTotal: Field<Int>? = null
        for (hit in hits.filter { it.label in RIGHT_COLUMN }.sortedBy { it.label.ordinal }) {
            if (hit.label in stats) continue
            if (hit.label == StatLabel.MAX_COMBO) {
                readCombo(hit, lines, tokens, used)?.let { (combo, total) ->
                    stats[StatLabel.MAX_COMBO] = combo
                    comboTotal = total
                }
                continue
            }
            val maxDigits = if (hit.label == StatLabel.SCORE || hit.label == StatLabel.BEST_SCORE) 7 else 5
            rightOf(hit, tokens, used, maxDigits)?.let { (n, conf) ->
                used += n
                stats[hit.label] = Field(n.value, conf)
            }
        }

        readJudgments(hits, tokens, used, rowHeight, stats)

        val score = findScore(page, stats[StatLabel.SCORE], tokens, used)
        stats.remove(StatLabel.SCORE)
        val chart = findChart(lines)
        val lampAndGrade = findLampAndGrade(lines)
        val title = findTitle(page.copy(lines = lines), hits, chart.bannerBottom)

        return ParsedResult(
            title = title?.first,
            artist = title?.second,
            style = chart.style,
            difficulty = chart.difficulty,
            level = chart.level,
            score = score,
            stats = stats,
            grade = lampAndGrade.grade,
            lamp = lampAndGrade.lamp,
            failed = lampAndGrade.failed,
            comboTotal = comboTotal,
            labelsSeen = hits.map { it.label }.toSet() + stats.keys,
        )
    }

    // --- Geometry -----------------------------------------------------------

    /**
     * Rotates every box about the page centre by the median text angle, so
     * "same row" means the same y. Photos of a cabinet are usually a degree
     * or two off, which across the stats panel is most of a row.
     */
    internal fun deskew(page: OcrPage): OcrPage {
        val angle = page.lines.filter { it.box.width > it.box.height * 2 }.map { it.angle }.median() ?: return page
        if (abs(angle) < 0.5f) return page
        val rad = Math.toRadians(-angle.toDouble())
        val c = kotlin.math.cos(rad); val s = kotlin.math.sin(rad)
        val cx = page.width / 2.0; val cy = page.height / 2.0
        fun rotate(b: Box): Box {
            val dx = b.centerX - cx; val dy = b.centerY - cy
            val nx = cx + dx * c - dy * s
            val ny = cy + dx * s + dy * c
            val hw = b.width / 2.0; val hh = b.height / 2.0
            return Box((nx - hw).toInt(), (ny - hh).toInt(), (nx + hw).toInt(), (ny + hh).toInt())
        }
        return page.copy(lines = page.lines.map { l ->
            l.copy(box = rotate(l.box), elements = l.elements.map { it.copy(box = rotate(it.box)) }, angle = l.angle - angle)
        })
    }

    /** Every number on the page except digits that are part of a label's line before the label. */
    private fun numberTokens(lines: List<OcrLine>, hits: List<LabelHit>): List<NumberToken> =
        lines.flatMapIndexed { idx, line ->
            val firstAfterLabel = hits.filter { it.lineIndex == idx }.minOfOrNull { it.nextElement } ?: 0
            val rest = if (firstAfterLabel > 0) line.copy(elements = line.elements.drop(firstAfterLabel)) else line
            Numbers.numbersInLine(rest, idx, lenient = true)
        }

    private fun List<Int>.median(): Int? = if (isEmpty()) null else sorted()[size / 2]
    private fun List<Float>.median(): Float? = if (isEmpty()) null else sorted()[size / 2]

    // --- Stats table ----------------------------------------------------------

    private fun readFastSlow(
        hits: List<LabelHit>,
        tokens: List<NumberToken>,
        used: MutableSet<NumberToken>,
        rowHeight: Int,
        stats: MutableMap<StatLabel, Field<Int>>,
    ) {
        fun below(label: Box) = tokens.filter { n ->
            n !in used && n.digits.length <= 4 &&
                n.box.top >= label.top + label.height / 2 && n.box.top - label.bottom < rowHeight * 1.6f &&
                n.box.centerX >= label.left - rowHeight && n.box.centerX <= label.right + rowHeight
        }.minByOrNull { it.box.top }

        val fast = hits.firstOrNull { it.label == StatLabel.FAST }
        val slow = hits.firstOrNull { it.label == StatLabel.SLOW }
        for (hit in listOfNotNull(fast, slow)) {
            val n = below(hit.box) ?: rightOf(hit, tokens, used, maxDigits = 4, maxGap = rowHeight * 3f)?.first ?: continue
            used += n
            stats[hit.label] = Field(n.value, confidence(n, hit.confidence, 0.9f))
        }
        // SLOW's stylised label often fails OCR; its value is the next number down the FAST column.
        if (fast != null && slow == null && StatLabel.FAST in stats) {
            val fastValue = used.last()
            tokens.filter { n ->
                n !in used && n.digits.length <= 4 && n.box.top > fastValue.box.bottom &&
                    n.box.top - fastValue.box.bottom < rowHeight * 4 &&
                    abs(n.box.centerX - fast.box.centerX) < fast.box.width
            }.minByOrNull { it.box.top }?.let { n ->
                used += n
                stats[StatLabel.SLOW] = Field(n.value, confidence(n, 0.8f, 0.85f))
            }
        }
    }

    /** MAX COMBO's value: "140 / 417" as one token or several, giving combo and step total. */
    private fun readCombo(
        hit: LabelHit,
        lines: List<OcrLine>,
        tokens: List<NumberToken>,
        used: MutableSet<NumberToken>,
    ): Pair<Field<Int>, Field<Int>?>? {
        val rowTexts = lines.withIndex().filter { (idx, l) ->
            (idx == hit.lineIndex || l.box.verticalOverlap(hit.box) >= 0.45f) && l.box.right > hit.box.right
        }.sortedBy { it.value.box.left }
        // Text to the right of the label on its row, read as one string.
        val tail = rowTexts.joinToString(" ") { (idx, l) ->
            if (idx == hit.lineIndex) l.elements.drop(hit.nextElement).joinToString(" ") { it.text } else l.text
        }
        val conf = hit.confidence * 0.9f
        // "140/417", "140 / 417" or "140/ 417", possibly followed by other text on the row.
        val words = tail.split(' ').filter { it.isNotBlank() }
        (1..minOf(3, words.size)).firstNotNullOfOrNull { n -> Numbers.comboPair(words.take(n).joinToString("")) }?.let { (combo, total) ->
            tokens.filter { it.lineIndex in rowTexts.map { r -> r.index } }.forEach { used += it }
            return Field(combo, conf) to Field(total, conf)
        }
        return rightOf(hit, tokens, used, maxDigits = 4)?.let { (n, c) ->
            used += n
            Field(n.value, c) to null
        }
    }

    /**
     * The number belonging to a label: next on the same line, or the closest
     * one to the right on the same row within [maxGap].
     */
    private fun rightOf(
        hit: LabelHit,
        tokens: List<NumberToken>,
        used: Set<NumberToken>,
        maxDigits: Int,
        maxGap: Float = Float.MAX_VALUE,
    ): Pair<NumberToken, Float>? {
        tokens.firstOrNull { it.lineIndex == hit.lineIndex && it !in used && it.digits.length <= maxDigits && it.box.left >= hit.box.left }
            ?.let { return it to confidence(it, hit.confidence, 1f) }
        val nearest = tokens.filter { n ->
            n !in used && n.digits.length <= maxDigits &&
                n.box.left >= hit.box.right - hit.box.height &&
                n.box.left - hit.box.right <= maxGap &&
                n.box.verticalOverlap(hit.box) >= 0.45f
        }.minByOrNull { it.box.left - hit.box.right } ?: return null
        return nearest to confidence(nearest, hit.confidence, 0.92f)
    }

    /**
     * Reads the six judgment counts as a column. The rows come in a fixed
     * order at an even pitch, so the row positions are fitted from whichever
     * labels were recognised; a row whose label OCR missed still has a known
     * y. A value has to lie right of the labels and left of whatever sits to
     * their right (the FAST/SLOW box, the score column), so those numbers
     * can't be taken for a count. A tall token spanning several rows is
     * stacked single digits read as one ("70" over Great and Good).
     */
    private fun readJudgments(
        hits: List<LabelHit>,
        tokens: List<NumberToken>,
        used: MutableSet<NumberToken>,
        rowHeight: Int,
        stats: MutableMap<StatLabel, Field<Int>>,
    ) {
        val allJudgmentHits = hits.filter { it.label.isJudgment }
        val judgmentHits = mainColumn(allJudgmentHits)
        val rows = judgmentHits?.let { fitRows(it, rowHeight) }
        if (judgmentHits == null || rows == null) {
            // Too few labels for a row model: fall back to each label's own row.
            for (hit in allJudgmentHits) {
                if (hit.label in stats) continue
                rightOf(hit, tokens, used, maxDigits = 4)?.let { (n, c) -> used += n; stats[hit.label] = Field(n.value, c) }
            }
            return
        }
        // Labels are right-aligned on WORLD and left-aligned elsewhere; the
        // shortest one's right edge is where values can start.
        val labelsRight = judgmentHits.minOf { it.box.right }
        // Without anything to the right, the column runs to the edge; the
        // nearest number on each row wins.
        val fence = hits.filter { it.label in COLUMN_FENCES && it.box.left > judgmentHits.maxOf { j -> j.box.right } }
            .minOfOrNull { it.box.left } ?: Int.MAX_VALUE / 2
        val column = tokens.filter { n ->
            n !in used && n.digits.length <= 4 &&
                n.box.left >= labelsRight - rowHeight && n.box.right <= fence + rowHeight / 3 &&
                n.box.bottom >= rows.centerOf(0) - rowHeight && n.box.top <= rows.centerOf(5) + rowHeight
        }.sortedBy { it.box.left }

        val byRow = arrayOfNulls<Pair<Int, Float>>(6)
        for (n in column) {
            val spanned = (0..5).filter { k -> rows.centerOf(k) in n.box.top.toFloat()..n.box.bottom.toFloat() }
            val labelConf = { k: Int -> judgmentHits.firstOrNull { it.label == StatLabel.JUDGMENTS[k] }?.confidence ?: 0.8f }
            if (spanned.size >= 2 && n.digits.length == spanned.size) {
                // Stacked digits: one per row, top to bottom.
                spanned.forEachIndexed { i, k ->
                    if (byRow[k] == null) byRow[k] = n.digits[i].digitToInt() to confidence(n, labelConf(k), 0.7f)
                }
                used += n
                continue
            }
            val k = rows.nearest(n.box.centerY)
            if (abs(rows.centerOf(k) - n.box.centerY) > rows.pitch * 0.45f) continue
            if (byRow[k] == null) {
                byRow[k] = n.value to confidence(n, labelConf(k), if (judgmentHits.any { it.label == StatLabel.JUDGMENTS[k] }) 0.95f else 0.8f)
                used += n
            }
        }
        byRow.forEachIndexed { k, v -> v?.let { stats[StatLabel.JUDGMENTS[k]] = Field(it.first, it.second) } }
    }

    /** Judgment labels of one table: the left-most cluster, if the screen shows more than one. */
    private fun mainColumn(judgmentHits: List<LabelHit>): List<LabelHit>? {
        if (judgmentHits.size < 2) return null
        val anchor = judgmentHits.minBy { it.box.left }
        val column = judgmentHits.filter { it.box.left - anchor.box.right < anchor.box.width }
        return column.groupBy { it.label }.map { (_, same) -> same.minBy { it.box.top } }.takeIf { it.size >= 2 }
    }

    /** Row k's centre is top + k * pitch, fitted by least squares over the labels found. */
    private class Rows(val top: Float, val pitch: Float) {
        fun centerOf(k: Int) = top + k * pitch
        fun nearest(y: Float) = ((y - top) / pitch).roundToInt().coerceIn(0, 5)
    }

    private fun fitRows(hits: List<LabelHit>, rowHeight: Int): Rows? {
        val pts = hits.map { StatLabel.JUDGMENTS.indexOf(it.label).toFloat() to it.box.centerY }
        val distinct = pts.map { it.first }.distinct()
        if (distinct.size < 2) return null
        val mx = pts.map { it.first }.average().toFloat()
        val my = pts.map { it.second }.average().toFloat()
        val sxx = pts.sumOf { ((it.first - mx) * (it.first - mx)).toDouble() }.toFloat()
        val sxy = pts.sumOf { ((it.first - mx) * (it.second - my)).toDouble() }.toFloat()
        val pitch = sxy / sxx
        // A pitch far from the text height means the labels were misassigned.
        if (pitch < rowHeight * 0.6f || pitch > rowHeight * 2.5f) return null
        return Rows(my - pitch * mx, pitch)
    }

    private fun confidence(n: NumberToken, labelConf: Float, placement: Float): Float =
        (n.confidence * labelConf * placement * 0.8f.pow(n.substitutions)).coerceIn(0f, 1f)

    // --- Score ------------------------------------------------------------

    /**
     * The score from the SCORE row if there is one, cross-checked against
     * the big number at the top; otherwise the tallest 5-7 digit number.
     */
    private fun findScore(page: OcrPage, labelled: Field<Int>?, tokens: List<NumberToken>, used: Set<NumberToken>): Field<Int>? {
        val big = tokens.filter { it !in used && it.digits.length in 5..7 && it.value <= 1_000_000 }
            .maxByOrNull { it.box.height }
        val labelledOk = labelled?.takeIf { it.value <= 1_000_000 && it.value >= 1000 }
        return when {
            labelledOk != null && big != null && big.value == labelledOk.value ->
                Field(labelledOk.value, minOf(1f, labelledOk.confidence + 0.1f))
            labelledOk != null -> labelledOk
            big != null -> Field(big.value, confidence(big, 1f, 0.85f))
            else -> null
        }
    }

    // --- Labels -----------------------------------------------------------

    internal fun findLabels(lines: List<OcrLine>): List<LabelHit> {
        val hits = mutableListOf<LabelHit>()
        lines.forEachIndexed { lineIndex, line ->
            val words = line.elements
            var i = 0
            while (i < words.size) {
                val match = if (mostlyLetters(words[i].text)) matchLabelAt(words, i) else null
                if (match == null) { i++; continue }
                val (label, span, distance) = match
                val after = words.getOrNull(i + span)?.text?.let(::labelKey)
                // "PERFECT FULL COMBO" is a lamp, not the Perfect row.
                val isLamp = after == "FULL" || after == "FULLCOMBO"
                if (!isLamp) {
                    val box = words.subList(i, i + span).map { it.box }.reduce(Box::union)
                    val conf = words.subList(i, i + span).minOf { it.confidence } * 0.9f.pow(distance)
                    hits += LabelHit(label, box, lineIndex, i + span, conf)
                }
                i += span
            }
        }
        return hits
    }

    /**
     * Tries one- and two-word spellings of every label starting at word [i],
     * keeping the closest match (the longer span on a tie, so "EX SCORE" is
     * not read as "EX" followed by the money-score label).
     */
    private fun matchLabelAt(words: List<OcrElement>, i: Int): Triple<StatLabel, Int, Int>? {
        var best: Triple<StatLabel, Int, Int>? = null
        for (span in 2 downTo 1) {
            if (i + span > words.size) continue
            val spanWords = words.subList(i, i + span)
            if (!spanWords.all { mostlyLetters(it.text) }) continue
            val key = spanWords.joinToString("") { labelKey(it.text) }
            if (key.isEmpty()) continue
            for ((label, spelling) in labelKeys) {
                if (abs(key.length - spelling.length) > tolerance(spelling.length)) continue
                val d = levenshtein(key, spelling)
                if (d <= tolerance(spelling.length) && (best == null || d < best.third)) {
                    best = Triple(label, span, d)
                }
            }
        }
        return best
    }

    /** Labels are words; "6000" must not be read as GOOD. */
    private fun mostlyLetters(text: String): Boolean {
        val letters = text.count { it.isLetter() }
        return letters > 0 && letters >= text.count { it.isDigit() }
    }

    // --- Chart ------------------------------------------------------------

    private data class ChartFields(
        val style: Field<PlayStyle>?,
        val difficulty: Field<Difficulty>?,
        val level: Field<Int>?,
        /** Bottom of the "SINGLE STYLE Expert 12" banner; the song title sits below it on WORLD. */
        val bannerBottom: Int?,
    )

    private val styleWords = mapOf(
        "SINGLE" to PlayStyle.SINGLE, "SP" to PlayStyle.SINGLE,
        "DOUBLE" to PlayStyle.DOUBLE, "DP" to PlayStyle.DOUBLE,
    )

    private fun findChart(lines: List<OcrLine>): ChartFields {
        var difficulty: Field<Difficulty>? = null
        var level: Field<Int>? = null
        var style: Field<PlayStyle>? = null
        var bannerBottom: Int? = null

        for ((idx, line) in lines.withIndex()) {
            line.elements.forEachIndexed { i, el ->
                val key = labelKey(el.text)
                styleWords[key]?.let { if (style == null) style = Field(it, el.confidence) }

                if (difficulty != null) return@forEachIndexed
                // The level is sometimes fused to the name: "EXPERT14".
                val fused = Regex("^([A-Za-z]{4,})(\\d{1,2})$").find(el.text.trim())
                val wordPart = fused?.groupValues?.get(1)?.let(::labelKey) ?: key
                val match = Difficulty.entries.firstOrNull { d ->
                    levenshtein(wordPart, d.name) <= tolerance(d.name.length)
                } ?: return@forEachIndexed
                difficulty = Field(match, el.confidence * if (wordPart == match.name) 1f else 0.85f)
                bannerBottom = line.box.bottom

                val levelText = fused?.groupValues?.get(2)
                    ?: line.elements.drop(i + 1).firstNotNullOfOrNull { Numbers.read(it.text, lenient = true)?.digits }
                    ?: nearbyLevel(lines, idx, el.box)
                levelText?.toIntOrNull()?.takeIf { it in LEVEL_RANGE }?.let {
                    level = Field(it, el.confidence * 0.95f)
                }
            }
        }
        return ChartFields(style, difficulty, level, bannerBottom)
    }

    /** A 1-2 digit number on the same row as, or just below, the difficulty name. */
    private fun nearbyLevel(lines: List<OcrLine>, skip: Int, near: Box): String? =
        lines.withIndex()
            .filter { it.index != skip }
            .flatMap { (idx, l) -> Numbers.numbersInLine(l, idx, lenient = true) }
            .filter { it.digits.length <= 2 }
            .filter { n ->
                val sameRow = n.box.verticalOverlap(near) > 0.4f && n.box.left >= near.left
                val below = n.box.top >= near.top && n.box.top - near.bottom < near.height * 1.5f &&
                    abs(n.box.centerX - near.centerX) < near.width
                sameRow || below
            }
            .minByOrNull { abs(it.box.centerX - near.right) + abs(it.box.centerY - near.centerY) }
            ?.digits

    // --- Lamp and grade ---------------------------------------------------

    private data class LampAndGrade(val lamp: Field<ClearLamp>?, val grade: Field<Grade>?, val failed: Boolean)

    private val lampPhrases = listOf(
        "MARVELOUSFULLCOMBO" to ClearLamp.MARVELOUS_FULL_COMBO,
        "PERFECTFULLCOMBO" to ClearLamp.PERFECT_FULL_COMBO,
        "GREATFULLCOMBO" to ClearLamp.GREAT_FULL_COMBO,
        "GOODFULLCOMBO" to ClearLamp.FULL_COMBO,
        "FULLCOMBO" to ClearLamp.FULL_COMBO,
    )

    private fun findLampAndGrade(lines: List<OcrLine>): LampAndGrade {
        var lamp: Field<ClearLamp>? = null
        var grade: Field<Grade>? = null
        var failed = false
        for (line in lines) {
            val key = labelKey(line.text)
            if (lamp == null) {
                lampPhrases.firstOrNull { (phrase, _) ->
                    key.contains(phrase) || (key.length >= phrase.length - 2 && levenshtein(key, phrase) <= 2)
                }?.let { lamp = Field(it.second, line.confidence) }
            }
            if (key == "FAILED" || key == "STAGEFAILED") failed = true
            // A grade only counts when it stands alone; "A" appears in titles too.
            if (grade == null && line.elements.size == 1) {
                Grade.fromLabel(line.text.trim())?.takeIf { it != Grade.E || failed }?.let {
                    grade = Field(it, line.confidence * 0.9f)
                }
            }
        }
        if (failed) lamp = Field(ClearLamp.FAILED, 1f)
        return LampAndGrade(lamp, grade, failed)
    }

    // --- Title ------------------------------------------------------------

    /** Text on and around a result screen that is never a song title. */
    private val notTitleParts = listOf(
        "RESULT", "STAGE", "RECORD", "DANCEDANCE", "DANCEIDANCE", "REVOLUTION", "KONAMI", "BEMANI", "AMUSEMENT",
        "EXTRASTAR", "SIMPLERESULT", "DETAILS", "PLAYGRAPH", "FLARE", "ENERGY", "CALORIE", "CHECKYOUR",
        "SWITCHTABS", "BUTTONS", "ONLINE", "CREDIT", "FULLCOMBO", "FULCOMBO", "HEREWEGO", "CAUTION",
        "COPYRIGHT", "PLAYDATA", "PRESSSTART", "HIGHSCORE", "RIVAL", "MACHINE", "TOTAL", "PLAYER", "CLEARED",
    )

    private fun isNotTitle(text: String): Boolean {
        val key = labelKey(text.trimEnd { it.isDigit() || it.isWhitespace() })
        if (key.length >= 5 && notTitleParts.any { part -> key.contains(part) || (key.length <= part.length + 3 && levenshtein(key, part) <= 2) }) return true
        if (Difficulty.entries.any { levenshtein(key, it.name) <= 1 } || key in styleWords) return true
        return lampPhrases.any { (phrase, _) -> key.length >= 8 && levenshtein(key, phrase) <= 3 }
    }

    /**
     * Finds the song title and artist.
     *
     * On WORLD they are small text under the jacket, the title with the
     * artist directly beneath it, between the difficulty banner and the stats
     * panel; above them sit the player name, "RESULTS" and cabinet decals. So
     * the best evidence is a pair of left-aligned stacked lines with the lower
     * one no bigger, preferably below the banner. A lone line is only a
     * low-confidence guess, flagged for the player to check. A catalog match
     * beats both.
     */
    private fun findTitle(page: OcrPage, hits: List<LabelHit>, bannerBottom: Int?): Pair<Field<String>, Field<String>?>? {
        val labelLines = hits.map { it.lineIndex }.toSet()
        // The details panel's tab row ("Simple result", "Details", "Play Graph")
        // or else the table itself bounds the title from below.
        val tabsTop = page.lines.filter { l -> labelKey(l.text).let { k -> TAB_WORDS.any { w -> k.contains(w) } } }.minOfOrNull { it.box.top }
        val tableTop = listOfNotNull(tabsTop, hits.filter { it.label.isJudgment || it.label in RIGHT_COLUMN }.minOfOrNull { it.box.top })
            .minOrNull() ?: page.height
        val candidates = page.lines.withIndex().filter { (idx, line) ->
            val text = line.text.trim()
            idx !in labelLines &&
                line.box.bottom <= tableTop &&
                line.box.height <= line.box.width * 1.2f && // not vertical text on the jacket
                text.length in 2..48 &&
                text.count { it.isLetter() } >= 2 &&
                text.count { it.isDigit() } * 2 < text.length &&
                Grade.fromLabel(text) == null &&
                !isNotTitle(text)
        }.map { it.value }.let { all ->
            // WORLD puts the title below the difficulty banner, with the
            // player name and cabinet text above it; when anything readable is
            // below the banner, only that is considered.
            val below = bannerBottom?.let { b -> all.filter { it.box.top >= b } }.orEmpty()
            below.ifEmpty { all }
        }
        if (candidates.isEmpty()) return null

        catalog?.let { matchCatalog(it, candidates) }?.let { (line, field) ->
            return field to artistUnder(line, candidates)?.let { Field(it.text.trim(), it.confidence * 0.75f) }
        }

        val pairs = candidates.flatMap { a ->
            candidates.filter { b ->
                b !== a && b.box.top >= a.box.bottom - a.box.height * 0.3f &&
                    b.box.top - a.box.bottom <= a.box.height * 0.9f &&
                    abs(b.box.left - a.box.left) <= a.box.height * 1.2f &&
                    b.box.height <= a.box.height * 1.15f
            }.map { b -> a to b }
        }
        val best = pairs.maxByOrNull { (a, _) -> a.box.bottom }
        if (best != null) {
            val (title, artist) = best
            return Field(title.text.trim(), title.confidence * 0.85f) to Field(artist.text.trim(), artist.confidence * 0.75f)
        }
        val lone = candidates.maxBy { it.box.height }
        return Field(lone.text.trim(), lone.confidence * 0.4f) to null
    }

    private fun artistUnder(title: OcrLine, candidates: List<OcrLine>): OcrLine? = candidates
        .filter { it !== title && it.box.top >= title.box.bottom - title.box.height / 4 }
        .filter { it.box.top - title.box.bottom < title.box.height * 1.5f && it.box.height <= title.box.height * 1.15f }
        .minByOrNull { it.box.top }

    /**
     * Matches candidate lines, and pairs of stacked lines for titles that
     * wrap, against the catalog. Pairs go first so "Healing Vision" over
     * "~Angelic mix~" resolves to the full title rather than its prefix.
     */
    private fun matchCatalog(catalog: SongCatalog, ranked: List<OcrLine>): Pair<OcrLine, Field<String>>? {
        val texts = buildList {
            for (a in ranked) for (b in ranked) {
                if (a !== b && b.box.top > a.box.top && b.box.top - a.box.bottom < a.box.height &&
                    abs(b.box.left - a.box.left) < a.box.height * 2
                ) add(a to "${a.text} ${b.text}")
            }
            ranked.forEach { add(it to it.text) }
        }
        val match = catalog.bestMatch(texts.map { it.second }) ?: return null
        val line = texts.first { it.second == match.matchedText }.first
        return line to Field(match.song.title, (line.confidence * match.similarity).coerceIn(0f, 1f))
    }
}
