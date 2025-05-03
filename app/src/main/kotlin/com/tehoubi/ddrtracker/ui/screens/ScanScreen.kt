package com.tehoubi.ddrtracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Camera + scan screen. Shows a CameraX preview with a capture button.
 * On capture, runs OCR and navigates to a confirmation screen.
 */
@Composable
fun ScanScreen(
    onScoreConfirmed: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        // TODO: Embed CameraX preview via AndroidView

        // Capture button
        Button(
            onClick = { viewModel.captureAndParse() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
        ) {
            Text("Scan Score Screen")
        }

        // Show loading indicator while OCR is running
        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        // Show error snackbar if parsing failed
        uiState.error?.let { error ->
            // TODO: Show Snackbar
        }
    }
}
