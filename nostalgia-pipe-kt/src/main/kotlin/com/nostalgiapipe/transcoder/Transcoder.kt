package com.nostalgiapipe.transcoder

import com.nostalgiapipe.models.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.pathString

object Transcoder {

    suspend fun createProxy(inputPath: Path, outputPath: Path): Boolean = withContext(Dispatchers.IO) {
        val command = arrayOf(
            "ffmpeg",
            "-y",
            "-i", inputPath.pathString,
            "-vf", "scale=-1:360",
            "-r", "5",
            "-c:v", "libx264",
            "-crf", "32",
            "-preset", "veryfast",
            "-c:a", "aac",
            "-b:a", "32k",
            "-ac", "1",
            outputPath.pathString
        )

        return@withContext runFfmpegCommand(command, "proxy creation")
    }

    suspend fun transcodeSegment(
        inputPath: Path,
        outputPath: Path,
        start: Double,
        end: Double,
        metadata: Map<String, String>? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val duration = end - start

        val metadataArgs = mutableListOf<String>()
        metadata?.forEach { (key, value) ->
            metadataArgs.add("-metadata")
            val ffmpegKey = when(key) {
                "year" -> "date"
                "description" -> "comment"
                else -> key
            }
            metadataArgs.add("$ffmpegKey=$value")
        }

        // Handle duplicate description for 'description' tag if needed, but 'comment' is standard.

        val baseCommand = mutableListOf(
            "ffmpeg",
            "-y",
            "-ss", start.toString(),
            "-t", duration.toString(),
            "-i", inputPath.pathString
        )

        baseCommand.addAll(metadataArgs)

        baseCommand.addAll(listOf(
            "-c:v", "libx265",
            "-crf", "18",
            "-preset", "slower",
            "-vf", "bwdif=mode=1,format=yuv420p10le",
            "-pix_fmt", "yuv420p10le",
            "-c:a", "aac",
            "-b:a", "256k",
            "-movflags", "+faststart",
            outputPath.pathString
        ))

        return@withContext runFfmpegCommand(baseCommand.toTypedArray(), "segment transcoding")
    }

    private suspend fun runFfmpegCommand(command: Array<String>, processName: String): Boolean {
        // println("Running FFmpeg: ${command.joinToString(" ")}") // Debug logging
        return try {
            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()

            // Consume output to prevent blocking (deadlock fix)
            process.inputStream.bufferedReader().use { it.readText() }

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                println("Error: FFmpeg process for $processName exited with code $exitCode")
                false
            } else {
                true
            }
        } catch (e: Exception) {
            println("Exception running FFmpeg for $processName: ${e.message}")
            false
        }
    }
}
