package com.example.evid.extractor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.example.evid.data.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class FrameExtractionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_INPUT_URI = "input_uri"
        const val KEY_QUALITY_LEVEL = "quality_level"
        const val KEY_METADATA = "metadata"
        const val NOTIFICATION_CHANNEL_ID = "frame_extraction_channel"
        const val NOTIFICATION_ID = 1
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val inputUri = inputData.getString(KEY_INPUT_URI)?.let { Uri.parse(it) }
                ?: return@withContext Result.failure()
            val qualityLevelString = inputData.getString(KEY_QUALITY_LEVEL)
                ?: return@withContext Result.failure()
            val qualityLevel = QualityLevel.valueOf(qualityLevelString)
            val metadataJson = inputData.getString(KEY_METADATA)
                ?: return@withContext Result.failure()
            val metadata = VideoMetadata.createEmpty().copy(
                filePath = inputData.getString("filePath") ?: "",
                fileName = inputData.getString("fileName") ?: "",
                duration = inputData.getLong("duration", 0L),
                width = inputData.getInt("width", 0),
                height = inputData.getInt("height", 0),
                frameRate = inputData.getFloat("frameRate", 30f),
                bitRate = inputData.getInt("bitRate", 0),
                codec = inputData.getString("codec") ?: "",
                mimeType = inputData.getString("mimeType") ?: "",
                fileSize = inputData.getLong("fileSize", 0L),
                hash = inputData.getString("hash") ?: "",
                creationTime = inputData.getLong("creationTime", 0L),
                rotation = inputData.getInt("rotation", 0)
            )

            // Create notification channel
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Frame Extraction",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)

            // Setup notification
            val notificationBuilder = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_save)
                .setContentTitle("Frame Extraction")
                .setContentText("Processing frames...")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)

            // Validate storage
            val estimatedFrames = (metadata.duration / 1000 * metadata.frameRate).toInt()
            if (!FrameExtractionUtils.validateStorageSpace(estimatedFrames)) {
                notificationManager.notify(NOTIFICATION_ID, notificationBuilder
                    .setContentText("Insufficient storage")
                    .setOngoing(false)
                    .build())
                return@withContext Result.failure()
            }

            // Create temporary input file
            val tempFile = File(applicationContext.getExternalFilesDir(null), "temp_worker_input_${UUID.randomUUID()}.mp4")
            try {
                applicationContext.contentResolver.openInputStream(inputUri)?.use { inputStream ->
                    tempFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                } ?: return@withContext Result.failure()
            } catch (e: Exception) {
                tempFile.delete()
                notificationManager.notify(NOTIFICATION_ID, notificationBuilder
                    .setContentText("Failed to read input file")
                    .setOngoing(false)
                    .build())
                return@withContext Result.failure()
            }

            // Create output directory
            val timestamp = System.currentTimeMillis()
            val qualityDir = when (qualityLevel) {
                QualityLevel.Thumbnail -> "thumbnail"
                QualityLevel.Preview -> "preview"
                QualityLevel.Full -> "full"
            }
            val outputDir = File(
                applicationContext.getExternalFilesDir(null),
                "frames/$timestamp/$qualityDir"
            ).apply { mkdirs() }

            // Calculate scaled dimensions
            val config = FrameExtractionConfig.createForQuality(qualityLevel)
            val targetHeight = config.getMaxDimensionForLevel(metadata.width, metadata.height)
            val aspectRatio = metadata.aspectRatio
            val scaledWidth = (targetHeight * aspectRatio).toInt()
            val jpegQuality = config.getJpegQualityForLevel()

            // Extract frames
            val totalFrames = estimatedFrames
            val extractedFrames = mutableListOf<File>()
            for (index in 0 until totalFrames) {
                if (isStopped) {
                    tempFile.delete()
                    outputDir.deleteRecursively()
                    notificationManager.notify(NOTIFICATION_ID, notificationBuilder
                        .setContentText("Extraction cancelled")
                        .setOngoing(false)
                        .build())
                    return@withContext Result.failure()
                }

                val timestamp = (index / metadata.frameRate).toDouble()
                val outputFile = File(outputDir, "frame_%04d.jpg".format(index))
                val command = "-y -i ${tempFile.absolutePath} -vf scale=$scaledWidth:$targetHeight -q:v $jpegQuality -ss $timestamp -vframes 1 ${outputFile.absolutePath}"
                val session = FFmpegKit.execute(command)

                if (!ReturnCode.isSuccess(session.returnCode)) {
                    tempFile.delete()
                    outputDir.deleteRecursively()
                    notificationManager.notify(NOTIFICATION_ID, notificationBuilder
                        .setContentText("Failed to extract frame $index")
                        .setOngoing(false)
                        .build())
                    return@withContext Result.retry()
                }

                extractedFrames.add(outputFile)
                val progress = ((index + 1).toFloat() / totalFrames * 100).toInt()
                notificationManager.notify(NOTIFICATION_ID, notificationBuilder
                    .setProgress(100, progress, false)
                    .setContentText("Processing frame ${index + 1}/$totalFrames")
                    .build())
                setProgress(workDataOf("progress" to progress))
            }

            tempFile.delete()
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder
                .setContentText("Extraction completed")
                .setOngoing(false)
                .build())
            Result.success(workDataOf("output_dir" to outputDir.absolutePath, "total_frames" to extractedFrames.size))
        } catch (e: Exception) {
            Result.retry()
        }
    }
}