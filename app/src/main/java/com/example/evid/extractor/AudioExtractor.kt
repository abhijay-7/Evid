package com.example.evid.extractor

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.example.evid.data.AudioTrackMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

class AudioExtractor(private val context: Context) {

    suspend fun extractAudio(
        inputUri: Uri,
        outputFormat: String = "wav",
        onProgress: (FrameExtractionResult.AudioProgress) -> Unit
    ): FrameExtractionResult = withContext(Dispatchers.IO) {
        try {
            // Validate storage
            if (!FrameExtractionUtils.validateStorageSpace(estimatedFiles = 5, avgFileSizeKB = 1000)) {
                return@withContext FrameExtractionResult.InsufficientStorage
            }

            // Create temporary input file
            val tempFile = File(context.getExternalFilesDir(null), "temp_audio_input_${UUID.randomUUID()}.mp4")
            try {
                context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
                    tempFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                } ?: return@withContext FrameExtractionResult.VideoNotFound
            } catch (e: Exception) {
                tempFile.delete()
                return@withContext FrameExtractionResult.Error(e, "Failed to read input file")
            }

            // Probe for audio tracks
            val probeSession = FFprobeKit.execute(
                "-i ${tempFile.absolutePath} -show_streams -select_streams a -v quiet -of json"
            )
            if (!ReturnCode.isSuccess(probeSession.returnCode)) {
                tempFile.delete()
                return@withContext FrameExtractionResult.Error(
                    Exception(probeSession.failStackTrace ?: "Unknown error"),
                    "Failed to probe audio tracks"
                )
            }

            val json = JSONObject(probeSession.output)
            val streams = json.getJSONArray("streams")
            if (streams.length() == 0) {
                tempFile.delete()
                return@withContext FrameExtractionResult.Error(Exception("No audio tracks"), "Video has no audio tracks")
            }

            // Create output directory
            val timestamp = System.currentTimeMillis()
            val outputDir = File(
                context.getExternalFilesDir(null),
                "audio/$timestamp"
            ).apply { mkdirs() }

            val extractedFiles = mutableListOf<File>()
            val audioMetadata = mutableListOf<AudioTrackMetadata>()

            // Extract each audio track
            for (i in 0 until streams.length()) {
                val stream = streams.getJSONObject(i)
                val sampleRate = stream.getInt("sample_rate")
                val channels = stream.getInt("channels")
                val bitrate = stream.getString("bit_rate").toLongOrNull() ?: 0L
                val codec = stream.getString("codec_name")

                val outputFile = File(outputDir, "audio_track_${i}_$timestamp.$outputFormat")
                val command = when (outputFormat) {
                    "mp3" -> "-y -i ${tempFile.absolutePath} -map 0:a:$i -c:a mp3 -b:a 192k ${outputFile.absolutePath}"
                    else -> "-y -i ${tempFile.absolutePath} -map 0:a:$i -c:a pcm_s16le ${outputFile.absolutePath}"
                }

                val progressPipe = File(context.getExternalFilesDir(null), "progress_audio_$i.txt")
                val session = FFmpegKit.execute("$command -progress ${progressPipe.absolutePath} -stats_period 0.1")

                if (ReturnCode.isSuccess(session.returnCode)) {
                    extractedFiles.add(outputFile)
                    audioMetadata.add(
                        AudioTrackMetadata(
                            index = i,
                            sampleRate = sampleRate,
                            channels = channels,
                            bitrate = bitrate,
                            codec = codec
                        )
                    )
                    onProgress(FrameExtractionResult.AudioProgress(i + 1, streams.length(), (i + 1).toFloat() / streams.length()))
                } else {
                    tempFile.delete()
                    progressPipe.delete()
                    outputDir.deleteRecursively()
                    return@withContext FrameExtractionResult.Error(
                        Exception(session.failStackTrace ?: "Unknown error"),
                        "Failed to extract audio track $i"
                    )
                }
                progressPipe.delete()
            }

            tempFile.delete()
            FrameExtractionResult.AudioSuccess(outputDir, extractedFiles, audioMetadata)
        } catch (e: Exception) {
            FrameExtractionResult.Error(e, "Audio extraction failed: ${e.message}")
        }
    }
}