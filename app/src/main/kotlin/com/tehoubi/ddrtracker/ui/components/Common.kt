package com.tehoubi.ddrtracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tehoubi.ddrtracker.core.model.Judgments
import com.tehoubi.ddrtracker.ui.theme.ScoreDigits

/** A labelled number on the dashboard. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, accent: Color = MaterialTheme.colorScheme.primary) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(value, style = ScoreDigits.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize), color = accent)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

val judgmentColors = listOf(
    Color(0xFFF2F6FF), // marvelous
    Color(0xFFFFD84D), // perfect
    Color(0xFF6CF0A0), // great
    Color(0xFF4FA3FF), // good
    Color(0xFFFF9F43), // ok (freezes)
    Color(0xFFFF5C5C), // miss
)

/** The judgment mix as one stacked bar. */
@Composable
fun JudgmentBar(j: Judgments, modifier: Modifier = Modifier) {
    val counts = listOf(j.marvelous, j.perfect, j.great, j.good, j.ok, j.miss)
    val total = counts.sum().coerceAtLeast(1)
    Row(modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        counts.forEachIndexed { i, n ->
            if (n > 0) Box(Modifier.weight(n.toFloat() / total).height(10.dp).background(judgmentColors[i]))
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        actions()
    }
}

fun Int.formatScore(): String = "%,d".format(this)

fun Double.formatPercent(digits: Int = 1): String = "%.${digits}f%%".format(this * 100)
