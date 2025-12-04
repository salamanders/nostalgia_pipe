package com.nostalgiapipe.config

import io.github.cdimascio.dotenv.dotenv

data class Config(
    val inputPath: String,
    val outputPath: String,
    val googleApiKey: String
) {
    companion object {
        fun load(): Config {
            val dotenv = dotenv()
            return Config(
                inputPath = dotenv["INPUT_PATH"] ?: throw IllegalStateException("INPUT_PATH not set in .env file"),
                outputPath = dotenv["OUTPUT_PATH"] ?: throw IllegalStateException("OUTPUT_PATH not set in .env file"),
                googleApiKey = dotenv["GOOGLE_API_KEY"] ?: throw IllegalStateException("GOOGLE_API_KEY not set in .env file")
            )
        }
    }
}
