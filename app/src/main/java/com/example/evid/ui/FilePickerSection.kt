package com.example.evid.ui


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun FilePickerSection(
    filename: String,
    onFilenameChange: (String) -> Unit,
    onSelectInput: () -> Unit,
    onSelectSecondInput: () -> Unit,
    isProcessing: Boolean
) {
    TextField(
        value = filename,
        onValueChange = onFilenameChange,
        label = { Text("Output Filename (optional)") },
        placeholder = { Text("e.g., my_video.mp4") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !isProcessing
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Button(onClick = onSelectInput, enabled = !isProcessing) {
            Text("Select Input Video")
        }
        Button(onClick = onSelectSecondInput, enabled = !isProcessing) {
            Text("Select Second Input")
        }
    }
}