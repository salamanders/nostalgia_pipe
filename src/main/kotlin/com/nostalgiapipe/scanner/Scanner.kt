@file:OptIn(kotlin.io.path.ExperimentalPathApi::class)

package com.nostalgiapipe.scanner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import java.nio.file.Path
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile
import kotlin.io.path.extension
import kotlin.io.path.walk

object Scanner {
    private val VALID_EXTENSIONS = setOf("vob", "mp4", "mov", "mkv", "avi")

    /**
     * Scans the given input path for valid video files.
     * Supported formats: VOB, MP4, MOV, MKV, AVI
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
     * Checks if a file is a valid video file (extension match and size > 1KB).
     */
    private fun isValidVideoFile(file: Path): Boolean {
        return file.isRegularFile() &&
               VALID_EXTENSIONS.contains(file.extension.lowercase()) &&
               file.fileSize() > 1024
    }
}
