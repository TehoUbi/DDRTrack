package com.tehoubi.ddrtracker.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tehoubi.ddrtracker.core.scoring.DdrScoring
import com.tehoubi.ddrtracker.domain.model.Play
import com.tehoubi.ddrtracker.ui.components.JudgmentBar
import com.tehoubi.ddrtracker.ui.components.Tag
import com.tehoubi.ddrtracker.ui.components.color
import com.tehoubi.ddrtracker.ui.components.formatPercent
import com.tehoubi.ddrtracker.ui.components.formatScore
import com.tehoubi.ddrtracker.ui.components.judgmentColors
import com.tehoubi.ddrtracker.ui.theme.NeonGreen
import com.tehoubi.ddrtracker.ui.theme.NeonYellow
import com.tehoubi.ddrtracker.ui.theme.ScoreDigits
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun PlayDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onChart: (Play) -> Unit,
    viewModel: PlayDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PlayDetailContent(state, onBack, onEdit, onChart, onDelete = { viewModel.delete(onBack) })
}

private val playedFormat = DateTimeFormatter.ofPattern("EEEE, MMM d yyyy 'at' h:mm a").withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayDetailContent(
    state: PlayDetailUiState,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onChart: (Play) -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val play = state.play
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(play?.chart?.label ?: "") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (play != null) {
                        IconButton(onClick = { onEdit(play.id) }) { Icon(Icons.Default.Edit, "Edit") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete") }
                    }
                },
            )
        },
    ) { padding ->
        if (play == null) {
            if (!state.isLoading) Text("This play was deleted.", Modifier.padding(padding).padding(24.dp))
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column {
                Text(play.songTitle, style = MaterialTheme.typography.headlineSmall)
                play.artist?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Tag("${play.style.label} ${play.difficulty.label} ${play.level}", play.difficulty.color())
                Tag(play.lamp.label, play.lamp.color())
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    play.grade.label,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color = play.grade.color(),
                )
                Column {
                    Text(play.score.formatScore(), style = ScoreDigits.copy(fontSize = MaterialTheme.typography.headlineMedium.fontSize))
                    Text(
                        "EX ${play.exScore} · ${play.exRatio.formatPercent()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            BestLine(state)
            JudgmentTable(play)
            Text(
                "Played ${playedFormat.format(play.playedAt)} · ${play.source.name.lowercase().replaceFirstChar { it.uppercase() }}" +
                    (play.scanConfidence?.let { " · scan confidence ${(it * 100).toInt()}%" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            play.note?.let { Text(it) }
            OutlinedButton(onClick = { onChart(play) }, modifier = Modifier.fillMaxWidth()) {
                Text("Chart history (${state.attempts} plays)")
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this play?") },
            text = { Text("It will be removed from your history and stats.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BestLine(state: PlayDetailUiState) {
    val play = state.play ?: return
    val text = when {
        state.best?.id == play.id && state.attempts > 1 -> "Personal best · attempt ${state.attempt} of ${state.attempts}"
        state.best?.id == play.id -> "First play of this chart"
        else -> "Best is ${state.best!!.score.formatScore()} (${(play.score - state.best.score).formatSigned()})"
    }
    val delta = state.deltaToPreviousBest
    Column {
        Text(text, color = if (state.best?.id == play.id) NeonYellow else MaterialTheme.colorScheme.onSurface)
        if (delta != null && delta > 0) Text("${delta.formatSigned()} over the previous best", color = NeonGreen)
    }
}

private fun Int.formatSigned(): String = (if (this >= 0) "+" else "−") + kotlin.math.abs(this).formatScore()

@Composable
private fun JudgmentTable(play: Play) {
    val j = play.judgments
    val rows = listOf("Marvelous" to j.marvelous, "Perfect" to j.perfect, "Great" to j.great, "Good" to j.good, "O.K." to j.ok, "Miss" to j.miss)
    val loss = DdrScoring.scoreLoss(j)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            JudgmentBar(j)
            rows.forEachIndexed { i, (name, n) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(judgmentColors[i], CircleShape))
                    Text(name, Modifier.padding(start = 8.dp).weight(1f))
                    loss[name]?.takeIf { it > 0 }?.let {
                        Text("−${it.formatScore()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
                    }
                    Text(n.toString(), style = ScoreDigits)
                }
            }
            Row {
                Text("Max combo", Modifier.weight(1f))
                Text(play.maxCombo.toString(), style = ScoreDigits)
            }
            if (play.fast != null || play.slow != null) {
                Row {
                    Text("Fast / Slow", Modifier.weight(1f))
                    Text("${play.fast ?: "–"} / ${play.slow ?: "–"}", style = ScoreDigits)
                }
            }
        }
    }
}
