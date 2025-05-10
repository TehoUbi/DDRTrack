package com.tehoubi.ddrtracker.core.model

/** Clear status ("lamp"), worst to best. Ordinal order is meaningful. */
enum class ClearLamp(val label: String, val shortLabel: String) {
    FAILED("Failed", "F"),
    CLEAR("Clear", "C"),
    FULL_COMBO("Full Combo", "FC"),
    GREAT_FULL_COMBO("Great Full Combo", "GFC"),
    PERFECT_FULL_COMBO("Perfect Full Combo", "PFC"),
    MARVELOUS_FULL_COMBO("Marvelous Full Combo", "MFC");

    val isFullCombo: Boolean get() = this >= FULL_COMBO
}
