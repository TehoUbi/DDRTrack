package com.tehoubi.ddrtracker.ui.chart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tehoubi.ddrtracker.core.api.Leaderboard
import com.tehoubi.ddrtracker.core.api.LeaderboardEntry
import com.tehoubi.ddrtracker.core.model.Grade
import com.tehoubi.ddrtracker.ui.charts.ChartPoint
import com.tehoubi.ddrtracker.ui.charts.LineChart
import com.tehoubi.ddrtracker.ui.components.PlayCard
import com.tehoubi.ddrtracker.ui.components.SectionHeader
import com.tehoubi.ddrtracker.ui.components.StatTile
import com.tehoubi.ddrtracker.ui.components.color
import com.tehoubi.ddrtracker.ui.components.formatPercent
import com.tehoubi.ddrtracker.ui.components.formatScore
import com.tehoubi.ddrtracker.ui.theme.ScoreDigits
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
    ChartContent(state, onBack, onPlayClick, onRefresh = viewModel::refresh)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartContent(state: ChartUiState, onBack: () -> Unit, onPlayClick: (String) -> Unit, onRefresh: () -> Unit = {}) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(state.label, style = MaterialTheme.typography.labelMedium)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (state.board != BoardState.NotConnected) IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh leaderboard") }
                },
            )
        },
    ) { padding ->
        val progress = state.progress
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (progress != null) {
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
            }
            item { LeaderboardCard(state.board) }
            if (progress != null) {
                val bestIds = progress.bestSteps.map { it.id }.toSet()
                item { SectionHeader("Every play") }
                items(progress.plays.reversed(), key = { it.id }) { play ->
                    PlayCard(play, onClick = { onPlayClick(play.id) }, newBest = play.id in bestIds && play != progress.plays.first())
                }
            }
        }
    }
}

@Composable
private fun LeaderboardCard(board: BoardState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Leaderboard", style = MaterialTheme.typography.titleMedium)
            when (board) {
                BoardState.NotConnected -> Muted("Sign up on a leaderboard server in Settings to see how you rank.")
                BoardState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                BoardState.Empty -> Muted("Nobody has uploaded this chart yet.")
                is BoardState.Error -> Muted("Couldn't load the leaderboard: ${board.message}")
                is BoardState.Loaded -> Board(board.board)
            }
        }
    }
}

@Composable
private fun Board(b: Leaderboard) {
    val you = b.you
    Muted(
        if (you != null) {
            "You're #${you.rank} of ${b.players}" + (b.youBeat?.let { " · ahead of ${it.formatPercent(0)} of players" } ?: "")
        } else "${b.players} player${if (b.players == 1) "" else "s"}"
    )
    b.entries.forEach { e -> EntryRow(e, isYou = e.playerId == you?.playerId) }
    if (you != null && b.entries.none { it.playerId == you.playerId }) {
        HorizontalDivider()
        EntryRow(you, isYou = true)
    }
}

@Composable
private fun EntryRow(e: LeaderboardEntry, isYou: Boolean) {
    val grade = runCatching { Grade.valueOf(e.grade) }.getOrNull()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("#${e.rank}", style = ScoreDigits, modifier = Modifier.width(44.dp))
        Text(
            if (isYou) "${e.displayName} (you)" else e.displayName,
            fontWeight = if (isYou) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        grade?.let { Text(it.label, color = it.color(), fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp)) }
        Text(e.score.formatScore(), style = ScoreDigits)
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
