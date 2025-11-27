package com.nostalgiapipe

import com.nostalgiapipe.config.Config
import com.nostalgiapipe.models.Scene
import com.nostalgiapipe.models.VideoMetadata
import com.nostalgiapipe.orchestrator.Orchestrator
import com.nostalgiapipe.visionary.Visionary
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.writeBytes
import java.io.File

class IntegrationTest {

    class MockVisionary : Visionary("fake_key") {
        override suspend fun analyzeVideo(proxyVideoPath: Path): VideoMetadata? {
            return VideoMetadata(
                scenes = listOf(
                    Scene(
                        title = "Test Scene",
                        description = "A beautiful sunny day",
                        start = "00:00:00",
                        end = "00:00:05",
                        year = "2023",
                        location = "Park",
                        people = listOf("Person A")
                    )
                )
            )
        }
    }

    @Test
    fun `full pipeline test`() = runBlocking {
        val inputDir = createTempDirectory("input")
        val outputDir = createTempDirectory("output")

        // Create a synthetic test video using ffmpeg
        // Use MJPEG AVI which is more widely supported by default OpenCV builds if ffmpeg backend is missing
        val inputVideo = inputDir.resolve("test_video.avi")

        val command = arrayOf(
            "ffmpeg",
            "-f", "lavfi", "-i", "testsrc=duration=5:size=640x480:rate=30",
            "-c:v", "mjpeg",
            "-t", "5",
            inputVideo.toAbsolutePath().toString()
        )

        val process = ProcessBuilder(*command)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            println("FFmpeg failed with exit code $exitCode")
            println("FFmpeg output: $output")
        }

        assertTrue(inputVideo.exists(), "Test video was not created. FFmpeg output: $output")
        assertTrue(Files.size(inputVideo) > 0, "Test video is empty")

        val config = Config(
            inputPath = inputDir.toAbsolutePath().toString(),
            outputPath = outputDir.toAbsolutePath().toString(),
            googleApiKey = "fake_key"
        )

        val orchestrator = Orchestrator(config, MockVisionary())

        // Run Submit Phase
        orchestrator.submit()

        // Verify sidecar exists
        val sidecar = inputVideo.resolveSibling("test_video.avi.nostalgia_pipe.json")
        assertTrue(sidecar.exists(), "Sidecar file was not created. Expected at $sidecar")

        // Verify proxy exists
        val proxy = outputDir.resolve("proxy_test_video.avi.mp4")
        assertTrue(proxy.exists(), "Proxy video was not created/kept. Expected at $proxy")

        // Run Finalize Phase
        orchestrator.finalize()

        // Verify Final Output
        // Transcoder sanitizes the filename: spaces -> underscores
        val finalOutput = outputDir.resolve("2023_-_Test_Scene.mp4")
        assertTrue(finalOutput.exists(), "Final output video was not created. Expected at $finalOutput")
    }
}
