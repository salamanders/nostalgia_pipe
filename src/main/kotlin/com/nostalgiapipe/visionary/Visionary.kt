package com.nostalgiapipe.visionary

import com.google.genai.Client
import com.google.genai.types.*
import com.nostalgiapipe.models.VideoMetadata
import com.nostalgiapipe.utils.toNullable
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.serialization.json.Json
import java.nio.file.Path

open class Visionary(apiKey: String) {

    private val client = Client.builder().apiKey(apiKey).build()
    private val json = Json { ignoreUnknownKeys = true }

    open suspend fun analyzeVideo(proxyVideoPath: Path): VideoMetadata? {
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

        var batchJobName: String? = null

        try {
            // 2. Create the prompt using the file's URI
            val videoPart = Part.fromUri(fileName, "video/mp4")
            val promptPart = Part.fromText(prompt)
            val content = Content.fromParts(promptPart, videoPart)

            // 3. Configure Media Resolution
            val config = GenerateContentConfig.builder()
                .mediaResolution("MEDIA_RESOLUTION_LOW")
                .build()

            // 4. Batch Processing
            val modelId = "gemini-3-pro-preview"

            val inlinedRequest = InlinedRequest.builder()
                .model(modelId)
                .contents(listOf(content))
                .config(config)
                .build()

            val source = BatchJobSource.builder()
                .inlinedRequests(listOf(inlinedRequest))
                .build()

            val batchConfig = CreateBatchJobConfig.builder().build()

            println("Creating Batch Job with $modelId...")
            var job: BatchJob
            try {
                job = client.async.batches.create(modelId, source, batchConfig).await()
            } catch (e: Exception) {
                println("Error creating batch job: ${e.message}")
                e.printStackTrace()
                throw e
            }
            batchJobName = job.name().orElse(null)

            println("Batch job created: $batchJobName. Waiting for completion...")

            // Poll for completion
            while (true) {
                // Wait before polling (start with 10s)
                delay(10000)

                // get requires config, pass null
                job = client.async.batches.get(batchJobName, null).await()

                val jobState = job.state().orElse(null)
                val stateEnum = jobState?.knownEnum()

                if (stateEnum == JobState.Known.JOB_STATE_SUCCEEDED) {
                    println("Batch job succeeded.")
                    break
                } else if (stateEnum == JobState.Known.JOB_STATE_FAILED ||
                           stateEnum == JobState.Known.JOB_STATE_CANCELLED ||
                           stateEnum == JobState.Known.JOB_STATE_EXPIRED) {
                    val errorMsg = job.error().map { it.message().orElse("Unknown error") }.orElse("Unknown error")
                    println("Batch job failed with state $stateEnum: $errorMsg")
                    return null
                }
                println("Batch job status: $stateEnum")
            }

            // 5. Retrieve result
            // With inline requests, results are in inlinedResponses
            val responses = job.dest().flatMap { it.inlinedResponses() }.orElse(emptyList())
            if (responses.isEmpty()) {
                println("Error: Batch job succeeded but no responses found.")
                return null
            }

            val response = responses[0].response().orElse(null)
            if (response == null) {
                 println("Error: Response object is null.")
                 return null
            }

            val responseText = response.text()
            if (responseText.isNullOrBlank()) {
                println("Error: No text in Gemini response.")
                return null
            }

            return parseGeminiResponse(responseText)

        } finally {
            // 6. Clean up
            // Delete the video file
            // delete requires config, pass null
            try {
                client.async.files.delete(fileName, null)
            } catch (e: Exception) {
                println("Warning: Failed to delete file $fileName: ${e.message}")
            }
        }
    }

    fun parseGeminiResponse(responseText: String): VideoMetadata? {
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
    }
}
