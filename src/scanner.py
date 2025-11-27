
import os
from pathlib import Path
from typing import List, Dict

class Scanner:
    def __init__(self, input_path: str):
        self.input_path = Path(input_path)

    def scan(self) -> List[Dict]:
        """
        Recursively scan the input path for video files.
        Supported formats: .VOB, .mp4, .mov, .mkv, .avi
        Ignores tiny files (<= 1KB) to avoid artifacts.
        """
        video_files = []
        valid_extensions = {'.vob', '.mp4', '.mov', '.mkv', '.avi'}

        if not self.input_path.exists():
            print(f"Input path {self.input_path} does not exist.")
            return []

        for root, dirs, files in os.walk(self.input_path):
            for file in files:
                file_path = Path(root) / file
                if file_path.suffix.lower() in valid_extensions:
                    # Heuristic: Ignore tiny files/artifacts (<= 1KB)
                    if file_path.stat().st_size > 1024:
                        context = Path(root).name
                        video_files.append({
                            "path": file_path,
                            "context": context,
                            "filename": file_path.name
                        })

        return video_files
