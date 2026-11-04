package com.tehoubi.ddrtracker.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tehoubi.ddrtracker.core.api.ComparedChart
import com.tehoubi.ddrtracker.core.api.Comparison
import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.PlayStyle
import com.tehoubi.ddrtracker.ui.charts.LampGroupColors
import com.tehoubi.ddrtracker.ui.components.EmptyState
import com.tehoubi.ddrtracker.ui.components.Explanation
import com.tehoubi.ddrtracker.ui.components.ExplanationDialog
import com.tehoubi.ddrtracker.ui.components.OverviewTiles
import com.tehoubi.ddrtracker.ui.components.PlayCard
import com.tehoubi.ddrtracker.ui.components.SectionHeader
import com.tehoubi.ddrtracker.ui.components.SessionCard
import com.tehoubi.ddrtracker.ui.components.SongJacket
import com.tehoubi.ddrtracker.ui.components.Tag
import com.tehoubi.ddrtracker.ui.components.color
import com.tehoubi.ddrtracker.ui.components.formatScore
import com.tehoubi.ddrtracker.ui.theme.ScoreDigits
import kotlin.math.abs

@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onOpenChart: (songKey: String, style: String, difficulty: String) -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PlayerContent(state, onBack, onOpenChart, onFollow = viewModel::follow, onUnfollow = viewModel::unfollow)
}

/** Head-to-head charts shown before "Show all". */
private const val SHARED_PREVIEW = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerContent(
    state: PlayerUiState,
    onBack: () -> Unit,
    onOpenChart: (String, String, String) -> Unit,
    onFollow: () -> Unit = {},
    onUnfollow: () -> Unit = {},
) {
    var explaining by remember { mutableStateOf<Explanation?>(null) }
    var allShared by remember { mutableStateOf(false) }
    explaining?.let { ExplanationDialog(it) { explaining = null } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isMe) "${state.name} (you)" else state.name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (!state.isMe) {
                        TextButton(onClick = if (state.following) onUnfollow else onFollow) {
                            Text(if (state.following) "Unfollow" else "Follow")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            state.error != null -> EmptyState("Couldn't load this player", state.error, Modifier.padding(padding).padding(top = 48.dp))
            state.overview == null || state.overview.totalPlays == 0 -> EmptyState(
                "No plays yet", "${state.name} hasn't uploaded any plays to this server.", Modifier.padding(padding).padding(top = 48.dp),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(top = padding.calculateTopPadding() + 4.dp, start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { OverviewTiles(state.overview, onExplain = { explaining = it }) }
                state.comparison?.let { c ->
                    item { SectionHeader("Head to head") }
                    item { Record(c) }
                    val shown = if (allShared) c.charts else c.charts.take(SHARED_PREVIEW)
                    items(shown, key = { "h-${it.songKey}/${it.style}/${it.difficulty}" }) { chart ->
                        ComparedRow(chart, c.rival.displayName) { onOpenChart(chart.songKey, chart.style, chart.difficulty) }
                    }
                    if (c.charts.size > SHARED_PREVIEW) {
                        item {
                            TextButton(onClick = { allShared = !allShared }) {
                                Text(if (allShared) "Show fewer" else "Show all ${c.charts.size} charts in common")
                            }
                        }
                    }
                }
                state.lastSession?.let { s ->
                    item { SectionHeader("Last session") }
                    item {
                        SessionCard(
                            s.startedAt, s.endedAt, s.playCount, s.highestLevel, s.averageScore, s.bestScore, s.averageExRatio,
                            newBests = s.plays.count { it.id in state.newBestIds },
                        )
                    }
                }
                item { SectionHeader("Recent plays") }
                items(state.recent, key = { "p-" + it.id }) { play ->
                    PlayCard(
                        play = play,
                        onClick = { onOpenChart(play.songKey, play.style.name, play.difficulty.name) },
                        newBest = play.id in state.newBestIds,
                    )
                }
            }
        }
    }
}

/** Win / tie / loss split as one bar, with the counts as text beside it. */
@Composable
private fun Record(c: Comparison) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${c.charts.size} charts in common", style = MaterialTheme.typography.titleMedium)
            if (c.charts.isEmpty()) {
                Text("Once you've both played the same chart it shows up here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            val total = c.charts.size
            val gap = MaterialTheme.colorScheme.surfaceContainerHigh
            Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(4.dp))) {
                listOf(c.wins to LampGroupColors.fullCombo, c.ties to LampGroupColors.clear, c.losses to LampGroupColors.failed)
                    .filter { it.first > 0 }
                    .forEachIndexed { i, (n, color) ->
                        if (i > 0) Box(Modifier.width(2.dp).height(12.dp).background(gap))
                        Box(Modifier.weight(n.toFloat() / total).height(12.dp).background(color))
                    }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend("You win ${c.wins}", LampGroupColors.fullCombo)
                Legend("Tied ${c.ties}", LampGroupColors.clear)
                Legend("${c.rival.displayName} wins ${c.losses}", LampGroupColors.failed)
            }
        }
    }
}

@Composable
private fun Legend(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(10.dp).height(10.dp).background(color, RoundedCornerShape(2.dp)))
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun ComparedRow(c: ComparedChart, rivalName: String, onClick: () -> Unit) {
    val d = Difficulty.valueOf(c.difficulty)
    val s = PlayStyle.valueOf(c.style)
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SongJacket(c.songTitle, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(c.songTitle, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Tag("${d.shortCode(s)} ${c.level}", d.color())
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("You ${c.playerScore.formatScore()}", style = ScoreDigits)
                Text("$rivalName ${c.rivalScore.formatScore()}", style = ScoreDigits, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val sign = when { c.delta > 0 -> "▲ +"; c.delta < 0 -> "▼ −"; else -> "= " }
                Text("$sign${abs(c.delta).formatScore()}", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
