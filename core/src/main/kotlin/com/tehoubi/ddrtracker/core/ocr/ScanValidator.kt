package com.tehoubi.ddrtracker.core.ocr

import com.tehoubi.ddrtracker.core.model.ClearLamp
import com.tehoubi.ddrtracker.core.model.Judgments
import com.tehoubi.ddrtracker.core.scoring.DdrScoring

enum class Severity { INFO, WARNING, ERROR }

data class ScanIssue(val field: String?, val message: String, val severity: Severity)

/**
 * The parsed result after cross-checking, with any repairs applied.
 *
 * [repaired] maps a field name to the value OCR originally produced;
 * [inferred] names fields OCR couldn't read that were worked out from the
 * rest of the screen.
 */
data class CheckedScan(
    val result: ParsedResult,
    val issues: List<ScanIssue>,
    val repaired: Map<String, Int>,
    val inferredNg: Int?,
    val inferred: Set<String> = emptySet(),
) {
    val isConsistent: Boolean get() = issues.none { it.severity >= Severity.WARNING }
}

/**
 * Cross-checks a parse against DDR's scoring rules and fixes single-digit
 * OCR mistakes when exactly one fix explains the screen.
 *
 * The judgments, EX score and money score are redundant: EX must equal
 * 3*Marvelous + 2*Perfect + Great + 3*O.K., and the money score must follow
 * from the judgments for some small number of dropped freezes. When those
 * disagree, every field is tried with each digit swapped for its usual OCR
 * look-alikes (and with one digit dropped); a candidate is applied only if
 * it makes all checks pass and no other field offers an equally good fix.
 */
object ScanValidator {

    /** Digit pairs that OCR mixes up on the result screen's font. Symmetric. */
    private val lookAlikes: Map<Char, Set<Char>> = listOf(
        "08", "06", "09", "17", "14", "27", "23", "38", "35", "49", "56", "58", "68", "69", "89",
    ).flatMap { listOf(it[0] to it[1], it[1] to it[0]) }
        .groupBy({ it.first }, { it.second })
        .mapValues { it.value.toSet() }

    private val repairable = StatLabel.JUDGMENTS + StatLabel.EX_SCORE

    fun check(parsed: ParsedResult): CheckedScan {
        val repaired = linkedMapOf<String, Int>()
        val inferred = linkedSetOf<String>()
        var result = parsed

        // Unread or contradicted judgment rows, worked out from the totals.
        JudgmentSolver.solve(result)?.let { sol ->
            val rows = sol.filled + sol.replaced.keys
            val values = mapOf(
                StatLabel.MARVELOUS to sol.judgments.marvelous, StatLabel.PERFECT to sol.judgments.perfect,
                StatLabel.GREAT to sol.judgments.great, StatLabel.GOOD to sol.judgments.good,
                StatLabel.OK to sol.judgments.ok, StatLabel.MISS to sol.judgments.miss,
            )
            result = result.copy(stats = result.stats + rows.associateWith { Field(values.getValue(it), 0.7f) })
            inferred += sol.filled.map { it.fieldName }
            sol.replaced.forEach { (label, old) -> repaired[label.fieldName] = old }
        }

        val fix = if (!isConsistent(result)) findRepair(result) else null
        if (fix != null && fix.first !in repaired) {
            repaired[fix.first] = valueOf(result, fix.first)!!
            result = withValue(result, fix.first, fix.second)
        }

        val issues = mutableListOf<ScanIssue>()
        result.missingFields().forEach { issues += ScanIssue(it, "Couldn't read $it", Severity.WARNING) }

        val j = result.judgments()
        val score = result.score?.value
        var ng: Int? = null

        if (score != null && score % 10 != 0) {
            issues += ScanIssue("score", "Scores always end in 0; check the last digit", Severity.ERROR)
        }
        if (score != null && score > DdrScoring.MAX_SCORE) {
            issues += ScanIssue("score", "Score is above 1,000,000", Severity.ERROR)
        }
        result.exScore?.value?.let { ex ->
            if (result.hasAllJudgments && ex != DdrScoring.exScore(j)) {
                issues += ScanIssue(
                    "exScore",
                    "EX score $ex doesn't match the judgments (expected ${DdrScoring.exScore(j)})",
                    Severity.WARNING,
                )
            }
        }
        if (score != null && result.hasAllJudgments && !result.failed) {
            ng = DdrScoring.inferNg(j, score)
            if (ng == null) {
                issues += ScanIssue(
                    "score",
                    "Score doesn't follow from the judgments; check the counts",
                    Severity.WARNING,
                )
            } else if (ng > 0) {
                issues += ScanIssue(null, "Looks like $ng freeze${if (ng == 1) "" else "s"} dropped (N.G.)", Severity.INFO)
            }
        }
        result.maxCombo?.value?.let { combo ->
            if (result.hasAllJudgments && combo > j.total) {
                issues += ScanIssue("maxCombo", "Max combo $combo is more than the chart's ${j.total} objects", Severity.WARNING)
            }
        }
        result.comboTotal?.value?.let { total ->
            if (result.hasAllJudgments && j.steps != total) {
                issues += ScanIssue(null, "The judgments add up to ${j.steps} steps but the screen says $total", Severity.WARNING)
            }
        }

        // Grade and lamp are better derived than read: the grade is a stylised
        // graphic and the lamp text is small.
        if (score != null && score <= DdrScoring.MAX_SCORE) {
            val derived = DdrScoring.grade(score, result.failed)
            result.grade?.value?.let { read ->
                if (read != derived) {
                    issues += ScanIssue("grade", "Read grade ${read.label} but the score earns ${derived.label}", Severity.INFO)
                }
            }
            result = result.copy(grade = Field(derived, result.score!!.confidence))
        }
        if (result.hasAllJudgments) {
            val derived = DdrScoring.lamp(j, result.failed)
            val read = result.lamp?.value
            if (read != null && read != derived && !(read.isFullCombo && derived.isFullCombo && ng == 0)) {
                issues += ScanIssue("lamp", "Screen says ${read.label} but the judgments give ${derived.label}", Severity.INFO)
            }
            // A dropped freeze breaks the combo even with no Miss.
            val lamp = if (ng != null && ng > 0 && derived.isFullCombo) ClearLamp.CLEAR else derived
            result = result.copy(lamp = Field(lamp, minConfidence(result)))
        }

        repaired.forEach { (field, old) ->
            issues += ScanIssue(field, "Corrected $field from $old to ${valueOf(result, field)}", Severity.INFO)
        }
        if (inferred.isNotEmpty()) {
            issues += ScanIssue(
                null,
                "Couldn't read ${inferred.joinToString()}; worked out from the score, EX score and step count",
                Severity.INFO,
            )
        }
        return CheckedScan(result, issues, repaired, ng, inferred)
    }

    private fun minConfidence(r: ParsedResult): Float =
        StatLabel.JUDGMENTS.mapNotNull { r.stats[it]?.confidence }.minOrNull() ?: 0f

    // --- Consistency ------------------------------------------------------

    private fun checks(r: ParsedResult, maxNg: Int): List<Boolean> = buildList {
        if (!r.hasAllJudgments) return@buildList
        val j = r.judgments()
        r.exScore?.let { add(it.value == DdrScoring.exScore(j)) }
        r.comboTotal?.let { add(it.value == j.steps) }
        r.score?.let { s ->
            add(s.value % 10 == 0 && s.value <= DdrScoring.MAX_SCORE)
            if (!r.failed) add(DdrScoring.inferNg(j, s.value, maxNg = maxNg) != null)
        }
    }

    private fun isConsistent(r: ParsedResult) = checks(r, maxNg = 200).all { it }

    /** Returns (field, new value) for the single repair that fixes everything. */
    private fun findRepair(r: ParsedResult): Pair<String, Int>? {
        if (!r.hasAllJudgments) return null
        // Without the EX checksum, only the score check constrains a fix, so
        // demand a tight explanation (few dropped freezes).
        val maxNg = if (r.exScore != null) 200 else 3
        val needed = checks(r, maxNg).size
        if (needed < 2) return null

        val fields = repairable.filter { it in r.stats }.map { it.name to r.stats.getValue(it) } +
            listOfNotNull(r.score?.let { "SCORE" to it })

        data class Fix(val field: String, val value: Int, val cost: Int, val confidence: Float)

        val fixes = fields.flatMap { (name, field) ->
            alternatives(field.value).mapNotNull { (alt, cost) ->
                val candidate = withValue(r, name, alt)
                val passing = checks(candidate, maxNg)
                if (passing.size == needed && passing.all { it }) Fix(name, alt, cost, field.confidence) else null
            }
        }
        if (fixes.isEmpty()) return null
        // A swapped digit is likelier than a phantom one, and a shaky field
        // likelier to be wrong than a confident one.
        val cheapest = fixes.minOf { it.cost }
        val leastSure = fixes.filter { it.cost == cheapest }.minOf { it.confidence }
        val best = fixes.filter { it.cost == cheapest && it.confidence - leastSure < 1e-4f }
        // Two different fixes explain the screen equally well: don't guess.
        if (best.map { it.field to it.value }.distinct().size != 1) return null
        return best.first().let { fieldKey(it.field) to it.value }
    }

    /** Values [value] might really be, with a cost: 1 for a swapped digit, 2 for a dropped one. */
    internal fun alternatives(value: Int): Map<Int, Int> {
        val digits = value.toString()
        val out = mutableMapOf<Int, Int>()
        digits.forEachIndexed { i, c ->
            lookAlikes[c]?.forEach { alt ->
                val v = (digits.substring(0, i) + alt + digits.substring(i + 1)).toInt()
                out[v] = minOf(out[v] ?: Int.MAX_VALUE, 1)
            }
            if (digits.length > 1) {
                val v = digits.removeRange(i, i + 1).toInt()
                out[v] = minOf(out[v] ?: Int.MAX_VALUE, 2)
            }
        }
        out -= value
        return out
    }

    // --- Field access -----------------------------------------------------

    private fun fieldKey(statName: String): String =
        if (statName == "SCORE") "score" else StatLabel.valueOf(statName).fieldName

    private val StatLabel.fieldName: String
        get() = when (this) {
            StatLabel.EX_SCORE -> "exScore"
            StatLabel.MAX_COMBO -> "maxCombo"
            else -> name.lowercase()
        }

    private fun valueOf(r: ParsedResult, field: String): Int? =
        if (field == "score") r.score?.value
        else StatLabel.entries.firstOrNull { it.fieldName == field || it.name == field }?.let { r.stats[it]?.value }

    private fun withValue(r: ParsedResult, field: String, value: Int): ParsedResult {
        if (field.equals("score", ignoreCase = true)) {
            return r.copy(score = Field(value, (r.score?.confidence ?: 0.5f) * 0.9f))
        }
        val label = StatLabel.entries.first { it.fieldName == field || it.name == field }
        val old = r.stats[label]
        return r.copy(stats = r.stats + (label to Field(value, (old?.confidence ?: 0.5f) * 0.9f)))
    }

    /** Convenience for manual entry: issues with hand-typed values. */
    fun checkManual(j: Judgments, score: Int, exScore: Int?, failed: Boolean): List<ScanIssue> {
        val stats = mapOf(
            StatLabel.MARVELOUS to j.marvelous, StatLabel.PERFECT to j.perfect, StatLabel.GREAT to j.great,
            StatLabel.GOOD to j.good, StatLabel.OK to j.ok, StatLabel.MISS to j.miss,
        ).mapValues { Field(it.value, 1f) } + listOfNotNull(exScore?.let { StatLabel.EX_SCORE to Field(it, 1f) })
        val parsed = ParsedResult(title = Field("", 1f), score = Field(score, 1f), stats = stats, failed = failed)
        return check(parsed).issues.filter { it.field !in setOf("title", "difficulty", "level") }
    }
}
