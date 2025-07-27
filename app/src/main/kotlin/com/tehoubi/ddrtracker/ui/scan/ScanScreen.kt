package com.tehoubi.ddrtracker.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeoSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tehoubi.ddrtracker.core.ocr.Box as OcrBox
import com.tehoubi.ddrtracker.core.ocr.ParsedResult
import com.tehoubi.ddrtracker.core.ocr.StatLabel
import com.tehoubi.ddrtracker.scan.LiveTextAnalyzer
import com.tehoubi.ddrtracker.ui.theme.NeonCyan
import com.tehoubi.ddrtracker.ui.theme.NeonGreen
import com.tehoubi.ddrtracker.ui.theme.ScoreDigits
import java.util.concurrent.Executors

/**
 * Camera screen. Preview frames are read live and voted on field by field;
 * once the score and judgments agree across frames a full-resolution still
 * is taken automatically (or with the shutter button) and opens on review.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onBack: () -> Unit,
    onReview: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::onImagePicked)
    }
    val openGallery = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(uiState.message) {
        uiState.message?.let { snackbar.showSnackbar(it); viewModel.onMessageShown() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan result") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = openGallery, enabled = !uiState.isProcessing) {
                        Text("From gallery", color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.4f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color.Black,
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            if (hasPermission) {
                LiveCamera(viewModel = viewModel, onReview = onReview, uiState = uiState)
            } else {
                PermissionRationale(
                    onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onGallery = openGallery,
                    modifier = Modifier.align(Alignment.Center).padding(padding),
                )
            }
            if (uiState.isProcessing) {
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                ) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text("Reading the screen…", Modifier.padding(top = 12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveCamera(viewModel: ScanViewModel, onReview: () -> Unit, uiState: ScanUiState) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember { LiveTextAnalyzer(onFrame = viewModel::onLiveFrame) }
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            // 720p keeps the digits legible from a step or two away from the cabinet.
            imageAnalysisResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                )
                .build()
            setImageAnalysisAnalyzer(analysisExecutor, analyzer)
        }
    }
    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            analyzer.close()
            analysisExecutor.shutdown()
        }
    }

    val takeStill by rememberUpdatedState {
        controller.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val rotation = image.imageInfo.rotationDegrees
                    val bitmap: Bitmap = image.toBitmap()
                    image.close()
                    viewModel.onPhotoCaptured(bitmap, rotation)
                }

                override fun onError(exception: ImageCaptureException) = viewModel.onCaptureFailed(exception)
            },
        )
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ScanEvent.TakeStill -> takeStill()
                ScanEvent.OpenReview -> onReview()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    this.controller = controller
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        TextBoxOverlay(uiState.boxes, uiState.frameWidth, uiState.frameHeight, locked = uiState.phase >= LivePhase.LOCKED)

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusChip(uiState)
            if (uiState.phase != LivePhase.SEARCHING) LiveReadout(uiState.live)
            ShutterButton(enabled = !uiState.isProcessing && uiState.phase != LivePhase.CAPTURING) {
                viewModel.requestStill()
            }
        }
    }
}

/** Draws the latest frame's text boxes over the preview (FILL_CENTER mapping). */
@Composable
private fun TextBoxOverlay(boxes: List<OcrBox>, frameWidth: Int, frameHeight: Int, locked: Boolean) {
    if (frameWidth == 0 || frameHeight == 0) return
    val color = if (locked) NeonGreen else NeonCyan
    Canvas(Modifier.fillMaxSize()) {
        val scale = maxOf(size.width / frameWidth, size.height / frameHeight)
        val dx = (size.width - frameWidth * scale) / 2f
        val dy = (size.height - frameHeight * scale) / 2f
        boxes.forEach { b ->
            drawRect(
                color = color.copy(alpha = 0.8f),
                topLeft = Offset(b.left * scale + dx, b.top * scale + dy),
                size = GeoSize(b.width * scale, b.height * scale),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

@Composable
private fun StatusChip(state: ScanUiState) {
    val progress by animateFloatAsState(state.progress, label = "progress")
    Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.65f)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(state.hint ?: "Point at a DDR result screen", color = Color.White)
            if (state.phase == LivePhase.READING || state.phase == LivePhase.LOCKED) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.padding(top = 6.dp).size(width = 160.dp, height = 4.dp),
                    color = if (state.phase == LivePhase.LOCKED) NeonGreen else NeonCyan,
                )
            }
        }
    }
}

@Composable
private fun LiveReadout(live: ParsedResult) {
    Surface(shape = MaterialTheme.shapes.medium, color = Color.Black.copy(alpha = 0.65f)) {
        Column(Modifier.padding(12.dp)) {
            Text(
                listOfNotNull(live.title?.value, live.difficulty?.value?.label, live.level?.value?.toString())
                    .joinToString(" · ").ifEmpty { "…" },
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                live.score?.value?.let { "%,d".format(it) } ?: "—",
                color = Color.White,
                style = ScoreDigits.copy(fontSize = MaterialTheme.typography.headlineSmall.fontSize),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatLabel.JUDGMENTS.forEach { label ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label.name.take(4), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                        Text(live.stats[label]?.value?.toString() ?: "·", color = Color.White, style = ScoreDigits)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(76.dp)
            .border(4.dp, Color.White, CircleShape)
            .padding(8.dp)
            .background(if (enabled) Color.White else Color.Gray, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun PermissionRationale(onRequest: () -> Unit, onGallery: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "The camera is used to read your result screen. Photos never leave the phone.",
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRequest) { Text("Allow camera") }
        TextButton(onClick = onGallery) { Text("Or pick a screenshot", color = Color.White) }
    }
}
