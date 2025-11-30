package com.nostalgiapipe.orchestrator

import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.terminal.Terminal
import com.nostalgiapipe.config.Config
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
import kotlin.io.path.createDirectories
import kotlin.io.path.div

class Orchestrator(
    private val config: Config,
    private val visionary: Visionary = Visionary(config.googleApiKey)
) {

    private val terminal = Terminal()
    private val json = Json { prettyPrint = true }

    // Job Manager Lite implementation (using jobs.json like python version)
    private val jobsFile = Path(config.outputPath) / "jobs.json"

    // Simple state tracking
    private fun loadJobs(): MutableMap<String, MutableMap<String, String>> {
        if (!jobsFile.exists()) return mutableMapOf()
        return try {
            val content = jobsFile.readText()
            if (content.isBlank()) mutableMapOf()
            else json.decodeFromString(content)
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    private fun saveJobs(jobs: Map<String, MutableMap<String, String>>) {
        jobsFile.writeText(json.encodeToString(jobs))
    }

    suspend fun submit() {
        terminal.println(green("Starting 'submit' phase..."))

        Path(config.outputPath).createDirectories()
        val proxyDir = Path(config.outputPath) / "proxies"
        proxyDir.createDirectories()

        val jobs = loadJobs()
        val videoFiles = Scanner.findVideoFiles(Path(config.inputPath)).toList()

        terminal.println("Found ${videoFiles.size} files.")

        // 1. Scan and Register
        videoFiles.forEach { file ->
            val pathStr = file.toString()
            if (!jobs.containsKey(pathStr)) {
                jobs[pathStr] = mutableMapOf(
                    "status" to "pending",
                    "context" to file.parent.name
                )
            }
        }
        saveJobs(jobs)

        // 2. Create Proxies
        jobs.filter { it.value["status"] == "pending" }.forEach { (originalPath, job) ->
            terminal.println("Creating proxy for ${blue(originalPath)}")
            val inputPath = Path(originalPath)
            val proxyPath = proxyDir / "${inputPath.fileName}_proxy.mp4"

            if (Transcoder.createProxy(inputPath, proxyPath)) {
                job["status"] = "proxy_created"
                job["proxy_path"] = proxyPath.toString()
                saveJobs(jobs)
                terminal.println(green("Proxy created."))
            } else {
                terminal.println(red("Failed to create proxy."))
            }
        }

        // 3. Upload and Analyze
        // Pending Uploads
        jobs.filter { it.value["status"] == "proxy_created" }.forEach { (originalPath, job) ->
            val proxyPathStr = job["proxy_path"] ?: return@forEach
            terminal.println("Uploading proxy: $proxyPathStr")

            val uri = visionary.uploadVideo(Path(proxyPathStr))
            if (uri != null) {
                job["status"] = "uploaded"
                job["gemini_uri"] = uri.uri().get()
                job["gemini_name"] = uri.name().get()
                saveJobs(jobs)
                terminal.println(green("Uploaded."))
            } else {
                 terminal.println(red("Upload failed."))
            }
        }

        // Pending Analysis
        jobs.filter { it.value["status"] == "uploaded" }.forEach { (originalPath, job) ->
            val fileName = job["gemini_name"] ?: return@forEach
            terminal.println("Analyzing $fileName...")

            val fileObj = visionary.getFile(fileName)
            if (fileObj != null) {
                val result = visionary.analyzeVideo(fileObj)
                if (result != null) {
                    job["status"] = "analyzed"
                    job["analysis_result"] = json.encodeToString(result)
                    saveJobs(jobs)
                    terminal.println(green("Analysis complete."))
                } else {
                    terminal.println(red("Analysis failed."))
                }
            } else {
                 terminal.println(red("Could not retrieve file from Gemini."))
            }
        }
    }

    suspend fun finalize() {
        terminal.println(green("Starting 'finalize' phase..."))
        val jobs = loadJobs()

        val readyJobs = jobs.filter { it.value["status"] == "analyzed" }

        if (readyJobs.isEmpty()) {
            terminal.println(yellow("No jobs ready to finalize."))
            return
        }

        readyJobs.forEach { (originalPathStr, job) ->
            val originalPath = Path(originalPathStr)
            terminal.println("Finalizing: ${blue(originalPathStr)}")

            val analysisStr = job["analysis_result"]
            if (analysisStr == null) {
                terminal.println(red("Missing analysis result."))
                return@forEach
            }

            try {
                val metadata = json.decodeFromString<VideoMetadata>(analysisStr)
                val globalYear = metadata.global_year
                val globalLocation = metadata.global_location

                var sceneIdx = 1
                metadata.scenes.forEach { scene ->
                    val start = scene.start_time
                    val end = scene.end_time

                    if (end - start < 1.0) return@forEach

                    val title = scene.title
                    val year = scene.year ?: globalYear ?: "0000"

                    // Filename sanitization
                    val safeTitle = title.filter { it.isLetterOrDigit() || it == ' ' || it == '-' }.trim()
                    val safeYear = year.filter { it.isDigit() }

                    val baseName = "$safeYear - $safeTitle"
                    var outputName = "$baseName.mp4"
                    var outputFile = Path(config.outputPath) / outputName
                    var counter = 1
                    while (outputFile.exists()) {
                        outputName = "$baseName ($counter).mp4"
                        outputFile = Path(config.outputPath) / outputName
                        counter++
                    }

                    // Metadata map
                    val metaMap = mapOf(
                        "title" to title,
                        "description" to scene.description,
                        "year" to year,
                        "location" to (scene.location ?: globalLocation ?: ""),
                        "people" to (scene.people?.joinToString(", ") ?: "")
                    )

                    // Sidecar
                    val sidecar = outputFile.resolveSibling("$outputName.json")
                    sidecar.writeText(json.encodeToString(metaMap))

                    terminal.println("  Transcoding: $outputName")
                    if (Transcoder.transcodeSegment(originalPath, outputFile, start, end, metaMap)) {
                        terminal.println(green("  Success."))
                    } else {
                        terminal.println(red("  Failed."))
                    }
                    sceneIdx++
                }

                job["status"] = "complete"
                saveJobs(jobs)

            } catch (e: Exception) {
                terminal.println(red("Error finalizing: ${e.message}"))
            }
        }
    }
}
