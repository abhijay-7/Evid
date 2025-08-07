package com.example.evid.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.*
import com.example.evid.analyzer.VideoAnalyzer
import com.example.evid.extractor.FrameExtractionConfig
import com.example.evid.extractor.FrameExtractionResult
import com.example.evid.extractor.FrameExtractionUtils
import com.example.evid.extractor.FrameScaler
import com.example.evid.extractor.FrameExtractionWorker
import com.example.evid.extractor.QualityLevel
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrameExtractionScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inputUri by remember { mutableStateOf<Uri?>(null) }
    var qualityLevel by remember { mutableStateOf(QualityLevel.Preview) }
    var isProcessing by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf<Float?>(null) }
    val frameScaler = remember { FrameScaler(context) }
    val videoAnalyzer = remember { VideoAnalyzer(context) }
    val workManager = WorkManager.getInstance(context)

    val selectVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            inputUri = it
            scope.launch {
                when (val result = videoAnalyzer.analyzeVideo(uri)) {
                    is com.example.evid.analyzer.VideoAnalysisResult.Success -> {
                        resultText = "Video selected: ${result.metadata.fileName}"
                    }
                    is com.example.evid.analyzer.VideoAnalysisResult.Error -> {
                        resultText = "Error: ${result.message}"
                    }
                    else -> {
                        resultText = "Invalid or missing video file"
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Frame Extraction") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = { selectVideoLauncher.launch(arrayOf("video/*")) },
                enabled = !isProcessing
            ) {
                Text("Select Video")
            }
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = inputUri?.let { "Selected: ${inputUri?.lastPathSegment}" } ?: "No video selected",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text("Quality Level", style = MaterialTheme.typography.labelLarge)
            Row {
                QualityLevel.values().forEach { level ->
                    RadioButton(
                        selected = qualityLevel == level,
                        onClick = { qualityLevel = level },
                        enabled = !isProcessing
                    )
                    Text(level.name, modifier = Modifier.padding(start = 8.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Row {
                Button(
                    onClick = {
                        if (inputUri == null) {
                            resultText = "Please select a video"
                            return@Button
                        }
                        if (isProcessing) return@Button
                        isProcessing = true
                        scope.launch {
                            val metadata = when (val result = videoAnalyzer.analyzeVideo(inputUri!!)) {
                                is com.example.evid.analyzer.VideoAnalysisResult.Success -> result.metadata
                                else -> {
                                    resultText = "Failed to analyze video"
                                    isProcessing = false
                                    return@launch
                                }
                            }
                            val startTime = System.currentTimeMillis()
                            val config = FrameExtractionConfig.createForQuality(qualityLevel)
                            val result = frameScaler.scaleFrames(inputUri!!, metadata, config) { progressResult ->
                                progress = progressResult.percentage
                            }
                            isProcessing = false
                            progress = null
                            when (result) {
                                is FrameExtractionResult.Success -> {
                                    resultText = FrameExtractionUtils.createExtractionSummary(
                                        result,
                                        config,
                                        System.currentTimeMillis() - startTime
                                    )
                                    FrameExtractionUtils.shareFramesDirectory(context, result.framesDirectory)
                                }
                                is FrameExtractionResult.Error -> {
                                    resultText = "Error: ${result.message}"
                                }
                                is FrameExtractionResult.InsufficientStorage -> {
                                    resultText = "Insufficient storage space"
                                }
                                is FrameExtractionResult.VideoNotFound -> {
                                    resultText = "Video file not found"
                                }
                                else -> {
                                    resultText = "Unexpected error"
                                }
                            }
                        }
                    },
                    enabled = !isProcessing
                ) {
                    Text("Extract Frames")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = {
                        if (inputUri == null) {
                            resultText = "Please select a video"
                            return@Button
                        }
                        if (isProcessing) return@Button
                        isProcessing = true
                        scope.launch {
                            val metadata = when (val result = videoAnalyzer.analyzeVideo(inputUri!!)) {
                                is com.example.evid.analyzer.VideoAnalysisResult.Success -> result.metadata
                                else -> {
                                    resultText = "Failed to analyze video"
                                    isProcessing = false
                                    return@launch
                                }
                            }
                            val workRequest = OneTimeWorkRequestBuilder<FrameExtractionWorker>()
                                .setInputData(workDataOf(
                                    FrameExtractionWorker.KEY_INPUT_URI to inputUri.toString(),
                                    FrameExtractionWorker.KEY_QUALITY_LEVEL to qualityLevel.name,
                                    FrameExtractionWorker.KEY_METADATA to mapOf(
                                        "filePath" to metadata.filePath,
                                        "fileName" to metadata.fileName,
                                        "duration" to metadata.duration,
                                        "width" to metadata.width,
                                        "height" to metadata.height,
                                        "frameRate" to metadata.frameRate,
                                        "bitRate" to metadata.bitRate,
                                        "codec" to metadata.codec,
                                        "mimeType" to metadata.mimeType,
                                        "fileSize" to metadata.fileSize,
                                        "hash" to metadata.hash,
                                        "creationTime" to metadata.creationTime,
                                        "rotation" to metadata.rotation
                                    ).toString()
                                ))
                                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, java.util.concurrent.TimeUnit.SECONDS)
                                .build()
                            workManager.enqueueUniqueWork(
                                "frame_extraction_${UUID.randomUUID()}",
                                ExistingWorkPolicy.REPLACE,
                                workRequest
                            )
                            workManager.getWorkInfoByIdLiveData(workRequest.id).observeForever { workInfo ->
                                when (workInfo.state) {
                                    WorkInfo.State.SUCCEEDED -> {
                                        isProcessing = false
                                        progress = null
                                        val outputDir = workInfo.outputData.getString("output_dir")
                                        val totalFrames = workInfo.outputData.getInt("total_frames", 0)
                                        if (outputDir != null) {
                                            resultText = "Background extraction completed: $totalFrames frames at $outputDir"
                                            FrameExtractionUtils.shareFramesDirectory(context, File(outputDir))
                                        }
                                    }
                                    WorkInfo.State.FAILED -> {
                                        isProcessing = false
                                        progress = null
                                        resultText = "Background extraction failed"
                                    }
                                    WorkInfo.State.CANCELLED -> {
                                        isProcessing = false
                                        progress = null
                                        resultText = "Background extraction cancelled"
                                    }
                                    else -> {
                                        progress = workInfo.progress.getInt("progress", 0).toFloat() / 100
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isProcessing
                ) {
                    Text("Extract in Background")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = {
                        workManager.cancelAllWork()
                        isProcessing = false
                        progress = null
                        resultText = "Extraction cancelled"
                    },
                    enabled = isProcessing
                ) {
                    Text("Cancel")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            progress?.let {
                LinearProgressIndicator(progress = it, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                Text("Progress: ${(it * 100).toInt()}%")
            }
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = resultText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}