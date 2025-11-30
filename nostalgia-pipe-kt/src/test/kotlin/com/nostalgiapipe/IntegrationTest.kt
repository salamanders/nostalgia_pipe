package com.nostalgiapipe

import com.nostalgiapipe.config.Config
import com.nostalgiapipe.filter.KeyFrameSelector
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
import kotlin.io.path.copyTo
import java.io.File

class IntegrationTest {

    class MockVisionary : Visionary("fake_key") {
        override suspend fun analyzeVideo(proxyVideoPath: Path): VideoMetadata? {
            return VideoMetadata(
                scenes = listOf(
                    Scene(
                        title = "Scene One",
                        description = "First part",
                        start = "00:00:00",
                        end = "00:00:02",
                        year = "2023",
                        location = "Park",
                        people = listOf("Person A")
                    ),
                    Scene(
                        title = "Scene Two",
                        description = "Second part",
                        start = "00:00:03",
                        end = "00:00:05",
                        year = "2023",
                        location = "Home",
                        people = listOf("Person B")
                    )
                )
            )
        }
    }

    class MockKeyFrameSelector : KeyFrameSelector {
        override suspend fun selectKeyFrames(videoPath: Path): List<Double> {
            // Return fake timestamps (e.g., every 1 second)
            return listOf(0.0, 1.0, 2.0, 3.0, 4.0)
        }
    }

    @Test
    fun `full pipeline test with multiple scenes`() = runBlocking {
        val inputDir = createTempDirectory("input")
        val outputDir = createTempDirectory("output")

        // Use the static test file
        val resourcePath = Path.of("src/test/resources/VTS_01_1.VOB")
        assertTrue(resourcePath.exists(), "Test resource not found at $resourcePath")

        // Rename to .mp4 for the test context (transcoder uses it)
        val inputVideo = inputDir.resolve("test_video.mp4")
        resourcePath.copyTo(inputVideo)

        val config = Config(
            inputPath = inputDir.toAbsolutePath().toString(),
            outputPath = outputDir.toAbsolutePath().toString(),
            googleApiKey = "fake_key"
        )

        // Inject Mock Visionary and Mock KeyFrameSelector to bypass OpenCV issues
        val orchestrator = Orchestrator(config, MockVisionary(), MockKeyFrameSelector())

        // Run Submit Phase
        orchestrator.submit()

        // Verify sidecar exists
        val sidecar = inputVideo.resolveSibling("test_video.mp4.nostalgia_pipe.json")
        assertTrue(sidecar.exists(), "Sidecar file was not created. Expected at $sidecar")

        // Verify proxy exists
        val proxy = outputDir.resolve("proxy_test_video.mp4.mp4")
        assertTrue(proxy.exists(), "Proxy video was not created/kept. Expected at $proxy")

        // Run Finalize Phase
        orchestrator.finalize()

        // Verify Final Outputs
        val output1 = outputDir.resolve("2023_-_Scene_One.mp4")
        val output2 = outputDir.resolve("2023_-_Scene_Two.mp4")

        assertTrue(output1.exists(), "First scene output was not created. Expected at $output1")
        assertTrue(output2.exists(), "Second scene output was not created. Expected at $output2")
    }
}
