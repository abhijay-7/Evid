package com.example.evid

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
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
    var secondInputUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var videoDuration by remember { mutableStateOf(0f) }
    var clipRange by remember { mutableStateOf(0f..10f) }
    var originalWidth by remember { mutableStateOf(0) }
    var originalHeight by remember { mutableStateOf(0) }
    var scaleWidth by remember { mutableStateOf("") }
    var scaleHeight by remember { mutableStateOf("") }
    var probeOutput by remember { mutableStateOf("") } // Store FFprobe JSON
    var isAudioFile by remember { mutableStateOf(false) } // True if audio-only

// Updated selectInputFile launcher
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
                    try {
                        val tempFile = File(context.getExternalFilesDir(null), "temp_probe.mp4")
                        try {
                            withTimeout(30000) {
                                context.contentResolver.openInputStream(it)?.use { inputStream ->
                                    tempFile.outputStream().use { outputStream ->
                                        val buffer = ByteArray(8192)
                                        var bytesRead: Int
                                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                            outputStream.write(buffer, 0, bytesRead)
                                        }
                                        outputStream.flush()
                                    }
                                } ?: throw Exception("Failed to open input stream")
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                outputText = "Failed to copy input for probing: ${e.message}"
                            }
                            return@launch
                        }

                        val probeSession = FFprobeKit.execute(
                            "-i ${tempFile.absolutePath} -show_entries format=duration:stream=width,height -select_streams v:0 -v quiet -of json"
                        )
                        tempFile.delete()

                        if (ReturnCode.isSuccess(probeSession.returnCode)) {
                            val json = JSONObject(probeSession.output)
                            val format = json.getJSONObject("format")
                            val duration = format.getString("duration").toFloatOrNull() ?: 0f
                            val streams = json.getJSONArray("streams")
                            var width = 0
                            var height = 0
                            if (streams.length() > 0) {
                                val stream = streams.getJSONObject(0)
                                width = stream.getInt("width")
                                height = stream.getInt("height")
                            }
                            withContext(Dispatchers.Main) {
                                videoDuration = duration
                                clipRange = 0f..minOf(10f, duration)
                                originalWidth = width
                                originalHeight = height
                                scaleWidth = width.toString()
                                scaleHeight = height.toString()
                                outputText = "Video info: ${if (width > 0) "${width}x${height}, " else ""}${duration}s"
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                outputText = "Failed to probe video: ${probeSession.failStackTrace ?: "Unknown error"}"
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            outputText = "Probe error: ${e.message}"
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
                // Clean extra extension if multiple dots are present
                 val cleanedFileName = if (outputFileName.count { it == '.' } >= 2) {
                    outputFileName.substringBeforeLast('.')
                } else {
                    outputFileName
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentResolver = context.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, cleanedFileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
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
                            outputText += "\nCopied to Download/EviD/$cleanedFileName"
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
                    val destinationFile = File(evidDir, cleanedFileName)
                    outputFile.copyTo(destinationFile, overwrite = true)
                    withContext(Dispatchers.Main) {
                        outputText += "\nCopied to Download/EviD/$cleanedFileName"
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

            val selectSecondInputFile = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                uri?.let {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            it,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        secondInputUri = it
                        outputText = "Selected second input: $it"
                    } catch (e: Exception) {
                        outputText = "Failed to persist second input URI: ${e.message}"
                    }
                }
            }

            Button(
                onClick = {
                    selectSecondInputFile.launch(arrayOf("video/*"))
                },
                enabled = !isProcessing
            ) {
                Text("Select Second Input Video")
            }

            // Updated Clip Video button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        val startTime = clipRange.start
                        val endTime = clipRange.endInclusive
                        if (inputUri == null) {
                            outputText = "Please select an input video"
                            return@Button
                        }
                        if (startTime >= endTime || (videoDuration > 0 && endTime > videoDuration) || startTime < 0) {
                            outputText = "Invalid clip range: Start must be less than end and within duration (${if (videoDuration > 0) videoDuration else "unknown"} s)"
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
                                val tempProbeFile = File(context.getExternalFilesDir(null), "temp_probe_clip.mp4")
                                try {
                                    withTimeout(30000) {
                                        context.contentResolver.openInputStream(inputUri!!)?.use { inputStream ->
                                            tempProbeFile.outputStream().use { outputStream ->
                                                inputStream.copyTo(outputStream)
                                            }
                                        } ?: throw Exception("Failed to open input stream")
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        outputText = "Failed to copy input for validation: ${e.message}"
                                        isProcessing = false
                                    }
                                    return@launch
                                }
                                val probeSession = FFprobeKit.execute("-i ${tempProbeFile.absolutePath} -show_streams -show_format -v quiet -of json")
                                tempProbeFile.delete()
                                if (!ReturnCode.isSuccess(probeSession.returnCode)) {
                                    withContext(Dispatchers.Main) {
                                        outputText = "Invalid input file: ${probeSession.failStackTrace ?: "Unknown error"}"
                                        isProcessing = false
                                    }
                                    return@launch
                                }

                                val tempFile = File(context.getExternalFilesDir(null), "temp_clip.mp4")
                                try {
                                    withTimeout(30000) {
                                        context.contentResolver.openInputStream(inputUri!!)?.use { inputStream ->
                                            tempFile.outputStream().use { outputStream ->
                                                val buffer = ByteArray(8192)
                                                var bytesRead: Int
                                                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                                    outputStream.write(buffer, 0, bytesRead)
                                                }
                                                outputStream.flush()
                                            }
                                        } ?: throw Exception("Failed to open input stream")
                                    }
                                } catch (e: TimeoutCancellationException) {
                                    withContext(Dispatchers.Main) {
                                        outputText = "Timeout copying input file: ${e.message}"
                                        isProcessing = false
                                    }
                                    return@launch
                                }

                                val outputFile = File(context.getExternalFilesDir(null), "clipped.mp4")
                                val outputFilename = prepareFilename(filename, "clipped.mp4", inputUri!!, isAudio = false)
                                val duration = endTime - startTime
                                val progressPipe = File(context.getExternalFilesDir(null), "progress.txt")
                                val command = "-y -i ${tempFile.absolutePath} -ss $startTime -t $duration -c:v libx264 -preset fast -crf 23 -c:a aac -progress ${progressPipe.absolutePath} -stats_period 0.1 ${outputFile.absolutePath}"

                                withContext(Dispatchers.Main) {
                                    ffmpegLog = ""
                                    progress = 0f
                                }
                                FFmpegKitConfig.enableStatisticsCallback { statistics ->
                                    Log.d("FFmpeg", "Statistics: time=${statistics.time}")
                                    coroutineScope.launch(Dispatchers.Main) {
                                        val duration = videoDuration.takeIf { it > 0 } ?: duration // Use clip duration
                                        val timeSeconds = statistics.time / 1000.0
                                        val progressPercent = (timeSeconds / duration).coerceIn(0.0, 1.0).toFloat()
                                        progress = progressPercent
                                    }
                                }

                                val progressJob = coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        while (isActive && !progressPipe.exists()) delay(100)
                                        progressPipe.bufferedReader().use { reader ->
                                            var durationMs = (duration * 1000).toLong()
                                            while (isActive) {
                                                val line = reader.readLine() ?: continue
                                                if ("out_time_ms=" in line) {
                                                    val timeMs = line.substringAfter("out_time_ms=").toLongOrNull() ?: continue
                                                    val progressPercent = (timeMs / durationMs.toDouble()).coerceIn(0.0, 1.0).toFloat()
                                                    withContext(Dispatchers.Main) {
                                                        progress = progressPercent
                                                    }
                                                }
                                                if ("progress=end" in line) break
                                                delay(100)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("FFmpeg", "Progress reading error: ${e.message}")
                                    }
                                }

                                val session = FFmpegKit.execute(command)
                                progressJob.cancel()

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

                                tempFile.delete()
                                progressPipe.delete()
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    progress = null
                                    isProcessing = false
                                    outputText = "Clip error: ${e.message}\nLogs:\n$ffmpegLog"
                                }
                            }
                        }
                    },
                    enabled = !isProcessing,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clip Video")
                }

                Column(
                    modifier = Modifier
                        .weight(2f)
                        .padding(start = 8.dp)
                ) {
                    RangeSlider(
                        value = clipRange,
                        onValueChange = { range ->
                            clipRange = range.start.coerceIn(0f, videoDuration) .. range.endInclusive.coerceIn(0f, videoDuration)
                        },
                        valueRange = 0f..videoDuration.coerceAtLeast(1f),
                        enabled = !isProcessing
                    )
                    Text(
                        text = "Clip: ${String.format("%.1f", clipRange.start)}s to ${String.format("%.1f", clipRange.endInclusive)}s",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            // Updated Scale Video button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (inputUri == null) {
                            outputText = "Please select an input video"
                            return@Button
                        }
                        val width = scaleWidth.toIntOrNull()
                        val height = scaleHeight.toIntOrNull()
                        if (width == null || height == null || width <= 0 || height <= 0 || width > 8192 || height > 8192) {
                            outputText = "Invalid resolution: Width and height must be positive integers (max 8192)"
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
                                val tempProbeFile = File(context.getExternalFilesDir(null), "temp_probe_scale.mp4")
                                try {
                                    withTimeout(30000) {
                                        context.contentResolver.openInputStream(inputUri!!)?.use { inputStream ->
                                            tempProbeFile.outputStream().use { outputStream ->
                                                inputStream.copyTo(outputStream)
                                            }
                                        } ?: throw Exception("Failed to open input stream")
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        outputText = "Failed to copy input for validation: ${e.message}"
                                        isProcessing = false
                                    }
                                    return@launch
                                }
                                val probeSession = FFprobeKit.execute("-i ${tempProbeFile.absolutePath} -show_streams -show_format -v quiet -of json")
                                tempProbeFile.delete()
                                if (!ReturnCode.isSuccess(probeSession.returnCode)) {
                                    withContext(Dispatchers.Main) {
                                        outputText = "Invalid input file: ${probeSession.failStackTrace ?: "Unknown error"}"
                                        isProcessing = false
                                    }
                                    return@launch
                                }

                                val tempFile = File(context.getExternalFilesDir(null), "temp_scale.mp4")
                                try {
                                    withTimeout(30000) {
                                        context.contentResolver.openInputStream(inputUri!!)?.use { inputStream ->
                                            tempFile.outputStream().use { outputStream ->
                                                val buffer = ByteArray(8192)
                                                var bytesRead: Int
                                                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                                    outputStream.write(buffer, 0, bytesRead)
                                                }
                                                outputStream.flush()
                                            }
                                        } ?: throw Exception("Failed to open input stream")
                                    }
                                } catch (e: TimeoutCancellationException) {
                                    withContext(Dispatchers.Main) {
                                        outputText = "Timeout copying input file: ${e.message}"
                                        isProcessing = false
                                    }
                                    return@launch
                                }

                                val outputFile = File(context.getExternalFilesDir(null), "scaled.mp4")
                                val outputFilename = prepareFilename(filename, "scaled.mp4", inputUri!!, isAudio = false)
                                val progressPipe = File(context.getExternalFilesDir(null), "progress.txt")
                                val command = "-y -i ${tempFile.absolutePath} -vf scale=$width:$height -c:v libx264 -preset fast -crf 23 -c:a aac -progress ${progressPipe.absolutePath} -stats_period 0.1 ${outputFile.absolutePath}"

                                withContext(Dispatchers.Main) {
                                    ffmpegLog = ""
                                    progress = 0f
                                }
                                FFmpegKitConfig.enableStatisticsCallback { statistics ->
                                    Log.d("FFmpeg", "Statistics: time=${statistics.time}")
                                    coroutineScope.launch(Dispatchers.Main) {
                                        val duration = videoDuration.takeIf { it > 0 } ?: 10f // Fallback
                                        val timeSeconds = statistics.time / 1000.0
                                        val progressPercent = (timeSeconds / duration).coerceIn(0.0, 1.0).toFloat()
                                        progress = progressPercent
                                    }
                                }

                                // Fallback progress reading
                                val progressJob = coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        while (isActive && !progressPipe.exists()) delay(100)
                                        progressPipe.bufferedReader().use { reader ->
                                            var durationMs = (videoDuration * 1000).toLong().takeIf { videoDuration > 0 } ?: 10000
                                            while (isActive) {
                                                val line = reader.readLine() ?: continue
                                                if ("out_time_ms=" in line) {
                                                    val timeMs = line.substringAfter("out_time_ms=").toLongOrNull() ?: continue
                                                    val progressPercent = (timeMs / durationMs.toDouble()).coerceIn(0.0, 1.0).toFloat()
                                                    withContext(Dispatchers.Main) {
                                                        progress = progressPercent
                                                    }
                                                }
                                                if ("progress=end" in line) break
                                                delay(100)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("FFmpeg", "Progress reading error: ${e.message}")
                                    }
                                }

                                val session = FFmpegKit.execute(command)
                                progressJob.cancel()

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

                                tempFile.delete()
                                progressPipe.delete()
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    progress = null
                                    isProcessing = false
                                    outputText = "Scale error: ${e.message}\nLogs:\n$ffmpegLog"
                                }
                            }
                        }
                    },
                    enabled = !isProcessing,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Scale Video")
                }

                Column(
                    modifier = Modifier
                        .weight(2f)
                        .padding(start = 8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = scaleWidth,
                            onValueChange = { scaleWidth = it.filter { c -> c.isDigit() } },
                            label = { Text("Width") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            enabled = !isProcessing
                        )
                        OutlinedTextField(
                            value = scaleHeight,
                            onValueChange = { scaleHeight = it.filter { c -> c.isDigit() } },
                            label = { Text("Height") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            enabled = !isProcessing
                        )
                    }
                    Text(
                        text = "Original: ${if (originalWidth > 0) originalWidth else "Unknown"}x${if (originalHeight > 0) originalHeight else "Unknown"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                var customCommand by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = customCommand,
                    onValueChange = { customCommand = it },
                    label = { Text("Custom FFmpeg Command") },
                    modifier = Modifier.weight(2f),
                    enabled = !isProcessing
                )
                Button(
                    onClick = {
                        if (inputUri == null) {
                            outputText = "Please select an input file"
                            return@Button
                        }
                        if (customCommand.isBlank()) {
                            outputText = "Please enter a valid FFmpeg command"
                            return@Button
                        }
                        // Extract output extension from command
                        val outputMatch = Regex("output\\.([a-zA-Z0-9]+)").find(customCommand)
                        val outputExtension = outputMatch?.groupValues?.get(1) ?: "mp4" // Fallback to .mp4
                        if (outputExtension.isEmpty()) {
                            outputText = "Command must include output.<extension> (e.g., output.mp3)"
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
                                val tempProbeFile = File(context.getExternalFilesDir(null), "temp_probe_custom.mp4")
                                try {
                                    withTimeout(30000) {
                                        context.contentResolver.openInputStream(inputUri!!)?.use { inputStream ->
                                            tempProbeFile.outputStream().use { outputStream ->
                                                inputStream.copyTo(outputStream)
                                            }
                                        } ?: throw Exception("Failed to open input stream")
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        outputText = "Failed to copy input for validation: ${e.message}"
                                        isProcessing = false
                                    }
                                    return@launch
                                }
                                val probeSession = FFprobeKit.execute("-i ${tempProbeFile.absolutePath} -show_streams -show_format -v quiet -of json")
                                tempProbeFile.delete()
                                if (!ReturnCode.isSuccess(probeSession.returnCode)) {
                                    withContext(Dispatchers.Main) {
                                        outputText = "Invalid input file: ${probeSession.failStackTrace ?: "Unknown error"}"
                                        isProcessing = false
                                    }
                                    return@launch
                                }

                                val tempFile = File(context.getExternalFilesDir(null), "temp_custom.mp4")
                                try {
                                    withTimeout(30000) {
                                        context.contentResolver.openInputStream(inputUri!!)?.use { inputStream ->
                                            tempFile.outputStream().use { outputStream ->
                                                val buffer = ByteArray(8192)
                                                var bytesRead: Int
                                                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                                    outputStream.write(buffer, 0, bytesRead)
                                                }
                                                outputStream.flush()
                                            }
                                        } ?: throw Exception("Failed to open input stream")
                                    }
                                } catch (e: TimeoutCancellationException) {
                                    withContext(Dispatchers.Main) {
                                        outputText = "Timeout copying input file: ${e.message}"
                                        isProcessing = false
                                    }
                                    return@launch
                                }

                                val outputFile = File(context.getExternalFilesDir(null), "custom_output.$outputExtension")
                                val outputFilename = prepareFilename(filename, "custom_output.$outputExtension", inputUri!!, isAudio = false) // isAudio irrelevant
                                val command = "-y " +customCommand.replace("input", tempFile.absolutePath)
                                    .replace("output.$outputExtension", outputFile.absolutePath)
                                val progressPipe = File(context.getExternalFilesDir(null), "progress.txt")

                                withContext(Dispatchers.Main) {
                                    ffmpegLog = ""
                                    progress = 0f
                                }
                                FFmpegKitConfig.enableStatisticsCallback { statistics ->
                                    Log.d("FFmpeg", "Statistics: time=${statistics.time}")
                                    coroutineScope.launch(Dispatchers.Main) {
                                        val duration = videoDuration.takeIf { it > 0 } ?: 10f
                                        val timeSeconds = statistics.time / 1000.0
                                        val progressPercent = (timeSeconds / duration).coerceIn(0.0, 1.0).toFloat()
                                        progress = progressPercent
                                    }
                                }

                                val progressJob = coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        while (isActive && !progressPipe.exists()) delay(100)
                                        progressPipe.bufferedReader().use { reader ->
                                            var durationMs = (videoDuration * 1000).toLong().takeIf { videoDuration > 0 } ?: 10000
                                            while (isActive) {
                                                val line = reader.readLine() ?: continue
                                                if ("out_time_ms=" in line) {
                                                    val timeMs = line.substringAfter("out_time_ms=").toLongOrNull() ?: continue
                                                    val progressPercent = (timeMs / durationMs.toDouble()).coerceIn(0.0, 1.0).toFloat()
                                                    withContext(Dispatchers.Main) {
                                                        progress = progressPercent
                                                    }
                                                }
                                                if ("progress=end" in line) break
                                                delay(100)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("FFmpeg", "Progress reading error: ${e.message}")
                                    }
                                }

                                val session = FFmpegKit.execute("$command -progress ${progressPipe.absolutePath} -stats_period 0.1")
                                progressJob.cancel()

                                withContext(Dispatchers.Main) {
                                    progress = null
                                    isProcessing = false
                                    outputText = if (ReturnCode.isSuccess(session.returnCode)) {
                                        "Custom command completed: ${outputFile.absolutePath}\n${session.output}"
                                    } else {
                                        "Custom command failed: ${session.failStackTrace ?: "No stack trace"}\nLogs:\n$ffmpegLog"
                                    }
                                }
                                if (ReturnCode.isSuccess(session.returnCode)) {
                                    copyFileToDownloads(outputFile, outputFilename, isAudio = false) // isAudio irrelevant
                                }

                                tempFile.delete()
                                progressPipe.delete()
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    progress = null
                                    isProcessing = false
                                    outputText = "Custom command error: ${e.message}\nLogs:\n$ffmpegLog"
                                }
                            }
                        }
                    },
                    enabled = !isProcessing,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Run Command")
                }
            }
            // Updated Join Videos button (minimal changes)
            Button(
                onClick = {
                    val firstUri = inputUri
                    val secondUri = secondInputUri
                    if (firstUri == null || secondUri == null) {
                        outputText = "Please select both input videos"
                        return@Button
                    }
                    isProcessing = true
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val inputSaf = FFmpegKitConfig.getSafParameterForRead(context, firstUri)
                            val secondInputSaf = FFmpegKitConfig.getSafParameterForRead(context, secondUri)
                            if (inputSaf.isEmpty() || secondInputSaf.isEmpty()) {
                                withContext(Dispatchers.Main) {
                                    outputText = "Failed to get SAF parameter for inputs"
                                    isProcessing = false
                                }
                                return@launch
                            }
                            val tempProbeFile1 = File(context.getExternalFilesDir(null), "temp_probe_join1.mp4")
                            val tempProbeFile2 = File(context.getExternalFilesDir(null), "temp_probe_join2.mp4")
                            var totalDuration = 0f
                            try {
                                withTimeout(30000) {
                                    context.contentResolver.openInputStream(firstUri)?.use { inputStream ->
                                        tempProbeFile1.outputStream().use { outputStream ->
                                            inputStream.copyTo(outputStream)
                                        }
                                    } ?: throw Exception("Failed to open first input stream")
                                    context.contentResolver.openInputStream(secondUri)?.use { inputStream ->
                                        tempProbeFile2.outputStream().use { outputStream ->
                                            inputStream.copyTo(outputStream)
                                        }
                                    } ?: throw Exception("Failed to open second input stream")
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    outputText = "Failed to copy inputs for validation: ${e.message}"
                                    isProcessing = false
                                }
                                return@launch
                            }
                            val probeSession1 = FFprobeKit.execute("-i ${tempProbeFile1.absolutePath} -show_streams -show_format -v quiet -of json")
                            val probeSession2 = FFprobeKit.execute("-i ${tempProbeFile2.absolutePath} -show_streams -show_format -v quiet -of json")
                            if (ReturnCode.isSuccess(probeSession1.returnCode) && ReturnCode.isSuccess(probeSession2.returnCode)) {
                                val json1 = JSONObject(probeSession1.output)
                                val json2 = JSONObject(probeSession2.output)
                                totalDuration += json1.getJSONObject("format").getString("duration").toFloatOrNull() ?: 0f
                                totalDuration += json2.getJSONObject("format").getString("duration").toFloatOrNull() ?: 0f
                            }
                            tempProbeFile1.delete()
                            tempProbeFile2.delete()
                            if (!ReturnCode.isSuccess(probeSession1.returnCode) || !ReturnCode.isSuccess(probeSession2.returnCode)) {
                                withContext(Dispatchers.Main) {
                                    outputText = "Invalid input files:\nFirst: ${probeSession1.failStackTrace ?: "Unknown error"}\nSecond: ${probeSession2.failStackTrace ?: "Unknown error"}"
                                    isProcessing = false
                                }
                                return@launch
                            }
                            withContext(Dispatchers.Main) {
                                outputText = "Input 1: ${probeSession1.output}\nInput 2: ${probeSession2.output}"
                            }

                            val tempFile1 = File(context.getExternalFilesDir(null), "temp1.mp4")
                            val tempFile2 = File(context.getExternalFilesDir(null), "temp2.mp4")
                            try {
                                withTimeout(30000) {
                                    context.contentResolver.openInputStream(firstUri)?.use { inputStream ->
                                        tempFile1.outputStream().use { outputStream ->
                                            val buffer = ByteArray(8192)
                                            var bytesRead: Int
                                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                                outputStream.write(buffer, 0, bytesRead)
                                            }
                                            outputStream.flush()
                                        }
                                    } ?: throw Exception("Failed to open first input stream")
                                    context.contentResolver.openInputStream(secondUri)?.use { inputStream ->
                                        tempFile2.outputStream().use { outputStream ->
                                            val buffer = ByteArray(8192)
                                            var bytesRead: Int
                                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                                outputStream.write(buffer, 0, bytesRead)
                                            }
                                            outputStream.flush()
                                        }
                                    } ?: throw Exception("Failed to open second input stream")
                                }
                            } catch (e: TimeoutCancellationException) {
                                withContext(Dispatchers.Main) {
                                    outputText = "Timeout copying input files: ${e.message}"
                                    isProcessing = false
                                }
                                return@launch
                            }

                            val filesTxt = File(context.getExternalFilesDir(null), "files.txt")
                            val fileContent = "file '${tempFile1.absolutePath}'\nfile '${tempFile2.absolutePath}'"
                            filesTxt.writeText(fileContent)

                            val outputFile = File(context.getExternalFilesDir(null), "joined.mp4")
                            val outputFilename = prepareFilename(filename, "joined.mp4", firstUri, isAudio = false)
                            val progressPipe = File(context.getExternalFilesDir(null), "progress.txt")
                            val command = "-y -f concat -safe 0 -i ${filesTxt.absolutePath} -c:v libx264 -preset fast -crf 23 -c:a aac -progress ${progressPipe.absolutePath} -stats_period 0.1 ${outputFile.absolutePath}"

                            withContext(Dispatchers.Main) {
                                ffmpegLog = ""
                                progress = 0f
                            }
                            FFmpegKitConfig.enableStatisticsCallback { statistics ->
                                Log.d("FFmpeg", "Statistics: time=${statistics.time}")
                                coroutineScope.launch(Dispatchers.Main) {
                                    val duration = totalDuration.takeIf { it > 0 } ?: 20f // Fallback
                                    val timeSeconds = statistics.time / 1000.0
                                    val progressPercent = (timeSeconds / duration).coerceIn(0.0, 1.0).toFloat()
                                    progress = progressPercent
                                }
                            }

                            val progressJob = coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    while (isActive && !progressPipe.exists()) delay(100)
                                    progressPipe.bufferedReader().use { reader ->
                                        var durationMs = (totalDuration * 1000).toLong().takeIf { totalDuration > 0 } ?: 20000
                                        while (isActive) {
                                            val line = reader.readLine() ?: continue
                                            if ("out_time_ms=" in line) {
                                                val timeMs = line.substringAfter("out_time_ms=").toLongOrNull() ?: continue
                                                val progressPercent = (timeMs / durationMs.toDouble()).coerceIn(0.0, 1.0).toFloat()
                                                withContext(Dispatchers.Main) {
                                                    progress = progressPercent
                                                }
                                            }
                                            if ("progress=end" in line) break
                                            delay(100)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("FFmpeg", "Progress reading error: ${e.message}")
                                }
                            }

                            val session = FFmpegKit.execute(command)
                            progressJob.cancel()

                            withContext(Dispatchers.Main) {
                                progress = null
                                isProcessing = false
                                outputText = if (ReturnCode.isSuccess(session.returnCode)) {
                                    "Joined video saved to ${outputFile.absolutePath}\n${session.output}"
                                } else {
                                    "Join failed: ${session.failStackTrace ?: "No stack trace"}\nLogs:\n$ffmpegLog"
                                }
                            }
                            if (ReturnCode.isSuccess(session.returnCode)) {
                                copyFileToDownloads(outputFile, outputFilename, isAudio = false)
                            }

                            tempFile1.delete()
                            tempFile2.delete()
                            filesTxt.delete()
                            progressPipe.delete()
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                progress = null
                                isProcessing = false
                                outputText = "Join error: ${e.message}\nLogs:\n$ffmpegLog"
                            }
                        }
                    }
                },
                enabled = !isProcessing
            ) {
                Text("Join Videos")
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