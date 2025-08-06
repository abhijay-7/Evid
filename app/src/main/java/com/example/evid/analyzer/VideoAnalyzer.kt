package com.example.evid.analyzer

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.evid.data.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.io.FileInputStream

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

            val retriever = MediaMetadataRetriever()

            try {
                retriever.setDataSource(filePath)
                extractMetadata(retriever, file)
            } catch (e: Exception) {
                VideoAnalysisResult.Error(e, "Failed to analyze video: ${e.message}")
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {
                    // Ignore release errors
                }
            }
        } catch (e: Exception) {
            VideoAnalysisResult.Error(e, "Unexpected error during video analysis: ${e.message}")
        }
    }

    suspend fun analyzeVideo(uri: Uri): VideoAnalysisResult = withContext(Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()

            try {
                retriever.setDataSource(context, uri)
                extractMetadataFromUri(retriever, uri)
            } catch (e: Exception) {
                VideoAnalysisResult.Error(e, "Failed to analyze video from URI: ${e.message}")
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {
                    // Ignore release errors
                }
            }
        } catch (e: Exception) {
            VideoAnalysisResult.Error(e, "Unexpected error during video analysis: ${e.message}")
        }
    }

    private suspend fun extractMetadata(
        retriever: MediaMetadataRetriever,
        file: File
    ): VideoAnalysisResult = withContext(Dispatchers.IO) {
        try {
            // Extract basic metadata
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 30f
            val bitRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "video/unknown"
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0

            // Extract codec information
            val codec = extractCodecInfo(retriever, mimeType)

            // Generate file hash for cache validation
            val hash = generateFileHash(file)

            val metadata = VideoMetadata(
                filePath = file.absolutePath,
                fileName = file.name,
                duration = duration,
                width = width,
                height = height,
                frameRate = frameRate,
                bitRate = bitRate,
                codec = codec,
                mimeType = mimeType,
                fileSize = file.length(),
                hash = hash,
                creationTime = file.lastModified(),
                rotation = rotation
            )

            VideoAnalysisResult.Success(metadata)

        } catch (e: Exception) {
            VideoAnalysisResult.Error(e, "Failed to extract metadata: ${e.message}")
        }
    }

    private suspend fun extractMetadataFromUri(
        retriever: MediaMetadataRetriever,
        uri: Uri
    ): VideoAnalysisResult = withContext(Dispatchers.IO) {
        try {
            // Extract basic metadata
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 30f
            val bitRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "video/unknown"
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0

            // Extract codec information
            val codec = extractCodecInfo(retriever, mimeType)

            // Get file size from content resolver
            val fileSize = getFileSizeFromUri(uri)

            // Generate hash from URI content
            val hash = generateHashFromUri(uri)

            val metadata = VideoMetadata(
                filePath = uri.toString(),
                fileName = getFileNameFromUri(uri),
                duration = duration,
                width = width,
                height = height,
                frameRate = frameRate,
                bitRate = bitRate,
                codec = codec,
                mimeType = mimeType,
                fileSize = fileSize,
                hash = hash,
                creationTime = System.currentTimeMillis(),
                rotation = rotation
            )

            VideoAnalysisResult.Success(metadata)

        } catch (e: Exception) {
            VideoAnalysisResult.Error(e, "Failed to extract metadata from URI: ${e.message}")
        }
    }

    private fun extractCodecInfo(retriever: MediaMetadataRetriever, mimeType: String): String {
        return try {
            // First try to get codec from MIME type (more reliable)
            val codecFromMime = when {
                mimeType.contains("avc") || mimeType.contains("h264") -> "H.264/AVC"
                mimeType.contains("hevc") || mimeType.contains("h265") -> "H.265/HEVC"
                mimeType.contains("vp8") -> "VP8"
                mimeType.contains("vp9") -> "VP9"
                mimeType.contains("av01") -> "AV1"
                mimeType.contains("mpeg4") -> "MPEG-4"
                mimeType.contains("mpeg2") -> "MPEG-2"
                mimeType.contains("wmv") -> "WMV"
                mimeType.contains("flv") -> "FLV"
                else -> null
            }

            if (codecFromMime != null) {
                return codecFromMime
            }

            // Try to get additional metadata that might contain codec info
            // Note: METADATA_KEY_VIDEO_CODEC is not available in all Android versions
            val additionalMetadata = mutableListOf<String>()

            // Try various metadata keys that might contain codec information
            listOf(
                MediaMetadataRetriever.METADATA_KEY_TITLE,
                MediaMetadataRetriever.METADATA_KEY_COMPOSER,
                MediaMetadataRetriever.METADATA_KEY_GENRE
            ).forEach { key ->
                try {
                    retriever.extractMetadata(key)?.let { value ->
                        if (value.contains("codec", ignoreCase = true) ||
                            value.contains("h264", ignoreCase = true) ||
                            value.contains("h265", ignoreCase = true) ||
                            value.contains("hevc", ignoreCase = true)) {
                            additionalMetadata.add(value)
                        }
                    }
                } catch (e: Exception) {
                    // Ignore errors when trying to extract metadata
                }
            }

            // Analyze additional metadata for codec hints
            additionalMetadata.firstOrNull()?.let { metadata ->
                when {
                    metadata.contains("h264", ignoreCase = true) ||
                            metadata.contains("avc", ignoreCase = true) -> return "H.264/AVC"
                    metadata.contains("h265", ignoreCase = true) ||
                            metadata.contains("hevc", ignoreCase = true) -> return "H.265/HEVC"
                    metadata.contains("vp8", ignoreCase = true) -> return "VP8"
                    metadata.contains("vp9", ignoreCase = true) -> return "VP9"
                    else -> {}
                }
            }

            // Fallback: analyze by file extension or container format
            when {
                mimeType.contains("mp4") -> "H.264/AVC (assumed)"
                mimeType.contains("webm") -> "VP8/VP9 (assumed)"
                mimeType.contains("avi") -> "Various (AVI)"
                mimeType.contains("mov") -> "H.264/AVC (assumed)"
                mimeType.contains("mkv") -> "Various (MKV)"
                else -> "Unknown Codec"
            }

        } catch (e: Exception) {
            "Unknown Codec"
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
            // Fallback to file path + size + modified time hash
            val fallbackString = "${file.absolutePath}:${file.length()}:${file.lastModified()}"
            MessageDigest.getInstance("MD5").digest(fallbackString.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }

    private suspend fun generateHashFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }

            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            // Fallback hash based on URI and timestamp
            val fallbackString = "${uri}:${System.currentTimeMillis()}"
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