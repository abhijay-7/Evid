package com.example.evid.extractor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.example.evid.data.VideoMetadata
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

@RequiresApi(Build.VERSION_CODES.O)
class BackgroundProcessor(
    private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "frame_extraction_channel"
        const val NOTIFICATION_ID = 1
        const val WORK_TAG = "frame_extraction"
        const val KEY_URI = "uri"
        const val KEY_CONFIG_EXTRACT_ALL = "config_extract_all"
        const val KEY_CONFIG_INTERVAL = "config_interval"
        const val KEY_CONFIG_PROGRESS_INTERVAL = "config_progress_interval"
        const val KEY_CONFIG_CLEANUP = "config_cleanup"
        const val KEY_CONFIG_QUALITY_LEVEL = "config_quality_level"
        const val KEY_CONFIG_BATCH_SIZE = "config_batch_size"
        const val KEY_CONFIG_CUSTOM_TIMESTAMPS = "config_custom_timestamps"
        const val KEY_METADATA = "metadata"
        const val KEY_RESULT_TYPE = "result_type"
        const val KEY_RESULT_FRAMES_DIR = "result_frames_dir"
        const val KEY_RESULT_TOTAL_FRAMES = "result_total_frames"
        const val KEY_RESULT_EXTRACTED_FRAMES = "result_extracted_frames"
        const val KEY_RESULT_ERROR_MESSAGE = "result_error_message"
        const val KEY_RESULT_FRAME_INDEX = "result_frame_index"
    }

    init {
        createNotificationChannel()
    }

    fun enqueueExtraction(
        uri: Uri,
        metadata: VideoMetadata,
        config: FrameExtractionConfig,
        onProgress: (FrameExtractionResult.Progress) -> Unit,
        onResult: (FrameExtractionResult) -> Unit
    ): UUID {
        val data = Data.Builder()
            .putString(KEY_URI, uri.toString())
            .putBoolean(KEY_CONFIG_EXTRACT_ALL, config.extractAllFrames)
            .putLong(KEY_CONFIG_INTERVAL, config.frameInterval)
            .putInt(KEY_CONFIG_PROGRESS_INTERVAL, config.progressUpdateInterval)
            .putBoolean(KEY_CONFIG_CLEANUP, config.cleanupOnError)
            .putString(KEY_CONFIG_QUALITY_LEVEL, config.qualityLevel.name)
            .putInt(KEY_CONFIG_BATCH_SIZE, config.batchSize)
            .putLongArray(KEY_CONFIG_CUSTOM_TIMESTAMPS, config.customTimestamps?.toLongArray() ?: longArrayOf())
            .putString(KEY_METADATA, mapOf(
                "filePath" to metadata.filePath,
                "fileName" to metadata.fileName,
                "duration" to metadata.duration,
                "width" to metadata.width,
                "height" to metadata.height,
                "frameRate" to metadata.frameRate,
                "bitRate" to metadata.bitRate,
                "codec" to metadata.codec,
                "mimeType" to metadata.mimeType,
                "fileSize" to metadata.fileSize,
                "hash" to metadata.hash,
                "creationTime" to metadata.creationTime,
                "rotation" to metadata.rotation
            ).toString())
            .build()

        val workRequest = OneTimeWorkRequestBuilder<FrameExtractionWorker>()
            .setInputData(data)
            .addTag(WORK_TAG)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                10,
                TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(context).apply {
            enqueueUniqueWork(uri.toString(), ExistingWorkPolicy.APPEND_OR_REPLACE, workRequest)
            getWorkInfoByIdLiveData(workRequest.id).observeForever { workInfo ->
                when (workInfo?.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        val resultType = workInfo.outputData.getString(KEY_RESULT_TYPE)
                        val result = when (resultType) {
                            "Success" -> {
                                val framesDir = workInfo.outputData.getString(KEY_RESULT_FRAMES_DIR)?.let { File(it) }
                                val extractedFrames = workInfo.outputData.getStringArray(KEY_RESULT_EXTRACTED_FRAMES)?.map { File(it) } ?: emptyList()
                                FrameExtractionResult.Success(
                                    framesDirectory = framesDir ?: File(context.getExternalFilesDir(null), "frames"),
                                    totalFrames = workInfo.outputData.getInt(KEY_RESULT_TOTAL_FRAMES, 0),
                                    extractedFrames = extractedFrames
                                )
                            }
                            "Error" -> FrameExtractionResult.Error(
                                exception = Exception(workInfo.outputData.getString(KEY_RESULT_ERROR_MESSAGE) ?: "Unknown error"),
                                message = workInfo.outputData.getString(KEY_RESULT_ERROR_MESSAGE) ?: "Unknown error",
                                frameIndex = workInfo.outputData.getInt(KEY_RESULT_FRAME_INDEX, -1)
                            )
                            "Cancelled" -> FrameExtractionResult.Cancelled
                            "InsufficientStorage" -> FrameExtractionResult.InsufficientStorage
                            "VideoNotFound" -> FrameExtractionResult.VideoNotFound
                            "NoAudioTracks" -> FrameExtractionResult.NoAudioTracks
                            else -> FrameExtractionResult.Error(
                                Exception("Invalid result type"),
                                "Invalid result type"
                            )
                        }
                        onResult(result)
                    }
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        onResult(FrameExtractionResult.Error(Exception("Work ${workInfo.state.name}"), "Extraction ${workInfo.state.name.lowercase()}"))
                    }
                    else -> {
                        val progress = workInfo.progress.getInt("progress", 0).toFloat() / 100
                        onProgress(FrameExtractionResult.Progress(
                            currentFrame = workInfo.progress.getInt("current_frame", 0),
                            totalFrames = workInfo.progress.getInt("total_frames", 0),
                            percentage = progress,
                            currentTimestamp = workInfo.progress.getLong("current_timestamp", 0)
                        ))
                    }
                }
            }
        }

        return workRequest.id
    }

    fun cancelExtraction(workId: UUID) {
        WorkManager.getInstance(context).cancelWorkById(workId)
        clearNotification()
    }

    private fun createNotificationChannel() {
        val name = "Frame Extraction"
        val descriptionText = "Notifications for frame extraction progress"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    fun updateProgressNotification(progress: FrameExtractionResult.Progress) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Frame Extraction")
            .setContentText("Processing: ${(progress.percentage * 100).toInt()}%")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setProgress(100, (progress.percentage * 100).toInt(), false)
            .setOngoing(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun clearNotification() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
