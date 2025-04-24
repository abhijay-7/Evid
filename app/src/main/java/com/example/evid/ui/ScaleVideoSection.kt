package com.example.evid.ui


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun ScaleVideoSection(
    scaleWidth: String,
    scaleHeight: String,
    onScaleWidthChange: (String) -> Unit,
    onScaleHeightChange: (String) -> Unit,
    onScale: () -> Unit,
    originalWidth: Int,
    originalHeight: Int,
    isProcessing: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onScale,
            enabled = !isProcessing,
            modifier = Modifier.weight(1f)
        ) {
            Text("Scale Video")
        }

        Column(
            modifier = Modifier.weight(2f).padding(start = 8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = scaleWidth,
                    onValueChange = { onScaleWidthChange(it.filter { c -> c.isDigit() }) },
                    label = { Text("Width") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !isProcessing
                )
                OutlinedTextField(
                    value = scaleHeight,
                    onValueChange = { onScaleHeightChange(it.filter { c -> c.isDigit() }) },
                    label = { Text("Height") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !isProcessing
                )
            }
            Text(
                text = "Original: ${if (originalWidth > 0) originalWidth else "Unknown"}x${if (originalHeight > 0) originalHeight else "Unknown"}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}