package com.example.evid.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth

@Composable
fun OutputTextSection(outputText: String) {
    Text(
        text = outputText,
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    )
}