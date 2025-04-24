package com.example.evid.ui

import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.example.evid.ui.*
import com.example.evid.util.getOriginalFilename
import com.example.evid.util.prepareFilename
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FFmpegTestScreen2() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var outputText by remember { mutableStateOf("Ready to run FFmpeg") }
    var filename by remember { mutableStateOf("") }
    var inputUri by remember { mutableStateOf<Uri?>(null) }
    var secondInputUri by remember { mutableStateOf<Uri?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var clipRange by remember { mutableStateOf(0f..10f) }
    var videoDuration by remember { mutableStateOf(10f) }
    var scaleWidth by remember { mutableStateOf("") }
    var scaleHeight by remember { mutableStateOf("") }
    var originalWidth by remember { mutableStateOf(0) }
    var originalHeight by remember { mutableStateOf(0) }
    var progress by remember { mutableStateOf<Float?>(null) }

    val scrollState = rememberScrollState()

    val selectInputFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            inputUri = it
            outputText = "Selected input: $it"

            scope.launch(Dispatchers.IO) {
                val tempFile = File(context.getExternalFilesDir(null), "temp_probe.mp4")
                try {
                    context.contentResolver.openInputStream(it)?.use { inputStream ->
                        tempFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    val probeSession = FFprobeKit.execute(
                        "-i ${tempFile.absolutePath} -show_entries format=duration:stream=width,height -select_streams v:0 -v quiet -of json"
                    )
                    if (ReturnCode.isSuccess(probeSession.returnCode)) {
                        val json = JSONObject(probeSession.output)
                        val format = json.getJSONObject("format")
                        val duration = format.getString("duration").toFloatOrNull() ?: 0f
                        val stream = json.getJSONArray("streams").getJSONObject(0)
                        val width = stream.getInt("width")
                        val height = stream.getInt("height")
                        withContext(Dispatchers.Main) {
                            videoDuration = duration
                            clipRange = 0f..minOf(10f, duration)
                            originalWidth = width
                            originalHeight = height
                            scaleWidth = width.toString()
                            scaleHeight = height.toString()
                            outputText = "Video info: ${width}x${height}, ${duration}s"
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            outputText = "Failed to probe video: ${probeSession.failStackTrace ?: "Unknown error"}"
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        outputText = "Error reading video: ${e.message}"
                    }
                } finally {
                    tempFile.delete()
                }
            }
        }
    }

    val selectSecondInputFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            secondInputUri = it
            outputText = "Selected second input: $it"
        }
    }

    fun copyFileToDownloads(file: File, name: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val cleanedFileName = if (name.count { it == '.' } >= 2) name.substringBeforeLast('.') else name
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, cleanedFileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/EviD")
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    uri?.let {
                        resolver.openOutputStream(it)?.use { outStream ->
                            FileInputStream(file).use { it.copyTo(outStream) }
                        }
                        withContext(Dispatchers.Main) {
                            outputText += "\nCopied to Download/EviD/$cleanedFileName"
                        }
                    }
                } else {
                    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "EviD")
                    if (!dir.exists()) dir.mkdirs()
                    file.copyTo(File(dir, cleanedFileName), overwrite = true)
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
        topBar = { TopAppBar(title = { Text("FFmpeg Test App") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            FilePickerSection(
                filename = filename,
                onFilenameChange = { filename = it },
                onSelectInput = { selectInputFile.launch(arrayOf("video/*")) },
                onSelectSecondInput = { selectSecondInputFile.launch(arrayOf("video/*")) },
                isProcessing = isProcessing
            )

            ClipVideoSection(
                clipRange = clipRange,
                videoDuration = videoDuration,
                isProcessing = isProcessing,
                onClipRangeChange = { clipRange = it },
                onClip = {
                    if (inputUri == null) {
                        outputText = "Please select an input file"
                        return@ClipVideoSection
                    }
                    if (isProcessing) return@ClipVideoSection
                    isProcessing = true
                    scope.launch(Dispatchers.IO) {
                        try {
                            val inputSaf = FFmpegKitConfig.getSafParameterForRead(context, inputUri!!)
                            if (inputSaf.isEmpty()) {
                                withContext(Dispatchers.Main) {
                                    outputText = "Failed to get SAF parameter for input"
                                    isProcessing = false
                                }
                                return@launch
                            }
                            val tempFile = File(context.getExternalFilesDir(null), "temp_clip.mp4")
                            withTimeout(30000) {
                                context.contentResolver.openInputStream(inputUri!!)?.use { inputStream ->
                                    tempFile.outputStream().use { outputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                } ?: throw Exception("Failed to open input stream")
                            }
                            val outputFile = File(context.getExternalFilesDir(null), "clipped.mp4")
                            val outputFilename = prepareFilename(filename, "clipped.mp4", inputUri!!, isAudio = false, context = context)
                            val command = " -y -i ${tempFile.absolutePath} -ss ${clipRange.start} -to ${clipRange.endInclusive} -c:v copy -c:a copy ${outputFile.absolutePath}"
                            val progressPipe = File(context.getExternalFilesDir(null), "progress.txt")

                            withContext(Dispatchers.Main) {
                                progress = 0f
                            }
                            val progressJob = scope.launch(Dispatchers.IO) {
                                try {
                                    while (isActive && !progressPipe.exists()) delay(100)
                                    progressPipe.bufferedReader().use { reader ->
                                        val durationMs = ((clipRange.endInclusive - clipRange.start) * 1000).toLong()
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
                                    "Clip completed: ${outputFile.absolutePath}\n${session.output}"
                                } else {
                                    "Clip failed: ${session.failStackTrace ?: "No stack trace"}"
                                }
                            }
                            if (ReturnCode.isSuccess(session.returnCode)) {
                                copyFileToDownloads(outputFile, outputFilename)
                            }

                            tempFile.delete()
                            progressPipe.delete()
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                progress = null
                                isProcessing = false
                                outputText = "Clip error: ${e.message}"
                            }
                        }
                    }
                }
            )

            ScaleVideoSection(
                scaleWidth = scaleWidth,
                scaleHeight = scaleHeight,
                onScaleWidthChange = { scaleWidth = it },
                onScaleHeightChange = { scaleHeight = it },
                onScale = {
                    if (inputUri == null) {
                        outputText = "Please select an input file"
                        return@ScaleVideoSection
                    }
                    if (scaleWidth.isBlank() || scaleHeight.isBlank()) {
                        outputText = "Please enter width and height"
                        return@ScaleVideoSection
                    }
                    val width = scaleWidth.toIntOrNull() ?: 0
                    val height = scaleHeight.toIntOrNull() ?: 0
                    if (width <= 0 || height <= 0) {
                        outputText = "Invalid width or height"
                        return@ScaleVideoSection
                    }
                    if (isProcessing) return@ScaleVideoSection
                    isProcessing = true
                    scope.launch(Dispatchers.IO) {
                        try {
                            val inputSaf = FFmpegKitConfig.getSafParameterForRead(context, inputUri!!)
                            if (inputSaf.isEmpty()) {
                                withContext(Dispatchers.Main) {
                                    outputText = "Failed to get SAF parameter for input"
                                    isProcessing = false
                                }
                                return@launch
                            }
                            val tempFile = File(context.getExternalFilesDir(null), "temp_scale.mp4")
                            withTimeout(30000) {
                                context.contentResolver.openInputStream(inputUri!!)?.use { inputStream ->
                                    tempFile.outputStream().use { outputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                } ?: throw Exception("Failed to open input stream")
                            }
                            val outputFile = File(context.getExternalFilesDir(null), "scaled.mp4")
                            val outputFilename = prepareFilename(filename, "scaled.mp4", inputUri!!, isAudio = false, context = context)
                            val command = "-y -i ${tempFile.absolutePath} -vf scale=$width:$height -c:v libx264 -c:a copy ${outputFile.absolutePath}"
                            val progressPipe = File(context.getExternalFilesDir(null), "progress.txt")

                            withContext(Dispatchers.Main) {
                                progress = 0f
                            }
                            val progressJob = scope.launch(Dispatchers.IO) {
                                try {
                                    while (isActive && !progressPipe.exists()) delay(100)
                                    progressPipe.bufferedReader().use { reader ->
                                        val durationMs = (videoDuration * 1000).toLong()
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
                                    "Scale completed: ${outputFile.absolutePath}\n${session.output}"
                                } else {
                                    "Scale failed: ${session.failStackTrace ?: "No stack trace"}"
                                }
                            }
                            if (ReturnCode.isSuccess(session.returnCode)) {
                                copyFileToDownloads(outputFile, outputFilename)
                            }

                            tempFile.delete()
                            progressPipe.delete()
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                progress = null
                                isProcessing = false
                                outputText = "Scale error: ${e.message}"
                            }
                        }
                    }
                },
                originalWidth = originalWidth,
                originalHeight = originalHeight,
                isProcessing = isProcessing
            )

            CustomCommandSection(
                isProcessing = isProcessing,
                onRunCommand = { command ->
                    if (inputUri == null) {
                        outputText = "Please select an input file"
                        return@CustomCommandSection
                    }
                    if (command.isBlank()) {
                        outputText = "Please enter a valid FFmpeg command"
                        return@CustomCommandSection
                    }
                    val outputMatch = Regex("output\\.([a-zA-Z0-9]+)").find(command)
                    val outputExtension = outputMatch?.groupValues?.get(1) ?: run {
                        outputText = "Command must include output.<extension> (e.g., output.mp3)"
                        return@CustomCommandSection
                    }
                    if (isProcessing) return@CustomCommandSection
                    isProcessing = true

                    scope.launch(Dispatchers.IO) {
                        try {
                            val inputSaf = FFmpegKitConfig.getSafParameterForRead(context, inputUri!!)
                            if (inputSaf.isEmpty()) {
                                withContext(Dispatchers.Main) {
                                    outputText = "Failed to get SAF parameter for input"
                                    isProcessing = false
                                }
                                return@launch
                            }
                            val tempFile = File(context.getExternalFilesDir(null), "temp_custom.mp4")
                            withTimeout(30000) {
                                context.contentResolver.openInputStream(inputUri!!)?.use { inputStream ->
                                    tempFile.outputStream().use { outputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                } ?: throw Exception("Failed to open input stream")
                            }
                            val outputFile = File(context.getExternalFilesDir(null), "custom_output.$outputExtension")
                            val outputFilename = prepareFilename(filename, "custom_output.$outputExtension", inputUri!!, isAudio = false, context = context)
                            val replacedCommand = command.replace("input", tempFile.absolutePath)
                                .replace("output.$outputExtension", outputFile.absolutePath)
                            val progressPipe = File(context.getExternalFilesDir(null), "progress.txt")

                            withContext(Dispatchers.Main) {
                                progress = 0f
                            }
                            val progressJob = scope.launch(Dispatchers.IO) {
                                try {
                                    while (isActive && !progressPipe.exists()) delay(100)
                                    progressPipe.bufferedReader().use { reader ->
                                        val durationMs = (videoDuration * 1000).toLong().takeIf { videoDuration > 0 } ?: 10000
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

                            val session = FFmpegKit.execute("-y $replacedCommand -progress ${progressPipe.absolutePath} -stats_period 0.1")
                            progressJob.cancel()

                            withContext(Dispatchers.Main) {
                                progress = null
                                isProcessing = false
                                outputText = if (ReturnCode.isSuccess(session.returnCode)) {
                                    "Custom command completed: ${outputFile.absolutePath}\n${session.output}"
                                } else {
                                    "Custom command failed: ${session.failStackTrace ?: "No stack trace"}"
                                }
                            }
                            if (ReturnCode.isSuccess(session.returnCode)) {
                                copyFileToDownloads(outputFile, outputFilename)
                            }

                            tempFile.delete()
                            progressPipe.delete()
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                progress = null
                                isProcessing = false
                                outputText = "Custom command error: ${e.message}"
                            }
                        }
                    }
                }
            )

            JoinVideoSection(
                isProcessing = isProcessing,
                onJoin = {
                    if (inputUri == null || secondInputUri == null) {
                        outputText = "Please select both input files"
                        return@JoinVideoSection
                    }
                    if (isProcessing) return@JoinVideoSection
                    isProcessing = true
                    scope.launch(Dispatchers.IO) {
                        try {
                            val inputSaf = FFmpegKitConfig.getSafParameterForRead(context, inputUri!!)
                            val secondInputSaf = FFmpegKitConfig.getSafParameterForRead(context, secondInputUri!!)
                            if (inputSaf.isEmpty() || secondInputSaf.isEmpty()) {
                                withContext(Dispatchers.Main) {
                                    outputText = "Failed to get SAF parameter for inputs"
                                    isProcessing = false
                                }
                                return@launch
                            }
                            val tempFile1 = File(context.getExternalFilesDir(null), "temp_join1.mp4")
                            val tempFile2 = File(context.getExternalFilesDir(null), "temp_join2.mp4")
                            withTimeout(30000) {
                                context.contentResolver.openInputStream(inputUri!!)?.use { inputStream ->
                                    tempFile1.outputStream().use { outputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                } ?: throw Exception("Failed to open first input stream")
                                context.contentResolver.openInputStream(secondInputUri!!)?.use { inputStream ->
                                    tempFile2.outputStream().use { outputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                } ?: throw Exception("Failed to open second input stream")
                            }
                            val concatFile = File(context.getExternalFilesDir(null), "concat.txt")
                            concatFile.writeText("file '${tempFile1.absolutePath}'\nfile '${tempFile2.absolutePath}'")
                            val outputFile = File(context.getExternalFilesDir(null), "joined.mp4")
                            val outputFilename = prepareFilename(filename, "joined.mp4", inputUri!!, isAudio = false, context = context)
                            val command = "-y -f concat -i ${concatFile.absolutePath} -c:v copy -c:a copy ${outputFile.absolutePath}"
                            val progressPipe = File(context.getExternalFilesDir(null), "progress.txt")

                            withContext(Dispatchers.Main) {
                                progress = 0f
                            }
                            val progressJob = scope.launch(Dispatchers.IO) {
                                try {
                                    while (isActive && !progressPipe.exists()) delay(100)
                                    progressPipe.bufferedReader().use { reader ->
                                        val durationMs = (videoDuration * 1000).toLong().takeIf { videoDuration > 0 } ?: 10000
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
                                    "Join completed: ${outputFile.absolutePath}\n${session.output}"
                                } else {
                                    "Join failed: ${session.failStackTrace ?: "No stack trace"}"
                                }
                            }
                            if (ReturnCode.isSuccess(session.returnCode)) {
                                copyFileToDownloads(outputFile, outputFilename)
                            }

                            tempFile1.delete()
                            tempFile2.delete()
                            concatFile.delete()
                            progressPipe.delete()
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                progress = null
                                isProcessing = false
                                outputText = "Join error: ${e.message}"
                            }
                        }
                    }
                }
            )

            OutputTextSection(outputText = outputText)
        }
    }
}
