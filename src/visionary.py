from google import genai
from google.genai import types
from pathlib import Path
import time
import json
from typing import Dict, Any, Optional

class Visionary:
    def __init__(self, api_key: str):
        self.api_key = api_key
        if api_key:
            self.client = genai.Client(api_key=api_key)
        else:
            self.client = None

    def upload_video(self, video_path: Path):
        """
        Uploads video to Gemini File API and waits for it to be ready.
        Returns the file object or None if failed.
        """
        if not self.client:
            print("No API key provided.")
            return None

        try:
            print(f"Uploading {video_path.name} to Gemini...")
            # Using client.files.upload
            video_file = self.client.files.upload(file=video_path)

            # Wait for processing to complete
            while video_file.state.name == "PROCESSING":
                time.sleep(5)
                video_file = self.client.files.get(name=video_file.name)

            if video_file.state.name == "FAILED":
                print(f"Video processing failed for {video_path.name}")
                return None

            print(f"Upload complete: {video_file.uri}")
            return video_file

        except Exception as e:
            print(f"Error uploading video {video_path}: {e}")
            return None

    def get_file(self, file_name: str):
        """Retrieves a file object from Gemini by name."""
        try:
            return self.client.files.get(name=file_name)
        except Exception as e:
            print(f"Error retrieving file {file_name}: {e}")
            return None

    def analyze_video(self, video_file) -> Dict[str, Any]:
        """
        Sends the uploaded video file to Gemini for scene analysis.
        Expects a JSON response.
        """
        if not self.client: return {}

        prompt = """
        Analyze this home movie video carefully.
        1. Identify the creation date/year from the content (clothing, technology, overlaid text) or context.
        2. Identify the general location (e.g. "Paris", "Backyard", "Disneyland").
        3. Split the video into distinct scenes based on events, activities, or significant visual changes.
           - Ensure scenes cover the entire duration if possible, or skip static/empty parts.
           - 'start_time' and 'end_time' must be in seconds (float).

        Return a JSON object with this exact structure:
        {
          "global_year": "YYYY or null",
          "global_location": "Location or null",
          "scenes": [
            {
              "start_time": 0.0,
              "end_time": 10.5,
              "title": "Short Descriptive Title",
              "description": "Detailed description of the event, action, and context.",
              "people": ["Person description or name if known"],
              "year": "YYYY (override global if specific)",
              "location": "Location (override global if specific)"
            }
          ]
        }
        """

        try:
            print(f"Analyzing {video_file.display_name}...")
            response = self.client.models.generate_content(
                model='gemini-1.5-flash',
                contents=[video_file, prompt],
                config=types.GenerateContentConfig(
                    response_mime_type="application/json"
                )
            )
            return json.loads(response.text)
        except Exception as e:
            print(f"Error calling Gemini for analysis: {e}")
            return {}
