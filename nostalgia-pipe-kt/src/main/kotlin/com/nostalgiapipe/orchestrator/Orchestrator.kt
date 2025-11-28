package com.nostalgiapipe.orchestrator

import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.terminal.Terminal
import com.nostalgiapipe.config.Config
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

class Orchestrator(
    private val config: Config,
    private val visionary: Visionary = Visionary(config.googleApiKey)
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
            val keyFrames = NostalgiaFilter.selectKeyFrames(videoPath)
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

                terminal.println("  - Transcoding final high-quality video...")
                val finalVideo = Transcoder.createFinalVideo(videoPath, metadata, Path(config.outputPath))

                if (finalVideo != null) {
                    terminal.println(green("  - Successfully created final video: $finalVideo"))
                    sidecarFile.toFile().delete()
                    terminal.println(cyan("  - Removed sidecar file."))
                } else {
                    terminal.println(red("  - Error: Failed to create final video."))
                }
            } catch (e: Exception) {
                terminal.println(red("  - Error processing ${videoPath.fileName}: ${e.message}"))
            }
        }
    }
}
