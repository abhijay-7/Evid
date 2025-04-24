package com.example.evid.ui

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun JoinVideoSection(
    isProcessing: Boolean,
    onJoin: () -> Unit
) {
    Button(
        onClick = onJoin,
        enabled = !isProcessing
    ) {
        Text("Join Videos")
    }
}
