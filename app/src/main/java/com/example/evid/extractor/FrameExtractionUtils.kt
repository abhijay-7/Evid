package com.example.evid.extractor

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object FrameExtractionUtils {

    /**
     * Opens the frames directory in the system file manager
     */
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
                // Fallback: open with generic intent
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(directory.absolutePath)
                }
                context.startActivity(fallbackIntent)
            }
        } catch (e: Exception) {
            // Handle error - could show a toast or dialog
        }
    }

    /**
     * Shares the extracted frames directory
     */
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

    /**
     * Gets the total size of extracted frames in MB
     */
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

    /**
     * Cleans up old frame extraction folders to free space
     */
    fun cleanupOldExtractions(context: Context, keepLastN: Int = 5): Long {
        try {
            val appDir = File(
                android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                ), "EviD"
            )
            val framesDir = File(appDir, "frames")

            if (!framesDir.exists()) {
                return 0L
            }

            val extractionFolders = framesDir.listFiles { file ->
                file.isDirectory
            }?.sortedByDescending { it.lastModified() } ?: return 0L

            if (extractionFolders.size <= keepLastN) {
                return 0L
            }

            val foldersToDelete = extractionFolders.drop(keepLastN)
            var deletedBytes = 0L

            foldersToDelete.forEach { folder ->
                val folderSize = folder.walkTopDown()
                    .filter { it.isFile }
                    .mapNotNull {
                        try {
                            it.length()
                        } catch (e: SecurityException) {
                            null
                        }
                    }
                    .sum()

                if (folder.deleteRecursively()) {
                    deletedBytes += folderSize
                }
            }

            return deletedBytes
        } catch (e: Exception) {
            return 0L
        }
    }

    /**
     * Creates a summary of extraction results
     */
    fun createExtractionSummary(
        result: FrameExtractionResult.Success,
        config: FrameExtractionConfig,
        processingTimeMs: Long
    ): String {
        val folderSize = getFramesFolderSize(result.framesDirectory)
        val processingTimeSeconds = processingTimeMs / 1000.0
        val framesPerSecond = if (processingTimeSeconds > 0) {
            result.totalFrames / processingTimeSeconds
        } else 0.0

        return buildString {
            appendLine("Frame Extraction Summary")
            appendLine("=" * 30)
            appendLine("Total Frames: ${result.totalFrames}")
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

    /**
     * Validates if the extraction directory has enough space
     */
    fun validateStorageSpace(estimatedFrames: Int, avgFrameSizeKB: Int = 100): Boolean {
        try {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            val stat = android.os.StatFs(downloadsDir.path)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            val requiredBytes = estimatedFrames * avgFrameSizeKB * 1024L
            val bufferBytes = 100L * 1024 * 1024 // 100MB buffer

            return availableBytes > (requiredBytes + bufferBytes)
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Gets frame extraction presets for common use cases
     */
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