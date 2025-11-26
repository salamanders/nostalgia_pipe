package com.nostalgiapipe.scanner

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.writeBytes
import kotlin.test.assertEquals

class ScannerTest {

    private lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        tempDir = Files.createTempDirectory("scanner-test-")
    }

    @AfterEach
    fun teardown() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `findVideoTsDirectories should find valid directories and ignore invalid ones`() = runBlocking {
        // 1. Valid VIDEO_TS directory
        val validDir = tempDir.resolve("movie1/VIDEO_TS")
        Files.createDirectories(validDir)
        validDir.resolve("VTS_01_1.VOB").createFile().writeBytes(ByteArray(2048))

        // 2. Empty VIDEO_TS directory
        val emptyDir = tempDir.resolve("movie2/VIDEO_TS")
        Files.createDirectories(emptyDir)

        // 3. VIDEO_TS directory with only small files
        val smallFileDir = tempDir.resolve("movie3/VIDEO_TS")
        Files.createDirectories(smallFileDir)
        smallFileDir.resolve("VTS_01_1.VOB").createFile().writeBytes(ByteArray(512))

        // 4. A directory not named VIDEO_TS
        val notVideoTsDir = tempDir.resolve("movie4/NOT_VIDEO_TS")
        Files.createDirectories(notVideoTsDir)
        notVideoTsDir.resolve("some_file.txt").createFile().writeBytes(ByteArray(2048))

        val result = Scanner.findVideoTsDirectories(tempDir).toList()

        assertEquals(1, result.size)
        assertEquals(validDir.toRealPath(), result[0].toRealPath())
    }
}
