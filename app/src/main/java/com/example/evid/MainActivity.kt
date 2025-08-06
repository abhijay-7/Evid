// File: app/src/main/java/com/videoeditor/MainActivity.kt
package com.example.evid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.evid.permissions.PermissionManager
import com.example.evid.permissions.rememberPermissionManager
import com.example.evid.ui.components.PermissionRequestScreen
import com.example.evid.ui.theme.EviDTheme

class MainActivity : ComponentActivity() {
    private lateinit var permissionManager: PermissionManager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()


        permissionManager = PermissionManager(this)
        permissionManager.registerLaunchers()
        setContent {
            EviDTheme {
                VideoEditorApp(permissionManager)
            }
        }
    }
}

@Composable
fun VideoEditorApp(permissionManager: PermissionManager) {

    var hasPermissions by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        hasPermissions = permissionManager.checkPermissions()
    }

    if (hasPermissions) {
        VideoEditorMainScreen()
    } else {
        PermissionRequestScreen(
            permissionManager = permissionManager,
            onPermissionsGranted = {
                hasPermissions = true
            }
        )
    }
}

@Composable
fun VideoEditorMainScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Video Editor",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Feature 1.1.1: Video Analysis & Metadata Extraction - Completed ✓",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Ready for video analysis implementation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}