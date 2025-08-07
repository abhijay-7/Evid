package com.example.evid.permissions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

class PermissionManager(private val activity: ComponentActivity) {

    // Runtime permissions based on Android version
    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    } else {
        arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        ).let {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                it + Manifest.permission.WRITE_EXTERNAL_STORAGE
            } else it
        }
    }

    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private var onPermissionsResult: (Boolean) -> Unit = {}

    fun registerLaunchers() {
        permissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.all { it.value }
            onPermissionsResult(allGranted)
        }
    }

    fun checkPermissions(): Boolean {
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestPermissions(onResult: (Boolean) -> Unit) {
        onPermissionsResult = onResult

        val deniedPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (deniedPermissions.isNotEmpty()) {
            permissionLauncher.launch(deniedPermissions)
        } else {
            onResult(true)
        }
    }

    fun getDeniedPermissions(): List<String> {
        return requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    fun getPermissionDescription(permission: String): Pair<String, String> {
        return when (permission) {
            Manifest.permission.READ_MEDIA_VIDEO -> "Video Access" to "Required to read and import video files from your device"
            Manifest.permission.READ_MEDIA_AUDIO -> "Audio Access" to "Required to read audio files and extract audio from videos"
            Manifest.permission.READ_MEDIA_IMAGES -> "Image Access" to "Required to read image files for thumbnails and overlays"
            Manifest.permission.READ_EXTERNAL_STORAGE -> "Storage Access" to "Required to read video and audio files from your device storage"
            Manifest.permission.WRITE_EXTERNAL_STORAGE -> "Storage Write" to "Required to save edited videos and export projects"
            Manifest.permission.CAMERA -> "Camera Access" to "Required to record videos directly within the app"
            Manifest.permission.RECORD_AUDIO -> "Microphone Access" to "Required to record audio with videos and voice-overs"
            else -> "Unknown Permission" to "Required for app functionality"
        }
    }
}
