package com.example.evid.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.evid.extractor.*
import kotlinx.coroutines.launch
import java.io.File
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.material3.AlertDialog



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrameExtractionScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var extractionResult by remember { mutableStateOf<FrameExtractionResult?>(null) }
    var isExtracting by remember { mutableStateOf(false) }
    var extractionProgress by remember { mutableStateOf<FrameExtractionResult.Progress?>(null) }
    var extractionConfig by remember { mutableStateOf(FrameExtractionConfig.createDefault()) }
    var frameExtractor by remember { mutableStateOf<FrameExtractor?>(null) }
    var showConfigDialog by remember { mutableStateOf(false) }

    // Initialize frame extractor
    LaunchedEffect(extractionConfig) {
        frameExtractor = FrameExtractor(context, extractionConfig)
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedVideoUri = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Frame Extraction") },
                actions = {
                    IconButton(
                        onClick = { showConfigDialog = true }
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Video Selection Card
            item {
                VideoSelectionCard(
                    selectedVideoPath = selectedVideoUri?.toString(),
                    onVideoSelect = { videoPickerLauncher.launch("video/*") },
                    onClearVideo = {
                        selectedVideoUri = null
                        extractionResult = null
                        extractionProgress = null
                    }
                )
            }

            // Extraction Configuration Card
            item {
                ExtractionConfigCard(
                    config = extractionConfig,
                    onConfigChange = { extractionConfig = it },
                    enabled = !isExtracting
                )
            }

            // Extraction Controls Card
            item {
                ExtractionControlsCard(
                    hasVideo = selectedVideoUri != null,
                    isExtracting = isExtracting,
                    onStartExtraction = {
                        selectedVideoUri?.let { uri ->
                            scope.launch {
                                isExtracting = true
                                extractionResult = null
                                extractionProgress = null

                                frameExtractor?.extractFrames(
                                    uri = uri,
                                    onProgress = { progress ->
                                        extractionProgress = progress
                                    },
                                    onResult = { result ->
                                        extractionResult = result
                                        isExtracting = false
                                    }
                                )
                            }
                        }
                    },
                    onCancelExtraction = {
                        frameExtractor?.cancelExtraction()
                        isExtracting = false
                    }
                )
            }

            // Progress Card
            extractionProgress?.let { progress ->
                item {
                    ProgressCard(progress = progress)
                }
            }

            // Results Card
            extractionResult?.let { result ->
                item {
                    ResultsCard(result = result)
                }
            }
        }
    }

    // Configuration Dialog
    if (showConfigDialog) {
        AlertDialog(
            onDismissRequest = { showConfigDialog = false },
            title = { Text("Extraction Settings") },
            text = {
                ExtractionConfigCard(
                    config = extractionConfig,
                    onConfigChange = { extractionConfig = it },
                    enabled = !isExtracting
                )
            },
            confirmButton = {
                TextButton(onClick = { showConfigDialog = false }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfigDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
@Composable
private fun VideoSelectionCard(
    selectedVideoPath: String?,
    onVideoSelect: () -> Unit,
    onClearVideo: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Video Selection",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                if (selectedVideoPath != null) {
                    IconButton(onClick = onClearVideo) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Clear video",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedVideoPath != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.VideoFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = selectedVideoPath.substringAfterLast('/'),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Button(
                    onClick = onVideoSelect,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Video File")
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(progress: FrameExtractionResult.Progress) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Extraction Progress",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { progress.percentage / 100f },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Frame ${progress.currentFrame} / ${progress.totalFrames}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${String.format("%.1f", progress.percentage)}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Timestamp: ${formatTimestamp(progress.currentTimestamp)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ResultsCard(result: FrameExtractionResult) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (result) {
                is FrameExtractionResult.Success -> MaterialTheme.colorScheme.primaryContainer
                is FrameExtractionResult.Error -> MaterialTheme.colorScheme.errorContainer
                is FrameExtractionResult.InsufficientStorage -> MaterialTheme.colorScheme.errorContainer
                is FrameExtractionResult.VideoNotFound -> MaterialTheme.colorScheme.errorContainer
                is FrameExtractionResult.Cancelled -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            val (title, icon, color) = when (result) {
                is FrameExtractionResult.Success -> Triple(
                    "Extraction Complete",
                    Icons.Default.CheckCircle,
                    MaterialTheme.colorScheme.onPrimaryContainer
                )
                is FrameExtractionResult.Error -> Triple(
                    "Extraction Failed",
                    Icons.Default.Error,
                    MaterialTheme.colorScheme.onErrorContainer
                )
                is FrameExtractionResult.InsufficientStorage -> Triple(
                    "Insufficient Storage",
                    Icons.Default.Storage,
                    MaterialTheme.colorScheme.onErrorContainer
                )
                is FrameExtractionResult.VideoNotFound -> Triple(
                    "Video Not Found",
                    Icons.Default.ErrorOutline,
                    MaterialTheme.colorScheme.onErrorContainer
                )
                is FrameExtractionResult.Cancelled -> Triple(
                    "Extraction Cancelled",
                    Icons.Default.Cancel,
                    MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> Triple(
                    "Unknown Result",
                    Icons.Default.Help,
                    MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (result) {
                is FrameExtractionResult.Success -> {
                    Text(
                        text = "Successfully extracted ${result.totalFrames} frames",
                        style = MaterialTheme.typography.bodyMedium,
                        color = color
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Saved to: ${result.framesDirectory.absolutePath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                // Open folder using a file explorer intent
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(
                                        Uri.fromFile(result.framesDirectory),
                                        "resource/folder"
                                    )
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(Intent.createChooser(intent, "Open Folder"))
                            }
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open Folder")
                        }
                        OutlinedButton(
                            onClick = {
                                // Share frames directory
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, result.framesDirectory.absolutePath)
                                }
                                context.startActivity(
                                    Intent.createChooser(shareIntent, "Share Frames")
                                )
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share")
                        }
                    }
                }
                is FrameExtractionResult.Error -> {
                    Text(
                        text = result.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = color
                    )
                    if (result.frameIndex >= 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Failed at frame: ${result.frameIndex}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is FrameExtractionResult.InsufficientStorage -> {
                    Text(
                        text = "Not enough storage space available. Please free up space and try again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = color
                    )
                }
                is FrameExtractionResult.VideoNotFound -> {
                    Text(
                        text = "The selected video file could not be found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = color
                    )
                }
                is FrameExtractionResult.Cancelled -> {
                    Text(
                        text = "Frame extraction was cancelled by user.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = color
                    )
                }
                else -> {
                    Text(
                        text = "Unknown extraction result",
                        style = MaterialTheme.typography.bodyMedium,
                        color = color
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtractionConfigCard(
    config: FrameExtractionConfig,
    onConfigChange: (FrameExtractionConfig) -> Unit,
    enabled: Boolean
) {
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        Text(
            text = "Extraction Settings",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // JPEG Quality
        Text(
            text = "JPEG Quality: ${config.jpegQuality}%",
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = config.jpegQuality.toFloat(),
            onValueChange = {
                if (enabled) {
                    onConfigChange(config.copy(jpegQuality = it.toInt()))
                }
            },
            valueRange = 10f..100f,
            enabled = enabled
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Max Frame Dimension
        Text(
            text = "Max Frame Size: ${config.maxFrameDimension}px",
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = config.maxFrameDimension.toFloat(),
            onValueChange = {
                if (enabled) {
                    onConfigChange(config.copy(maxFrameDimension = it.toInt()))
                }
            },
            valueRange = 480f..2160f,
            steps = 10,
            enabled = enabled
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Extract All Frames Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Extract All Frames",
                style = MaterialTheme.typography.bodyMedium
            )
            Switch(
                checked = config.extractAllFrames,
                onCheckedChange = {
                    if (enabled) {
                        onConfigChange(config.copy(extractAllFrames = it))
                    }
                },
                enabled = enabled
            )
        }

        // Frame Interval (only shown if not extracting all frames)
        if (!config.extractAllFrames) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Frame Interval: ${config.frameInterval / 1000.0}s",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = (config.frameInterval / 1000f),
                onValueChange = {
                    if (enabled) {
                        onConfigChange(config.copy(frameInterval = (it * 1000).toLong()))
                    }
                },
                valueRange = 0.1f..5f,
                enabled = enabled
            )
        }
    }
}

@Composable
private fun ExtractionControlsCard(
    hasVideo: Boolean,
    isExtracting: Boolean,
    onStartExtraction: () -> Unit,
    onCancelExtraction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Extraction Controls",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onStartExtraction,
                    enabled = hasVideo && !isExtracting,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isExtracting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isExtracting) "Extracting..." else "Extract Frames")
                }

                if (isExtracting) {
                    OutlinedButton(
                        onClick = onCancelExtraction,
                        modifier = Modifier.weight(0.5f)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(timestampMs: Long): String {
    val totalSeconds = timestampMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}