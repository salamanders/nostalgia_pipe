package com.nostalgiapipe

import com.nostalgiapipe.config.Config
import com.nostalgiapipe.models.VideoMetadata
import com.nostalgiapipe.orchestrator.Orchestrator
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.copyTo
import kotlin.io.path.readText
import kotlin.io.path.fileSize
import kotlin.io.path.listDirectoryEntries

class LiveVisionaryTest {

    @Test
    fun `live pipeline test with real Gemini API`() = runBlocking {
        val apiKey = System.getenv("GEMINI_API_KEY")
        assumeTrue(apiKey != null && apiKey.isNotBlank(), "GEMINI_API_KEY not set in environment, skipping live test.")

        println("GEMINI_API_KEY found, running live test...")

        val inputDir = createTempDirectory("live_input")
        val outputDir = createTempDirectory("live_output")

        // Use the static test file
        val resourceUrl = this.javaClass.classLoader.getResource("VTS_01_1.VOB")
        assertTrue(resourceUrl != null, "Test resource VTS_01_1.VOB not found in classpath")
        val resourcePath = Path.of(resourceUrl!!.toURI())
        assertTrue(resourcePath.exists(), "Test resource not found at $resourcePath")

        // Copy VOB to input directory
        val inputVideo = inputDir.resolve("test_video_live.VOB")
        resourcePath.copyTo(inputVideo)

        val config = Config(
            inputPath = inputDir.toAbsolutePath().toString(),
            outputPath = outputDir.toAbsolutePath().toString(),
            googleApiKey = apiKey
        )

        // Use real Visionary
        val orchestrator = Orchestrator(config)

        // --- SUBMIT PHASE ---
        println("Running Submit Phase...")
        orchestrator.submit()

        // Verify sidecar exists
        val sidecar = inputVideo.resolveSibling("test_video_live.VOB.nostalgia_pipe.json")
        assertTrue(sidecar.exists(), "Sidecar file was not created at $sidecar")

        // Parse sidecar to check for valid content
        val jsonContent = sidecar.readText()
        println("Generated JSON content:\n$jsonContent")

        val jsonParser = Json { ignoreUnknownKeys = true }
        val metadata = jsonParser.decodeFromString<VideoMetadata>(jsonContent)

        // Gemini might return empty scenes if the video is too abstract, but we expect at least a valid object.
        // If scenes are empty, print a warning, but strictly we want to see if it works.
        // For a 5s test video, it might see one scene.
        if (metadata.scenes.isEmpty()) {
            println("WARNING: Gemini returned 0 scenes. This might be due to the short/abstract nature of the test video.")
        } else {
            println("Gemini detected ${metadata.scenes.size} scenes.")
            // Inspect first scene
            val firstScene = metadata.scenes[0]
            println("First Scene Detected: Title='${firstScene.title}', Year='${firstScene.year}', Start='${firstScene.start}', End='${firstScene.end}'")
        }

        // Verify proxy exists
        val proxy = outputDir.resolve("proxy_test_video_live.VOB.mp4")
        assertTrue(proxy.exists(), "Proxy video was not created at $proxy")

        // --- FINALIZE PHASE ---
        println("Running Finalize Phase...")
        orchestrator.finalize()

        // Verify that we have some output files IF there were scenes
        if (metadata.scenes.isNotEmpty()) {
            val outputFiles = outputDir.listDirectoryEntries("*.mp4").filter { it.fileName.toString() != proxy.fileName.toString() }

            println("Output files generated: ${outputFiles.map { it.fileName }}")
            assertTrue(outputFiles.isNotEmpty(), "No output video clips were generated in finalize phase despite detected scenes.")

            // Verify files are playable (not empty)
            outputFiles.forEach { file ->
                assertTrue(file.fileSize() > 1000, "Output file $file is too small (${file.fileSize()} bytes), likely invalid.")
            }
        } else {
            println("Skipping output file verification as no scenes were detected.")
        }
    }
}
