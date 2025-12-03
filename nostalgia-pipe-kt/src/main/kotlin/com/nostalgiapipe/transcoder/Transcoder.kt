package com.nostalgiapipe.transcoder

import com.nostalgiapipe.models.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.io.path.extension

object Transcoder {

    suspend fun createProxyVideo(inputPath: Path, outputPath: Path): Path? = withContext(Dispatchers.IO) {
        val proxyVideoPath = outputPath.resolve("proxy_${inputPath.fileName}.mp4")

        // Using thumbnail filter to pick representative frames (1 every 150 frames),
        // drawing timestamp, and condensing video into a slideshow.
        // We use double backslash for escaping the colon in pts function for the drawtext filter.
        val filterChain = "thumbnail=150,scale=480:-1,drawtext=text='%{pts\\:hms}':x=(w-text_w-10):y=(h-text_h-10):fontsize=24:fontcolor=yellow:box=1:boxcolor=black@0.5,setpts=N/FRAME_RATE/TB"

        val command = arrayOf(
            "ffmpeg",
            "-y",
            "-i", inputPath.pathString,
            "-filter:v", filterChain,
            "-c:v", "libx264",
            "-crf", "28",
            "-preset", "fast",
            "-an", // Remove audio as this is a condensed slideshow
            "-movflags", "+faststart",
            proxyVideoPath.pathString
        )

        runFfmpegCommand(command, "smart proxy generation")

        return@withContext proxyVideoPath
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
