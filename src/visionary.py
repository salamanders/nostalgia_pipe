
import ffmpeg
import google.generativeai as genai
from pathlib import Path
import os
import time

class Visionary:
    def __init__(self, api_key: str):
        self.api_key = api_key
        if api_key:
            genai.configure(api_key=api_key)
            self.model = genai.GenerativeModel('gemini-1.5-flash')
        else:
            self.model = None

    def extract_frame(self, video_path: Path, output_image_path: Path):
        """
        Extracts a frame from 50% of the video duration.
        """
        try:
            # Get video duration
            probe = ffmpeg.probe(str(video_path))
            video_info = next(s for s in probe['streams'] if s['codec_type'] == 'video')
            duration = float(video_info['duration'])

            timestamp = duration / 2

            (
                ffmpeg
                .input(str(video_path), ss=timestamp)
                .filter('scale', -1, 720) # reasonable resolution
                .output(str(output_image_path), vframes=1)
                .overwrite_output()
                .run(quiet=True)
            )
            return True
        except Exception as e:
            print(f"Error extracting frame from {video_path}: {e}")
            return False

    def get_description(self, image_path: Path) -> str:
        """
        Sends the image to Gemini API to get a description.
        """
        if not self.model:
            # Mock response if no API key
            return "Unidentified Event"

        try:
            # Upload the file
            # But we can just pass the path or image data depending on the library version.
            # Using File API or inline data.
            # Let's try uploading to File API as it's cleaner for large images, or PIL image.

            # For simplicity, let's use the file API if available or just pass the image path if supported.
            # The standard way now is often:
            myfile = genai.upload_file(image_path)

            prompt = (
                "Analyze this image from a home movie. Provide a succinct, 3-5 word filename description "
                "of the event or action (e.g., 'Kids Opening Presents', 'Grandma Blowing Candles', 'Beach Volleyball'). "
                "Do not include file extensions."
            )

            result = self.model.generate_content([myfile, prompt])
            return result.text.strip()
        except Exception as e:
            print(f"Error getting description from Gemini: {e}")
            return "Unknown Event"
