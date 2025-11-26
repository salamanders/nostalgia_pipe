@file:OptIn(kotlin.io.path.ExperimentalPathApi::class)

package com.nostalgiapipe.scanner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.walk

object Scanner {
    /**
     * Scans the given input path for VIDEO_TS directories that are not empty.
     *
     * @param inputPath The root directory to start the scan from.
     * @return A Flow that emits the Path of each valid VIDEO_TS directory found.
     */
    fun findVideoTsDirectories(inputPath: Path): Flow<Path> {
        return inputPath.walk()
            .asFlow()
            .filter { it.isDirectory() && it.name.equals("VIDEO_TS", ignoreCase = true) }
            .filter { containsVideoFiles(it) }
            .flowOn(Dispatchers.IO) // Perform file operations on the IO dispatcher
    }

    /**
     * Checks if a directory contains at least one file larger than 1KB.
     * This is to avoid processing empty or artifact VIDEO_TS folders.
     */
    private fun containsVideoFiles(directory: Path): Boolean {
        // Reverting to walk() as it's more robust for this check.
        return directory.walk().any { it.isRegularFile() && it.fileSize() > 1024 }
    }
}
