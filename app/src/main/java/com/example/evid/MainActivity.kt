package com.example.evid

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.example.evid.ui.theme.EviDTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    var filename by remember { mutableStateOf("") }
    var userCommand by remember { mutableStateOf("") }
    var ffmpegLog by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf<Float?>(null) }
    var inputDuration by remember { mutableStateOf<Double?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    val selectInputFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                inputUri = it
                outputText = "Selected input: $it"
                coroutineScope.launch(Dispatchers.IO) {
                    val inputSaf = FFmpegKitConfig.getSafParameterForRead(context, it)
                    val probeSession = FFprobeKit.execute("-i $inputSaf -show_entries format=duration -v quiet -of csv=\"p=0\"")
                    withContext(Dispatchers.Main) {
                        if (ReturnCode.isSuccess(probeSession.returnCode)) {
                            inputDuration = probeSession.output.toDoubleOrNull()
                            outputText += "\nInput duration: ${inputDuration ?: "Unknown"} seconds"
                        } else {
                            outputText += "\nFailed to get duration: ${probeSession.failStackTrace ?: "No stack trace"}"
                        }
                    }
                }
            } catch (e: Exception) {
                outputText = "Failed to persist input URI: ${e.message}"
            }
        }
    }

    fun getOriginalFilename(uri: android.net.Uri): String? {
        var filename: String? = null
        val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    filename = it.getString(nameIndex)
                }
            }
        }
        return filename
    }

    fun prepareFilename(userInput: String, defaultName: String, inputUri: android.net.Uri?, isAudio: Boolean): String {
        val sanitized = userInput.trim().replace("[^a-zA-Z0-9.]".toRegex(), "_")
        val extension = if (isAudio) ".mp3" else ".mp4"
        return when {
            sanitized.isNotEmpty() -> {
                if (sanitized.endsWith(extension, ignoreCase = true)) sanitized else "$sanitized$extension"
            }
            inputUri != null -> {
                val original = getOriginalFilename(inputUri)
                if (original != null && original.endsWith(".mp4", ignoreCase = true)) {
                    if (isAudio) original.replace(".mp4", ".mp3", ignoreCase = true) else original
                } else {
                    defaultName
                }
            }
            else -> defaultName
        }
    }

    fun isAudioCommand(command: String): Boolean {
        return command.contains("-map a", ignoreCase = true)
    }

    fun sanitizeCommand(command: String): String {
//        return command.trim().replace("(-i\\s+|\\s*-c\\s+copy\\s*)".toRegex(), "")
        return  command
    }

    suspend fun copyFileToDownloads(outputFile: File, outputFileName: String, isAudio: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentResolver = context.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, outputFileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, if (isAudio) "audio/mpeg" else "video/mp4")
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
                        withContext(Dispatchers.Main) {
                            outputText += "\nCopied to Download/EviD/$outputFileName"
                        }
                    } ?: run {
                        withContext(Dispatchers.Main) {
                            outputText += "\nFailed to create file in Download/EviD"
                        }
                    }
                } else {
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val evidDir = File(downloadsDir, "EviD")
                    if (!evidDir.exists()) {
                        evidDir.mkdirs()
                    }
                    val destinationFile = File(evidDir, outputFileName)
                    outputFile.copyTo(destinationFile, overwrite = true)
                    withContext(Dispatchers.Main) {
                        outputText += "\nCopied to Download/EviD/$outputFileName"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    outputText += "\nCopy failed: ${e.message}"
                }
            }
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
            TextField(
                value = filename,
                onValueChange = { filename = it },
                label = { Text("Output Filename (optional)") },
                placeholder = { Text("e.g., my_video.mp4") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isProcessing
            )

            TextField(
                value = userCommand,
                onValueChange = { userCommand = it },
                label = { Text("Custom Command (optional)") },
                placeholder = { Text("e.g., -q:a 0 -map a") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isProcessing
            )

            progress?.let {
                CircularProgressIndicator(
                    progress = it,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Progress: ${(it * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Button(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        val session = FFmpegKit.execute("-version")
                        withContext(Dispatchers.Main) {
                            outputText = if (ReturnCode.isSuccess(session.returnCode)) {
                                session.output
                            } else {
                                "Failed: ${session.failStackTrace ?: "No stack trace"}"
                            }
                        }
                    }
                },
                enabled = !isProcessing
            ) {
                Text("Check FFmpeg Version")
            }

            Button(
                onClick = {
                    selectInputFile.launch(arrayOf("video/*"))
                },
                enabled = !isProcessing
            ) {
                Text("Select Input Video")
            }

            Button(
                onClick = {
                    if (inputUri == null) {
                        outputText = "Please select an input video first"
                        return@Button
                    }
                    isProcessing = true
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val inputSaf = FFmpegKitConfig.getSafParameterForRead(context, inputUri!!)
                            if (inputSaf.isEmpty()) {
                                withContext(Dispatchers.Main) {
                                    outputText = "Failed to get SAF parameter for input"
                                    isProcessing = false
                                }
                                return@launch
                            }
                            val outputFile = File(context.getExternalFilesDir(null), "clipped_output.mp4")
                            val outputFilename = prepareFilename(filename, "clipped_output.mp4", inputUri, isAudio = false)
                            val command = "-y -i $inputSaf -ss 00:00:00 -t 10 -c:v mpeg4 -preset fast -crf 0 -c:a aac ${outputFile.absolutePath}"
                            withContext(Dispatchers.Main) {
                                ffmpegLog = ""
                                progress = 0f
                            }
                            FFmpegKitConfig.enableStatisticsCallback { statistics ->
                                coroutineScope.launch(Dispatchers.Main) {
                                    inputDuration?.let { duration ->
                                        val timeSeconds = statistics.time / 1000.0
                                        val progressPercent = (timeSeconds / duration).coerceIn(0.0, 1.0).toFloat()
                                        progress = progressPercent
                                    }
                                }
                            }
                            val session = FFmpegKit.execute(command)
                            withContext(Dispatchers.Main) {
                                progress = null
                                isProcessing = false
                                outputText = if (ReturnCode.isSuccess(session.returnCode)) {
                                    "Clipped video saved to ${outputFile.absolutePath}\n${session.output}"
                                } else {
                                    "Clip failed: ${session.failStackTrace ?: "No stack trace"}\nLogs:\n$ffmpegLog"
                                }
                            }
                            if (ReturnCode.isSuccess(session.returnCode)) {
                                copyFileToDownloads(outputFile, outputFilename, isAudio = false)
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                progress = null
                                isProcessing = false
                                outputText = "Clip error: ${e.message}\nLogs:\n$ffmpegLog"
                            }
                        }
                    }
                },
                enabled = !isProcessing
            ) {
                Text("Clip Video (10s)")
            }

            Button(
                onClick = {
                    if (inputUri == null) {
                        outputText = "Please select an input video first"
                        return@Button
                    }
                    isProcessing = true
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val inputSaf = FFmpegKitConfig.getSafParameterForRead(context, inputUri!!)
                            if (inputSaf.isEmpty()) {
                                withContext(Dispatchers.Main) {
                                    outputText = "Failed to get SAF parameter for input"
                                    isProcessing = false
                                }
                                return@launch
                            }
                            val outputFile = File(context.getExternalFilesDir(null), "scaled_output.mp4")
                            val outputFilename = prepareFilename(filename, "scaled_output.mp4", inputUri, isAudio = false)
                            val command = "-y -i $inputSaf -vf scale=640:360 -c:v libx264 -crf 0 -preset medium  -c:a copy ${outputFile.absolutePath}"
                            withContext(Dispatchers.Main) {
                                ffmpegLog = ""
                                progress = 0f
                            }
                            FFmpegKitConfig.enableStatisticsCallback { statistics ->
                                coroutineScope.launch(Dispatchers.Main) {
                                    inputDuration?.let { duration ->
                                        val timeSeconds = statistics.time / 1000.0
                                        val progressPercent = (timeSeconds / duration).coerceIn(0.0, 1.0).toFloat()
                                        progress = progressPercent
                                    }
                                }
                            }
                            val session = FFmpegKit.execute(command)
                            withContext(Dispatchers.Main) {
                                progress = null
                                isProcessing = false
                                outputText = if (ReturnCode.isSuccess(session.returnCode)) {
                                    "Scaled video saved to ${outputFile.absolutePath}\n${session.output}"
                                } else {
                                    "Scale failed: ${session.failStackTrace ?: "No stack trace"}\nLogs:\n$ffmpegLog"
                                }
                            }
                            if (ReturnCode.isSuccess(session.returnCode)) {
                                copyFileToDownloads(outputFile, outputFilename, isAudio = false)
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                progress = null
                                isProcessing = false
                                outputText = "Scale error: ${e.message}\nLogs:\n$ffmpegLog"
                            }
                        }
                    }
                },
                enabled = !isProcessing
            ) {
                Text("Scale Video (640x360)")
            }

            Button(
                onClick = {
                    if (inputUri == null || userCommand.isBlank()) {
                        outputText = "Please select an input video and enter a command"
                        return@Button
                    }
                    isProcessing = true
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val inputSaf = FFmpegKitConfig.getSafParameterForRead(context, inputUri!!)
                            if (inputSaf.isEmpty()) {
                                withContext(Dispatchers.Main) {
                                    outputText = "Failed to get SAF parameter for input"
                                    isProcessing = false
                                }
                                return@launch
                            }
                            val isAudio = isAudioCommand(userCommand)
                            val defaultName = if (isAudio) "custom_output.mp3" else "custom_output.mp4"
                            val outputFile = File(context.getExternalFilesDir(null), defaultName)
                            val outputFilename = prepareFilename(filename, defaultName, inputUri, isAudio)
                            val sanitizedCommand = sanitizeCommand(userCommand)
                            val command = "-y -i $inputSaf $sanitizedCommand ${outputFile.absolutePath}"
                            withContext(Dispatchers.Main) {
                                ffmpegLog = ""
                                progress = 0f
                            }
                            FFmpegKitConfig.enableStatisticsCallback { statistics ->
                                coroutineScope.launch(Dispatchers.Main) {
                                    inputDuration?.let { duration ->
                                        val timeSeconds = statistics.time / 1000.0
                                        val progressPercent = (timeSeconds / duration).coerceIn(0.0, 1.0).toFloat()
                                        progress = progressPercent
                                    }
                                }
                            }
                            val session = FFmpegKit.execute(command)
                            withContext(Dispatchers.Main) {
                                progress = null
                                isProcessing = false
                                outputText = if (ReturnCode.isSuccess(session.returnCode)) {
                                    "Custom command saved to ${outputFile.absolutePath}\n${session.output}"
                                } else {
                                    "Custom failed: ${session.failStackTrace ?: "No stack trace"}\nLogs:\n$ffmpegLog"
                                }
                            }
                            if (ReturnCode.isSuccess(session.returnCode)) {
                                copyFileToDownloads(outputFile, outputFilename, isAudio)
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                progress = null
                                isProcessing = false
                                outputText = "Custom error: ${e.message}\nLogs:\n$ffmpegLog"
                            }
                        }
                    }
                },
                enabled = !isProcessing
            ) {
                Text("Run Custom Command")
            }

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

    LaunchedEffect(Unit) {
        FFmpegKitConfig.enableLogCallback { log ->
            ffmpegLog += "${log.message}\n"
            println("FFmpeg Log: ${log.message}")
        }
    }
}