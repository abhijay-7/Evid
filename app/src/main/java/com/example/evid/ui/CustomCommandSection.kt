package com.example.evid.ui


import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CustomCommandSection(
    isProcessing: Boolean,
    onRunCommand: (String) -> Unit
) {
    var customCommand by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = customCommand,
            onValueChange = { customCommand = it },
            label = { Text("Custom FFmpeg Command") },
            modifier = Modifier.weight(2f),
            enabled = !isProcessing
        )
        Button(
            onClick = { onRunCommand(customCommand) },
            enabled = !isProcessing,
            modifier = Modifier.weight(1f)
        ) {
            Text("Run Command")
        }
    }
}