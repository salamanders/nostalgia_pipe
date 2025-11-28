package com.nostalgiapipe.transcoder

import com.nostalgiapipe.filter.Frame
import com.nostalgiapipe.models.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.imgcodecs.Imgcodecs
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.pathString
import kotlin.io.path.extension

object Transcoder {

    suspend fun createProxyVideo(frames: List<Frame>, audioPath: Path, outputPath: Path): Path? = withContext(Dispatchers.IO) {
        val tempDir = createTempDirectory("nostalgia-pipe-frames-")

        frames.forEachIndexed { index, frame ->
            val frameFile = tempDir.resolve("frame-%04d.png".format(index))
            Imgcodecs.imwrite(frameFile.pathString, frame.image)
            frame.image.release() // Release the mat to free memory
        }

        val proxyVideoPath = outputPath.resolve("proxy_${audioPath.fileName}.mp4")

        val command = arrayOf(
            "ffmpeg",
            "-y", // Overwrite output file if it exists
            "-framerate", "1", // 1 frame per second
            "-i", tempDir.resolve("frame-%04d.png").pathString,
            "-i", audioPath.pathString, // Use original audio
            "-c:a", "aac", // Re-encode audio to AAC
            "-b:a", "128k",
            "-c:v", "libx264",
            "-pix_fmt", "yuv420p",
            "-r", "1", // Set output frame rate
            proxyVideoPath.pathString
        )

        runFfmpegCommand(command, "proxy video generation")

        // Clean up temp frame images
        tempDir.toFile().deleteRecursively()

        return@withContext proxyVideoPath
    }

    suspend fun createFinalVideo(inputPath: Path, metadata: VideoMetadata, outputPath: Path): Path? = withContext(Dispatchers.IO) {
        val mainTitle = metadata.scenes.firstOrNull()?.title ?: "Untitled Event"
        val year = metadata.scenes.firstOrNull()?.year ?: "UnknownYear"
        val finalFileName = "$year - $mainTitle.mp4".replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val finalOutputPath = outputPath.resolve(finalFileName)

        val command = if (inputPath.toFile().isDirectory) {
            // Concatenate all VOB files for transcoding (Legacy VOB support)
            val vobFiles = inputPath.toFile().walk()
                .filter { it.isFile && it.extension.equals("VOB", ignoreCase = true) }
                .sorted()
                .joinToString("|") { it.absolutePath }

             arrayOf(
                "ffmpeg",
                "-y",
                "-i", "concat:$vobFiles",
                "-c:v", "libx265",
                "-crf", "18",
                "-preset", "slower",
                "-pix_fmt", "yuv420p10le", // 10-bit color
                "-vf", "bwdif=mode=1", // Deinterlace to 60fps
                "-c:a", "aac",
                "-b:a", "256k",
                "-movflags", "+faststart",
                finalOutputPath.pathString
            )
        } else {
             // Single file input
             arrayOf(
                "ffmpeg",
                "-y",
                "-i", inputPath.pathString,
                "-c:v", "libx265",
                "-crf", "18",
                "-preset", "slower",
                "-pix_fmt", "yuv420p10le", // 10-bit color
                "-vf", "bwdif=mode=1", // Deinterlace to 60fps
                "-c:a", "aac",
                "-b:a", "256k",
                "-movflags", "+faststart",
                finalOutputPath.pathString
            )
        }

        runFfmpegCommand(command, "final video transcoding")

        return@withContext finalOutputPath
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
