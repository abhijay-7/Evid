package com.example.evid.extractor

import java.io.File

sealed class FrameExtractionResult {
    data class Success(
        val framesDirectory: File,
        val totalFrames: Int,
        val extractedFrames: List<File>
    ) : FrameExtractionResult()

    data class Progress(
        val currentFrame: Int,
        val totalFrames: Int,
        val percentage: Float,
        val currentTimestamp: Long
    ) : FrameExtractionResult()

    data class Error(
        val exception: Exception,
        val message: String,
        val frameIndex: Int = -1
    ) : FrameExtractionResult()

    object Cancelled : FrameExtractionResult()
    object InsufficientStorage : FrameExtractionResult()
    object VideoNotFound : FrameExtractionResult()
}