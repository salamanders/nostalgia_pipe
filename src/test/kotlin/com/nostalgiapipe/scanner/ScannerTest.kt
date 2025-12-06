package com.nostalgiapipe.scanner

import com.nostalgiapipe.utils.CommandRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.pathString
import kotlin.io.path.writeBytes

class ScannerTest {
    @Test
    fun `findVideoFiles finds valid video files`() = runBlocking {
        val tempDir = createTempDirectory("scanner-test")
        val videoDir = tempDir.resolve("videos")
        Files.createDirectories(videoDir)

        val validFiles = listOf("test.mp4", "test.vob", "test.mkv")
        val invalidFiles = listOf("ignore.txt", "tiny.mp4")

        // Helper to create a valid video file using ffmpeg
        suspend fun createValidVideo(fileName: String) {
            val file = videoDir.resolve(fileName)
            // Generate 1 second of black video
            val command = listOf(
                "ffmpeg",
                "-y",
                "-f", "lavfi",
                "-i", "color=c=black:s=640x480:d=1",
                "-c:v", "libx264",
                "-t", "1",
                file.pathString
            )
            CommandRunner.runCommandSilent(command)
        }

        // Create valid files
        validFiles.forEach { name ->
            createValidVideo(name)
        }

        // Create invalid files
        invalidFiles.forEach { name ->
            val file = videoDir.resolve(name)
            if (name == "tiny.mp4") {
                file.writeBytes(ByteArray(100)) // < 1KB
            } else {
                file.writeBytes(ByteArray(2048)) // Large enough but not a video (random bytes)
            }
        }

        // Run scanner
        val flow = Scanner.findVideoFiles(tempDir)
        val results = mutableListOf<String>()
        flow.collect { results.add(it.fileName.toString()) }

        assertTrue(results.containsAll(validFiles), "Scanner should find all valid video files")
        assertFalse(results.any { invalidFiles.contains(it) }, "Scanner should not find invalid or fake files")
    }
}
