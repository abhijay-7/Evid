package com.example.evid.extractor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.evid.data.AudioTrackMetadata
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object FrameExtractionUtils {

    fun openFramesDirectory(context: Context, directory: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                directory
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(directory.absolutePath)
                }
                context.startActivity(fallbackIntent)
            }
        } catch (e: Exception) {
            // Handle error
        }
    }

    fun shareFramesDirectory(context: Context, directory: File) {
        try {
            val files = directory.listFiles()?.filter { it.isFile && it.extension.lowercase() == "jpg" }
            if (files.isNullOrEmpty()) {
                return
            }
            val uris = files.map { file ->
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }
            val intent = Intent().apply {
                if (uris.size == 1) {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                } else {
                    action = Intent.ACTION_SEND_MULTIPLE
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                }
                type = "image/jpeg"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_SUBJECT, "Extracted Video Frames")
                putExtra(Intent.EXTRA_TEXT, "Extracted ${files.size} frames from video")
            }
            context.startActivity(Intent.createChooser(intent, "Share Frames"))
        } catch (e: Exception) {
            // Handle error
        }
    }

    fun shareAudioFiles(context: Context, directory: File) {
        try {
            val files = directory.listFiles()?.filter { it.isFile && it.extension.lowercase() in setOf("wav", "mp3") }
            if (files.isNullOrEmpty()) {
                return
            }
            val uris = files.map { file ->
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }
            val intent = Intent().apply {
                if (uris.size == 1) {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                } else {
                    action = Intent.ACTION_SEND_MULTIPLE
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                }
                type = "audio/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_SUBJECT, "Extracted Audio Tracks")
                putExtra(Intent.EXTRA_TEXT, "Extracted ${files.size} audio tracks from video")
            }
            context.startActivity(Intent.createChooser(intent, "Share Audio"))
        } catch (e: Exception) {
            // Handle error
        }
    }

    fun getFramesFolderSize(directory: File): Double {
        if (!directory.exists() || !directory.isDirectory) {
            return 0.0
        }
        val totalBytes = directory.walkTopDown()
            .filter { it.isFile }
            .mapNotNull {
                try {
                    it.length()
                } catch (e: SecurityException) {
                    null
                }
            }
            .sum()
        return totalBytes / (1024.0 * 1024.0) // Convert to MB
    }

    fun cleanupOldExtractions(context: Context, keepLastN: Int = 5): Long {
        try {
            val appDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "EviD")
            val framesDir = File(appDir, "frames")
            val audioDir = File(appDir, "audio")

            var deletedBytes = 0L
            listOf(framesDir, audioDir).forEach { dir ->
                if (!dir.exists()) return@forEach
                val extractionFolders = dir.listFiles { file -> file.isDirectory }?.sortedByDescending { it.lastModified() } ?: return@forEach
                if (extractionFolders.size <= keepLastN) return@forEach
                val foldersToDelete = extractionFolders.drop(keepLastN)
                foldersToDelete.forEach { folder ->
                    val folderSize = folder.walkTopDown()
                        .filter { it.isFile }
                        .mapNotNull { try { it.length() } catch (e: SecurityException) { null } }
                        .sum()
                    if (folder.deleteRecursively()) {
                        deletedBytes += folderSize
                    }
                }
            }
            return deletedBytes
        } catch (e: Exception) {
            return 0L
        }
    }

    fun createExtractionSummary(
        result: FrameExtractionResult.Success,
        config: FrameExtractionConfig,
        processingTimeMs: Long
    ): String {
        val folderSize = getFramesFolderSize(result.framesDirectory)
        val processingTimeSeconds = processingTimeMs / 1000.0
        val framesPerSecond = if (processingTimeSeconds > 0) result.totalFrames / processingTimeSeconds else 0.0
        return buildString {
            appendLine("Frame Extraction Summary")
            appendLine("=" * 30)
            appendLine("Total Frames: ${result.totalFrames}")
            appendLine("Quality Level: ${config.qualityLevel}")
            appendLine("JPEG Quality: ${config.jpegQuality}%")
            appendLine("Max Dimension: ${config.maxFrameDimension}px")
            appendLine("Total Size: ${"%.2f".format(folderSize)} MB")
            appendLine("Processing Time: ${"%.1f".format(processingTimeSeconds)}s")
            appendLine("Speed: ${"%.1f".format(framesPerSecond)} frames/sec")
            appendLine("Location: ${result.framesDirectory.absolutePath}")
            appendLine()
            appendLine("Extraction completed at: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
        }
    }

    fun createAudioExtractionSummary(
        result: FrameExtractionResult.AudioSuccess,
        processingTimeMs: Long
    ): String {
        val folderSize = getFramesFolderSize(result.audioDirectory)
        val processingTimeSeconds = processingTimeMs / 1000.0
        return buildString {
            appendLine("Audio Extraction Summary")
            appendLine("=" * 30)
            appendLine("Total Tracks: ${result.extractedFiles.size}")
            appendLine("Total Size: ${"%.2f".format(folderSize)} MB")
            appendLine("Processing Time: ${"%.1f".format(processingTimeSeconds)}s")
            result.audioMetadata.forEachIndexed { index, metadata ->
                appendLine("Track $index: ${metadata.channelsDescription}, ${metadata.bitrateFormatted}, ${metadata.codec}")
            }
            appendLine("Location: ${result.audioDirectory.absolutePath}")
            appendLine()
            appendLine("Extraction completed at: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
        }
    }

    fun validateStorageSpace(estimatedFiles: Int, avgFileSizeKB: Int = 100): Boolean {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val stat = android.os.StatFs(downloadsDir.path)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            val requiredBytes = estimatedFiles * avgFileSizeKB * 1024L
            val bufferBytes = 100L * 1024 * 1024 // 100MB buffer
            return availableBytes > (requiredBytes + bufferBytes)
        } catch (e: Exception) {
            return false
        }
    }

    fun getPresets(): Map<String, FrameExtractionConfig> {
        return mapOf(
            "High Quality" to FrameExtractionConfig.createHighQuality(),
            "Balanced" to FrameExtractionConfig.createDefault(),
            "Memory Optimized" to FrameExtractionConfig.createMemoryOptimized(),
            "Every 5 Seconds" to FrameExtractionConfig.createSampled(5000L),
            "Every 10 Seconds" to FrameExtractionConfig.createSampled(10000L),
            "Every 30 Seconds" to FrameExtractionConfig.createSampled(30000L)
        )
    }

    private operator fun String.times(n: Int): String {
        return this.repeat(n)
    }
}