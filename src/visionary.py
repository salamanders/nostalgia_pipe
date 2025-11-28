import ffmpeg
from google import genai
from google.genai import types
from pathlib import Path
import os
import time
from typing import List, Union

class Visionary:
    def __init__(self, api_key: str):
        self.api_key = api_key
        if api_key:
            self.client = genai.Client(
                api_key=api_key,
                http_options={
                    'api_version': 'v1alpha',
                }
            )
        else:
            self.client = None

    def get_description(self, video_proxy_path: Path) -> str:
        """
        Sends the video proxy to Gemini API to get a description.
        """
        if not self.client:
            # Mock response if no API key
            return "Unidentified Event"

        try:
            # Upload the file directly using the path to avoid memory issues
            upload_result = self.client.files.upload(
                file=video_proxy_path,
                config=types.UploadFileConfig(
                    mime_type="video/mp4",
                    display_name=video_proxy_path.name
                )
            )

            # Wait for processing to complete
            while upload_result.state == types.FileState.PROCESSING:
                time.sleep(1)
                upload_result = self.client.files.get(name=upload_result.name)

            if upload_result.state != types.FileState.ACTIVE:
                 raise Exception(f"File processing failed: {upload_result.state}")

            prompt = (
                "Analyze this video summary from a home movie. Provide a succinct, 3-5 word filename description "
                "of the event or action (e.g., 'Kids Opening Presents', 'Grandma Blowing Candles', 'Beach Volleyball'). "
                "Do not include file extensions."
            )

            # Configure Global Media Resolution
            config = types.GenerateContentConfig(
                media_resolution=types.MediaResolution.MEDIA_RESOLUTION_LOW
            )

            response = self.client.models.generate_content(
                model='gemini-3-pro-preview',
                contents=[
                    types.Content(
                        parts=[
                            types.Part.from_uri(
                                file_uri=upload_result.uri,
                                mime_type=upload_result.mime_type
                            ),
                            types.Part.from_text(text=prompt)
                        ]
                    )
                ],
                config=config
            )

            # Clean up the uploaded file from the API side
            try:
                self.client.files.delete(name=upload_result.name)
            except Exception as delete_error:
                print(f"Warning: Failed to delete file {upload_result.name}: {delete_error}")

            return response.text.strip()
        except Exception as e:
            print(f"Error getting description from Gemini: {e}")
            return "Unknown Event"
