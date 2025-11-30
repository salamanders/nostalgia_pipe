package com.nostalgiapipe.transcoder

import com.nostalgiapipe.models.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.io.path.extension

object Transcoder {

    suspend fun createProxyVideo(timestamps: List<Double>, inputPath: Path, outputPath: Path): Path? = withContext(Dispatchers.IO) {
        if (timestamps.isEmpty()) return@withContext null

        val proxyVideoPath = outputPath.resolve("proxy_${inputPath.fileName}.mp4")

        // Build select filter string: "between(t,ts,ts+0.001)+..."
        val selectFilter = timestamps.joinToString("+") { ts ->
            "between(t,$ts,${ts + 0.001})"
        }

        val command = arrayOf(
            "ffmpeg",
            "-y",
            "-i", inputPath.pathString,
            "-filter:v", "select='$selectFilter',setpts=N/FRAME_RATE/TB,scale=-1:360",
            "-c:v", "libx264",
            "-crf", "28",
            "-preset", "fast",
            "-c:a", "aac",
            "-b:a", "64k",
            "-movflags", "+faststart",
            proxyVideoPath.pathString
        )

        runFfmpegCommand(command, "proxy video generation")

        return@withContext proxyVideoPath
    }

    suspend fun createFinalVideo(inputPath: Path, metadata: VideoMetadata, outputPath: Path): Path? = withContext(Dispatchers.IO) {
        // This method might be deprecated or needs to loop through scenes.
        // For strict compliance with the plan, I am leaving it as a fallback or upgrading it to handle the first scene
        // but the 'finalize' method in Orchestrator will be calling a new segment method.
        // Let's implement transcodeSegment instead and remove this logic if unused,
        // OR update this to handle single-file scenarios.
        // Given the prompt: "Update Transcoder to Support Segment Transcoding", I will add transcodeSegment.
        return@withContext null
    }

    suspend fun transcodeSegment(inputPath: Path, outputPath: Path, start: Double, end: Double): Path? = withContext(Dispatchers.IO) {
        val duration = end - start

        // Ensure duration is positive
        if (duration <= 0) return@withContext null

        val command = arrayOf(
            "ffmpeg",
            "-y",
            "-ss", start.toString(),
            "-t", duration.toString(),
            "-i", inputPath.pathString,
            "-c:v", "libx265",
            "-crf", "18",
            "-preset", "slower",
            "-pix_fmt", "yuv420p10le", // 10-bit color
            "-vf", "bwdif=mode=1", // Deinterlace to 60fps
            "-c:a", "aac",
            "-b:a", "256k",
            "-movflags", "+faststart",
            outputPath.pathString
        )

        runFfmpegCommand(command, "segment transcoding")
        return@withContext outputPath
    }

    private suspend fun runFfmpegCommand(command: Array<String>, processName: String) {
        println("Running FFmpeg for $processName: ${command.joinToString(" ")}")
        try {
            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()

            process.inputStream.bufferedReader().use { reader ->
                reader.lines().forEach { println("FFMPEG: $it") }
            }

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw RuntimeException("Error: FFmpeg process for $processName exited with code $exitCode")
            } else {
                println("Success: FFmpeg process for $processName completed.")
            }
        } catch (e: Exception) {
            println("Exception while running FFmpeg for $processName: ${e.message}")
            throw e
        }
    }
}
