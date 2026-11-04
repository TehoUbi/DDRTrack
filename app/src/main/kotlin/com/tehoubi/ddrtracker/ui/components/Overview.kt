package com.tehoubi.ddrtracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tehoubi.ddrtracker.core.stats.Overview
import com.tehoubi.ddrtracker.core.stats.TimingBias
import com.tehoubi.ddrtracker.ui.theme.NeonMagenta
import com.tehoubi.ddrtracker.ui.theme.NeonYellow
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A popup explaining what a number on the dashboard means. */
data class Explanation(val title: String, val body: String)

object Explanations {
    val comfortLevel = Explanation(
        "Comfort level",
        "The highest level where your personal bests average AA (900,000) or better, over at least three charts. " +
            "It's a rough measure of the level you're at home on, and it climbs as you get better at harder charts.",
    )
    val exPercent = Explanation(
        "EX%",
        "Your EX score as a share of the most possible, averaged over your plays in the last four weeks. " +
            "EX counts 3 for a Marvelous or O.K., 2 for a Perfect, 1 for a Great and nothing for a Good or Miss, " +
            "so it rewards precise timing much more than the regular score does. 100% means every step Marvelous.",
    )

    fun timing(bias: TimingBias): Explanation {
        val early = bias.fastShare.formatPercent(0)
        val late = (1 - bias.fastShare).formatPercent(0)
        val what = "Over your last ${bias.plays} plays, $early of your timing errors were FAST (early) and $late SLOW (late). " +
            "Only Perfects, Greats and Goods are counted; Marvelous is on time."
        val advice = when (bias.lean) {
            TimingBias.Lean.EARLY -> "That's a clear lean early. Adjust your timing offset so the arrows reach the targets a little " +
                "later, and your natural timing will land on the beat. Or wait a touch longer before each step."
            TimingBias.Lean.LATE -> "That's a clear lean late. Adjust your timing offset so the arrows reach the targets a little " +
                "earlier, and your natural timing will land on the beat. Or step a touch sooner."
            TimingBias.Lean.EVEN -> if (bias.total < TimingBias.MIN_JUDGMENTS) {
                "That's too few timing errors to show a pattern yet."
            } else {
                "That's balanced, so your offset is fine."
            }
        }
        return Explanation("Timing", "$what\n\n$advice")
    }
}

@Composable
fun ExplanationDialog(explanation: Explanation, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
        title = { Text(explanation.title) },
        text = { Text(explanation.body) },
    )
}

/** The six headline numbers of a player's log, as on the home page. */
@Composable
fun OverviewTiles(o: Overview, onExplain: (Explanation) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Plays", o.totalPlays.toString(), Modifier.weight(1f))
            StatTile("Sessions", o.sessions.toString(), Modifier.weight(1f))
            StatTile("AAA", o.aaaCharts.toString(), Modifier.weight(1f), accent = NeonYellow)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Charts", o.chartsPlayed.toString(), Modifier.weight(1f))
            StatTile("Full combos", o.fullComboCharts.toString(), Modifier.weight(1f))
            StatTile(
                "Comfort level", o.comfortLevel?.toString() ?: "—", Modifier.weight(1f),
                accent = NeonMagenta, onClick = { onExplain(Explanations.comfortLevel) },
            )
        }
    }
}

/** Timing over the last few plays: "Even", "68% early" or "71% late", highlighted when it leans. */
@Composable
fun TimingTile(bias: TimingBias?, modifier: Modifier = Modifier, onExplain: (Explanation) -> Unit) {
    val value = when (bias?.lean) {
        null -> "—"
        TimingBias.Lean.EARLY -> "${bias.fastShare.formatPercent(0)} early"
        TimingBias.Lean.LATE -> "${(1 - bias.fastShare).formatPercent(0)} late"
        TimingBias.Lean.EVEN -> "Even"
    }
    val leaning = bias != null && bias.lean != TimingBias.Lean.EVEN
    StatTile(
        "Timing", value, modifier,
        accent = if (leaning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        onClick = bias?.let { b -> { onExplain(Explanations.timing(b)) } },
    )
}

/** A highlighted recommendation, shown only when recent timing leans clearly one way. */
@Composable
fun TimingAdvice(bias: TimingBias?, onExplain: (Explanation) -> Unit) {
    if (bias == null || bias.lean == TimingBias.Lean.EVEN) return
    val early = bias.lean == TimingBias.Lean.EARLY
    val share = if (early) bias.fastShare else 1 - bias.fastShare
    Card(
        onClick = { onExplain(Explanations.timing(bias)) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "You're stepping ${if (early) "early" else "late"}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                "${share.formatPercent(0)} of your timing errors in the last ${bias.plays} plays were ${if (early) "FAST" else "SLOW"}. " +
                    "Consider changing your timing offset so the arrows arrive a little ${if (early) "later" else "earlier"}. Tap for more.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

private val sessionDate = DateTimeFormatter.ofPattern("EEEE, MMM d").withZone(ZoneId.systemDefault())

/** One visit to the arcade, summarised. */
@Composable
fun SessionCard(
    startedAt: Instant,
    endedAt: Instant,
    playCount: Int,
    highestLevel: Int,
    averageScore: Int,
    bestScore: Int,
    averageExRatio: Double,
    newBests: Int,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(sessionDate.format(startedAt), style = MaterialTheme.typography.titleMedium)
            Text(
                "$playCount plays · ${Duration.between(startedAt, endedAt).toMinutes()} min · up to level $highestLevel",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Average ${averageScore.formatScore()} · best ${bestScore.formatScore()} · EX ${averageExRatio.formatPercent()}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (newBests > 0) Text("$newBests new personal best${if (newBests > 1) "s" else ""}", color = NeonYellow)
        }
    }
}
