package com.tehoubi.ddrtracker.ui.compare

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tehoubi.ddrtracker.core.api.ChartSummary
import com.tehoubi.ddrtracker.core.api.PlayerProfile
import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.PlayStyle
import com.tehoubi.ddrtracker.core.songs.Song
import com.tehoubi.ddrtracker.ui.components.EmptyState
import com.tehoubi.ddrtracker.ui.components.SectionHeader
import com.tehoubi.ddrtracker.ui.components.SongJacket
import com.tehoubi.ddrtracker.ui.components.Tag
import com.tehoubi.ddrtracker.ui.components.color
import com.tehoubi.ddrtracker.ui.components.formatScore

@Composable
fun CompareScreen(
    onOpenSettings: () -> Unit,
    onOpenPlayer: (playerId: String, name: String) -> Unit,
    onOpenChart: (songKey: String, style: String, difficulty: String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: CompareViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CompareContent(
        state = state,
        onQuery = viewModel::onQueryChanged,
        onOpenPlayer = onOpenPlayer,
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
    onOpenPlayer: (String, String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenChart: (String, String, String) -> Unit,
    onErrorShown: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it); onErrorShown() } }
    Scaffold(
        modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
        topBar = { TopAppBar(title = { Text("Compare") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (!state.connected) {
            EmptyState(
                "Compare with other players",
                "Sign up on a leaderboard server to see chart rankings, other players' stats and how you stack up.",
                Modifier.padding(padding).padding(top = 48.dp),
            ) { Button(onClick = onOpenSettings) { Text("Set up in Settings") } }
        } else {
            Finder(state, padding, onQuery, onOpenPlayer, onOpenChart)
        }
    }
}

@Composable
private fun Finder(
    state: CompareUiState,
    padding: PaddingValues,
    onQuery: (String) -> Unit,
    onOpenPlayer: (String, String) -> Unit,
    onOpenChart: (String, String, String) -> Unit,
) {
    val searching = state.query.trim().length >= 2
    LazyColumn(
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQuery,
                placeholder = { Text("Find a player or song") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = if (state.query.isNotEmpty()) ({ IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, "Clear") } }) else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
        if (searching) {
            item { SectionHeader("Players", Modifier.padding(horizontal = 16.dp)) }
            if (state.results.isEmpty()) item { Hint("No players match") }
            items(state.results, key = { "r-" + it.playerId }) { p -> PlayerRow(p.playerId, p.displayName, null, onOpenPlayer) }
            item { SectionHeader("Songs", Modifier.padding(horizontal = 16.dp)) }
            if (state.songs.isEmpty()) item { Hint("No songs match") }
            items(state.songs, key = { "s-" + it.key }) { s -> SongRow(s, onOpenChart) }
            return@LazyColumn
        }
        if (state.rivals.isNotEmpty()) {
            item { SectionHeader("Rivals", Modifier.padding(horizontal = 16.dp)) }
            items(state.rivals, key = { "v-" + it.playerId }) { p -> PlayerRow(p.playerId, p.displayName, null, onOpenPlayer, Icons.Default.Star) }
        }
        if (state.players.isNotEmpty()) {
            item { SectionHeader("Players", Modifier.padding(horizontal = 16.dp)) }
            items(state.players, key = { "a-" + it.playerId }) { p ->
                val you = p.playerId == state.me?.playerId
                PlayerRow(p.playerId, p.displayName, p.summary(), onOpenPlayer, you = you)
            }
        }
        if (state.popular.isNotEmpty()) {
            item { SectionHeader("Popular charts", Modifier.padding(horizontal = 16.dp)) }
            items(state.popular, key = { "c-${it.songKey}/${it.style}/${it.difficulty}" }) { c -> PopularRow(c, onOpenChart) }
        }
    }
}

private fun PlayerProfile.summary(): String =
    "${plays.formatScore()} play${if (plays == 1) "" else "s"} · ${charts.formatScore()} chart${if (charts == 1) "" else "s"}" +
        (averageBest?.let { " · average best ${it.formatScore()}" } ?: "")

@Composable
private fun Hint(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
}

@Composable
private fun PlayerRow(
    id: String,
    name: String,
    detail: String?,
    onOpen: (String, String) -> Unit,
    icon: ImageVector = Icons.Default.Person,
    you: Boolean = false,
) {
    ListItem(
        headlineContent = { Text(if (you) "$name (you)" else name) },
        supportingContent = detail?.let { { Text(it) } },
        leadingContent = { Icon(icon, null) },
        modifier = Modifier.clickable { onOpen(id, name) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** A song from the list, with a chip per chart that opens its history and leaderboard. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SongRow(song: Song, onOpenChart: (String, String, String) -> Unit) {
    val charts = PlayStyle.entries.flatMap { style ->
        Difficulty.entries.mapNotNull { d -> song.level(style, d)?.let { Triple(style, d, it) } }
    }
    ListItem(
        headlineContent = { Text(song.title) },
        supportingContent = {
            Column {
                song.aliases.firstOrNull()?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    charts.forEach { (style, d, level) ->
                        Tag("${d.shortCode(style)} $level", d.color(), Modifier.clickable { onOpenChart(song.key, style.name, d.name) })
                    }
                }
            }
        },
        leadingContent = { SongJacket(song.title, size = 40.dp) },
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
        leadingContent = { SongJacket(c.songTitle, size = 40.dp) },
        trailingContent = { Tag("${d.shortCode(s)} ${c.level}", d.color()) },
        modifier = Modifier.clickable { onOpenChart(c.songKey, c.style, c.difficulty) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}
