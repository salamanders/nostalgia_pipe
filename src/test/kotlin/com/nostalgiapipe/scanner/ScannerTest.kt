package com.nostalgiapipe.scanner

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeBytes
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList

class ScannerTest {
    @Test
    fun `findVideoFiles finds valid video files`() = runBlocking {
        val tempDir = createTempDirectory("scanner-test")
        val videoDir = tempDir.resolve("videos")
        Files.createDirectories(videoDir)

        val validFiles = listOf("test.mp4", "test.vob", "test.mkv")
        val invalidFiles = listOf("ignore.txt", "tiny.mp4")

        validFiles.forEach { name ->
            val file = videoDir.resolve(name)
            file.writeBytes(ByteArray(2048)) // > 1KB
        }

        invalidFiles.forEach { name ->
            val file = videoDir.resolve(name)
            if (name == "tiny.mp4") {
                file.writeBytes(ByteArray(100)) // < 1KB
            } else {
                file.writeBytes(ByteArray(2048))
            }
        }

        val results = Scanner.findVideoFiles(tempDir).toList().map { it.fileName.toString() }

        assertTrue(results.containsAll(validFiles))
        assertFalse(results.any { invalidFiles.contains(it) })
    }
}
