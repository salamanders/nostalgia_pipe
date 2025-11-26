package com.nostalgiapipe.filter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.videoio.VideoCapture
import org.opencv.videoio.Videoio
import java.nio.file.Path

data class Frame(val timestamp: Double, val image: Mat)

object NostalgiaFilter {

    init {
        // Load the native OpenCV library
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME)
    }

    private const val LAPLACIAN_VARIANCE_THRESHOLD = 100.0
    private const val SSIM_THRESHOLD = 0.98
    private const val FRAME_SKIP = 5 // Process every 5th frame to speed up analysis

    suspend fun selectKeyFrames(videoPath: Path): List<Frame> = withContext(Dispatchers.IO) {
        val cap = VideoCapture(videoPath.toString())
        if (!cap.isOpened) {
            println("Error: Could not open video file: $videoPath")
            return@withContext emptyList()
        }

        val keyFrames = mutableListOf<Frame>()
        var previousFrame: Mat? = null
        var frameIndex = 0

        try {
            while (cap.isOpened) {
                val frame = Mat()
                if (!cap.read(frame)) {
                    break // End of video
                }

                if (frameIndex % FRAME_SKIP != 0) {
                    frameIndex++
                    frame.release()
                    continue
                }

                // 1. Blur Detection
                if (!isBlurry(frame)) {
                    // 2. Scene Change Detection
                    if (previousFrame == null || isSceneChange(previousFrame, frame)) {
                        val timestamp = cap.get(Videoio.CAP_PROP_POS_MSEC)
                        keyFrames.add(Frame(timestamp, frame.clone())) // Clone frame to store it
                        previousFrame?.release() // Release old previous frame
                        previousFrame = frame.clone() // Clone new frame to be the next previous frame
                    }
                }

                frame.release()
                frameIndex++
            }
        } finally {
            cap.release()
            previousFrame?.release()
        }

        // If no keyframes were found (e.g., short video), take the first non-blurry one
        if (keyFrames.isEmpty()) {
            findFirstGoodFrame(videoPath)?.let { keyFrames.add(it) }
        }

        return@withContext keyFrames
    }

    private fun findFirstGoodFrame(videoPath: Path): Frame? {
        val cap = VideoCapture(videoPath.toString())
        if (!cap.isOpened) return null

        try {
            while(cap.isOpened) {
                val frame = Mat()
                if (!cap.read(frame)) break
                if(!isBlurry(frame)) {
                    return Frame(cap.get(Videoio.CAP_PROP_POS_MSEC), frame.clone())
                }
                frame.release()
            }
        } finally {
            cap.release()
        }
        return null
    }

    private fun isBlurry(frame: Mat): Boolean {
        val gray = Mat()
        Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY)
        val laplacian = Mat()
        Imgproc.Laplacian(gray, laplacian, 3)
        val meanStdDev = MatOfDouble()
        Core.meanStdDev(laplacian, MatOfDouble(), meanStdDev)
        val variance = meanStdDev.toArray()[0] * meanStdDev.toArray()[0]

        gray.release()
        laplacian.release()
        meanStdDev.release()

        return variance < LAPLACIAN_VARIANCE_THRESHOLD
    }

    private fun isSceneChange(prev: Mat, curr: Mat): Boolean {
        val ssim = getSsim(prev, curr)
        return ssim < SSIM_THRESHOLD
    }

    private fun getSsim(img1: Mat, img2: Mat): Double {
        val C1 = 6.5025
        val C2 = 58.5225

        val i1 = Mat()
        val i2 = Mat()
        img1.convertTo(i1, 6) // CV_64F
        img2.convertTo(i2, 6) // CV_64F

        val i2_2 = i2.mul(i2)
        val i1_1 = i1.mul(i1)
        val i1_2 = i1.mul(i2)

        val mu1 = Mat()
        val mu2 = Mat()
        Imgproc.GaussianBlur(i1, mu1, Size(11.0, 11.0), 1.5)
        Imgproc.GaussianBlur(i2, mu2, Size(11.0, 11.0), 1.5)

        val mu1_1 = mu1.mul(mu1)
        val mu2_2 = mu2.mul(mu2)
        val mu1_2 = mu1.mul(mu2)

        val sigma1_2 = Mat()
        val sigma2_2 = Mat()
        val sigma12 = Mat()

        Imgproc.GaussianBlur(i1_1, sigma1_2, Size(11.0, 11.0), 1.5)
        Core.subtract(sigma1_2, mu1_1, sigma1_2)

        Imgproc.GaussianBlur(i2_2, sigma2_2, Size(11.0, 11.0), 1.5)
        Core.subtract(sigma2_2, mu2_2, sigma2_2)

        Imgproc.GaussianBlur(i1_2, sigma12, Size(11.0, 11.0), 1.5)
        Core.subtract(sigma12, mu1_2, sigma12)

        val t1 = Mat()
        val t2 = Mat()
        val t3 = Mat()
        Core.multiply(mu1_2, Scalar.all(2.0), t3)
        Core.add(t3, Scalar.all(C1), t1)

        val t4 = Mat()
        Core.multiply(sigma12, Scalar.all(2.0), t4)
        Core.add(t4, Scalar.all(C2), t2)

        val t5 = t1.mul(t2)

        Core.add(mu1_1, mu2_2, t1)
        Core.add(t1, Scalar(C1), t1)

        Core.add(sigma1_2, sigma2_2, t2)
        Core.add(t2, Scalar(C2), t2)

        val t6 = t1.mul(t2)

        val ssim_map = Mat()
        Core.divide(t5, t6, ssim_map)

        val mssim = Core.mean(ssim_map)

        // Release all the mats
        i1.release()
        i2.release()
        i2_2.release()
        i1_1.release()
        i1_2.release()
        mu1.release()
        mu2.release()
        mu1_1.release()
        mu2_2.release()
        mu1_2.release()
        sigma1_2.release()
        sigma2_2.release()
        sigma12.release()
        t1.release()
        t2.release()
        t3.release()
        t4.release()
        t5.release()
        t6.release()
        ssim_map.release()

        return mssim.`val`[0]
    }
}
