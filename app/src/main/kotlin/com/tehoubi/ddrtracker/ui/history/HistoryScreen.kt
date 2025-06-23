package com.tehoubi.ddrtracker.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.LEVEL_RANGE
import com.tehoubi.ddrtracker.core.model.PlayStyle
import com.tehoubi.ddrtracker.ui.components.EmptyState
import com.tehoubi.ddrtracker.ui.components.PlayCard
import com.tehoubi.ddrtracker.ui.components.formatScore
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(
    onPlayClick: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryContent(state, viewModel::update, viewModel::clear, onPlayClick, contentPadding)
}

private val headerDate = DateTimeFormatter.ofPattern("EEE MMM d, yyyy · h:mm a").withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryContent(
    state: HistoryUiState,
    onFilter: ((HistoryFilter) -> HistoryFilter) -> Unit,
    onClear: () -> Unit,
    onPlayClick: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val f = state.filter
    Scaffold(
        modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
        topBar = {
            TopAppBar(
                title = { Text("History") },
                actions = {
                    if (f.isActive) TextButton(onClick = onClear) { Text("Clear") }
                    SortMenu(f.sort) { s -> onFilter { it.copy(sort = s) } }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = f.query,
                    onValueChange = { q -> onFilter { it.copy(query = q) } },
                    placeholder = { Text("Search songs") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (f.query.isNotEmpty()) ({
                        IconButton(onClick = { onFilter { it.copy(query = "") } }) { Icon(Icons.Default.Close, "Clear search") }
                    }) else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            item { FilterChips(f, onFilter) }
            item { LevelRange(f, onFilter) }
            item {
                Text(
                    if (f.isActive) "${state.shown} of ${state.total} plays" else "${state.total} plays",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (!state.isLoading && state.shown == 0) {
                item {
                    EmptyState(
                        if (state.total == 0) "Nothing logged yet" else "No plays match",
                        if (state.total == 0) "Scanned and typed plays show up here." else "Try loosening the filters.",
                    )
                }
            }
            state.groups.forEach { group ->
                group.session?.let { s ->
                    stickyHeader(key = "session-${s.id}-${group.plays.first().id}") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(headerDate.format(s.startedAt), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(
                                "${s.playCount} plays · avg ${s.averageScore.formatScore()}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(group.plays, key = { it.id }) { play ->
                    PlayCard(
                        play = play,
                        onClick = { onPlayClick(play.id) },
                        newBest = play.id in state.bestIds,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChips(f: HistoryFilter, onFilter: ((HistoryFilter) -> HistoryFilter) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(PlayStyle.entries) { style ->
            FilterChip(
                selected = f.style == style,
                onClick = { onFilter { it.copy(style = if (it.style == style) null else style) } },
                label = { Text(style.code) },
            )
        }
        items(Difficulty.entries) { d ->
            FilterChip(
                selected = d in f.difficulties,
                onClick = { onFilter { it.copy(difficulties = if (d in it.difficulties) it.difficulties - d else it.difficulties + d) } },
                label = { Text(d.label) },
            )
        }
        item {
            FilterChip(
                selected = f.fullCombosOnly,
                onClick = { onFilter { it.copy(fullCombosOnly = !it.fullCombosOnly) } },
                label = { Text("Full combos") },
            )
        }
        item {
            FilterChip(
                selected = f.bestsOnly,
                onClick = { onFilter { it.copy(bestsOnly = !it.bestsOnly) } },
                label = { Text("Bests only") },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LevelRange(f: HistoryFilter, onFilter: ((HistoryFilter) -> HistoryFilter) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            if (f.levels == LEVEL_RANGE) "All levels" else "Levels ${f.levels.first}–${f.levels.last}",
            style = MaterialTheme.typography.labelMedium,
        )
        RangeSlider(
            value = f.levels.first.toFloat()..f.levels.last.toFloat(),
            onValueChange = { r -> onFilter { it.copy(levels = r.start.roundToInt()..r.endInclusive.roundToInt()) } },
            valueRange = LEVEL_RANGE.first.toFloat()..LEVEL_RANGE.last.toFloat(),
            steps = LEVEL_RANGE.last - LEVEL_RANGE.first - 1,
        )
    }
}

@Composable
private fun SortMenu(current: HistorySort, onSort: (HistorySort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text(current.label) }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        HistorySort.entries.forEach { s ->
            DropdownMenuItem(text = { Text(s.label) }, onClick = { onSort(s); open = false })
        }
    }
}
