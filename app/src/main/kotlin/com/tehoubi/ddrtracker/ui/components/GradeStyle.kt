package com.tehoubi.ddrtracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tehoubi.ddrtracker.core.model.ClearLamp
import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.Grade

/** Colours follow the cabinet: gold AAA, silver AA band, and lamp colours from the game. */
fun Grade.color(): Color = when (this) {
    Grade.AAA -> Color(0xFFFFD84D)
    Grade.AA_PLUS, Grade.AA, Grade.AA_MINUS -> Color(0xFFD8DEE9)
    Grade.A_PLUS, Grade.A, Grade.A_MINUS -> Color(0xFFFF9F43)
    Grade.B_PLUS, Grade.B, Grade.B_MINUS -> Color(0xFF6CB4FF)
    Grade.C_PLUS, Grade.C, Grade.C_MINUS -> Color(0xFFB98CFF)
    Grade.D_PLUS, Grade.D -> Color(0xFF9AA0A6)
    Grade.E -> Color(0xFFFF5C5C)
}

fun ClearLamp.color(): Color = when (this) {
    ClearLamp.FAILED -> Color(0xFFFF5C5C)
    ClearLamp.CLEAR -> Color(0xFF9AA0A6)
    ClearLamp.FULL_COMBO -> Color(0xFF4FA3FF)
    ClearLamp.GREAT_FULL_COMBO -> Color(0xFF6CF0A0)
    ClearLamp.PERFECT_FULL_COMBO -> Color(0xFFFFD84D)
    ClearLamp.MARVELOUS_FULL_COMBO -> Color(0xFFF2F6FF)
}

fun Difficulty.color(): Color = when (this) {
    Difficulty.BEGINNER -> Color(0xFF6CE8FF)
    Difficulty.BASIC -> Color(0xFFFFD84D)
    Difficulty.DIFFICULT -> Color(0xFFFF5C7A)
    Difficulty.EXPERT -> Color(0xFF6CF0A0)
    Difficulty.CHALLENGE -> Color(0xFFC77DFF)
}

/** A small filled tag, e.g. "ESP 14" or "PFC". */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color(0xFF0E0B1A),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(color, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
