package com.nostalgiapipe.visionary

import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.MediaResolution
import com.google.genai.types.Part
import com.google.genai.types.UploadFileConfig
import com.nostalgiapipe.models.VideoMetadata
import com.nostalgiapipe.utils.toNullable
import kotlinx.coroutines.future.await
import kotlinx.serialization.json.Json
import java.nio.file.Path

class Visionary(apiKey: String) {

    private val client = Client.builder().apiKey(apiKey).build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun analyzeVideo(proxyVideoPath: Path): VideoMetadata? {
        val prompt = """
            Analyze this video of a home movie and provide a structured JSON output. The video contains a series of keyframes from a single event. Your task is to identify the main event, break it down into distinct scenes, and provide the following details for each scene:

            - Title: A short, descriptive title for the scene (e.g., "Opening Presents," "Singing Happy Birthday").
            - Description: A one-sentence summary of what happens in the scene.
            - Year: The estimated year the video was filmed.
            - Location: The estimated location (e.g., "Living Room," "Backyard").
            - People: A list of people identified in the scene.

            The final output must be a single JSON object with a "scenes" key, which contains a list of these scene objects. Do not include any text or formatting outside of the JSON object itself.
        """.trimIndent()

        // 1. Upload the file to the Files API
        val uploadConfig = UploadFileConfig.builder().mimeType("video/mp4").build()
        val uploadedFileFuture = client.async.files.upload(proxyVideoPath.toString(), uploadConfig)
        val uploadedFile = uploadedFileFuture.await()

        val fileName = uploadedFile.name().toNullable()
        if (fileName == null) {
            println("Error: Uploaded file has no name.")
            return null
        }

        try {
            // 2. Create the prompt using the file's URI
            val videoPart = Part.fromUri(fileName, "video/mp4")
            val promptPart = Part.fromText(prompt)
            val content = Content.fromParts(promptPart, videoPart)

            // 3. Configure Media Resolution
            val config = GenerateContentConfig.builder()
                .mediaResolution("MEDIA_RESOLUTION_LOW")
                .build()

            // 4. Generate the content
            val responseFuture = client.async.models.generateContent("gemini-3-pro-preview", content, config)
            val response = responseFuture.await()

            val responseText = response.text()
            if (responseText.isNullOrBlank()) {
                println("Error: No text in Gemini response.")
                return null
            }

            val cleanedJson = responseText.substringAfter("```json").substringBeforeLast("```").trim()
            return try {
                json.decodeFromString<VideoMetadata>(cleanedJson)
            } catch (e: Exception) {
                // If clean up failed, try raw text just in case it wasn't wrapped in markdown block
                try {
                    json.decodeFromString<VideoMetadata>(responseText.trim())
                } catch (e2: Exception) {
                    println("Error decoding JSON from Visionary API: ${e.message}")
                    null
                }
            }
        } finally {
            // 5. Clean up the uploaded file
            client.async.files.delete(fileName, null)
        }
    }
}
