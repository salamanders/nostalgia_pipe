import ffmpeg
import google.generativeai as genai
from pathlib import Path
import os
import time
from typing import List, Union

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

    def extract_frames(self, video_path: Path, timestamps: List[float], output_paths: List[Path]):
        """
        Extracts frames at specified timestamps.
        """
        success = True
        for ts, out_path in zip(timestamps, output_paths):
            try:
                (
                    ffmpeg
                    .input(str(video_path), ss=ts)
                    .filter('scale', -1, 720)
                    .output(str(out_path), vframes=1)
                    .overwrite_output()
                    .run(quiet=True)
                )
            except Exception as e:
                print(f"Error extracting frame at {ts} from {video_path}: {e}")
                success = False
        return success

    def get_description(self, image_input: Union[Path, List[Path]]) -> str:
        """
        Sends the image(s) to Gemini API to get a description.
        """
        if not self.model:
            # Mock response if no API key
            return "Unidentified Event"

        try:
            content = []
            images_to_delete = []

            if isinstance(image_input, list):
                for img_path in image_input:
                    myfile = genai.upload_file(img_path)
                    content.append(myfile)
                    # We shouldn't delete them here immediately because the API call needs them,
                    # but typically upload_file returns a handle that is valid.
            else:
                myfile = genai.upload_file(image_input)
                content.append(myfile)

            prompt = (
                "Analyze these images from a scene in a home movie. Provide a succinct, 3-5 word filename description "
                "of the event or action (e.g., 'Kids Opening Presents', 'Grandma Blowing Candles', 'Beach Volleyball'). "
                "Do not include file extensions."
            )

            content.append(prompt)

            result = self.model.generate_content(content)
            return result.text.strip()
        except Exception as e:
            print(f"Error getting description from Gemini: {e}")
            return "Unknown Event"
