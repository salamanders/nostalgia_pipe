package com.nostalgiapipe.models

import kotlinx.serialization.Serializable

@Serializable
data class Scene(
    val start: String,
    val end: String,
    val title: String,
    val description: String,
    val year: String, // Changed to String to accommodate "2023" or similar from AI
    val location: String,
    val people: List<String>
)

@Serializable
data class VideoMetadata(
    val scenes: List<Scene>
)
