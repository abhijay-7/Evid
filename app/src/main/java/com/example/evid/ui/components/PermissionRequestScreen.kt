package com.example.evid.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.evid.permissions.PermissionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionRequestScreen(
    permissionManager: PermissionManager,
    onPermissionsGranted: () -> Unit
) {
    var isRequestingPermissions by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permissions Required") }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.VideoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Video Editor Needs Permissions",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "To provide the best video editing experience, we need access to the following:",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(permissionManager.getDeniedPermissions()) { permission ->
                val (title, description) = permissionManager.getPermissionDescription(permission)

                PermissionCard(
                    icon = getPermissionIcon(permission),
                    title = title,
                    description = description
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        isRequestingPermissions = true
                        permissionManager.requestPermissions { granted ->
                            isRequestingPermissions = false
                            if (granted) {
                                onPermissionsGranted()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isRequestingPermissions
                ) {
                    if (isRequestingPermissions) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isRequestingPermissions) "Requesting..." else "Grant Permissions")
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (permissionManager.getDeniedPermissions().isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Why We Need These Permissions",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "These permissions ensure you can import videos, record new content, save your projects, and access advanced features like cloud sync and background processing.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun getPermissionIcon(permission: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when {
        permission.contains("VIDEO") || permission.contains("STORAGE") ||
                permission.contains("EXTERNAL") || permission.contains("MANAGE") -> Icons.Default.VideoLibrary
        permission.contains("AUDIO") || permission.contains("RECORD_AUDIO") -> Icons.Default.Mic
        permission.contains("IMAGES") -> Icons.Default.Image
        permission.contains("CAMERA") -> Icons.Default.Camera
        permission.contains("INTERNET") || permission.contains("NETWORK") -> Icons.Default.Wifi
        permission.contains("WAKE_LOCK") -> Icons.Default.Power
        else -> Icons.Default.Security
    }
}