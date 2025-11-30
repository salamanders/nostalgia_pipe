package com.nostalgiapipe.filter

import java.nio.file.Path

interface KeyFrameSelector {
    suspend fun selectKeyFrames(videoPath: Path): List<Double>
}
