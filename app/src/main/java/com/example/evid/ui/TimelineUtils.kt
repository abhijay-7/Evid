//// TimelineUtils.kt
package com.example.evid.ui
//
//import androidx.compose.foundation.Canvas
//import androidx.compose.foundation.background
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.gestures.detectDragGestures
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material.icons.filled.AudioFile
//import androidx.compose.material.icons.filled.Image
//import androidx.compose.material.icons.filled.Lock
//import androidx.compose.material.icons.filled.TextFields
//import androidx.compose.material.icons.filled.Videocam
//import androidx.compose.material.icons.filled.Visibility
//import androidx.compose.material.icons.filled.VisibilityOff
//import androidx.compose.material.icons.filled.VolumeOff
//import androidx.compose.material.icons.filled.VolumeUp
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.draw.clip
//import androidx.compose.ui.geometry.Offset
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.graphics.Path
//import androidx.compose.ui.graphics.drawscope.DrawScope
//import androidx.compose.ui.input.pointer.pointerInput
//import androidx.compose.ui.platform.LocalDensity
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import kotlin.math.abs
//import kotlin.math.roundToInt
//
//// Enhanced Timeline Component with additional features
//@Composable
//fun EnhancedTimelineComponent(
//    clips: List<TimelineClip>,
//    tracks: List<TimelineTrack>,
//    currentTime: Float,
//    totalDuration: Float,
//    pixelsPerSecond: Float = 50f,
//    onTimeChange: (Float) -> Unit,
//    onClipMove: (String, Float, Int) -> Unit,
//    onClipResize: (String, Float, Float) -> Unit, // New: resize functionality
//    onClipSelect: (String?) -> Unit,
//    selectedClipId: String? = null,
//    snapToGrid: Boolean = true,
//    showWaveforms: Boolean = false,
//    modifier: Modifier = Modifier
//) {
//    val density = LocalDensity.current
//    var timelineWidth by remember { mutableStateOf(0f) }
//
//    Column(modifier = modifier) {
//        // Timeline ruler with enhanced features
//        EnhancedTimelineRuler(
//            totalDuration = totalDuration,
//            currentTime = currentTime,
//            pixelsPerSecond = pixelsPerSecond,
//            onTimeChange = onTimeChange,
//            snapToGrid = snapToGrid,
//            modifier = Modifier
//                .fillMaxWidth()
//                .height(50.dp)
//        ) { width ->
//            timelineWidth = width
//        }
//
//        // Track container with scrolling
//        Column(
//            modifier = Modifier
//                .fillMaxWidth()
//                .weight(1f)
//        ) {
//            tracks.forEachIndexed { index, track ->
//                EnhancedTimelineTrack(
//                    track = track,
//                    trackIndex = index,
//                    clips = clips.filter { it.trackIndex == index },
//                    currentTime = currentTime,
//                    pixelsPerSecond = pixelsPerSecond,
//                    timelineWidth = timelineWidth,
//                    onClipMove = onClipMove,
//                    onClipResize = onClipResize,
//                    onClipSelect = onClipSelect,
//                    selectedClipId = selectedClipId,
//                    snapToGrid = snapToGrid,
//                    showWaveforms = showWaveforms && track.type == ClipType.AUDIO
//                )
//            }
//        }
//    }
//}
//
//@Composable
//fun EnhancedTimelineRuler(
//    totalDuration: Float,
//    currentTime: Float,
//    pixelsPerSecond: Float,
//    onTimeChange: (Float) -> Unit,
//    snapToGrid: Boolean,
//    modifier: Modifier = Modifier,
//    onWidthMeasured: (Float) -> Unit = {}
//) {
//    var isDragging by remember { mutableStateOf(false) }
//
//    Canvas(
//        modifier = modifier
//            .pointerInput(Unit) {
//                detectDragGestures(
//                    onDragStart = { isDragging = true },
//                    onDragEnd = { isDragging = false }
//                ) { _, _ ->
//                    // Handle dragging for playhead
//                }
//            }
//            .clickable { /* Handle clicks */ }
//    ) {
//        val width = size.width
//        val height = size.height
//        onWidthMeasured(width)
//
//        // Background
//        drawRect(Color.Gray.copy(alpha = 0.1f), size = size)
//
//        // Enhanced time markers with different scales
//        drawTimeMarkers(width, height, totalDuration, pixelsPerSecond)
//
//        // Playhead with enhanced visuals
//        drawPlayhead(currentTime, pixelsPerSecond, width, height, isDragging)
//
//        // Loop markers (if needed)
//        // drawLoopMarkers(...)
//    }
//}
//
//@Composable
//fun EnhancedTimelineTrack(
//    track: TimelineTrack,
//    trackIndex: Int,
//    clips: List<TimelineClip>,
//    currentTime: Float,
//    pixelsPerSecond: Float,
//    timelineWidth: Float,
//    onClipMove: (String, Float, Int) -> Unit,
//    onClipResize: (String, Float, Float) -> Unit,
//    onClipSelect: (String?) -> Unit,
//    selectedClipId: String?,
//    snapToGrid: Boolean,
//    showWaveforms: Boolean
//) {
//    Row(
//        modifier = Modifier
//            .fillMaxWidth()
//            .height(track.height)
//    ) {
//        // Enhanced track header
//        EnhancedTrackHeader(
//            track = track,
//            modifier = Modifier.width(150.dp)
//        )
//
//        // Track content with drop zones
//        Box(
//            modifier = Modifier
//                .weight(1f)
//                .fillMaxHeight()
//                .background(
//                    if (track.isVisible) Color.Gray.copy(alpha = 0.05f)
//                    else Color.Gray.copy(alpha = 0.15f)
//                )
//        ) {
//            // Grid lines for snapping
//            if (snapToGrid) {
//                GridLines(
//                    pixelsPerSecond = pixelsPerSecond,
//                    timelineWidth = timelineWidth,
//                    trackHeight = track.height
//                )
//            }
//
//            // Clips with enhanced features
//            clips.forEach { clip ->
//                EnhancedTimelineClip(
//                    clip = clip,
//                    trackIndex = trackIndex,
//                    pixelsPerSecond = pixelsPerSecond,
//                    isSelected = clip.id == selectedClipId,
//                    showWaveform = showWaveforms && clip.type == ClipType.AUDIO,
//                    onMove = { newStartTime, newTrackIndex ->
//                        onClipMove(clip.id, newStartTime, newTrackIndex)
//                    },
//                    onResize = { newStartTime, newDuration ->
//                        onClipResize(clip.id, newStartTime, newDuration)
//                    },
//                    onSelect = { onClipSelect(clip.id) },
//                    snapToGrid = snapToGrid,
//                    modifier = Modifier.fillMaxHeight()
//                )
//            }
//
//            // Playhead indicator
//            PlayheadIndicator(
//                currentTime = currentTime,
//                pixelsPerSecond = pixelsPerSecond,
//                timelineWidth = timelineWidth,
//                trackHeight = track.height
//            )
//        }
//    }
//}
//
//@Composable
//fun EnhancedTrackHeader(
//    track: TimelineTrack,
//    modifier: Modifier = Modifier
//) {
//    var isExpanded by remember { mutableStateOf(false) }
//
//    Card(
//        modifier = modifier.fillMaxHeight(),
//        colors = CardDefaults.cardColors(
//            containerColor = when (track.type) {
//                ClipType.VIDEO -> Color.Blue.copy(alpha = 0.1f)
//                ClipType.AUDIO -> Color.Green.copy(alpha = 0.1f)
//                ClipType.TEXT -> Color.Magenta.copy(alpha = 0.1f)
//                ClipType.IMAGE -> Color.Gray.copy(alpha = 0.1f)
//            }
//        )
//    ) {
//        Column(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(8.dp)
//                .clickable { isExpanded = !isExpanded }
//        ) {
//            // Track name and type
//            Row(
//                modifier = Modifier.fillMaxWidth(),
//                horizontalArrangement = Arrangement.SpaceBetween,
//                verticalAlignment = Alignment.CenterVertically
//            ) {
//                Text(
//                    text = track.name,
//                    style = MaterialTheme.typography.bodySmall,
//                    maxLines = 1
//                )
//
//                Icon(
//                    imageVector = when (track.type) {
//                        ClipType.VIDEO -> androidx.compose.material.icons.Icons.Default.Videocam
//                        ClipType.AUDIO -> androidx.compose.material.icons.Icons.Default.AudioFile
//                        ClipType.TEXT -> androidx.compose.material.icons.Icons.Default.TextFields
//                        ClipType.IMAGE -> androidx.compose.material.icons.Icons.Default.Image
//                    },
//                    contentDescription = track.type.name,
//                    modifier = Modifier.size(16.dp)
//                )
//            }
//
//            Spacer(modifier = Modifier.height(4.dp))
//
//            // Control buttons
//            Row(
//                horizontalArrangement = Arrangement.spacedBy(4.dp),
//                verticalAlignment = Alignment.CenterVertically
//            ) {
//                // Mute button (for audio tracks)
//                if (track.type == ClipType.AUDIO || track.type == ClipType.VIDEO) {
//                    IconButton(
//                        onClick = { /* Handle mute */ },
//                        modifier = Modifier.size(24.dp)
//                    ) {
//                        Icon(
//                            imageVector = if (track.isMuted)
//                                androidx.compose.material.icons.Icons.Default.VolumeOff
//                            else
//                                androidx.compose.material.icons.Icons.Default.VolumeUp,
//                            contentDescription = "Mute",
//                            modifier = Modifier.size(14.dp)
//                        )
//                    }
//                }
//
//                // Visibility button
//                IconButton(
//                    onClick = { /* Handle visibility */ },
//                    modifier = Modifier.size(24.dp)
//                ) {
//                    Icon(
//                        imageVector = if (track.isVisible)
//                            androidx.compose.material.icons.Icons.Default.Visibility
//                        else
//                            androidx.compose.material.icons.Icons.Default.VisibilityOff,
//                        contentDescription = "Visibility",
//                        modifier = Modifier.size(14.dp)
//                    )
//                }
//
//                // Lock button
//                IconButton(
//                    onClick = { /* Handle lock */ },
//                    modifier = Modifier.size(24.dp)
//                ) {
//                    Icon(
//                        imageVector = androidx.compose.material.icons.Icons.Default.Lock,
//                        contentDescription = "Lock",
//                        modifier = Modifier.size(14.dp)
//                    )
//                }
//            }
//
//            // Expanded controls
//            if (isExpanded) {
//                Spacer(modifier = Modifier.height(4.dp))
//
//                // Volume slider for audio tracks
//                if (track.type == ClipType.AUDIO || track.type == ClipType.VIDEO) {
//                    Slider(
//                        value = 0.7f, // Replace with actual volume
//                        onValueChange = { /* Handle volume change */ },
//                        modifier = Modifier.fillMaxWidth()
//                    )
//                }
//
//                // Opacity slider for video/image tracks
//                if (track.type == ClipType.VIDEO || track.type == ClipType.IMAGE) {
//                    Slider(
//                        value = 1.0f, // Replace with actual opacity
//                        onValueChange = { /* Handle opacity change */ },
//                        modifier = Modifier.fillMaxWidth()
//                    )
//                }
//            }
//        }
//    }
//}
//
//@Composable
//fun EnhancedTimelineClip(
//    clip: TimelineClip,
//    trackIndex: Int,
//    pixelsPerSecond: Float,
//    isSelected: Boolean,
//    showWaveform: Boolean,
//    onMove: (Float, Int) -> Unit,
//    onResize: (Float, Float) -> Unit,
//    onSelect: () -> Unit,
//    snapToGrid: Boolean,
//    modifier: Modifier = Modifier
//) {
//    val clipWidth = clip.duration * pixelsPerSecond
//    val clipOffset = clip.startTime * pixelsPerSecond
//
//    var dragOffset by remember { mutableStateOf(Offset.Zero) }
//    var isResizing by remember { mutableStateOf(false) }
//    var resizeHandle by remember { mutableStateOf(ResizeHandle.NONE) }
//
//    val density = LocalDensity.current
//
//    Box(
//        modifier = modifier
//            .offset(
//                x = with(density) { (clipOffset + dragOffset.x).toDp() },
//                y = with(density) { dragOffset.y.toDp() }
//            )
//            .width(with(density) { clipWidth.toDp() })
//            .padding(vertical = 2.dp)
//    ) {
//        Card(
//            modifier = Modifier
//                .fillMaxSize()
//                .pointerInput(Unit) {
//                    detectDragGestures(
//                        onDragStart = { offset ->
//                            val handleWidth = 10.dp.toPx()
//                            resizeHandle = when {
//                                offset.x < handleWidth -> ResizeHandle.START
//                                offset.x > clipWidth - handleWidth -> ResizeHandle.END
//                                else -> ResizeHandle.NONE
//                            }
//                            isResizing = resizeHandle != ResizeHandle.NONE
//                            onSelect()
//                        },
//                        onDragEnd = {
//                            if (isResizing) {
//                                // Handle resize end
//                                when (resizeHandle) {
//                                    ResizeHandle.START -> {
//                                        val newStartTime = ((clipOffset + dragOffset.x) / pixelsPerSecond)
//                                            .coerceAtLeast(0f)
//                                        val newDuration = clip.duration + (clip.startTime - newStartTime)
//                                        onResize(newStartTime, newDuration.coerceAtLeast(0.1f))
//                                    }
//                                    ResizeHandle.END -> {
//                                        val newDuration = ((clipWidth + dragOffset.x) / pixelsPerSecond)
//                                            .coerceAtLeast(0.1f)
//                                        onResize(clip.startTime, newDuration)
//                                    }
//                                    ResizeHandle.NONE -> {}
//                                }
//                            } else {
//                                // Handle move end
//                                val newStartTime = ((clipOffset + dragOffset.x) / pixelsPerSecond)
//                                    .coerceAtLeast(0f)
//                                val newTrackIndex = if (abs(dragOffset.y) > 30) {
//                                    (trackIndex + (dragOffset.y / 60).roundToInt()).coerceIn(0, 4)
//                                } else trackIndex
//
//                                onMove(newStartTime, newTrackIndex)
//                            }
//
//                            dragOffset = Offset.Zero
//                            isResizing = false
//                            resizeHandle = ResizeHandle.NONE
//                        }
//                    ) { _, dragAmount ->
//                        dragOffset += dragAmount
//                    }
//                },
//            colors = CardDefaults.cardColors(
//                containerColor = if (isSelected)
//                    clip.color.copy(alpha = 0.9f)
//                else
//                    clip.color.copy(alpha = 0.7f)
//            ),
//            border = if (isSelected)
//                androidx.compose.foundation.BorderStroke(2.dp, Color.White)
//            else null,
//            shape = RoundedCornerShape(4.dp)
//        ) {
//            Box(modifier = Modifier.fillMaxSize()) {
//                // Waveform background for audio clips
//                if (showWaveform) {
//                    AudioWaveform(
//                        modifier = Modifier.fillMaxSize(),
//                        color = Color.White.copy(alpha = 0.3f)
//                    )
//                }
//
//                // Clip content
//                Row(
//                    modifier = Modifier
//                        .fillMaxSize()
//                        .padding(4.dp),
//                    verticalAlignment = Alignment.CenterVertically
//                ) {
//                    Text(
//                        text = clip.name,
//                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
//                        color = Color.White,
//                        maxLines = 1,
//                        modifier = Modifier.weight(1f)
//                    )
//
//                    // Duration indicator
//                    Text(
//                        text = formatTime(clip.duration),
//                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.sp),
//                        color = Color.White.copy(alpha = 0.8f)
//                    )
//                }
//
//                // Resize handles
//                if (isSelected) {
//                    // Left resize handle
//                    Box(
//                        modifier = Modifier
//                            .fillMaxHeight()
//                            .width(6.dp)
//                            .background(Color.White.copy(alpha = 0.8f))
//                            .align(Alignment.CenterStart)
//                    )
//
//                    // Right resize handle
//                    Box(
//                        modifier = Modifier
//                            .fillMaxHeight()
//                            .width(6.dp)
//                            .background(Color.White.copy(alpha = 0.8f))
//                            .align(Alignment.CenterEnd)
//                    )
//                }
//            }
//        }
//    }
//}
//
//@Composable
//fun GridLines(
//    pixelsPerSecond: Float,
//    timelineWidth: Float,
//    trackHeight: androidx.compose.ui.unit.Dp
//) {
//    Canvas(
//        modifier = Modifier
//            .fillMaxWidth()
//            .height(trackHeight)
//    ) {
//        val gridInterval = when {
//            pixelsPerSecond < 20 -> 10f
//            pixelsPerSecond < 50 -> 5f
//            else -> 1f
//        }
//
//        var time = 0f
//        while (time * pixelsPerSecond <= timelineWidth) {
//            val x = time * pixelsPerSecond
//            drawLine(
//                Color.Gray.copy(alpha = 0.2f),
//                start = Offset(x, 0f),
//                end = Offset(x, size.height),
//                strokeWidth = 1f
//            )
//            time += gridInterval
//        }
//    }
//}
//
//@Composable
//fun PlayheadIndicator(
//    currentTime: Float,
//    pixelsPerSecond: Float,
//    timelineWidth: Float,
//    trackHeight: androidx.compose.ui.unit.Dp
//) {
//    val playheadX = currentTime * pixelsPerSecond
//
//    if (playheadX <= timelineWidth) {
//        Canvas(
//            modifier = Modifier
//                .fillMaxWidth()
//                .height(trackHeight)
//        ) {
//            drawLine(
//                Color.Red.copy(alpha = 0.8f),
//                start = Offset(playheadX, 0f),
//                end = Offset(playheadX, size.height),
//                strokeWidth = 2f
//            )
//        }
//    }
//}
//
//@Composable
//fun AudioWaveform(
//    modifier: Modifier = Modifier,
//    color: Color = Color.White
//) {
//    Canvas(modifier = modifier) {
//        // Simplified waveform visualization
//        val points = mutableListOf<Offset>()
//        val width = size.width
//        val height = size.height
//        val centerY = height / 2
//
//        // Generate sample waveform data
//        for (i in 0 until width.toInt() step 2) {
//            val amplitude = (kotlin.math.sin(i * 0.1) * centerY * 0.6).toFloat()
//            points.add(Offset(i.toFloat(), centerY + amplitude))
//            points.add(Offset(i.toFloat(), centerY - amplitude))
//        }
//
//        // Draw waveform
//        for (i in points.indices step 2) {
//            if (i + 1 < points.size) {
//                drawLine(
//                    color,
//                    points[i],
//                    points[i + 1],
//                    strokeWidth = 1f
//                )
//            }
//        }
//    }
//}
//
//enum class ResizeHandle {
//    NONE, START, END
//}
//
//// Extension functions for timeline utilities
//fun DrawScope.drawTimeMarkers(
//    width: Float,
//    height: Float,
//    totalDuration: Float,
//    pixelsPerSecond: Float
//) {
//    val majorInterval = when {
//        pixelsPerSecond < 20 -> 10f
//        pixelsPerSecond < 50 -> 5f
//        pixelsPerSecond < 100 -> 2f
//        else -> 1f
//    }
//
//    var time = 0f
//    while (time <= totalDuration) {
//        val x = time * pixelsPerSecond
//        if (x <= width) {
//            // Major tick
//            drawLine(
//                Color.Gray,
//                start = Offset(x, height * 0.4f),
//                end = Offset(x, height),
//                strokeWidth = 2f
//            )
//        }
//        time += majorInterval
//    }
//}
//
//fun DrawScope.drawPlayhead(
//    currentTime: Float,
//    pixelsPerSecond: Float,
//    width: Float,
//    height: Float,
//    isDragging: Boolean
//) {
//    val playheadX = currentTime * pixelsPerSecond
//    if (playheadX <= width) {
//        // Playhead line
//        drawLine(
//            if (isDragging) Color.Red.copy(alpha = 1f) else Color.Red.copy(alpha = 0.8f),
//            start = Offset(playheadX, 0f),
//            end = Offset(playheadX, height),
//            strokeWidth = if (isDragging) 4f else 3f
//        )
//
//        // Playhead handle
//        val path = Path().apply {
//            moveTo(playheadX - 8f, 0f)
//            lineTo(playheadX + 8f, 0f)
//            lineTo(playheadX, 12f)
//            close()
//        }
//        drawPath(path, Color.Red)
//    }
//}