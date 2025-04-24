package com.example.evid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ClipVideoSection(
    clipRange: ClosedFloatingPointRange<Float>,
    videoDuration: Float,
    isProcessing: Boolean,
    onClipRangeChange: (ClosedFloatingPointRange<Float>) -> Unit,
    onClip: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onClip,
            enabled = !isProcessing,
            modifier = Modifier.weight(1f)
        ) {
            Text("Clip Video")
        }

        Column(
            modifier = Modifier.weight(2f).padding(start = 8.dp)
        ) {
            RangeSlider(
                value = clipRange,
                onValueChange = onClipRangeChange,
                valueRange = 0f..videoDuration.coerceAtLeast(1f),
                enabled = !isProcessing
            )
            Text(
                text = "Clip: %.1fs to %.1fs".format(clipRange.start, clipRange.endInclusive),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}