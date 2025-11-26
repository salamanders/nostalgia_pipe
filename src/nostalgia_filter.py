import cv2
import numpy as np
from skimage.metrics import structural_similarity as ssim

class NostalgiaFilter:
    def __init__(self, blur_threshold=100.0, ssim_threshold=0.9):
        self.blur_threshold = blur_threshold
        self.ssim_threshold = ssim_threshold

    def is_blurry(self, image: np.ndarray) -> bool:
        """
        Checks if an image is blurry using the variance of the Laplacian.
        """
        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        laplacian_var = cv2.Laplacian(gray, cv2.CV_64F).var()
        return laplacian_var < self.blur_threshold

    def are_similar(self, imageA: np.ndarray, imageB: np.ndarray) -> bool:
        """
        Checks if two images are structurally similar.
        """
        grayA = cv2.cvtColor(imageA, cv2.COLOR_BGR2GRAY)
        grayB = cv2.cvtColor(imageB, cv2.COLOR_BGR2GRAY)
        (score, _) = ssim(grayA, grayB, full=True)
        return score > self.ssim_threshold

    def select_best_frames(self, video_path: str, start_time: float, end_time: float) -> list[float]:
        """
        Analyzes a video scene and returns timestamps of the best frames.
        """
        cap = cv2.VideoCapture(video_path)
        if not cap.isOpened():
            print(f"Error opening video file: {video_path}")
            return []

        cap.set(cv2.CAP_PROP_POS_MSEC, start_time * 1000)

        best_frames_timestamps = []
        last_best_frame = None

        while True:
            ret, frame = cap.read()
            if not ret:
                break

            # Get the current timestamp in seconds
            current_msec = cap.get(cv2.CAP_PROP_POS_MSEC)
            timestamp = current_msec / 1000.0

            if timestamp > end_time:
                break

            if self.is_blurry(frame):
                continue

            # Resize frame for faster SSIM comparison
            small_frame = cv2.resize(frame, (0, 0), fx=0.5, fy=0.5)

            if last_best_frame is None or not self.are_similar(small_frame, last_best_frame):
                best_frames_timestamps.append(timestamp)
                last_best_frame = small_frame

        cap.release()
        return best_frames_timestamps
