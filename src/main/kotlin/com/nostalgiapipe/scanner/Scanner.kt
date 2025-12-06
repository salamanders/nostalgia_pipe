@file:OptIn(kotlin.io.path.ExperimentalPathApi::class)

package com.nostalgiapipe.scanner

import com.nostalgiapipe.utils.CommandRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import java.nio.file.Path
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString
import kotlin.io.path.walk

object Scanner {

    /**
     * Scans the given input path for valid video files.
     * Uses ffprobe to detect video streams, supporting any valid video format.
     *
     * @param inputPath The root directory to start the scan from.
     * @return A Flow that emits the Path of each valid video file found.
     */
    fun findVideoFiles(inputPath: Path): Flow<Path> {
        return inputPath.walk()
            .asFlow()
            .filter { isValidVideoFile(it) }
            .flowOn(Dispatchers.IO)
    }

    /**
     * Checks if a file is a valid video file using ffprobe.
     * It must be a regular file, > 1KB, and contain a video stream.
     */
    private suspend fun isValidVideoFile(file: Path): Boolean {
        if (!file.isRegularFile() || file.fileSize() <= 1024) return false

        // ffprobe command to check for video stream
        val command = listOf(
            "ffprobe",
            "-v", "error",
            "-select_streams", "v:0",
            "-show_entries", "stream=codec_type",
            "-of", "default=noprint_wrappers=1:nokey=1",
            file.pathString
        )

        return try {
            val output = CommandRunner.runCommandAndGetOutput(command, "ffprobe check")
            output.trim() == "video"
        } catch (e: Exception) {
            false
        }
    }
}
