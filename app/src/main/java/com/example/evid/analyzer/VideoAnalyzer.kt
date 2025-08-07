package com.example.evid.analyzer

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.example.evid.data.AudioTrackMetadata
import com.example.evid.data.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class VideoAnalyzer(private val context: Context) {

    suspend fun analyzeVideo(filePath: String): VideoAnalysisResult = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                return@withContext VideoAnalysisResult.FileNotFound
            }

            if (!VideoMetadata.isValidVideoFile(file.name)) {
                return@withContext VideoAnalysisResult.InvalidFile
            }

            extractMetadata(file)
        } catch (e: Exception) {
            VideoAnalysisResult.Error(e, "Unexpected error during video analysis: ${e.message}")
        }
    }

    suspend fun analyzeVideo(uri: Uri): VideoAnalysisResult = withContext(Dispatchers.IO) {
        try {
            val tempFile = File(context.getExternalFilesDir(null), "temp_probe.mp4")
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    tempFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                } ?: return@withContext VideoAnalysisResult.Error(Exception("Failed to open input stream"), "Failed to read URI")
                extractMetadata(tempFile, uri)
            } finally {
                tempFile.delete()
            }
        } catch (e: Exception) {
            VideoAnalysisResult.Error(e, "Unexpected error during video analysis: ${e.message}")
        }
    }

    private suspend fun extractMetadata(file: File, uri: Uri? = null): VideoAnalysisResult = withContext(Dispatchers.IO) {
        try {
            val probeSession = FFprobeKit.execute(
                "-i ${file.absolutePath} -show_entries format=duration,bit_rate:stream=width,height,codec_name,codec_type,sample_rate,channels,bit_rate -v quiet -of json"
            )
            if (!ReturnCode.isSuccess(probeSession.returnCode)) {
                return@withContext VideoAnalysisResult.Error(
                    Exception(probeSession.failStackTrace ?: "Unknown error"),
                    "Failed to probe video"
                )
            }

            val json = JSONObject(probeSession.output)
            val format = json.getJSONObject("format")
            val duration = (format.getString("duration").toFloatOrNull()?.times(1000)?.toLong()) ?: 0L
            val formatBitRate = format.getString("bit_rate").toLongOrNull() ?: 0L

            val streams = json.getJSONArray("streams")
            var width = 0
            var height = 0
            var videoCodec = "Unknown Codec"
            var frameRate = 30f
            val audioTracks = mutableListOf<AudioTrackMetadata>()

            for (i in 0 until streams.length()) {
                val stream = streams.getJSONObject(i)
                val codecType = stream.getString("codec_type")
                if (codecType == "video") {
                    width = stream.getInt("width")
                    height = stream.getInt("height")
                    videoCodec = stream.getString("codec_name")
                    frameRate = stream.optString("r_frame_rate").let { rate ->
                        if (rate.contains("/")) {
                            val (num, den) = rate.split("/").map { it.toFloatOrNull() ?: 1f }
                            if (den != 0f) num / den else 30f
                        } else {
                            rate.toFloatOrNull() ?: 30f
                        }
                    }
                } else if (codecType == "audio") {
                    audioTracks.add(
                        AudioTrackMetadata(
                            index = i,
                            sampleRate = stream.getInt("sample_rate"),
                            channels = stream.getInt("channels"),
                            bitrate = stream.getString("bit_rate").toLongOrNull() ?: 0L,
                            codec = stream.getString("codec_name")
                        )
                    )
                }
            }

            val mimeType = when (file.extension.lowercase()) {
                "mp4", "m4v" -> "video/mp4"
                "avi" -> "video/avi"
                "mov" -> "video/quicktime"
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                else -> "video/unknown"
            }

            val hash = generateFileHash(file)
            val fileSize = uri?.let { getFileSizeFromUri(it) } ?: file.length()
            val fileName = uri?.let { getFileNameFromUri(it) } ?: file.name
            val filePath = uri?.toString() ?: file.absolutePath

            VideoAnalysisResult.Success(
                VideoMetadata(
                    filePath = filePath,
                    fileName = fileName,
                    duration = duration,
                    width = width,
                    height = height,
                    frameRate = frameRate,
                    bitRate = formatBitRate.toInt(),
                    codec = videoCodec,
                    mimeType = mimeType,
                    fileSize = fileSize,
                    hash = hash,
                    creationTime = file.lastModified(),
                    rotation = 0, // FFprobe may not provide rotation reliably; consider additional logic if needed
                    audioTracks = audioTracks
                )
            )
        } catch (e: Exception) {
            VideoAnalysisResult.Error(e, "Failed to extract metadata: ${e.message}")
        }
    }

    private suspend fun generateFileHash(file: File): String = withContext(Dispatchers.IO) {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val fis = FileInputStream(file)
            val buffer = ByteArray(8192)
            var bytesRead: Int

            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }

            fis.close()
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            val fallbackString = "${file.absolutePath}:${file.length()}:${file.lastModified()}"
            MessageDigest.getInstance("MD5").digest(fallbackString.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }

    private fun getFileSizeFromUri(uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (sizeIndex >= 0) {
                    cursor.moveToFirst()
                    cursor.getLong(sizeIndex)
                } else {
                    0L
                }
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    cursor.moveToFirst()
                    cursor.getString(nameIndex) ?: "unknown_video"
                } else {
                    uri.lastPathSegment ?: "unknown_video"
                }
            } ?: uri.lastPathSegment ?: "unknown_video"
        } catch (e: Exception) {
            uri.lastPathSegment ?: "unknown_video"
        }
    }
}