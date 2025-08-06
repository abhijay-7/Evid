package com.example.evid.extractor

data class FrameExtractionConfig(
    val jpegQuality: Int = 85, // 0-100, higher = better quality
    val extractAllFrames: Boolean = true,
    val frameInterval: Long = 1000L, // milliseconds between frames if not extracting all
    val maxFrameDimension: Int = 1920, // max width or height, maintains aspect ratio
    val enableProgressUpdates: Boolean = true,
    val progressUpdateInterval: Int = 10, // update progress every N frames
    val cleanupOnError: Boolean = true, // delete partial extraction on error
    val memoryOptimized: Boolean = true, // enable memory optimizations for large videos
    val batchSize: Int = 50, // frames to process in each batch (for memory management)
    val customTimestamps: List<Long>? = null // specific timestamps in ms to extract (overrides other settings)
) {

    fun validate(): Boolean {
        return jpegQuality in 1..100 &&
                frameInterval > 0 &&
                maxFrameDimension > 0 &&
                progressUpdateInterval > 0 &&
                batchSize > 0
    }

    companion object {
        fun createDefault() = FrameExtractionConfig()

        fun createHighQuality() = FrameExtractionConfig(
            jpegQuality = 95,
            maxFrameDimension = 2160,
            memoryOptimized = false
        )

        fun createMemoryOptimized() = FrameExtractionConfig(
            jpegQuality = 75,
            maxFrameDimension = 1280,
            memoryOptimized = true,
            batchSize = 25
        )

        fun createSampled(intervalMs: Long) = FrameExtractionConfig(
            extractAllFrames = false,
            frameInterval = intervalMs
        )

        fun createFromTimestamps(timestamps: List<Long>) = FrameExtractionConfig(
            customTimestamps = timestamps
        )
    }
}