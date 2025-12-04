package com.nostalgiapipe.visionary

import com.nostalgiapipe.models.Scene
import com.nostalgiapipe.models.VideoMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class VisionaryTest {

    private val visionary = Visionary("fake_key")

    @Test
    fun `parseGeminiResponse parses valid markdown json`() {
        val json = """
            ```json
            {
              "scenes": [
                {
                  "title": "Opening Presents",
                  "description": "Family opening gifts.",
                  "start": "00:00:10",
                  "end": "00:00:45",
                  "year": "1998",
                  "location": "Living Room",
                  "people": ["Dad", "Mom"]
                }
              ]
            }
            ```
        """.trimIndent()

        val metadata = visionary.parseGeminiResponse(json)
        assertNotNull(metadata)
        assertEquals(1, metadata?.scenes?.size)
        assertEquals("Opening Presents", metadata?.scenes?.get(0)?.title)
    }

    @Test
    fun `parseGeminiResponse parses raw json without markdown`() {
        val json = """
            {
              "scenes": [
                {
                  "title": "Birthday Cake",
                  "description": "Cutting the cake.",
                  "start": "00:05:00",
                  "end": "00:05:30",
                  "year": "1998",
                  "location": "Kitchen",
                  "people": ["All"]
                }
              ]
            }
        """.trimIndent()

        val metadata = visionary.parseGeminiResponse(json)
        assertNotNull(metadata)
        assertEquals(1, metadata?.scenes?.size)
        assertEquals("Birthday Cake", metadata?.scenes?.get(0)?.title)
    }

    @Test
    fun `parseGeminiResponse returns null for invalid json`() {
        val json = """
            This is not JSON.
        """.trimIndent()

        val metadata = visionary.parseGeminiResponse(json)
        assertNull(metadata)
    }

    @Test
    fun `parseGeminiResponse handles extra text outside markdown`() {
        val json = """
            Here is the analysis:
            ```json
            {
              "scenes": [
                {
                  "title": "Scene 1",
                  "description": "Desc",
                  "start": "00:00:00",
                  "end": "00:00:10",
                  "year": "2000",
                  "location": "Outside",
                  "people": []
                }
              ]
            }
            ```
            Hope this helps!
        """.trimIndent()

        val metadata = visionary.parseGeminiResponse(json)
        assertNotNull(metadata)
        assertEquals(1, metadata?.scenes?.size)
    }
}
