package com.nostalgiapipe.visionary

import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.File
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import com.google.genai.types.UploadFileConfig
import com.nostalgiapipe.models.VideoMetadata
import kotlinx.coroutines.future.await
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import java.nio.file.Path

open class Visionary(apiKey: String) {

    private val client = Client.builder().apiKey(apiKey).build()
    private val json = Json { ignoreUnknownKeys = true }

    open suspend fun uploadVideo(videoPath: Path): File? {
        try {
            // Upload
            val uploadConfig = UploadFileConfig.builder().mimeType("video/mp4").build()
            val uploadedFile = client.async.files.upload(videoPath.toString(), uploadConfig).await()

            // Wait for processing
            var file = uploadedFile
            var state = file.state().toString() // Use toString() for safety
            while (state == "PROCESSING") {
                delay(5000)
                file = client.async.files.get(file.name().get(), null).await()
                state = file.state().toString()
            }

            if (state == "FAILED") {
                println("Video processing failed: ${file.error().get().message()}")
                return null
            }

            return file
        } catch (e: Exception) {
            println("Error uploading video: ${e.message}")
            return null
        }
    }

    open suspend fun getFile(name: String): File? {
        return try {
            client.async.files.get(name, null).await()
        } catch (e: Exception) {
            println("Error fetching file $name: ${e.message}")
            null
        }
    }

    open suspend fun analyzeVideo(videoFile: File): VideoMetadata? {
        val prompt = """
            Analyze this home movie video carefully.
            1. Identify the creation date/year from the content (clothing, technology, overlaid text) or context.
            2. Identify the general location (e.g. "Paris", "Backyard", "Disneyland").
            3. Split the video into distinct scenes based on events, activities, or significant visual changes.
               - Ensure scenes cover the entire duration if possible, or skip static/empty parts.
               - 'start_time' and 'end_time' must be in seconds (float).

            Return a JSON object with this exact structure:
            {
              "global_year": "YYYY or null",
              "global_location": "Location or null",
              "scenes": [
                {
                  "start_time": 0.0,
                  "end_time": 10.5,
                  "title": "Short Descriptive Title",
                  "description": "Detailed description of the event, action, and context.",
                  "people": ["Person description or name if known"],
                  "year": "YYYY (override global if specific)",
                  "location": "Location (override global if specific)"
                }
              ]
            }
        """.trimIndent()

        try {
            val fileName = videoFile.name().get()
            val videoPart = Part.fromUri(fileName, "video/mp4")
            val promptPart = Part.fromText(prompt)
            val content = Content.fromParts(promptPart, videoPart)

            val config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .build()

            // Use correct model: gemini-1.5-flash
            val response = client.async.models.generateContent("gemini-1.5-flash", content, config).await()

            val responseText = response.text()
            if (responseText.isNullOrBlank()) {
                println("Error: No text in Gemini response.")
                return null
            }

            // Clean markdown if present
            val cleanJson = if (responseText.contains("```json")) {
                 responseText.substringAfter("```json").substringBeforeLast("```").trim()
            } else if (responseText.contains("```")) {
                 responseText.substringAfter("```").substringBeforeLast("```").trim()
            } else {
                 responseText.trim()
            }

            return json.decodeFromString<VideoMetadata>(cleanJson)
        } catch (e: Exception) {
            println("Error analyzing video: ${e.message}")
            return null
        }
    }
}
