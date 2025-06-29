package com.tehoubi.ddrtracker.ui.chart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.tehoubi.ddrtracker.ui.charts.ChartPoint
import com.tehoubi.ddrtracker.ui.charts.LineChart
import com.tehoubi.ddrtracker.ui.components.PlayCard
import com.tehoubi.ddrtracker.ui.components.SectionHeader
import com.tehoubi.ddrtracker.ui.components.StatTile
import com.tehoubi.ddrtracker.ui.components.color
import com.tehoubi.ddrtracker.ui.components.formatScore
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault())

@Composable
fun ChartScreen(
    onBack: () -> Unit,
    onPlayClick: (String) -> Unit,
    viewModel: ChartViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ChartContent(state, onBack, onPlayClick)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartContent(state: ChartUiState, onBack: () -> Unit, onPlayClick: (String) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.title, style = MaterialTheme.typography.titleMedium)
                        Text(state.label, style = MaterialTheme.typography.labelMedium)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        val progress = state.progress ?: return@Scaffold
        val bestIds = progress.bestSteps.map { it.id }.toSet()
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Best", progress.best.score.formatScore(), Modifier.weight(1.4f), accent = progress.best.grade.color())
                    StatTile("Plays", progress.plays.size.toString(), Modifier.weight(1f))
                    StatTile("Gained", "+" + progress.improvement.formatScore(), Modifier.weight(1.2f))
                }
            }
            if (progress.plays.size >= 2) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Score by attempt", style = MaterialTheme.typography.titleMedium)
                            LineChart(
                                points = progress.plays.mapIndexed { i, p ->
                                    ChartPoint(i.toFloat(), p.score.toFloat(), "Attempt ${i + 1} · ${dateFormat.format(p.playedAt)}: ${p.score.formatScore()}")
                                },
                                yFormat = { "%.0fk".format(it / 1000) },
                            )
                        }
                    }
                }
            }
            item { SectionHeader("Every play") }
            items(progress.plays.reversed(), key = { it.id }) { play ->
                PlayCard(play, onClick = { onPlayClick(play.id) }, newBest = play.id in bestIds && play != progress.plays.first())
            }
        }
    }
}
