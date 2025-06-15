package com.tehoubi.ddrtracker.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.PlayStyle
import com.tehoubi.ddrtracker.core.ocr.Severity
import com.tehoubi.ddrtracker.core.ocr.StatLabel
import com.tehoubi.ddrtracker.core.songs.Song
import com.tehoubi.ddrtracker.ui.components.Tag
import com.tehoubi.ddrtracker.ui.components.color
import com.tehoubi.ddrtracker.ui.theme.ScoreDigits
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun ReviewScreen(
    onClose: () -> Unit,
    onSaved: (playId: String) -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReviewContent(
        state = state,
        onEdit = viewModel::edit,
        onTitleChanged = viewModel::onTitleChanged,
        onSuggestionPicked = viewModel::onSuggestionPicked,
        onSave = { viewModel.save(onSaved) },
        onClose = { viewModel.discard(); onClose() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewContent(
    state: ReviewUiState,
    onEdit: ((PlayForm) -> PlayForm) -> Unit,
    onTitleChanged: (String) -> Unit,
    onSuggestionPicked: (Song) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (state.mode) {
                            ReviewMode.SCAN -> "Check the scan"
                            ReviewMode.MANUAL -> "Add a play"
                            ReviewMode.EDIT -> "Edit play"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close") }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = state.check?.canSave == true && !state.saving) {
                        Text("Save")
                    }
                },
            )
        },
    ) { padding ->
        val form = state.form
        if (form == null) {
            Text(
                if (state.mode == ReviewMode.SCAN) "Nothing to review." else "Loading…",
                Modifier.padding(padding).padding(24.dp),
            )
            return@Scaffold
        }
        val check = state.check ?: form.check()
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.mode == ReviewMode.SCAN) ScanSummary(form)
            SongSection(form, check, state.suggestions, onEdit, onTitleChanged, onSuggestionPicked)
            ScoreSection(form, check, onEdit)
            JudgmentSection(form, check, onEdit)
            IssuesCard(check)
            Button(
                onClick = onSave,
                enabled = check.canSave && !state.saving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.mode == ReviewMode.EDIT) "Save changes" else "Save play") }
            Spacer(Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun ScanSummary(form: PlayForm) {
    val flagged = form.lowConfidence.size
    val text = when {
        form.repaired.isNotEmpty() -> "Read the screen and corrected ${form.repaired.size} misread value${if (form.repaired.size > 1) "s" else ""}."
        flagged > 0 -> "Read the screen. $flagged field${if (flagged > 1) "s are" else " is"} highlighted — give them a look."
        else -> "Read the screen. Everything checks out."
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SongSection(
    form: PlayForm,
    check: FormCheck,
    suggestions: List<Song>,
    onEdit: ((PlayForm) -> PlayForm) -> Unit,
    onTitleChanged: (String) -> Unit,
    onSuggestionPicked: (Song) -> Unit,
) {
    OutlinedTextField(
        value = form.title,
        onValueChange = onTitleChanged,
        label = { Text("Song") },
        isError = "title" in check.errors,
        supportingText = flagText("title", form, check),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (suggestions.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { song ->
                SuggestionChip(onClick = { onSuggestionPicked(song) }, label = { Text(song.title) })
            }
        }
    }
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        PlayStyle.entries.forEachIndexed { i, style ->
            SegmentedButton(
                selected = form.style == style,
                onClick = { onEdit { it.copy(style = style) } },
                shape = SegmentedButtonDefaults.itemShape(i, PlayStyle.entries.size),
            ) { Text(style.label) }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Difficulty.entries.forEach { d ->
            FilterChip(
                selected = form.difficulty == d,
                onClick = { onEdit { it.copy(difficulty = d) } },
                label = { Text(d.label) },
                leadingIcon = if (form.difficulty == d) ({ Tag(d.shortCode(form.style), d.color()) }) else null,
            )
        }
    }
    if ("difficulty" in check.errors) {
        Text(check.errors.getValue("difficulty"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        NumberField("Level", form.level, "level", form, check, Modifier.width(110.dp)) { v -> onEdit { it.copy(level = v.take(2)) } }
        PlayedAtField(form.playedAt) { at -> onEdit { it.copy(playedAt = at) } }
    }
}

@Composable
private fun ScoreSection(form: PlayForm, check: FormCheck, onEdit: ((PlayForm) -> PlayForm) -> Unit) {
    NumberField(
        label = "Score",
        value = form.score,
        field = "score",
        form = form,
        check = check,
        modifier = Modifier.fillMaxWidth(),
        large = true,
    ) { v -> onEdit { it.copy(score = v) } }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        check.grade?.let { Tag(it.label, it.color()) }
        check.lamp?.let { Tag(it.label, it.color()) }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = form.failed, onCheckedChange = { f -> onEdit { it.copy(failed = f) } })
            Text("Failed")
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberField(
            "EX score", form.exScore, "exScore", form, check, Modifier.weight(1f),
            placeholder = check.expectedExScore?.toString(),
        ) { v -> onEdit { it.copy(exScore = v) } }
        NumberField("Max combo", form.maxCombo, "maxCombo", form, check, Modifier.weight(1f)) { v -> onEdit { it.copy(maxCombo = v) } }
    }
}

@Composable
private fun JudgmentSection(form: PlayForm, check: FormCheck, onEdit: ((PlayForm) -> PlayForm) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Judgments", style = MaterialTheme.typography.titleMedium)
            StatLabel.JUDGMENTS.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { label ->
                        NumberField(
                            label = label.spellings.last(),
                            value = form.judgment(label),
                            field = label.name.lowercase(),
                            form = form,
                            check = check,
                            modifier = Modifier.weight(1f),
                        ) { v -> onEdit { it.withJudgment(label, v) } }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField("Fast", form.fast, "fast", form, check, Modifier.weight(1f)) { v -> onEdit { it.copy(fast = v) } }
                NumberField("Slow", form.slow, "slow", form, check, Modifier.weight(1f)) { v -> onEdit { it.copy(slow = v) } }
            }
        }
    }
}

@Composable
private fun IssuesCard(check: FormCheck) {
    val issues = check.issues.filter { it.severity >= Severity.INFO }
    if (issues.isEmpty()) return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            issues.forEach { issue ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val warn = issue.severity >= Severity.WARNING
                    Icon(
                        if (warn) Icons.Default.Warning else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                    Text(issue.message, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun flagText(field: String, form: PlayForm, check: FormCheck): (@Composable () -> Unit)? {
    val message = check.errors[field]
        ?: form.repaired[field]?.let { "Corrected from $it" }
        ?: if (field in form.lowConfidence) "Low confidence — check this" else null
    return message?.let { { Text(it) } }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    field: String,
    form: PlayForm,
    check: FormCheck,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    placeholder: String? = null,
    onChange: (String) -> Unit,
) {
    val flagged = field in form.lowConfidence || field in form.repaired
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.digitsOnly()) },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        isError = field in check.errors,
        supportingText = flagText(field, form, check),
        singleLine = true,
        textStyle = if (large) ScoreDigits.copy(fontSize = MaterialTheme.typography.headlineMedium.fontSize) else ScoreDigits,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = if (flagged && field !in check.errors) {
            androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.tertiary,
                unfocusedSupportingTextColor = MaterialTheme.colorScheme.tertiary,
            )
        } else androidx.compose.material3.OutlinedTextFieldDefaults.colors(),
        modifier = modifier,
    )
}

private val dateFormat = DateTimeFormatter.ofPattern("EEE, MMM d yyyy · h:mm a").withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayedAtField(playedAt: Instant, onChange: (Instant) -> Unit) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text(dateFormat.format(playedAt)) }
    if (!open) return
    val zone = ZoneId.systemDefault()
    val local = playedAt.atZone(zone)
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = local.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = { open = false },
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { millis ->
                    val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    onChange(LocalDate.from(date).atTime(local.toLocalTime()).atZone(zone).toInstant())
                }
                open = false
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
    ) { DatePicker(state = pickerState) }
}
