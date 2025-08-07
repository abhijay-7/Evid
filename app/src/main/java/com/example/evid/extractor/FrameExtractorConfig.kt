package com.example.evid.extractor

enum class QualityLevel {
    Thumbnail, Preview, Full
}

data class FrameExtractionConfig(
    val jpegQuality: Int = 85,
    val extractAllFrames: Boolean = true,
    val frameInterval: Long = 1000L,
    val maxFrameDimension: Int = 1920,
    val enableProgressUpdates: Boolean = true,
    val progressUpdateInterval: Int = 10,
    val cleanupOnError: Boolean = true,
    val memoryOptimized: Boolean = true,
    val batchSize: Int = 50,
    val customTimestamps: List<Long>? = null,
    val qualityLevel: QualityLevel = QualityLevel.Preview
) {

    fun validate(): Boolean {
        return jpegQuality in 1..100 &&
                frameInterval > 0 &&
                maxFrameDimension > 0 &&
                progressUpdateInterval > 0 &&
                batchSize > 0
    }

    fun getJpegQualityForLevel(): Int {
        return when (qualityLevel) {
            QualityLevel.Thumbnail -> 10
            QualityLevel.Preview -> 5
            QualityLevel.Full -> 2
        }
    }

    fun getMaxDimensionForLevel(originalWidth: Int, originalHeight: Int): Int {
        return when (qualityLevel) {
            QualityLevel.Thumbnail -> 240
            QualityLevel.Preview -> 480
            QualityLevel.Full -> maxOf(originalWidth, originalHeight)
        }
    }

    companion object {
        fun createDefault() = FrameExtractionConfig()

        fun createHighQuality() = FrameExtractionConfig(
            jpegQuality = 95,
            maxFrameDimension = 2160,
            memoryOptimized = false,
            qualityLevel = QualityLevel.Full
        )

        fun createMemoryOptimized() = FrameExtractionConfig(
            jpegQuality = 75,
            maxFrameDimension = 1280,
            memoryOptimized = true,
            batchSize = 25,
            qualityLevel = QualityLevel.Preview
        )

        fun createSampled(intervalMs: Long) = FrameExtractionConfig(
            extractAllFrames = false,
            frameInterval = intervalMs,
            qualityLevel = QualityLevel.Preview
        )

        fun createFromTimestamps(timestamps: List<Long>) = FrameExtractionConfig(
            customTimestamps = timestamps,
            qualityLevel = QualityLevel.Preview
        )

        fun createForQuality(qualityLevel: QualityLevel) = FrameExtractionConfig(
            qualityLevel = qualityLevel
        )
    }
}