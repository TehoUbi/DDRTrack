package com.tehoubi.ddrtracker.ui.settings

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tehoubi.ddrtracker.cloud.CloudStatus
import com.tehoubi.ddrtracker.core.ocr.ResultScreenParser

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
            setCloudSync = viewModel::setCloudSync,
            syncCloudNow = viewModel::syncCloudNow,
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
    val setCloudSync: (Boolean) -> Unit = {},
    val syncCloudNow: () -> Unit = {},
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

            SettingsHeader("Cloud backup")
            CloudSection(state, actions)
        }
    }
}

private val syncedFormat = java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(java.time.ZoneId.systemDefault())

@Composable
private fun CloudSection(state: SettingsUiState, actions: SettingsActions) {
    if (!state.cloudAvailable) {
        Text(
            "This build has no Firebase project configured, so plays are stored on this phone only. " +
                "Add app/google-services.json and rebuild to enable backup (see the README).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        return
    }
    val status = state.cloudStatus
    ListItem(
        headlineContent = { Text("Back up to Firebase") },
        supportingContent = {
            Text(
                when (status) {
                    CloudStatus.Unavailable -> "Unavailable"
                    CloudStatus.Off -> "Off — plays stay on this phone"
                    CloudStatus.Syncing -> "Syncing…"
                    is CloudStatus.Synced -> "Synced ${syncedFormat.format(java.time.Instant.ofEpochMilli(status.atMillis))} · " +
                        "${status.pushed} up, ${status.pulled} down"
                    is CloudStatus.Failed -> "Last sync failed: ${status.message}"
                } + (state.settings.cloudSyncedAt?.takeIf { status == CloudStatus.Off && state.settings.cloudSync }
                    ?.let { " · last synced ${syncedFormat.format(java.time.Instant.ofEpochMilli(it))}" } ?: "")
            )
        },
        trailingContent = { Switch(checked = state.settings.cloudSync, onCheckedChange = actions.setCloudSync) },
    )
    if (state.settings.cloudSync) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(onClick = actions.syncCloudNow, enabled = status != CloudStatus.Syncing) { Text("Sync now") }
            state.cloudUserId?.let {
                Text("Account ${it.take(8)}…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
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
