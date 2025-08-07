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
import com.example.evid.analyzer.VideoAnalyzer
import com.example.evid.extractor.AudioExtractor
import com.example.evid.extractor.FrameExtractionResult
import com.example.evid.extractor.FrameExtractionUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioExtractionScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inputUri by remember { mutableStateOf<Uri?>(null) }
    var outputFormat by remember { mutableStateOf("wav") }
    var isProcessing by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf<Float?>(null) }
    val audioExtractor = remember { AudioExtractor(context) }
    val videoAnalyzer = remember { VideoAnalyzer(context) }

    val selectVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            inputUri = it
            scope.launch {
                when (val result = videoAnalyzer.analyzeVideo(uri)) {
                    is com.example.evid.analyzer.VideoAnalysisResult.Success -> {
                        resultText = "Video selected: ${result.metadata.fileName}, ${result.metadata.audioTracks.size} audio tracks"
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
        topBar = { TopAppBar(title = { Text("Audio Extraction") }) }
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

            Text("Output Format", style = MaterialTheme.typography.labelLarge)
            Row {
                RadioButton(
                    selected = outputFormat == "wav",
                    onClick = { outputFormat = "wav" },
                    enabled = !isProcessing
                )
                Text("WAV (PCM)", modifier = Modifier.padding(start = 8.dp))
                Spacer(modifier = Modifier.width(16.dp))
                RadioButton(
                    selected = outputFormat == "mp3",
                    onClick = { outputFormat = "mp3" },
                    enabled = !isProcessing
                )
                Text("MP3", modifier = Modifier.padding(start = 8.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (inputUri == null) {
                        resultText = "Please select a video"
                        return@Button
                    }
                    if (isProcessing) return@Button
                    isProcessing = true
                    scope.launch {
                        val startTime = System.currentTimeMillis()
                        val result = audioExtractor.extractAudio(inputUri!!, outputFormat) { progressResult ->
                            progress = progressResult.percentage
                        }
                        isProcessing = false
                        progress = null
                        when (result) {
                            is FrameExtractionResult.AudioSuccess -> {
                                resultText = FrameExtractionUtils.createAudioExtractionSummary(
                                    result,
                                    System.currentTimeMillis() - startTime
                                )
                                FrameExtractionUtils.shareAudioFiles(context, result.audioDirectory)
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
                Text("Extract Audio")
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