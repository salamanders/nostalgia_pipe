
import os
from pathlib import Path
from typing import List, Dict

class Scanner:
    def __init__(self, input_path: str):
        self.input_path = Path(input_path)

    def scan(self) -> List[Dict]:
        """
        Recursively scan the input path for VIDEO_TS folders and return a list of VOB files
        that meet the criteria (>100MB).
        """
        vob_files = []

        if not self.input_path.exists():
            print(f"Input path {self.input_path} does not exist.")
            return []

        for root, dirs, files in os.walk(self.input_path):
            if "VIDEO_TS" in root:
                # We are inside a VIDEO_TS folder (or subdirectory of it)
                # According to specs, we look for folders containing VIDEO_TS subdirectories.
                # "Identify 'DVD structure' folders (folders containing a VIDEO_TS subdirectory)"
                # Then "Inside VIDEO_TS, identify valid content files (.VOB)"
                pass

        # Let's re-read the requirement:
        # 1. Recursively scan a user-provided INPUT_DIR.
        # 2. Identify "DVD structure" folders (folders containing a VIDEO_TS subdirectory).
        # 3. Inside VIDEO_TS, identify valid content files (.VOB).

        # So if I walk, when I see a folder that has a VIDEO_TS subdir, I should look inside that VIDEO_TS subdir.

        for root, dirs, files in os.walk(self.input_path):
            if "VIDEO_TS" in dirs:
                video_ts_path = Path(root) / "VIDEO_TS"
                context = Path(root).name

                # Scan files in VIDEO_TS
                for video_file in video_ts_path.glob("*.VOB"):
                    # Heuristic: > 100MB
                    if video_file.stat().st_size > 100 * 1024 * 1024: # 100MB
                        vob_files.append({
                            "path": video_file,
                            "context": context,
                            "filename": video_file.name
                        })

        return vob_files
