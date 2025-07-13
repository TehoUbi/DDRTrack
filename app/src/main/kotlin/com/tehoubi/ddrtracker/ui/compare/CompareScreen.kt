package com.tehoubi.ddrtracker.ui.compare

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tehoubi.ddrtracker.core.api.ChartSummary
import com.tehoubi.ddrtracker.core.api.ComparedChart
import com.tehoubi.ddrtracker.core.api.Comparison
import com.tehoubi.ddrtracker.core.api.PlayerSummary
import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.PlayStyle
import com.tehoubi.ddrtracker.ui.charts.LampGroupColors
import com.tehoubi.ddrtracker.ui.components.EmptyState
import com.tehoubi.ddrtracker.ui.components.SectionHeader
import com.tehoubi.ddrtracker.ui.components.Tag
import com.tehoubi.ddrtracker.ui.components.color
import com.tehoubi.ddrtracker.ui.components.formatScore
import com.tehoubi.ddrtracker.ui.theme.ScoreDigits
import kotlin.math.abs

@Composable
fun CompareScreen(
    onOpenSettings: () -> Unit,
    onOpenChart: (songKey: String, style: String, difficulty: String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: CompareViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(enabled = state.comparison != null) { viewModel.close() }
    CompareContent(
        state = state,
        onQuery = viewModel::onQueryChanged,
        onOpen = viewModel::open,
        onClose = viewModel::close,
        onSaveRival = viewModel::saveRival,
        onRemoveRival = viewModel::removeRival,
        onOpenSettings = onOpenSettings,
        onOpenChart = onOpenChart,
        onErrorShown = viewModel::onErrorShown,
        contentPadding = contentPadding,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareContent(
    state: CompareUiState,
    onQuery: (String) -> Unit,
    onOpen: (PlayerSummary) -> Unit,
    onClose: () -> Unit,
    onSaveRival: (PlayerSummary) -> Unit,
    onRemoveRival: (PlayerSummary) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenChart: (String, String, String) -> Unit,
    onErrorShown: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it); onErrorShown() } }
    val comparison = state.comparison
    Scaffold(
        modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
        topBar = {
            TopAppBar(
                title = { Text(comparison?.let { "vs ${it.rival.displayName}" } ?: "Compare") },
                navigationIcon = {
                    if (comparison != null) IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (comparison != null) {
                        val saved = state.rivals.any { it.playerId == comparison.rival.playerId }
                        TextButton(onClick = { if (saved) onRemoveRival(comparison.rival) else onSaveRival(comparison.rival) }) {
                            Text(if (saved) "Unfollow" else "Follow")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            !state.connected -> EmptyState(
                "Compare with other players",
                "Sign up on a leaderboard server to see chart rankings and go head to head with rivals.",
                Modifier.padding(padding).padding(top = 48.dp),
            ) { Button(onClick = onOpenSettings) { Text("Set up in Settings") } }
            comparison != null -> ComparisonView(comparison, padding, onOpenChart)
            else -> Finder(state, padding, onQuery, onOpen, onOpenChart)
        }
        if (state.loading) Box(Modifier.fillMaxWidth().padding(padding).padding(top = 120.dp), Alignment.Center) { CircularProgressIndicator() }
    }
}

@Composable
private fun Finder(
    state: CompareUiState,
    padding: PaddingValues,
    onQuery: (String) -> Unit,
    onOpen: (PlayerSummary) -> Unit,
    onOpenChart: (String, String, String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQuery,
                placeholder = { Text("Find a player") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = if (state.query.isNotEmpty()) ({ IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, "Clear") } }) else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
        items(state.results, key = { "r-" + it.playerId }) { p -> PlayerRow(p, onOpen) }
        if (state.rivals.isNotEmpty()) {
            item { SectionHeader("Rivals", Modifier.padding(horizontal = 16.dp)) }
            items(state.rivals, key = { "v-" + it.playerId }) { p -> PlayerRow(p, onOpen, Icons.Default.Star) }
        }
        if (state.popular.isNotEmpty()) {
            item { SectionHeader("Popular charts", Modifier.padding(horizontal = 16.dp)) }
            items(state.popular, key = { "c-${it.songKey}/${it.style}/${it.difficulty}" }) { c -> PopularRow(c, onOpenChart) }
        }
    }
}

@Composable
private fun PlayerRow(p: PlayerSummary, onOpen: (PlayerSummary) -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Person) {
    ListItem(
        headlineContent = { Text(p.displayName) },
        leadingContent = { Icon(icon, null) },
        modifier = Modifier.clickable { onOpen(p) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun PopularRow(c: ChartSummary, onOpenChart: (String, String, String) -> Unit) {
    val d = Difficulty.valueOf(c.difficulty)
    val s = PlayStyle.valueOf(c.style)
    ListItem(
        headlineContent = { Text(c.songTitle) },
        supportingContent = { Text("${c.players} players · top ${c.topScore.formatScore()}") },
        leadingContent = { Tag("${d.shortCode(s)} ${c.level}", d.color()) },
        modifier = Modifier.clickable { onOpenChart(c.songKey, c.style, c.difficulty) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun ComparisonView(c: Comparison, padding: PaddingValues, onOpenChart: (String, String, String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Record(c) }
        if (c.charts.isEmpty()) {
            item { EmptyState("No charts in common", "Once you've both played the same chart it shows up here.") }
        }
        items(c.charts, key = { "${it.songKey}/${it.style}/${it.difficulty}" }) { chart ->
            ComparedRow(chart, c.rival.displayName) { onOpenChart(chart.songKey, chart.style, chart.difficulty) }
        }
    }
}

/** Win / tie / loss split as one bar, with the counts as text beside it. */
@Composable
private fun Record(c: Comparison) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${c.charts.size} charts in common", style = MaterialTheme.typography.titleMedium)
            val total = c.charts.size.coerceAtLeast(1)
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
private fun Legend(text: String, color: androidx.compose.ui.graphics.Color) {
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
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
