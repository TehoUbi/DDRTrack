package com.tehoubi.ddrtracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val base = Typography()

val DdrTypography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Black),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.copy(letterSpacing = 1.sp),
)

/**
 * Tabular figures (every digit the same width) in the regular face, so
 * score columns line up without monospace's wide commas.
 */
val ScoreDigits = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontFeatureSettings = "tnum",
)
