// TimelineComponent.kt
package com.example.evid.ui.timeline

import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlin.math.roundToInt
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize

import androidx.media3.ui.AspectRatioFrameLayout
import android.view.SurfaceHolder
import androidx.media3.exoplayer.DefaultLoadControl


data class TimelineClip(
    val id: String,
    val name: String,
    val startTime: Float, // in seconds
    val duration: Float, // in seconds
    val trackIndex: Int,
    val type: ClipType = ClipType.VIDEO,
    val color: Color = Color.Blue
)

enum class ClipType {
    VIDEO, AUDIO, TEXT, IMAGE
}

data class TimelineTrack(
    val id: String,
    val name: String,
    val type: ClipType,
    val isVisible: Boolean = true,
    val isMuted: Boolean = false,
    val height: Dp = 60.dp
)

@Composable
fun TimelineComponent(
    clips: List<TimelineClip>,
    tracks: List<TimelineTrack>,
    currentTime: Float,
    totalDuration: Float,
    pixelsPerSecond: Float = 50f,
    onTimeChange: (Float) -> Unit,
    onClipMove: (String, Float, Int) -> Unit,
    onClipSelect: (String?) -> Unit,
    selectedClipId: String? = null,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var timelineWidth by remember { mutableStateOf(0f) }

    Column(modifier = modifier) {
        // Timeline ruler
        TimelineRuler(
            totalDuration = totalDuration,
            currentTime = currentTime,
            pixelsPerSecond = pixelsPerSecond,
            onTimeChange = onTimeChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
        ) { width ->
            timelineWidth = width
        }

        // Tracks
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(tracks) { track ->
                TimelineTrack(
                    track = track,
                    clips = clips.filter { it.trackIndex == tracks.indexOf(track) },
                    currentTime = currentTime,
                    pixelsPerSecond = pixelsPerSecond,
                    timelineWidth = timelineWidth,
                    onClipMove = onClipMove,
                    onClipSelect = onClipSelect,
                    selectedClipId = selectedClipId
                )
            }
        }
    }
}

@Composable
fun TimelineRuler(
    totalDuration: Float,
    currentTime: Float,
    pixelsPerSecond: Float,
    onTimeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onWidthMeasured: (Float) -> Unit = {}
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newTime = (offset.x / pixelsPerSecond).coerceIn(0f, totalDuration)
                    onTimeChange(newTime)
                }
            }
    ) {
        val width = size.width
        val height = size.height
        onWidthMeasured(width)

        // Background
        drawRect(Color.Gray.copy(alpha = 0.1f), size = size)

        // Time markers
        val majorInterval = when {
            pixelsPerSecond < 20 -> 10f
            pixelsPerSecond < 50 -> 5f
            pixelsPerSecond < 100 -> 2f
            else -> 1f
        }

        var time = 0f
        while (time <= totalDuration) {
            val x = time * pixelsPerSecond
            if (x <= width) {
                // Major tick
                drawLine(
                    Color.Gray,
                    start = Offset(x, height * 0.3f),
                    end = Offset(x, height),
                    strokeWidth = 2f
                )

                // Time label
                val timeText = formatTime(time)
                drawText(
                    textMeasurer,
                    timeText,
                    topLeft = Offset(x + 4f, 4f),
                    style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                )
            }
            time += majorInterval
        }

        // Minor ticks
        time = 0f
        val minorInterval = majorInterval / 5f
        while (time <= totalDuration) {
            val x = time * pixelsPerSecond
            if (x <= width && time % majorInterval != 0f) {
                drawLine(
                    Color.Gray.copy(alpha = 0.5f),
                    start = Offset(x, height * 0.7f),
                    end = Offset(x, height),
                    strokeWidth = 1f
                )
            }
            time += minorInterval
        }

        // Playhead
        val playheadX = currentTime * pixelsPerSecond
        if (playheadX <= width) {
            drawLine(
                Color.Red,
                start = Offset(playheadX, 0f),
                end = Offset(playheadX, height),
                strokeWidth = 3f
            )
        }
    }
}

@Composable
fun TimelineTrack(
    track: TimelineTrack,
    clips: List<TimelineClip>,
    currentTime: Float,
    pixelsPerSecond: Float,
    timelineWidth: Float,
    onClipMove: (String, Float, Int) -> Unit,
    onClipSelect: (String?) -> Unit,
    selectedClipId: String?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(track.height)
    ) {
        // Track header
        TrackHeader(
            track = track,
            modifier = Modifier.width(120.dp)
        )

        // Track content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color.Gray.copy(alpha = 0.05f))
        ) {
            // Clips
            clips.forEach { clip ->
                TimelineClipComponent(
                    clip = clip,
                    pixelsPerSecond = pixelsPerSecond,
                    isSelected = clip.id == selectedClipId,
                    onMove = { newStartTime ->
                        onClipMove(clip.id, newStartTime, clip.trackIndex)
                    },
                    onSelect = { onClipSelect(clip.id) },
                    modifier = Modifier.fillMaxHeight()
                )
            }

            // Playhead line
            val playheadX = currentTime * pixelsPerSecond
            if (playheadX <= timelineWidth) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                ) {
                    drawLine(
                        Color.Red.copy(alpha = 0.7f),
                        start = Offset(playheadX, 0f),
                        end = Offset(playheadX, size.height),
                        strokeWidth = 2f
                    )
                }
            }
        }
    }
}

@Composable
fun TrackHeader(
    track: TimelineTrack,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.cardColors(
            containerColor = when (track.type) {
                ClipType.VIDEO -> Color.Blue.copy(alpha = 0.1f)
                ClipType.AUDIO -> Color.Green.copy(alpha = 0.1f)
                ClipType.TEXT -> Color.Magenta.copy(alpha = 0.1f)
                ClipType.IMAGE -> Color.Gray.copy(alpha = 0.1f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = track.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (track.type == ClipType.AUDIO || track.type == ClipType.VIDEO) {
                    Icon(
                        imageVector = if (track.isMuted)
                            androidx.compose.material.icons.Icons.Default.VolumeOff
                        else
                            androidx.compose.material.icons.Icons.Default.VolumeUp,
                        contentDescription = "Mute",
                        modifier = Modifier.size(16.dp)
                    )
                }
                Icon(
                    imageVector = if (track.isVisible)
                        androidx.compose.material.icons.Icons.Default.Visibility
                    else
                        androidx.compose.material.icons.Icons.Default.VisibilityOff,
                    contentDescription = "Visibility",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun TimelineClipComponent(
    clip: TimelineClip,
    pixelsPerSecond: Float,
    isSelected: Boolean,
    onMove: (Float) -> Unit,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipWidth = clip.duration * pixelsPerSecond
    val clipOffset = clip.startTime * pixelsPerSecond

    var dragOffset by remember { mutableStateOf(0f) }

    Card(
        modifier = modifier
            .offset(x = with(LocalDensity.current) { (clipOffset + dragOffset).toDp() })
            .width(with(LocalDensity.current) { clipWidth.toDp() })
            .padding(vertical = 4.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onSelect() }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        val newStartTime = ((clipOffset + dragOffset) / pixelsPerSecond)
                            .coerceAtLeast(0f)
                        onMove(newStartTime)
                        dragOffset = 0f
                    }
                ) { _, dragAmount ->
                    dragOffset += dragAmount.x
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                clip.color.copy(alpha = 0.8f)
            else
                clip.color.copy(alpha = 0.6f)
        ),
        border = if (isSelected)
            androidx.compose.foundation.BorderStroke(2.dp, Color.White)
        else null
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = clip.name,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                maxLines = 1
            )
        }
    }
}

// PreviewComponent.kt - FIXED VERSION
// Fixed VideoPreviewComponent.kt
// Fixed VideoPreviewComponent.kt
// Fixed VideoPreviewComponent.kt
// Fixed VideoPreviewComponent.kt with proper state management
@OptIn(UnstableApi::class)
@Composable
fun VideoPreviewComponent(
    videoUri: String?,
    currentTime: Float,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onDurationChange: (Float) -> Unit,
    duration: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var isPlayerReady by remember { mutableStateOf(false) }
    var lastSeekTime by remember { mutableStateOf(0f) }

    // Create ExoPlayer with improved configuration
    val exoPlayer = remember(videoUri) {
        if (videoUri != null) {
            ExoPlayer.Builder(context)
                .setLoadControl(
                    DefaultLoadControl.Builder()
                        .setBufferDurationsMs(
                            500,  // Min buffer
                            2000, // Max buffer
                            500,  // Playback buffer
                            500   // Playback after rebuffer
                        )
                        .build()
                )
                .build().apply {
                    val mediaItem = MediaItem.fromUri(videoUri)
                    setMediaItem(mediaItem)
                    prepare()

                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            when (playbackState) {
                                Player.STATE_READY -> {
                                    if (!isPlayerReady) {
                                        isPlayerReady = true
                                        val videoDuration = this@apply.duration / 1000f
                                        if (videoDuration > 0 && videoDuration != duration) {
                                            onDurationChange(videoDuration)
                                        }
                                    }
                                }
                                Player.STATE_ENDED -> {
                                    // Video ended, could trigger callback here
                                }
                            }
                        }

                        override fun onVideoSizeChanged(videoSize: VideoSize) {
                            // Video size available
                        }

                        override fun onRenderedFirstFrame() {
                            // First frame rendered
                        }
                    })
                }
        } else {
            null
        }
    }

    // Sync ExoPlayer with external state - IMPROVED
    LaunchedEffect(currentTime) {
        exoPlayer?.let { player ->
            if (isPlayerReady) {
                val playerPosition = player.currentPosition / 1000f
                val timeDiff = kotlin.math.abs(playerPosition - currentTime)

                // Only seek if the difference is significant and it's not from our own update
                if (timeDiff > 0.5f && kotlin.math.abs(currentTime - lastSeekTime) > 0.1f) {
                    player.seekTo((currentTime * 1000).toLong())
                    lastSeekTime = currentTime
                }
            }
        }
    }

    // Handle play/pause state - FIXED
    LaunchedEffect(isPlaying) {
        exoPlayer?.let { player ->
            if (isPlayerReady) {
                if (isPlaying && !player.isPlaying) {
                    player.play()
                } else if (!isPlaying && player.isPlaying) {
                    player.pause()
                }
            }
        }
    }

    // Update timeline position periodically when playing
    LaunchedEffect(isPlaying) {
        if (isPlaying && exoPlayer != null && isPlayerReady) {
            while (isPlaying) {
                kotlinx.coroutines.delay(100)
                val playerPosition = exoPlayer.currentPosition / 1000f
                if (kotlin.math.abs(playerPosition - currentTime) > 0.2f) {
                    onSeek(playerPosition)
                }
            }
        }
    }

    DisposableEffect(videoUri) {
        onDispose {
            exoPlayer?.release()
        }
    }

    Column(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            colors = CardDefaults.cardColors(containerColor = Color.Black)
        ) {
            if (videoUri != null && exoPlayer != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            playerView = this
                        }
                    },
                    update = { pv ->
                        // Ensure player is properly attached
                        if (pv.player != exoPlayer) {
                            pv.player = exoPlayer
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Select a video to preview",
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        PlaybackControls(
            currentTime = currentTime,
            duration = duration,
            isPlaying = isPlaying,
            onPlayPause = onPlayPause,
            onSeek = { newTime ->
                lastSeekTime = newTime
                onSeek(newTime)
            }
        )
    }
}




@Composable
fun PlaybackControls(
    currentTime: Float,
    duration: Float,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Progress slider - IMPROVED
        var sliderValue by remember { mutableStateOf(0f) }
        var isDragging by remember { mutableStateOf(false) }

        // Update slider only when not dragging
        LaunchedEffect(currentTime) {
            if (!isDragging && duration > 0) {
                sliderValue = currentTime / duration
            }
        }

        Slider(
            value = sliderValue,
            onValueChange = { value ->
                isDragging = true
                sliderValue = value
            },
            onValueChangeFinished = {
                isDragging = false
                onSeek(sliderValue * duration)
            },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTime(currentTime),
                style = MaterialTheme.typography.bodySmall
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onSeek((currentTime - 10f).coerceAtLeast(0f)) }) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Replay10,
                        contentDescription = "Rewind 10s"
                    )
                }

                IconButton(onClick = onPlayPause) {
                    Icon(
                        imageVector = if (isPlaying)
                            androidx.compose.material.icons.Icons.Default.Pause
                        else
                            androidx.compose.material.icons.Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    )
                }

                IconButton(onClick = { onSeek((currentTime + 10f).coerceAtMost(duration)) }) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Forward10,
                        contentDescription = "Forward 10s"
                    )
                }
            }

            Text(
                text = formatTime(duration),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// Utility functions
fun formatTime(seconds: Float): String {
    val totalSeconds = seconds.roundToInt()
    val minutes = totalSeconds / 60
    val remainingSeconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, remainingSeconds)
}