package com.example.evid.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class VideoMetadata(
    val filePath: String,
    val fileName: String,
    val duration: Long, // in milliseconds
    val width: Int,
    val height: Int,
    val frameRate: Float,
    val bitRate: Int,
    val codec: String,
    val mimeType: String,
    val fileSize: Long,
    val hash: String,
    val creationTime: Long,
    val rotation: Int = 0
) : Parcelable {

    val aspectRatio: Float
        get() = if (height != 0) width.toFloat() / height else 1f

    val durationFormatted: String
        get() {
            val totalSeconds = duration / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60

            return if (hours > 0) {
                "%02d:%02d:%02d".format(hours, minutes, seconds)
            } else {
                "%02d:%02d".format(minutes, seconds)
            }
        }

    val resolutionString: String
        get() = "${width}x${height}"

    val fileSizeFormatted: String
        get() {
            val kb = fileSize / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0

            return when {
                gb >= 1 -> "%.2f GB".format(gb)
                mb >= 1 -> "%.2f MB".format(mb)
                else -> "%.2f KB".format(kb)
            }
        }

    val isLandscape: Boolean
        get() = width > height

    val isPortrait: Boolean
        get() = height > width

    val isSquare: Boolean
        get() = width == height

    val qualityDescription: String
        get() = when {
            width >= 3840 -> "4K Ultra HD"
            width >= 2560 -> "1440p Quad HD"
            width >= 1920 -> "1080p Full HD"
            width >= 1280 -> "720p HD"
            width >= 854 -> "480p SD"
            else -> "Low Quality"
        }

    val frameRateDescription: String
        get() = when {
            frameRate >= 60f -> "High Frame Rate (${frameRate.toInt()}fps)"
            frameRate >= 30f -> "Standard (${frameRate.toInt()}fps)"
            frameRate >= 24f -> "Cinema (${frameRate.toInt()}fps)"
            else -> "Low Frame Rate (${frameRate.toInt()}fps)"
        }

    val bitRateFormatted: String
        get() {
            val kbps = bitRate / 1000.0
            val mbps = kbps / 1000.0

            return if (mbps >= 1) {
                "%.1f Mbps".format(mbps)
            } else {
                "%.0f Kbps".format(kbps)
            }
        }

    val compressionRatio: String
        get() {
            if (duration <= 0) return "Unknown"
            val uncompressedSize = (width * height * 3 * frameRate * (duration / 1000.0)).toLong()
            val ratio = if (fileSize > 0) uncompressedSize.toDouble() / fileSize else 0.0
            return "%.1f:1".format(ratio)
        }

    val isValid: Boolean
        get() = width > 0 && height > 0 && duration > 0 && fileSize > 0

    fun getDurationInSeconds(): Long = duration / 1000

    fun getDurationInMinutes(): Long = duration / 60000

    fun getAspectRatioString(): String {
        val gcd = gcd(width, height)
        val ratioWidth = width / gcd
        val ratioHeight = height / gcd
        return "$ratioWidth:$ratioHeight"
    }

    private fun gcd(a: Int, b: Int): Int {
        return if (b == 0) a else gcd(b, a % b)
    }

    companion object {
        fun createEmpty(): VideoMetadata {
            return VideoMetadata(
                filePath = "",
                fileName = "",
                duration = 0L,
                width = 0,
                height = 0,
                frameRate = 0f,
                bitRate = 0,
                codec = "",
                mimeType = "",
                fileSize = 0L,
                hash = "",
                creationTime = 0L,
                rotation = 0
            )
        }

        fun isValidVideoFile(fileName: String): Boolean {
            val videoExtensions = setOf("mp4", "avi", "mov", "mkv", "wmv", "flv", "webm", "m4v", "3gp")
            val extension = fileName.substringAfterLast('.', "").lowercase()
            return extension in videoExtensions
        }
    }
}