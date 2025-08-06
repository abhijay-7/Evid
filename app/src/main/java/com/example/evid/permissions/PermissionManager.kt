package com.example.evid.permissions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

class PermissionManager(private val activity: ComponentActivity) {



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

    // Special permissions that need different handling
    private val specialPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        arrayOf(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
    } else {
        emptyArray()
    }




    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private var onPermissionsResult: (Boolean) -> Unit = {}

    fun registerLaunchers() {
        permissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.all { it.value }
            val specialPermissionsGranted = checkSpecialPermissions()
            onPermissionsResult(allGranted && specialPermissionsGranted)
        }
    }

    fun checkPermissions(): Boolean {
        val regularPermissions = requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
        }

        val specialPermissionsGranted = checkSpecialPermissions()

        return regularPermissions && specialPermissionsGranted
    }

    private fun checkSpecialPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
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
            val specialPermissionsGranted = checkSpecialPermissions()
            onResult(specialPermissionsGranted)
        }
    }

    fun requestManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            activity.startActivity(intent)
        }
    }

    fun getDeniedPermissions(): List<String> {
        val denied = mutableListOf<String>()

        denied.addAll(requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !checkSpecialPermissions()) {
            denied.add(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
        }

        return denied
    }

    fun getPermissionDescription(permission: String): Pair<String, String> {
        return when (permission) {
            Manifest.permission.READ_MEDIA_VIDEO -> Pair(
                "Video Access",
                "Required to read and import video files from your device"
            )
            Manifest.permission.READ_MEDIA_AUDIO -> Pair(
                "Audio Access",
                "Required to read audio files and extract audio from videos"
            )
            Manifest.permission.READ_MEDIA_IMAGES -> Pair(
                "Image Access",
                "Required to read image files for thumbnails and overlays"
            )
            Manifest.permission.READ_EXTERNAL_STORAGE -> Pair(
                "Storage Access",
                "Required to read video and audio files from your device storage"
            )
            Manifest.permission.WRITE_EXTERNAL_STORAGE -> Pair(
                "Storage Write",
                "Required to save edited videos and export projects"
            )
            Manifest.permission.MANAGE_EXTERNAL_STORAGE -> Pair(
                "Full Storage Access",
                "Required for advanced file management and large video processing"
            )
            Manifest.permission.CAMERA -> Pair(
                "Camera Access",
                "Required to record videos directly within the app"
            )
            Manifest.permission.RECORD_AUDIO -> Pair(
                "Microphone Access",
                "Required to record audio with videos and voice-overs"
            )
            else -> Pair("Unknown Permission", "Required for app functionality")
        }
    }
}

@Composable
fun rememberPermissionManager(): PermissionManager {
    val context = LocalContext.current
    val activity = remember(context) {
        context as? ComponentActivity
            ?: error("PermissionManager requires a ComponentActivity context")
    }
    return remember { PermissionManager(activity) }
}
