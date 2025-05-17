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

    internal data class LabelHit(
        val label: StatLabel,
        val box: Box,
        val lineIndex: Int,
        /** Index of the first element after the label in its line. */
        val nextElement: Int,
        val confidence: Float,
    )

    private val labelKeys: List<Pair<StatLabel, String>> = StatLabel.entries
        .flatMap { label -> label.spellings.map { label to labelKey(it) } }
        // Longest first so "EX SCORE" wins over "SCORE".
        .sortedByDescending { it.second.length }

    fun parse(page: OcrPage): ParsedResult {
        val lines = page.lines
        val hits = findLabels(lines)
        val used = mutableSetOf<Box>()

        val stats = linkedMapOf<StatLabel, Field<Int>>()
        for (hit in hits.sortedBy { it.label.ordinal }) {
            if (hit.label == StatLabel.SCORE || hit.label in stats) continue
            val number = valueFor(hit, lines, used, maxDigits = 5) ?: continue
            used += number.first.box
            stats[hit.label] = Field(number.first.value, number.second)
        }

        val score = findScore(page, hits, used)
        score?.let { used += it.first.box }

        val chart = findChart(lines)
        val lampAndGrade = findLampAndGrade(lines)
        val title = findTitle(page, hits, used)

        return ParsedResult(
            title = title?.first,
            artist = title?.second,
            style = chart.style,
            difficulty = chart.difficulty,
            level = chart.level,
            score = score?.let { Field(it.first.value, it.second) },
            stats = stats,
            grade = lampAndGrade.grade,
            lamp = lampAndGrade.lamp,
            failed = lampAndGrade.failed,
        )
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

    /**
     * The number belonging to a label: next on the same line if there is one,
     * otherwise the closest number to the right on the same row.
     */
    private fun valueFor(
        hit: LabelHit,
        lines: List<OcrLine>,
        used: Set<Box>,
        maxDigits: Int,
    ): Pair<NumberToken, Float>? {
        val line = lines[hit.lineIndex]
        val rest = line.copy(elements = line.elements.drop(hit.nextElement))
        Numbers.numbersInLine(rest, hit.lineIndex, lenient = true)
            .firstOrNull { it.digits.length <= maxDigits && it.box !in used }
            ?.let { return it to confidence(it, hit.confidence, 1f) }

        val candidates = lines.flatMapIndexed { idx, l ->
            if (idx == hit.lineIndex) emptyList() else Numbers.numbersInLine(l, idx, lenient = true)
        }.filter { n ->
            n.box !in used &&
                n.digits.length <= maxDigits &&
                n.box.left >= hit.box.right - hit.box.height &&
                n.box.verticalOverlap(hit.box) >= 0.45f &&
                lines[n.lineIndex].elements.none { findLabelsInText(it.text) }
        }
        val nearest = candidates.minByOrNull { it.box.left - hit.box.right } ?: return null
        return nearest to confidence(nearest, hit.confidence, 0.92f)
    }

    /** Labels are words; "6000" must not be read as GOOD. */
    private fun mostlyLetters(text: String): Boolean {
        val letters = text.count { it.isLetter() }
        return letters > 0 && letters >= text.count { it.isDigit() }
    }

    private fun findLabelsInText(text: String): Boolean {
        if (!mostlyLetters(text)) return false
        val key = labelKey(text)
        return key.length >= 3 && labelKeys.any { (_, s) -> levenshtein(key, s) <= tolerance(s.length) }
    }

    private fun confidence(n: NumberToken, labelConf: Float, placement: Float): Float =
        (n.confidence * labelConf * placement * 0.8f.pow(n.substitutions)).coerceIn(0f, 1f)

    // --- Score ------------------------------------------------------------

    private fun findScore(page: OcrPage, hits: List<LabelHit>, used: Set<Box>): Pair<NumberToken, Float>? {
        hits.filter { it.label == StatLabel.SCORE }.forEach { hit ->
            valueFor(hit, page.lines, used, maxDigits = 7)
                ?.takeIf { it.first.value <= 1_000_000 && it.first.digits.length >= 4 }
                ?.let { return it }
        }
        // No label: the score is the biggest six- or seven-digit number on screen.
        val candidates = page.lines.flatMapIndexed { idx, line -> Numbers.numbersInLine(line, idx) }
            .filter { it.box !in used && it.digits.length in 5..7 && it.value <= 1_000_000 }
        val biggest = candidates.maxByOrNull { it.box.height } ?: return null
        val placement = if (candidates.size == 1) 0.9f else 0.8f
        return biggest to confidence(biggest, 1f, placement)
    }

    // --- Chart ------------------------------------------------------------

    private data class ChartFields(
        val style: Field<PlayStyle>?,
        val difficulty: Field<Difficulty>?,
        val level: Field<Int>?,
    )

    private val styleWords = mapOf(
        "SINGLE" to PlayStyle.SINGLE, "SP" to PlayStyle.SINGLE,
        "DOUBLE" to PlayStyle.DOUBLE, "DP" to PlayStyle.DOUBLE,
    )

    private fun findChart(lines: List<OcrLine>): ChartFields {
        var difficulty: Field<Difficulty>? = null
        var level: Field<Int>? = null
        var style: Field<PlayStyle>? = null

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

                val levelText = fused?.groupValues?.get(2)
                    ?: line.elements.drop(i + 1).firstNotNullOfOrNull { Numbers.read(it.text, lenient = true)?.digits }
                    ?: nearbyLevel(lines, idx, el.box)
                levelText?.toIntOrNull()?.takeIf { it in LEVEL_RANGE }?.let {
                    level = Field(it, el.confidence * 0.95f)
                }
            }
        }
        return ChartFields(style, difficulty, level)
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

    private val notTitles = setOf(
        "RESULT", "RESULTS", "STAGE", "NEWRECORD", "RECORD", "PLAYER", "IP", "ZP", "TOTAL",
        "DANCEDANCEREVOLUTION", "DDR", "WORLD", "AIII", "CLEARED", "FAILED", "KCAL", "CALORIE",
        "BPM", "TARGET", "RIVAL", "BEST", "HIGHSCORE", "PRESSSTART", "COURSE", "EXTRASTAGE",
        "FINALSTAGE", "ISTSTAGE", "ZNDSTAGE", "SRDSTAGE", "GREATS", "FULLCOMBO", "FLARE",
    )

    /**
     * Picks the song title (and the artist line under it) from text that is
     * not part of the stats table: large, in the top part of the screen, and
     * mostly letters.
     */
    private fun findTitle(page: OcrPage, hits: List<LabelHit>, used: Set<Box>): Pair<Field<String>, Field<String>?>? {
        val labelLines = hits.map { it.lineIndex }.toSet()
        val tableTop = hits.filter { it.label.isJudgment }.minOfOrNull { it.box.top } ?: page.height
        val candidates = page.lines.withIndex().filter { (idx, line) ->
            val text = line.text.trim()
            val key = labelKey(text)
            idx !in labelLines &&
                line.box !in used &&
                line.box.bottom <= tableTop &&
                text.count { it.isLetter() } >= 2 &&
                text.count { it.isDigit() } * 2 < text.length &&
                key !in notTitles &&
                notTitles.none { key.startsWith(it) && key.length - it.length <= 3 } &&
                Difficulty.entries.none { levenshtein(key, it.name) <= 1 } &&
                Grade.fromLabel(text) == null &&
                key !in styleWords
        }.map { it.value }
        if (candidates.isEmpty()) return null

        val ranked = candidates.sortedByDescending { line ->
            val positional = 1f - line.box.top / page.height.toFloat()
            line.box.height * (1f + positional) + line.text.length * 0.5f
        }
        val known = catalog?.let { matchCatalog(it, ranked) }
        val title = known?.first ?: ranked.first()
        val artist = candidates
            .filter { it !== title && it.box.top >= title.box.bottom - title.box.height / 4 }
            .filter { it.box.top - title.box.bottom < title.box.height * 2 && it.box.height <= title.box.height }
            .minByOrNull { it.box.top }
        val titleField = known?.second ?: Field(title.text.trim(), title.confidence * 0.7f)

        return titleField to artist?.let { Field(it.text.trim(), it.confidence * 0.6f) }
    }

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
