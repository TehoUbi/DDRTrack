package com.tehoubi.ddrtracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tehoubi.ddrtracker.domain.model.Play
import com.tehoubi.ddrtracker.ui.theme.NeonYellow
import com.tehoubi.ddrtracker.ui.theme.ScoreDigits
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("MMM d · h:mm a").withZone(ZoneId.systemDefault())

/** A card summarizing a single [Play] for display in lists. */
@Composable
fun PlayCard(
    play: Play,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    newBest: Boolean = false,
    showDate: Boolean = true,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = play.songTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Tag(play.chart.label, play.difficulty.color())
                        if (play.lamp.isFullCombo || play.failed) Tag(play.lamp.shortLabel, play.lamp.color())
                        if (newBest) Tag("NEW BEST", NeonYellow)
                        if (showDate) {
                            Text(
                                dateFormatter.format(play.playedAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = play.grade.label,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = play.grade.color(),
                    )
                    Text(text = play.score.formatScore(), style = ScoreDigits)
                }
            }
            Spacer(Modifier.height(8.dp))
            JudgmentBar(play.judgments)
        }
    }
}
