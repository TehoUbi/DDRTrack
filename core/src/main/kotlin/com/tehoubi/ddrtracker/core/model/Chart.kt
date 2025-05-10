package com.tehoubi.ddrtracker.core.model

enum class PlayStyle(val label: String, val code: String) {
    SINGLE("Single", "SP"),
    DOUBLE("Double", "DP");

    companion object {
        fun fromCode(code: String): PlayStyle? =
            entries.firstOrNull { it.code.equals(code, true) || it.name.equals(code, true) }
    }
}

/** The five chart difficulties, in the order the game lists them. */
enum class Difficulty(val label: String, val letter: Char) {
    BEGINNER("Beginner", 'b'),
    BASIC("Basic", 'B'),
    DIFFICULT("Difficult", 'D'),
    EXPERT("Expert", 'E'),
    CHALLENGE("Challenge", 'C');

    /** The community's short form, e.g. "ESP" for Expert Single. */
    fun shortCode(style: PlayStyle): String = "$letter${style.code}"

    companion object {
        fun fromName(name: String): Difficulty? =
            entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
    }
}

/** Chart levels run 1..19 since DDR X. */
val LEVEL_RANGE = 1..19
