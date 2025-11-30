package com.nostalgiapipe.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class Scene(
    @SerialName("start_time") val start_time: Double,
    @SerialName("end_time") val end_time: Double,
    val title: String,
    val description: String,
    val year: String? = null,
    val location: String? = null,
    val people: List<String>? = null
)

@Serializable
data class VideoMetadata(
    val global_year: String? = null,
    val global_location: String? = null,
    val scenes: List<Scene>
)
