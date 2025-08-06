package com.example.evid.extractor

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class FrameExtractor(
    private val context: Context,
    private val config: FrameExtractionConfig = FrameExtractionConfig.createDefault()
) {
    private var extractionJob: Job? = null
    private val appName = "EviD"

    companion object {
        private const val FRAMES_SUBDIRECTORY = "frames"
        private const val TEMP_SUBDIRECTORY = "temp"
        private const val MIN_FREE_SPACE_MB = 100L
        private const val BYTES_PER_MB = 1024 * 1024L
        private const val PROBE_TIMEOUT_MS = 30_000L
        private const val COPY_TIMEOUT_MS = 30_000L
        private const val PROGRESS_POLL_INTERVAL_MS = 200L
    }

    fun extractFrames(
        uri: Uri,
        onProgress: ((FrameExtractionResult.Progress) -> Unit)? = null,
        onResult: (FrameExtractionResult) -> Unit
    ) {
        extractionJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!config.validate()) {
                    withContext(Dispatchers.Main) {
                        onResult(FrameExtractionResult.Error(IllegalArgumentException("Invalid extraction configuration"), "Configuration validation failed"))
                    }
                    return@launch
                }

                // Verify URI accessibility
                if (!isUriAccessible(uri)) {
                    withContext(Dispatchers.Main) {
                        onResult(FrameExtractionResult.VideoNotFound)
                    }
                    return@launch
                }

                if (!hasEnoughStorage()) {
                    withContext(Dispatchers.Main) {
                        onResult(FrameExtractionResult.InsufficientStorage)
                    }
                    return@launch
                }

                // Get video duration
                val tempDir = createTempDirectory()
                val tempProbeFile = File(tempDir, "temp_probe.mp4")
                val duration = try {
                    getVideoDuration(uri, tempProbeFile)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        onResult(FrameExtractionResult.Error(e, "Failed to probe video: ${e.message}"))
                    }
                    return@launch
                } finally {
                    tempProbeFile.delete()
                }

                if (duration <= 0) {
                    withContext(Dispatchers.Main) {
                        onResult(FrameExtractionResult.Error(IllegalStateException("Invalid video duration"), "Could not determine video duration"))
                    }
                    return@launch
                }

                // Prepare output directory and FFmpeg command
                val fileName = getFileNameFromUri(uri) ?: "video_${System.currentTimeMillis()}"
                val framesDirectory = createFramesDirectory(fileName)
                val framePrefix = "frame_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}"
                val outputPattern = "${framesDirectory.absolutePath}/${framePrefix}_%06d.jpg"
                val frameRate = if (config.extractAllFrames) "" else "-r ${1000.0 / config.frameInterval}"

                val inputSaf = FFmpegKitConfig.getSafParameterForRead(context, uri)
                if (inputSaf.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        onResult(FrameExtractionResult.Error(IllegalStateException("Invalid SAF parameter"), "Failed to get SAF parameter"))
                    }
                    return@launch
                }

                // Copy input to temp file
                val tempInput = File(tempDir, "temp_input.mp4")
                try {
                    copyUriToFile(uri, tempInput)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        onResult(FrameExtractionResult.Error(e, "Failed to copy input file: ${e.message}"))
                    }
                    return@launch
                }

                // FFmpeg command
                val progressPipe = File(tempDir, "progress.txt")
                val command = "-y -i ${tempInput.absolutePath} $frameRate -vf scale=${config.maxFrameDimension}:-2 -q:v ${mapJpegQuality(config.jpegQuality)} $outputPattern -progress pipe:2"

                // Track progress in a separate coroutine
                val progressJob = launch {
                    monitorProgress(progressPipe, duration, config, onProgress)
                }

                // Execute FFmpeg
                val session = FFmpegKit.execute("$command -progress ${progressPipe.absolutePath} -stats_period 0.1")
                progressJob.cancel()

                // Clean up
                tempInput.delete()
                progressPipe.delete()
                cleanupTempDirectory(tempDir)

                // Handle results
                withContext(Dispatchers.Main) {
                    if (ReturnCode.isSuccess(session.returnCode)) {
                        val extractedFrames = framesDirectory.listFiles { _, name -> name.endsWith(".jpg") }?.toList() ?: emptyList()
                        if (extractedFrames.isEmpty()) {
                            if (config.cleanupOnError) cleanupDirectory(framesDirectory)
                            onResult(FrameExtractionResult.Error(IllegalStateException("No frames extracted"), "No frames were extracted"))
                        } else {
                            onResult(FrameExtractionResult.Success(framesDirectory, extractedFrames.size, extractedFrames))
                        }
                    } else {
                        if (config.cleanupOnError) cleanupDirectory(framesDirectory)
                        onResult(FrameExtractionResult.Error(IllegalStateException("FFmpeg execution failed"), "Frame extraction failed: ${session.failStackTrace ?: "Unknown error"}"))
                    }
                }
            } catch (e: CancellationException) {
                withContext(Dispatchers.Main) {
                    onResult(FrameExtractionResult.Cancelled)
                }
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(FrameExtractionResult.Error(e, "Unexpected error: ${e.message}"))
                }
            }
        }
    }

    private suspend fun getVideoDuration(uri: Uri, tempFile: File): Double = withContext(Dispatchers.IO) {
        withTimeout(PROBE_TIMEOUT_MS) {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                tempFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: throw Exception("Failed to open input stream")
            val probeSession = FFprobeKit.execute("-i ${tempFile.absolutePath} -show_entries format=duration -v quiet -of json")
            if (ReturnCode.isSuccess(probeSession.returnCode)) {
                val json = JSONObject(probeSession.output)
                json.getJSONObject("format").getString("duration").toDoubleOrNull() ?: 0.0
            } else {
                throw IllegalStateException("Failed to probe video: ${probeSession.failStackTrace ?: "Unknown error"}")
            }
        }
    }

    private suspend fun copyUriToFile(uri: Uri, destFile: File) = withContext(Dispatchers.IO) {
        withTimeout(COPY_TIMEOUT_MS) {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                destFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: throw Exception("Failed to open input stream")
        }
    }

    private suspend fun monitorProgress(
        progressPipe: File,
        duration: Double,
        config: FrameExtractionConfig,
        onProgress: ((FrameExtractionResult.Progress) -> Unit)?
    ) = withContext(Dispatchers.IO) {
        try {
            while (isActive && !progressPipe.exists()) delay(100)
            progressPipe.bufferedReader().use { reader ->
                val durationMs = (duration * 1000).toLong()
                val totalFrames = if (config.extractAllFrames) (duration * 30).toInt() else (duration * 1000 / config.frameInterval).toInt()
                while (isActive) {
                    val line = reader.readLine() ?: continue
                    if ("out_time_ms=" in line) {
                        val timeMs = line.substringAfter("out_time_ms=").toLongOrNull() ?: continue
                        val progressPercent = (timeMs / durationMs.toDouble()).coerceIn(0.0, 1.0).toFloat()
                        val currentFrame = (progressPercent * totalFrames).toInt()
                        val progress = FrameExtractionResult.Progress(
                            currentFrame = currentFrame,
                            totalFrames = totalFrames,
                            percentage = progressPercent * 100f,
                            currentTimestamp = (timeMs / 1000).toLong()
                        )
                        withContext(Dispatchers.Main) {
                            onProgress?.invoke(progress)
                        }
                    }
                    if ("progress=end" in line) break
                    delay(PROGRESS_POLL_INTERVAL_MS)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FrameExtractor", "Progress reading error: ${e.message}")
        }
    }

    private fun mapJpegQuality(jpegQuality: Int): Int {
        return ((100 - jpegQuality) * 30 / 100) + 1
    }

    private fun createTempDirectory(): File {
        val tempDir = File(context.getExternalFilesDir(null), TEMP_SUBDIRECTORY)
        if (!tempDir.exists()) tempDir.mkdirs()
        return tempDir
    }

    private fun createFramesDirectory(videoName: String): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val appDirectory = File(downloadsDir, appName)
        val framesDirectory = File(appDirectory, FRAMES_SUBDIRECTORY)
        val videoFramesDir = File(framesDirectory, cleanFileName(videoName))
        if (!videoFramesDir.exists()) {
            check(videoFramesDir.mkdirs()) { "Failed to create frames directory: ${videoFramesDir.absolutePath}" }
        }
        check(videoFramesDir.canWrite()) { "Cannot write to frames directory: ${videoFramesDir.absolutePath}" }
        return videoFramesDir
    }

    private fun cleanFileName(fileName: String): String {
        return fileName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(50)
    }

    private fun hasEnoughStorage(): Boolean = try {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val stat = StatFs(downloadsDir.path)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        availableBytes / BYTES_PER_MB >= MIN_FREE_SPACE_MB
    } catch (e: Exception) {
        false
    }

    private fun getFileNameFromUri(uri: Uri): String? = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        }
    } catch (e: Exception) {
        null
    }

    private fun isUriAccessible(uri: Uri): Boolean = try {
        context.contentResolver.openInputStream(uri)?.close()
        true
    } catch (e: Exception) {
        false
    }

    private fun cleanupDirectory(directory: File) {
        try {
            if (directory.exists()) directory.deleteRecursively()
        } catch (_: Exception) {
            // Ignore cleanup errors
        }
    }

    private fun cleanupTempDirectory(tempDir: File) {
        try {
            if (tempDir.exists()) tempDir.deleteRecursively()
        } catch (_: Exception) {
            // Ignore cleanup errors
        }
    }

    fun cancelExtraction() {
        extractionJob?.cancel()
    }

    fun isExtracting(): Boolean {
        return extractionJob?.isActive == true
    }
}