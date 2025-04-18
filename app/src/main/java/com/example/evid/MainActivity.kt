package com.example.evid

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.example.evid.ui.theme.EviDTheme
import java.io.File
import java.io.FileInputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            EviDTheme {
                // Request storage permissions
                RequestStoragePermissions()
                // Main UI
                FFmpegTestScreen()
            }
        }
    }
}

@Composable
fun RequestStoragePermissions() {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            Toast.makeText(context, "Storage permissions granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Storage permissions denied", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        val permissions = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        if (permissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }) {
            // Permissions already granted
        } else {
            permissionLauncher.launch(permissions)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FFmpegTestScreen() {
    val context = LocalContext.current
    var outputText by remember { mutableStateOf("Ready to run FFmpeg") }
    var inputUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val scrollState = rememberScrollState()

    // SAF launcher for input file selection
    val selectInputFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            inputUri = it
            outputText = "Selected input: $it"
        }
    }

    // Function to copy file to Download/EviD
    fun copyFileToDownloads(outputFile: File, outputFileName: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+: Use MediaStore
                val contentResolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, outputFileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/EviD")
                }
                val newFileUri = contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values
                )
                newFileUri?.let {
                    contentResolver.openOutputStream(it)?.use { outputStream ->
                        FileInputStream(outputFile).use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    outputText += "\nCopied to Download/EviD/$outputFileName"
                } ?: run {
                    outputText += "\nFailed to create file in Download/EviD"
                }
            } else {
                // Pre-Android 10: Use File operations
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val evidDir = File(downloadsDir, "EviD")
                if (!evidDir.exists()) {
                    evidDir.mkdirs()
                }
                val destinationFile = File(evidDir, outputFileName)
                outputFile.copyTo(destinationFile, overwrite = true)
                outputText += "\nCopied to Download/EviD/$outputFileName"
            }
        } catch (e: Exception) {
            outputText += "\nCopy failed: ${e.message}"
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("FFmpeg Test App") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Button to check FFmpeg version
            Button(onClick = {
                val session = FFmpegKit.execute("-version")
                outputText = if (ReturnCode.isSuccess(session.returnCode)) {
                    session.output // E.g., "ffmpeg version 6.0"
                } else {
                    "Failed: ${session.failStackTrace}"
                }
            }) {
                Text("Check FFmpeg Version")
            }

            // Button to select input video
            Button(onClick = {
                selectInputFile.launch(arrayOf("video/*"))
            }) {
                Text("Select Input Video")
            }

            // Button to clip video (10-second segment)
            Button(onClick = {
                if (inputUri == null) {
                    outputText = "Please select an input video first"
                    return@Button
                }
                val inputSaf = FFmpegKitConfig.getSafParameterForRead(context, inputUri!!)
                val outputFile = File(context.getExternalFilesDir(null), "clipped_output.mp4")
                val command = "-y -i $inputSaf -ss 00:00:00 -t 10 -c copy ${outputFile.absolutePath}"
                val session = FFmpegKit.execute(command)
                outputText = if (ReturnCode.isSuccess(session.returnCode)) {
                    "Clipped video saved to ${outputFile.absolutePath}\n${session.output}"
                } else {
                    "Clip failed: ${session.failStackTrace}"
                }
                if (ReturnCode.isSuccess(session.returnCode)) {
                    copyFileToDownloads(outputFile, "clipped_output.mp4")
                }
            }) {
                Text("Clip Video (10s)")
            }

            // Button to scale video (640x360)
            Button(onClick = {
                if (inputUri == null) {
                    outputText = "Please select an input video first"
                    return@Button
                }
                val inputSaf = FFmpegKitConfig.getSafParameterForRead(context, inputUri!!)
                val outputFile = File(context.getExternalFilesDir(null), "scaled_output.mp4")
                val command = "-i $inputSaf -vf scale=640:360 -c:a copy ${outputFile.absolutePath}"
                val session = FFmpegKit.execute(command)
                outputText = if (ReturnCode.isSuccess(session.returnCode)) {
                    "Scaled video saved to ${outputFile.absolutePath}\n${session.output}"
                } else {
                    "Scale failed: ${session.failStackTrace}"
                }
                if (ReturnCode.isSuccess(session.returnCode)) {
                    copyFileToDownloads(outputFile, "scaled_output.mp4")
                }
            }) {
                Text("Scale Video (640x360)")
            }

            // Output display
            Text(
                text = outputText,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )
        }
    }

    // Enable FFmpeg logs
    LaunchedEffect(Unit) {
        FFmpegKitConfig.enableLogCallback { log ->
            println("FFmpeg Log: ${log.message}")
        }
    }
}