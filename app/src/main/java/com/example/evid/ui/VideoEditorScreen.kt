//// Fixed VideoEditorScreen.kt
package com.example.evid.ui
//
//import android.net.Uri
//import androidx.activity.compose.rememberLauncherForActivityResult
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.compose.foundation.layout.*
//import androidx.compose.material.icons.filled.Add
//import androidx.compose.material.icons.filled.Delete
//import androidx.compose.material.icons.filled.ZoomIn
//import androidx.compose.material.icons.filled.ZoomOut
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.unit.dp
//import com.example.evid.ui.timeline.*
//import kotlinx.coroutines.launch
//import java.util.*
//import androidx.media3.exoplayer.DefaultLoadControl
//
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun VideoEditorScreen() {
//    val context = LocalContext.current
//    val scope = rememberCoroutineScope()
//
//    // State management - IMPROVED
//    var clips by remember { mutableStateOf(listOf<TimelineClip>()) }
//    var tracks by remember { mutableStateOf(createDefaultTracks()) }
//    var currentTime by remember { mutableStateOf(0f) }
//    var totalDuration by remember { mutableStateOf(60f) }
//    var isPlaying by remember { mutableStateOf(false) }
//    var selectedClipId by remember { mutableStateOf<String?>(null) }
//    var pixelsPerSecond by remember { mutableStateOf(50f) }
//    var currentVideoUri by remember { mutableStateOf<String?>(null) }
//
//    // Add stability flags
//    var isUpdatingTime by remember { mutableStateOf(false) }
//    var lastUpdateTime by remember { mutableStateOf(0L) }
//
//    // File picker for adding clips
//    val selectVideoFile = rememberLauncherForActivityResult(
//        contract = ActivityResultContracts.OpenDocument()
//    ) { uri ->
//        uri?.let {
//            try {
//                context.contentResolver.takePersistableUriPermission(
//                    it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
//                )
//
//                // Add new clip to timeline
//                val newClip = TimelineClip(
//                    id = UUID.randomUUID().toString(),
//                    name = "Video ${clips.size + 1}",
//                    startTime = 0f,
//                    duration = 10f, // Will be updated when video loads
//                    trackIndex = 0,
//                    type = ClipType.VIDEO,
//                    color = Color.Blue
//                )
//
//                clips = clips + newClip
//                currentVideoUri = it.toString()
//
//                // Reset playback
//                currentTime = 0f
//                isPlaying = false
//            } catch (e: Exception) {
//                // Handle permission errors gracefully
//                e.printStackTrace()
//            }
//        }
//    }
//
//    // Improved playback control - FIXED
//    LaunchedEffect(isPlaying) {
//        if (isPlaying) {
//            while (isPlaying && currentTime < totalDuration) {
//                val currentSystemTime = System.currentTimeMillis()
//                if (currentSystemTime - lastUpdateTime >= 100) { // Only update every 100ms
//                    if (!isUpdatingTime) {
//                        currentTime = (currentTime + 0.1f).coerceAtMost(totalDuration)
//                        lastUpdateTime = currentSystemTime
//                    }
//                }
//                kotlinx.coroutines.delay(50) // Check more frequently but update less frequently
//            }
//            if (currentTime >= totalDuration) {
//                isPlaying = false
//            }
//        }
//    }
//
//    Scaffold(
//        topBar = {
//            TopAppBar(
//                title = { Text("Video Editor") },
//                actions = {
//                    // Zoom controls - IMPROVED with bounds checking
//                    IconButton(
//                        onClick = {
//                            pixelsPerSecond = (pixelsPerSecond * 0.8f)
//                                .coerceAtLeast(10f)
//                                .coerceAtMost(200f)
//                        }
//                    ) {
//                        Icon(
//                            imageVector = androidx.compose.material.icons.Icons.Default.ZoomOut,
//                            contentDescription = "Zoom Out"
//                        )
//                    }
//                    IconButton(
//                        onClick = {
//                            pixelsPerSecond = (pixelsPerSecond * 1.25f)
//                                .coerceAtLeast(10f)
//                                .coerceAtMost(200f)
//                        }
//                    ) {
//                        Icon(
//                            imageVector = androidx.compose.material.icons.Icons.Default.ZoomIn,
//                            contentDescription = "Zoom In"
//                        )
//                    }
//
//                    // Add clip button
//                    IconButton(
//                        onClick = { selectVideoFile.launch(arrayOf("video/*")) }
//                    ) {
//                        Icon(
//                            imageVector = androidx.compose.material.icons.Icons.Default.Add,
//                            contentDescription = "Add Video"
//                        )
//                    }
//                }
//            )
//        }
//    ) { innerPadding ->
//        Column(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(innerPadding)
//                .padding(16.dp)
//        ) {
//            // Video Preview - FIXED
//            VideoPreviewComponent(
//                videoUri = currentVideoUri,
//                currentTime = currentTime,
//                isPlaying = isPlaying,
//                onPlayPause = {
//                    isPlaying = !isPlaying
//                },
//                onSeek = { newTime ->
//                    isUpdatingTime = true
//                    currentTime = newTime.coerceIn(0f, totalDuration)
//                    // Pause when manually seeking
//                    if (isPlaying) {
//                        isPlaying = false
//                    }
//                    isUpdatingTime = false
//                },
//                onDurationChange = { newDuration ->
//                    scope.launch {
//                        // Update the total duration when video loads
//                        totalDuration = newDuration.coerceAtLeast(60f)
//
//                        // Update the first clip's duration
//                        clips = clips.mapIndexed { index, clip ->
//                            if (clip.trackIndex == 0 && index == clips.lastIndex) {
//                                clip.copy(duration = newDuration)
//                            } else clip
//                        }
//                    }
//                },
//                duration = totalDuration,
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .weight(1f)
//            )
//
//            Spacer(modifier = Modifier.height(16.dp))
//
//            // Timeline Controls - IMPROVED
//            TimelineControlsSection(
//                pixelsPerSecond = pixelsPerSecond,
//                onZoomChange = { newZoom ->
//                    pixelsPerSecond = newZoom.coerceIn(10f, 200f)
//                },
//                selectedClipId = selectedClipId,
//                onDeleteClip = {
//                    selectedClipId?.let { clipId ->
//                        scope.launch {
//                            clips = clips.filter { it.id != clipId }
//                            selectedClipId = null
//
//                            // Update total duration after deletion
//                            val newTotalDuration = clips.maxOfOrNull { it.startTime + it.duration } ?: 60f
//                            totalDuration = newTotalDuration.coerceAtLeast(60f)
//
//                            // Reset playback if current time is beyond new duration
//                            if (currentTime > totalDuration) {
//                                currentTime = 0f
//                                isPlaying = false
//                            }
//                        }
//                    }
//                }
//            )
//
//            // Timeline - IMPROVED
//            TimelineComponent(
//                clips = clips,
//                tracks = tracks,
//                currentTime = currentTime,
//                totalDuration = totalDuration,
//                pixelsPerSecond = pixelsPerSecond,
//                onTimeChange = { newTime ->
//                    scope.launch {
//                        isUpdatingTime = true
//                        currentTime = newTime.coerceIn(0f, totalDuration)
//                        // Pause when manually scrubbing timeline
//                        isPlaying = false
//                        isUpdatingTime = false
//                    }
//                },
//                onClipMove = { clipId, newStartTime, trackIndex ->
//                    scope.launch {
//                        clips = clips.map { clip ->
//                            if (clip.id == clipId) {
//                                clip.copy(
//                                    startTime = newStartTime.coerceAtLeast(0f),
//                                    trackIndex = trackIndex.coerceIn(0, tracks.size - 1)
//                                )
//                            } else clip
//                        }
//
//                        // Update total duration
//                        val newTotalDuration = clips.maxOfOrNull { it.startTime + it.duration } ?: 60f
//                        totalDuration = newTotalDuration.coerceAtLeast(60f)
//                    }
//                },
//                onClipSelect = { clipId ->
//                    selectedClipId = clipId
//                },
//                selectedClipId = selectedClipId,
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .height(300.dp)
//            )
//        }
//    }
//}
//
//@Composable
//fun TimelineControlsSection(
//    pixelsPerSecond: Float,
//    onZoomChange: (Float) -> Unit,
//    selectedClipId: String?,
//    onDeleteClip: () -> Unit,
//    modifier: Modifier = Modifier
//) {
//    Card(
//        modifier = modifier.fillMaxWidth()
//    ) {
//        Row(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(8.dp),
//            horizontalArrangement = Arrangement.SpaceBetween
//        ) {
//            // Zoom controls - IMPROVED
//            Row {
//                Text(
//                    text = "Zoom:",
//                    modifier = Modifier.padding(end = 8.dp)
//                )
//
//                // Use a more controlled slider
//                var sliderValue by remember { mutableStateOf(pixelsPerSecond) }
//
//                LaunchedEffect(pixelsPerSecond) {
//                    sliderValue = pixelsPerSecond
//                }
//
//                Slider(
//                    value = sliderValue,
//                    onValueChange = { value ->
//                        sliderValue = value
//                    },
//                    onValueChangeFinished = {
//                        onZoomChange(sliderValue)
//                    },
//                    valueRange = 10f..200f,
//                    modifier = Modifier.width(150.dp)
//                )
//                Text(
//                    text = "${pixelsPerSecond.toInt()}px/s",
//                    modifier = Modifier.padding(start = 8.dp)
//                )
//            }
//
//            // Clip operations
//            Row {
//                Button(
//                    onClick = onDeleteClip,
//                    enabled = selectedClipId != null,
//                    colors = ButtonDefaults.buttonColors(
//                        containerColor = MaterialTheme.colorScheme.error
//                    )
//                ) {
//                    Icon(
//                        imageVector = androidx.compose.material.icons.Icons.Default.Delete,
//                        contentDescription = "Delete Clip"
//                    )
//                    Spacer(modifier = Modifier.width(4.dp))
//                    Text("Delete")
//                }
//            }
//        }
//    }
//}
//
//// Helper function to create default tracks
//fun createDefaultTracks(): List<TimelineTrack> {
//    return listOf(
//        TimelineTrack(
//            id = "video1",
//            name = "Video 1",
//            type = ClipType.VIDEO,
//            height = 80.dp
//        ),
//        TimelineTrack(
//            id = "video2",
//            name = "Video 2",
//            type = ClipType.VIDEO,
//            height = 80.dp
//        ),
//        TimelineTrack(
//            id = "audio1",
//            name = "Audio 1",
//            type = ClipType.AUDIO,
//            height = 60.dp
//        ),
//        TimelineTrack(
//            id = "audio2",
//            name = "Audio 2",
//            type = ClipType.AUDIO,
//            height = 60.dp
//        ),
//        TimelineTrack(
//            id = "text1",
//            name = "Text/Titles",
//            type = ClipType.TEXT,
//            height = 50.dp
//        )
//    )
//}