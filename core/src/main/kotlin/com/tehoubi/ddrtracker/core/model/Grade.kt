package com.tehoubi.ddrtracker.core.model

/**
 * Letter grades as of DDR A20 PLUS / A3 / WORLD, highest first.
 *
 * [minScore] is the lowest score that earns the grade on a cleared chart.
 * E is only awarded for a failed stage, whatever the score.
 */
enum class Grade(val label: String, val minScore: Int) {
    AAA("AAA", 990_000),
    AA_PLUS("AA+", 950_000),
    AA("AA", 900_000),
    AA_MINUS("AA-", 890_000),
    A_PLUS("A+", 850_000),
    A("A", 800_000),
    A_MINUS("A-", 790_000),
    B_PLUS("B+", 750_000),
    B("B", 700_000),
    B_MINUS("B-", 690_000),
    C_PLUS("C+", 650_000),
    C("C", 600_000),
    C_MINUS("C-", 590_000),
    D_PLUS("D+", 550_000),
    D("D", 0),
    E("E", 0);

    companion object {
        /** Parses an on-screen label like "AA+" or "A-" (case and spacing tolerant). */
        fun fromLabel(raw: String): Grade? {
            val cleaned = raw.uppercase().replace(" ", "")
            return entries.firstOrNull { it.label == cleaned || it.name == cleaned }
        }
    }
}
