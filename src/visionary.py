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

    def get_description(self, video_proxy_path: Path) -> str:
        """
        Sends the video proxy to Gemini API to get a description.
        """
        if not self.model:
            # Mock response if no API key
            return "Unidentified Event"

        try:
            proxy_file = genai.upload_file(path=video_proxy_path)

            prompt = (
                "Analyze this video summary from a home movie. Provide a succinct, 3-5 word filename description "
                "of the event or action (e.g., 'Kids Opening Presents', 'Grandma Blowing Candles', 'Beach Volleyball'). "
                "Do not include file extensions."
            )

            result = self.model.generate_content([proxy_file, prompt])

            # Clean up the uploaded file from the API side
            genai.delete_file(proxy_file.name)

            return result.text.strip()
        except Exception as e:
            print(f"Error getting description from Gemini: {e}")
            return "Unknown Event"
