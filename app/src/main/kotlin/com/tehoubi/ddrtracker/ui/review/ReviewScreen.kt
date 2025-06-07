package com.tehoubi.ddrtracker.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import com.tehoubi.ddrtracker.core.ocr.StatLabel
import com.tehoubi.ddrtracker.ui.theme.ScoreDigits

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    onClose: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Check the scan") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.discard(); onClose() }) {
                        Icon(Icons.Default.Close, contentDescription = "Discard")
                    }
                },
            )
        },
    ) { padding ->
        val d = draft ?: return@Scaffold
        val r = d.scan.result
        val play = viewModel.toPlay(d)
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(r.title?.value ?: "Unknown song", style = MaterialTheme.typography.headlineSmall)
            Text(
                listOfNotNull(r.style?.value?.label, r.difficulty?.value?.label, r.level?.value?.toString())
                    .joinToString(" · "),
            )
            Text(r.score?.value?.let { "%,d".format(it) } ?: "No score", style = ScoreDigits.copy(fontSize = MaterialTheme.typography.displaySmall.fontSize))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StatLabel.entries.filter { it != StatLabel.SCORE }.forEach { label ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label.spellings.first())
                            Text(r.stats[label]?.value?.toString() ?: "—", style = ScoreDigits)
                        }
                    }
                }
            }
            d.scan.issues.forEach { Text("• ${it.message}", color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { play?.let { viewModel.save(it, onSaved) } },
                enabled = play != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save play") }
        }
    }
}
