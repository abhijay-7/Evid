package com.example.evid.extractor

import com.example.evid.data.AudioTrackMetadata
import java.io.File

sealed class FrameExtractionResult {
    data class Success(
        val framesDirectory: File,
        val totalFrames: Int,
        val extractedFrames: List<File>
    ) : FrameExtractionResult()

    data class AudioSuccess(
        val audioDirectory: File,
        val extractedFiles: List<File>,
        val audioMetadata: List<AudioTrackMetadata>
    ) : FrameExtractionResult()

    data class Progress(
        val currentFrame: Int,
        val totalFrames: Int,
        val percentage: Float,
        val currentTimestamp: Long
    ) : FrameExtractionResult()

    data class AudioProgress(
        val currentTrack: Int,
        val totalTracks: Int,
        val percentage: Float
    ) : FrameExtractionResult()

    data class Error(
        val exception: Exception,
        val message: String,
        val frameIndex: Int = -1
    ) : FrameExtractionResult()

    object Cancelled : FrameExtractionResult()
    object InsufficientStorage : FrameExtractionResult()
    object VideoNotFound : FrameExtractionResult()
    object NoAudioTracks : FrameExtractionResult()
}