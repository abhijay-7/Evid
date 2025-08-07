package com.example.evid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.evid.permissions.PermissionManager
import com.example.evid.ui.components.PermissionRequestScreen
import com.example.evid.ui.screens.AudioExtractionScreen
import com.example.evid.ui.screens.FrameExtractionScreen
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorMainScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }

    val tabs = listOf(
        "Overview",
        "Frame Extraction",
        "Audio Extraction"
    )

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTab) {
            0 -> OverviewScreen()
            1 -> FrameExtractionScreen()
            2 -> AudioExtractionScreen()
        }
    }
}

@Composable
fun OverviewScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Video Editor",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    FeatureStatusItem(
                        feature = "1.1.1: Video Analysis & Metadata Extraction",
                        status = "Completed",
                        isCompleted = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    FeatureStatusItem(
                        feature = "1.1.2: Frame Extraction System",
                        status = "Completed",
                        isCompleted = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    FeatureStatusItem(
                        feature = "1.1.3: Audio Extraction System",
                        status = "Completed",
                        isCompleted = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Ready for video analysis, frame, and audio extraction",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureStatusItem(
    feature: String,
    status: String,
    isCompleted: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = feature,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )

        Surface(
            color = if (isCompleted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = "$status ${if (isCompleted) "✓" else "⏳"}",
                style = MaterialTheme.typography.bodySmall,
                color = if (isCompleted) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}