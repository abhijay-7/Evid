package com.example.evid.extractor

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.example.evid.data.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class FrameScaler(private val context: Context) {

    suspend fun scaleFrames(
        inputUri: Uri,
        metadata: VideoMetadata,
        config: FrameExtractionConfig,
        onProgress: (FrameExtractionResult.Progress) -> Unit
    ): FrameExtractionResult = withContext(Dispatchers.IO) {
        try {
            // Validate storage
            val estimatedFrames = if (config.extractAllFrames) {
                (metadata.duration / 1000 * metadata.frameRate).toInt()
            } else {
                (metadata.duration / config.frameInterval).toInt()
            }
            if (!FrameExtractionUtils.validateStorageSpace(estimatedFrames)) {
                return@withContext FrameExtractionResult.InsufficientStorage
            }

            // Create temporary input file
            val tempFile = File(context.getExternalFilesDir(null), "temp_scale_input_${UUID.randomUUID()}.mp4")
            try {
                context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
                    tempFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                } ?: return@withContext FrameExtractionResult.VideoNotFound
            } catch (e: Exception) {
                tempFile.delete()
                return@withContext FrameExtractionResult.Error(e, "Failed to read input file")
            }

            // Create output directory
            val timestamp = System.currentTimeMillis()
            val qualityDir = when (config.qualityLevel) {
                QualityLevel.Thumbnail -> "thumbnail"
                QualityLevel.Preview -> "preview"
                QualityLevel.Full -> "full"
            }
            val outputDir = File(
                context.getExternalFilesDir(null),
                "frames/$timestamp/$qualityDir"
            ).apply { mkdirs() }

            // Calculate scaled dimensions
            val targetHeight = config.getMaxDimensionForLevel(metadata.width, metadata.height)
            val aspectRatio = metadata.aspectRatio
            val scaledWidth = (targetHeight * aspectRatio).toInt()
            val jpegQuality = config.getJpegQualityForLevel()

            // Extract frames
            val totalFrames = estimatedFrames
            val extractedFrames = mutableListOf<File>()
            val batchSize = config.batchSize
            var currentFrame = 0

            for (batchStart in 0 until totalFrames step batchSize) {
                val batchEnd = minOf(batchStart + batchSize, totalFrames)
                val batchFrames = mutableListOf<File>()
                val commands = (batchStart until batchEnd).map { index ->
                    val timestamp = if (config.extractAllFrames) {
                        (index / metadata.frameRate).toDouble()
                    } else {
                        (index * config.frameInterval / 1000.0).toDouble()
                    }
                    val outputFile = File(outputDir, "frame_%04d.jpg".format(index))
                    async {
                        val command = "-y -i ${tempFile.absolutePath} -vf scale=$scaledWidth:$targetHeight -q:v $jpegQuality -ss $timestamp -vframes 1 ${outputFile.absolutePath}"
                        val session = FFmpegKit.execute(command)
                        if (ReturnCode.isSuccess(session.returnCode)) {
                            batchFrames.add(outputFile)
                            onProgress(FrameExtractionResult.Progress(index + 1, totalFrames, (index + 1).toFloat() / totalFrames, timestamp.toLong()))
                        } else {
                            return@async FrameExtractionResult.Error(
                                Exception(session.failStackTrace ?: "Unknown error"),
                                "Failed to extract frame $index"
                            )
                        }
                        null
                    }
                }

                val results = commands.awaitAll()
                results.forEach { result ->
                    if (result is FrameExtractionResult.Error) {
                        tempFile.delete()
                        if (config.cleanupOnError) outputDir.deleteRecursively()
                        return@withContext result
                    }
                }
                extractedFrames.addAll(batchFrames)
                currentFrame = batchEnd
            }

            tempFile.delete()
            FrameExtractionResult.Success(outputDir, extractedFrames.size, extractedFrames)
        } catch (e: Exception) {
            FrameExtractionResult.Error(e, "Frame scaling failed: ${e.message}")
        }
    }
}