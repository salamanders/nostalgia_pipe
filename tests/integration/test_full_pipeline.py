
import pytest
import shutil
import os
from pathlib import Path
from src.orchestrator import Orchestrator

class MockVisionary:
    def __init__(self, api_key):
        pass

    def get_description(self, video_path):
        return "A beautiful sunny day in the park"

def test_full_pipeline(tmp_path):
    # Setup directories
    input_dir = tmp_path / "input"
    output_dir = tmp_path / "output"
    input_dir.mkdir()
    output_dir.mkdir()

    # Copy generated test file
    demo_file = Path("tests/demo/generated_test.mp4")
    if not demo_file.exists():
        pytest.skip("Generated test file not found")

    # Copy and rename to look like our target
    shutil.copy(demo_file, input_dir / "test_video.mp4")

    # Set ENV vars
    os.environ["INPUT_PATH"] = str(input_dir)
    os.environ["OUTPUT_PATH"] = str(output_dir)
    os.environ["GOOGLE_API_KEY"] = "fake_key"

    # Initialize Orchestrator with Mock Visionary
    app = Orchestrator(visionary=MockVisionary("fake"))
    app.run()

    # Assertions
    # Check if output file exists
    outputs = list(output_dir.glob("*.mp4"))

    print(f"Generated files: {[f.name for f in outputs]}")

    # Check for expected pattern.
    # We expect "input - Scene 001 - A beautiful sunny day in the park.mp4"
    # But wait, scene detection might return 1 scene, or 0?
    # Our generated video is 5 seconds. The threshold in Orchestrator is 1.0 second.
    # It should detect at least one scene (the whole video).

    # Verify we have at least one output video (the final transcode)
    # And maybe one proxy if kept.

    final_videos = [f for f in outputs if "proxy" not in f.name]
    assert len(final_videos) >= 1

    # Check if proxy exists (we requested it be kept)
    proxies = list(output_dir.glob("temp_proxy_*.mp4"))
    assert len(proxies) >= 1
