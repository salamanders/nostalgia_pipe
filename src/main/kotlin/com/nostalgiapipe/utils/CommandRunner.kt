package com.nostalgiapipe.utils

import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CommandRunner {

    suspend fun runCommand(command: List<String>, processName: String) = withContext(Dispatchers.IO) {
        println("Running $processName: ${command.joinToString(" ")}")
        try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            process.inputStream.bufferedReader().use { reader ->
                reader.lines().forEach { println("$processName: $it") }
            }

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw RuntimeException("Error: $processName exited with code $exitCode")
            } else {
                println("Success: $processName completed.")
            }
        } catch (e: Exception) {
            println("Exception while running $processName: ${e.message}")
            throw e
        }
    }

    suspend fun runCommandAndGetOutput(command: List<String>, processName: String): String = withContext(Dispatchers.IO) {
        // println("Running $processName (capture output): ${command.joinToString(" ")}")
        try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val output = StringBuilder()
            process.inputStream.bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
            }

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                // We don't throw immediately, as sometimes we need to parse the error output
                // But for general utility, maybe we should?
                // For ffprobe/idet, a non-zero exit might mean "not a video" or "failed".
                // Let's return output and let caller decide, or throw?
                // Given the usage, if ffprobe fails, it's not a valid video.
                // But we return output.
            }
            return@withContext output.toString()
        } catch (e: Exception) {
            println("Exception while running $processName: ${e.message}")
            throw e
        }
    }

    // Helper to run without logging everywhere if needed
     suspend fun runCommandSilent(command: List<String>): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

             // Consume stream to prevent deadlock
             process.inputStream.bufferedReader().use { it.readText() }

            val exitCode = process.waitFor()
            return@withContext exitCode == 0
        } catch (e: Exception) {
            return@withContext false
        }
    }
}
