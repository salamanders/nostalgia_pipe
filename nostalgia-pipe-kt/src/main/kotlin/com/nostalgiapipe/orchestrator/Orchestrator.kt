package com.nostalgiapipe.orchestrator

import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.terminal.Terminal
import com.nostalgiapipe.config.Config
import com.nostalgiapipe.filter.KeyFrameSelector
import com.nostalgiapipe.filter.NostalgiaFilter
import com.nostalgiapipe.models.VideoMetadata
import com.nostalgiapipe.scanner.Scanner
import com.nostalgiapipe.transcoder.Transcoder
import com.nostalgiapipe.visionary.Visionary
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.io.path.name
import com.nostalgiapipe.models.Scene
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class Orchestrator(
    private val config: Config,
    private val visionary: Visionary = Visionary(config.googleApiKey),
    private val frameSelector: KeyFrameSelector = NostalgiaFilter
) {

    private val terminal = Terminal()
    private val json = Json { prettyPrint = true }

    suspend fun submit() {
        terminal.println(green("Starting 'submit' phase..."))
        val videoFiles = Scanner.findVideoFiles(Path(config.inputPath)).toList()

        if (videoFiles.isEmpty()) {
            terminal.println(yellow("No video files found to process."))
            return
        }

        videoFiles.forEach { videoPath ->
            val sidecarFile = videoPath.resolveSibling("${videoPath.fileName}.nostalgia_pipe.json")
            if (sidecarFile.exists()) {
                terminal.println(cyan("Skipping ${videoPath.fileName}: Already processed (sidecar file exists)."))
                return@forEach
            }

            terminal.println("Processing: ${blue(videoPath.toString())}")

            terminal.println("  - Selecting keyframes...")
            val keyFrames = frameSelector.selectKeyFrames(videoPath)
            if (keyFrames.isEmpty()) {
                terminal.println(red("  - Error: Could not select any keyframes."))
                return@forEach
            }
            terminal.println(green("  - Found ${keyFrames.size} keyframes."))

            terminal.println("  - Creating proxy video...")
            val proxyVideo = Transcoder.createProxyVideo(keyFrames, videoPath, Path(config.outputPath))
            if (proxyVideo == null) {
                terminal.println(red("  - Error: Failed to create proxy video."))
                return@forEach
            }
            terminal.println(green("  - Proxy video created at: $proxyVideo"))
            // Keep proxy for inspection

            terminal.println("  - Analyzing video with Gemini AI (this may take a moment)...")
            val metadata = visionary.analyzeVideo(proxyVideo)
            if (metadata == null) {
                terminal.println(red("  - Error: Failed to get analysis from Visionary AI."))
                return@forEach
            }
            terminal.println(green("  - AI analysis complete."))

            val metadataJson = json.encodeToString(metadata)
            sidecarFile.writeText(metadataJson)
            terminal.println(green("  - Metadata saved to sidecar file: $sidecarFile"))
        }
    }

    suspend fun finalize() {
        terminal.println(green("Starting 'finalize' phase..."))
        val videoFiles = Scanner.findVideoFiles(Path(config.inputPath)).toList()
            .filter { it.resolveSibling("${it.fileName}.nostalgia_pipe.json").exists() }

        if (videoFiles.isEmpty()) {
            terminal.println(yellow("No projects found ready for finalization."))
            return
        }

        videoFiles.forEach { videoPath ->
            terminal.println("Finalizing: ${blue(videoPath.toString())}")
            val sidecarFile = videoPath.resolveSibling("${videoPath.fileName}.nostalgia_pipe.json")

            try {
                val metadata = json.decodeFromString<VideoMetadata>(sidecarFile.readText())
                terminal.println(green("  - Found ${metadata.scenes.size} scenes."))

                var successCount = 0
                metadata.scenes.forEachIndexed { index, scene ->
                    terminal.println("  - Processing Scene ${index + 1}: ${scene.title}")

                    val baseName = "${scene.year} - ${scene.title}"
                    val safeName = baseName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                    val fileName = "$safeName.mp4"
                    val outputFilePath = Path(config.outputPath).resolve(fileName)

                    val startTime = parseTimestamp(scene.start)
                    val endTime = parseTimestamp(scene.end)

                    if (startTime != null && endTime != null) {
                        val result = Transcoder.transcodeSegment(videoPath, outputFilePath, startTime, endTime)
                        if (result != null) {
                            terminal.println(green("    - Created: $fileName"))
                            successCount++
                        } else {
                            terminal.println(red("    - Failed to transcode: $fileName"))
                        }
                    } else {
                         terminal.println(red("    - Invalid timestamps for scene: ${scene.start} - ${scene.end}"))
                    }
                }

                if (successCount == metadata.scenes.size) {
                    sidecarFile.toFile().delete()
                    terminal.println(cyan("  - All scenes processed. Removed sidecar file."))
                } else {
                    terminal.println(yellow("  - Warning: Some scenes failed. Sidecar file preserved."))
                }

            } catch (e: Exception) {
                terminal.println(red("  - Error processing ${videoPath.fileName}: ${e.message}"))
                e.printStackTrace()
            }
        }
    }

    private fun parseTimestamp(timestamp: String): Double? {
        // Formats: "HH:MM:SS", "MM:SS", "SS" or just a number
        try {
             if (timestamp.contains(":")) {
                val parts = timestamp.split(":").map { it.toDouble() }
                return when (parts.size) {
                    3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                    2 -> parts[0] * 60 + parts[1]
                    else -> null
                }
             } else {
                 return timestamp.toDoubleOrNull()
             }
        } catch (e: Exception) {
            return null
        }
    }
}
