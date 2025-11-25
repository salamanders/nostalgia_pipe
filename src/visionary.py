import google.generativeai as genai
from pathlib import Path
import time
import json
from typing import Dict, Any, Optional

class Visionary:
    def __init__(self, api_key: str):
        self.api_key = api_key
        if api_key:
            genai.configure(api_key=api_key)
            # Use 1.5 Flash for efficiency and large context
            self.model = genai.GenerativeModel(
                model_name='gemini-1.5-flash',
                generation_config={"response_mime_type": "application/json"}
            )
        else:
            self.model = None

    def upload_video(self, video_path: Path):
        """
        Uploads video to Gemini File API and waits for it to be ready.
        Returns the file object or None if failed.
        """
        if not self.model:
            print("No API key provided.")
            return None

        try:
            print(f"Uploading {video_path.name} to Gemini...")
            video_file = genai.upload_file(path=video_path)

            # Wait for processing to complete
            while video_file.state.name == "PROCESSING":
                time.sleep(5)
                video_file = genai.get_file(video_file.name)

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
            return genai.get_file(file_name)
        except Exception as e:
            print(f"Error retrieving file {file_name}: {e}")
            return None

    def analyze_video(self, video_file) -> Dict[str, Any]:
        """
        Sends the uploaded video file to Gemini for scene analysis.
        Expects a JSON response.
        """
        if not self.model: return {}

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
            response = self.model.generate_content(
                [video_file, prompt],
                request_options={"timeout": 600} # 10 minute timeout
            )
            return json.loads(response.text)
        except Exception as e:
            print(f"Error calling Gemini for analysis: {e}")
            return {}
