package com.nostalgiapipe.transcoder

import com.nostalgiapipe.utils.CommandRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.pathString

object Transcoder {

    suspend fun createProxyVideo(inputPath: Path, outputPath: Path): Path? = withContext(Dispatchers.IO) {
        val proxyVideoPath = outputPath.resolve("proxy_${inputPath.fileName}.mp4")

        // select='gt(scene,0.01)': Skip completely static frames before thumbnailing.
        // thumbnail=150: Pick the most representative frame every 150 frames.
        // scale=480:-1: Downscale for AI.
        // drawtext: Burn in timestamp.
        // setpts: Condense to slideshow.
        val filterChain = "select='gt(scene,0.01)',thumbnail=150,scale=480:-1,drawtext=text='%{pts\\:hms}':x=(w-text_w-10):y=(h-text_h-10):fontsize=24:fontcolor=yellow:box=1:boxcolor=black@0.5,setpts=N/FRAME_RATE/TB"

        val command = listOf(
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

        try {
            CommandRunner.runCommand(command, "smart proxy generation")
            return@withContext proxyVideoPath
        } catch (e: Exception) {
            println("Error generating proxy: ${e.message}")
            return@withContext null
        }
    }

    suspend fun transcodeSegment(inputPath: Path, outputPath: Path, start: Double, end: Double): Path? = withContext(Dispatchers.IO) {
        val duration = end - start
        if (duration <= 0) return@withContext null

        val isInterlaced = detectInterlacing(inputPath)
        println("Interlacing detection for ${inputPath.fileName}: $isInterlaced")

        // Build filter chain
        // bwdif=mode=1: Deinterlace to 60fps (double rate)
        val videoFilters = if (isInterlaced) {
            "bwdif=mode=1"
        } else {
            "null" // No-op filter if not interlaced
        }

        val command = listOf(
            "ffmpeg",
            "-y",
            "-ss", start.toString(),
            "-t", duration.toString(),
            "-i", inputPath.pathString,
            "-c:v", "libx265",
            "-crf", "18",
            "-preset", "slower",
            "-pix_fmt", "yuv420p10le", // 10-bit color
            "-vf", videoFilters,
            "-c:a", "aac",
            "-b:a", "256k",
            "-movflags", "+faststart",
            outputPath.pathString
        )

        try {
            CommandRunner.runCommand(command, "segment transcoding")
            return@withContext outputPath
        } catch (e: Exception) {
             println("Error transcoding segment: ${e.message}")
             return@withContext null
        }
    }

    private suspend fun detectInterlacing(inputPath: Path): Boolean {
        // Run idet filter on first 100 frames
        val command = listOf(
            "ffmpeg",
            "-hide_banner",
            "-i", inputPath.pathString,
            "-vf", "idet",
            "-frames:v", "100",
            "-an",
            "-f", "null",
            "-"
        )

        return try {
            val output = CommandRunner.runCommandAndGetOutput(command, "interlace detection")

            // Parse output for "Multi frame detection:"
            // Example: TFF: 12 BFF: 0 Progressive: 0 Undetermined: 0
            val tffRegex = Regex("""TFF:\s*(\d+)""")
            val bffRegex = Regex("""BFF:\s*(\d+)""")

            val tffCount = tffRegex.find(output)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val bffCount = bffRegex.find(output)?.groupValues?.get(1)?.toIntOrNull() ?: 0

            // If TFF or BFF frames dominate, it's interlaced.
            // Using a low threshold since we only check 100 frames.
            (tffCount > 10 || bffCount > 10)
        } catch (e: Exception) {
            println("Warning: Failed to detect interlacing, assuming progressive. Error: ${e.message}")
            false
        }
    }
}
