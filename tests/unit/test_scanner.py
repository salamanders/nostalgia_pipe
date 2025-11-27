
import pytest
from pathlib import Path
from src.scanner import Scanner

def test_scanner_finds_video_files(tmp_path):
    # Setup
    video_dir = tmp_path / "videos"
    video_dir.mkdir()

    (video_dir / "test.mp4").touch()
    (video_dir / "test.vob").touch()
    (video_dir / "ignore.txt").touch()
    (video_dir / "tiny.mp4").touch() # 0 bytes

    # Write some data to make them > 1KB
    with open(video_dir / "test.mp4", "wb") as f:
        f.write(b'\0' * 2048)
    with open(video_dir / "test.vob", "wb") as f:
        f.write(b'\0' * 2048)

    scanner = Scanner(str(video_dir))
    results = scanner.scan()

    paths = [r["path"].name for r in results]
    assert "test.mp4" in paths
    assert "test.vob" in paths
    assert "ignore.txt" not in paths
    assert "tiny.mp4" not in paths
