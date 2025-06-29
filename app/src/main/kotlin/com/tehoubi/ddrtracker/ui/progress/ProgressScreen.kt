package com.tehoubi.ddrtracker.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tehoubi.ddrtracker.ui.charts.ChartBar
import com.tehoubi.ddrtracker.ui.charts.ChartPoint
import com.tehoubi.ddrtracker.ui.charts.ColumnChart
import com.tehoubi.ddrtracker.ui.charts.LampGroupColors
import com.tehoubi.ddrtracker.ui.charts.LineChart
import com.tehoubi.ddrtracker.ui.charts.StackRow
import com.tehoubi.ddrtracker.ui.charts.StackSegment
import com.tehoubi.ddrtracker.ui.charts.StackedRows
import com.tehoubi.ddrtracker.ui.components.EmptyState
import com.tehoubi.ddrtracker.ui.components.SectionHeader
import com.tehoubi.ddrtracker.ui.components.StatTile
import com.tehoubi.ddrtracker.ui.components.formatPercent
import com.tehoubi.ddrtracker.ui.components.formatScore
import com.tehoubi.ddrtracker.ui.theme.ScoreDigits
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ProgressScreen(contentPadding: PaddingValues = PaddingValues(), viewModel: ProgressViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProgressContent(state, contentPadding)
}

private val weekFormat = DateTimeFormatter.ofPattern("MMM d")
private val monthFormat = DateTimeFormatter.ofPattern("MMM yyyy").withZone(ZoneId.systemDefault())
private val sessionFormat = DateTimeFormatter.ofPattern("EEE MMM d").withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressContent(state: ProgressUiState, contentPadding: PaddingValues = PaddingValues()) {
    Scaffold(
        modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
        topBar = { TopAppBar(title = { Text("Progress") }) },
    ) { padding ->
        if (!state.isLoading && state.playCount == 0) {
            EmptyState("No progress yet", "Charts appear once you've logged a few plays.", Modifier.padding(padding).padding(top = 48.dp))
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Comfort level", state.comfortLevel?.toString() ?: "—", Modifier.weight(1f))
                    StatTile("EX%, last 4 weeks", state.recentExRatio?.formatPercent() ?: "—", Modifier.weight(1.3f))
                    StatTile("Early misses", state.fastShare?.formatPercent(0) ?: "—", Modifier.weight(1f))
                }
            }
            if (state.weeks.isNotEmpty()) {
                item {
                    ChartCard("Plays per week", "Last ${state.weeks.size} weeks") {
                        ColumnChart(
                            bars = state.weeks.map {
                                ChartBar(weekFormat.format(it.weekStart), it.plays.toFloat(), "Week of ${weekFormat.format(it.weekStart)}: ${it.plays} plays")
                            },
                            xLabels = weekFormat.format(state.weeks.first().weekStart) to weekFormat.format(state.weeks.last().weekStart),
                        )
                    }
                }
                val ex = state.weeks.filter { it.averageExRatio != null }
                if (ex.size >= 2) {
                    item {
                        ChartCard("Accuracy", "Average EX score as a share of the maximum, by week") {
                            LineChart(
                                points = ex.map {
                                    ChartPoint(
                                        it.weekStart.toEpochDay().toFloat(),
                                        it.averageExRatio!!.toFloat() * 100,
                                        "Week of ${weekFormat.format(it.weekStart)}: ${it.averageExRatio!!.formatPercent()}",
                                    )
                                },
                                yFormat = { "%.0f%%".format(it) },
                            )
                        }
                    }
                }
            }
            if (state.comfortHistory.size >= 2) {
                item {
                    ChartCard("Comfort level", "Highest level where your bests average AA, month by month") {
                        LineChart(
                            points = state.comfortHistory.map {
                                ChartPoint(it.at.epochSecond.toFloat(), it.value.toFloat(), "${monthFormat.format(it.at)}: level ${it.value.toInt()}")
                            },
                            step = true,
                            height = 120.dp,
                        )
                    }
                }
            }
            if (state.levelLamps.isNotEmpty()) {
                item {
                    ChartCard("Best clear by level", "Charts at each level, by your best lamp") {
                        StackedRows(
                            rows = state.levelLamps.map {
                                StackRow(
                                    "Lv ${it.level}",
                                    listOf(
                                        StackSegment(it.perfect, LampGroupColors.perfect, "PFC+"),
                                        StackSegment(it.fullCombo, LampGroupColors.fullCombo, "FC"),
                                        StackSegment(it.clear, LampGroupColors.clear, "clear"),
                                        StackSegment(it.failed, LampGroupColors.failed, "failed"),
                                    ),
                                )
                            },
                            legend = listOf(
                                "PFC or better" to LampGroupColors.perfect,
                                "Full combo" to LampGroupColors.fullCombo,
                                "Clear" to LampGroupColors.clear,
                                "Failed" to LampGroupColors.failed,
                            ),
                        )
                    }
                }
                item { LevelTable(state) }
            }
            if (state.sessions.isNotEmpty()) {
                item { SectionHeader("Recent sessions") }
                items(state.sessions, key = { it.id }) { s ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(sessionFormat.format(s.startedAt), Modifier.weight(1f))
                        Text("${s.playCount} plays", Modifier.width(72.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(s.averageScore.formatScore(), style = ScoreDigits)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

/** The same numbers as the lamp chart, as a table. */
@Composable
private fun LevelTable(state: ProgressUiState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("By level", style = MaterialTheme.typography.titleMedium)
            Row {
                listOf("Level" to 1f, "Charts" to 1f, "Avg best" to 1.6f, "AA+" to 0.8f, "FC" to 0.8f).forEach { (h, w) ->
                    Text(h, Modifier.weight(w), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            state.levels.sortedByDescending { it.level }.forEach { l ->
                Row {
                    Text(l.level.toString(), Modifier.weight(1f), style = ScoreDigits)
                    Text(l.charts.toString(), Modifier.weight(1f), style = ScoreDigits)
                    Text(l.averageBest.formatScore(), Modifier.weight(1.6f), style = ScoreDigits)
                    Text(l.aaOrBetter.toString(), Modifier.weight(0.8f), style = ScoreDigits)
                    Text(l.fullCombos.toString(), Modifier.weight(0.8f), style = ScoreDigits)
                }
            }
        }
    }
}
