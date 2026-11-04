package com.tehoubi.ddrtracker.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.tehoubi.ddrtracker.ui.components.EmptyState
import com.tehoubi.ddrtracker.ui.components.Explanation
import com.tehoubi.ddrtracker.ui.components.ExplanationDialog
import com.tehoubi.ddrtracker.ui.components.OverviewTiles
import com.tehoubi.ddrtracker.ui.components.PlayCard
import com.tehoubi.ddrtracker.ui.components.SectionHeader
import com.tehoubi.ddrtracker.ui.components.SessionCard
import com.tehoubi.ddrtracker.ui.components.TimingAdvice

@Composable
fun HomeScreen(
    onScanClick: () -> Unit,
    onManualClick: () -> Unit,
    onPlayClick: (playId: String) -> Unit,
    onSettingsClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(uiState, onScanClick, onManualClick, onPlayClick, onSettingsClick, contentPadding)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    uiState: HomeUiState,
    onScanClick: () -> Unit,
    onManualClick: () -> Unit,
    onPlayClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    var explaining by remember { mutableStateOf<Explanation?>(null) }
    explaining?.let { ExplanationDialog(it) { explaining = null } }
    Scaffold(
        modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
        topBar = {
            TopAppBar(
                title = { Text("DDR Tracker") },
                actions = {
                    IconButton(onClick = onManualClick) { Icon(Icons.Default.Edit, contentDescription = "Add a play by hand") }
                    IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScanClick,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Scan") },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            uiState.recent.isEmpty() -> EmptyState(
                title = "No plays yet",
                body = "Point the camera at a DDR result screen and the score, judgments and chart are read for you. " +
                    "You can also type a play in by hand.",
                modifier = Modifier.padding(padding).padding(top = 48.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onScanClick) { Text("Scan a result") }
                    OutlinedButton(onClick = onManualClick) { Text("Add by hand") }
                }
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 4.dp,
                    bottom = 96.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                uiState.overview?.let { o ->
                    item { OverviewTiles(o, onExplain = { explaining = it }) }
                }
                uiState.timing?.let { bias -> item { TimingAdvice(bias, onExplain = { explaining = it }) } }
                uiState.lastSession?.let { s ->
                    item { SectionHeader("Last session") }
                    item {
                        SessionCard(
                            s.startedAt, s.endedAt, s.playCount, s.highestLevel, s.averageScore, s.bestScore, s.averageExRatio,
                            newBests = uiState.lastSessionPlays.count { it.id in uiState.newBestIds },
                        )
                    }
                }
                item { SectionHeader("Recent plays") }
                items(uiState.recent, key = { it.id }) { play ->
                    PlayCard(play = play, onClick = { onPlayClick(play.id) }, newBest = play.id in uiState.newBestIds)
                }
            }
        }
    }
}
