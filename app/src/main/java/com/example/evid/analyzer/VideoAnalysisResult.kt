package com.example.evid.analyzer


import com.example.evid.data.VideoMetadata

sealed class VideoAnalysisResult {
    data class Success(val metadata: VideoMetadata) : VideoAnalysisResult()
    data class Error(val exception: Exception, val message: String) : VideoAnalysisResult()
    object InvalidFile : VideoAnalysisResult()
    object FileNotFound : VideoAnalysisResult()
}