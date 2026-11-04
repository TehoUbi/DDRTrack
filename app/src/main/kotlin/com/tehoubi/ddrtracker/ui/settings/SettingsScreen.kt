package com.tehoubi.ddrtracker.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tehoubi.ddrtracker.core.ocr.ResultScreenParser
import com.tehoubi.ddrtracker.sync.BackupStatus

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsContent(
        state = state,
        onBack = onBack,
        actions = SettingsActions(
            setAutoCapture = viewModel::setAutoCapture,
            setPlayerSide = viewModel::setPlayerSide,
            setAutoSubmit = viewModel::setAutoSubmit,
            onServerUrlChanged = viewModel::onServerUrlChanged,
            onNameChanged = viewModel::onNameChanged,
            testConnection = viewModel::testConnection,
            register = viewModel::register,
            rename = viewModel::rename,
            signOut = viewModel::signOut,
            retryRejected = viewModel::retryRejected,
            checkSongList = viewModel::checkSongList,
            setTimingAdvice = viewModel::setTimingAdvice,
            onRecoveryCodeChanged = viewModel::onRecoveryCodeChanged,
            recover = viewModel::recover,
            makeRecoveryCode = viewModel::makeRecoveryCode,
            onRecoveryCodeShown = viewModel::onRecoveryCodeShown,
            setBackup = viewModel::setBackup,
            backupNow = viewModel::backupNow,
            exportCsv = viewModel::exportCsv,
            exportBackup = viewModel::exportBackup,
            restore = viewModel::restore,
            onMessageShown = viewModel::onMessageShown,
        ),
    )
}

class SettingsActions(
    val setAutoCapture: (Boolean) -> Unit = {},
    val setPlayerSide: (ResultScreenParser.Side) -> Unit = {},
    val setAutoSubmit: (Boolean) -> Unit = {},
    val onServerUrlChanged: (String) -> Unit = {},
    val onNameChanged: (String) -> Unit = {},
    val testConnection: () -> Unit = {},
    val register: () -> Unit = {},
    val rename: () -> Unit = {},
    val signOut: () -> Unit = {},
    val retryRejected: () -> Unit = {},
    val checkSongList: () -> Unit = {},
    val setTimingAdvice: (Boolean) -> Unit = {},
    val onRecoveryCodeChanged: (String) -> Unit = {},
    val recover: () -> Unit = {},
    val makeRecoveryCode: () -> Unit = {},
    val onRecoveryCodeShown: () -> Unit = {},
    val setBackup: (Boolean) -> Unit = {},
    val backupNow: () -> Unit = {},
    val exportCsv: (Uri) -> Unit = {},
    val exportBackup: (Uri) -> Unit = {},
    val restore: (Uri) -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(state: SettingsUiState, onBack: () -> Unit, actions: SettingsActions) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); actions.onMessageShown() }
    }
    val settings = state.settings
    val account = settings.account
    state.newRecoveryCode?.let { RecoveryCodeDialog(it, actions.onRecoveryCodeShown) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SettingsHeader("Scanning")
            ListItem(
                headlineContent = { Text("Capture automatically") },
                supportingContent = { Text("Take the photo as soon as the live reading settles") },
                trailingContent = { Switch(checked = settings.autoCapture, onCheckedChange = actions.setAutoCapture) },
            )
            ListItem(
                headlineContent = { Text("Your side") },
                supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("On two-player screens, which table is yours")
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            val sides = listOf(ResultScreenParser.Side.AUTO to "Auto", ResultScreenParser.Side.LEFT to "1P", ResultScreenParser.Side.RIGHT to "2P")
                            sides.forEachIndexed { i, (side, label) ->
                                SegmentedButton(
                                    selected = settings.playerSide == side,
                                    onClick = { actions.setPlayerSide(side) },
                                    shape = SegmentedButtonDefaults.itemShape(i, sides.size),
                                ) { Text(label) }
                            }
                        }
                    }
                },
            )
            ListItem(
                headlineContent = { Text("Song list") },
                supportingContent = {
                    Text(
                        "%,d songs to match titles against".format(state.songCount) +
                            (state.songsCheckedAt?.let { " · checked ${syncedFormat.format(java.time.Instant.ofEpochMilli(it))}" } ?: "") +
                            ". New songs are added weekly.",
                    )
                },
                trailingContent = { TextButton(onClick = actions.checkSongList, enabled = !state.busy) { Text("Check now") } },
            )
            ListItem(
                headlineContent = { Text("Timing advice") },
                supportingContent = { Text("Point it out when your recent plays lean early or late") },
                trailingContent = { Switch(checked = settings.timingAdvice, onCheckedChange = actions.setTimingAdvice) },
            )
            HorizontalDivider()

            SettingsHeader("Leaderboard")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Compare scores with other players on a leaderboard server. Run your own (see the README) " +
                        "or use one a friend hosts. Without one, everything stays on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.serverUrlDraft,
                    onValueChange = actions.onServerUrlChanged,
                    label = { Text("Server URL") },
                    placeholder = { Text("http://192.168.1.20:8080") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = account == null,
                )
                OutlinedTextField(
                    value = state.nameDraft,
                    onValueChange = actions.onNameChanged,
                    label = { Text("Display name") },
                    supportingText = { Text("2–24 letters, digits, spaces or ._-") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (account == null) {
                        OutlinedButton(onClick = actions.testConnection, enabled = !state.busy && state.serverUrlDraft.isNotBlank()) { Text("Test") }
                        Button(
                            onClick = actions.register,
                            enabled = !state.busy && state.serverUrlDraft.isNotBlank() && state.nameDraft.trim().length >= 2,
                        ) { Text("Sign up") }
                    } else {
                        Button(
                            onClick = actions.rename,
                            enabled = !state.busy && state.nameDraft.trim() != account.displayName && state.nameDraft.trim().length >= 2,
                        ) { Text("Rename") }
                        TextButton(onClick = actions.signOut, enabled = !state.busy) { Text("Sign out") }
                    }
                }
                if (account == null) {
                    Text(
                        "Already have an account on this server? Restore it with its recovery code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = state.recoveryCodeDraft,
                        onValueChange = actions.onRecoveryCodeChanged,
                        label = { Text("Recovery code") },
                        placeholder = { Text("XXXXX-XXXXX-XXXXX-XXXXX-XXXXX") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        onClick = actions.recover,
                        enabled = !state.busy && state.serverUrlDraft.isNotBlank() && state.recoveryCodeDraft.isNotBlank(),
                    ) { Text("Restore account") }
                }
            }
            if (account != null) {
                ListItem(
                    headlineContent = { Text("Recovery code") },
                    supportingContent = { Text("Signs you back in to this account on a new phone. Making a new one replaces the old.") },
                    trailingContent = { TextButton(onClick = actions.makeRecoveryCode, enabled = !state.busy) { Text("New code") } },
                )
            }
            if (account != null) {
                ListItem(
                    headlineContent = { Text("Upload plays automatically") },
                    supportingContent = {
                        val u = state.uploads
                        Text("${u.submitted} uploaded · ${u.pending} waiting" + if (u.rejected > 0) " · ${u.rejected} not accepted" else "")
                    },
                    trailingContent = { Switch(checked = settings.autoSubmit, onCheckedChange = actions.setAutoSubmit) },
                )
                if (state.uploads.rejected > 0) {
                    TextButton(onClick = actions.retryRejected, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text("Retry plays that weren't accepted")
                    }
                }
            }
            HorizontalDivider()

            SettingsHeader("Backup")
            BackupSection(state, actions)
            HorizontalDivider()

            SettingsHeader("Your data")
            DataSection(state, actions)
        }
    }
}

@Composable
private fun DataSection(state: SettingsUiState, actions: SettingsActions) {
    val stamp = java.time.LocalDate.now().toString()
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let(actions.exportCsv) }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(actions.exportBackup) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(actions.restore) }
    ListItem(
        headlineContent = { Text("Export as CSV") },
        supportingContent = { Text("Every play, one row each, for spreadsheets") },
        modifier = Modifier.clickable(enabled = !state.busy) { csv.launch("ddr-plays-$stamp.csv") },
    )
    ListItem(
        headlineContent = { Text("Back up to a file") },
        supportingContent = { Text("Everything needed to restore the log on any phone") },
        modifier = Modifier.clickable(enabled = !state.busy) { backup.launch("ddr-tracker-backup-$stamp.json") },
    )
    ListItem(
        headlineContent = { Text("Restore from a file") },
        supportingContent = { Text("Merges a backup into this log; plays already here are replaced, not duplicated") },
        modifier = Modifier.clickable(enabled = !state.busy) { restore.launch(arrayOf("application/json", "text/plain", "*/*")) },
    )
}

private val syncedFormat = java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(java.time.ZoneId.systemDefault())

@Composable
private fun BackupSection(state: SettingsUiState, actions: SettingsActions) {
    val settings = state.settings
    if (settings.account == null) {
        Text(
            "Sign up on a leaderboard server to back up your whole log there, privately, and restore it on a new phone " +
                "with your recovery code. You can also back up to a file below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        return
    }
    val status = state.backupStatus
    ListItem(
        headlineContent = { Text("Back up to your account") },
        supportingContent = {
            Text(
                when {
                    !settings.backup -> "Off. Plays still go to the leaderboard, but notes and FAST/SLOW stay on this phone"
                    status is BackupStatus.Running -> "Backing up…"
                    status is BackupStatus.Failed -> "Last backup failed: ${status.message}"
                    settings.backedUpAt != null -> "Every play, with notes and FAST/SLOW, only visible to you · backed up " +
                        syncedFormat.format(java.time.Instant.ofEpochMilli(settings.backedUpAt))
                    else -> "Every play, with notes and FAST/SLOW, only visible to you"
                }
            )
        },
        trailingContent = { Switch(checked = settings.backup, onCheckedChange = actions.setBackup) },
    )
    if (settings.backup) {
        TextButton(
            onClick = actions.backupNow,
            enabled = status != BackupStatus.Running,
            modifier = Modifier.padding(horizontal = 8.dp),
        ) { Text("Back up now") }
    }
}

/** Shown once, right after the code is made: the server can't show it again. */
@Composable
private fun RecoveryCodeDialog(code: String, onDone: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Your recovery code") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Save this somewhere safe, like a password manager. It's the only way back into this account " +
                        "on a new phone, and it won't be shown again."
                )
                Text(code, style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace))
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("I've saved it") } },
        dismissButton = { TextButton(onClick = { clipboard.setText(AnnotatedString(code)) }) { Text("Copy") } },
    )
}

@Composable
fun SettingsHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}
