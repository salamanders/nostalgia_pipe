import unittest
import tempfile
import shutil
import os
from pathlib import Path
from src.scanner import Scanner

class TestScannerRealFS(unittest.TestCase):
    def setUp(self):
        self.test_dir = tempfile.mkdtemp()
        self.video_ts_dir = Path(self.test_dir) / "MyDVD" / "VIDEO_TS"
        self.video_ts_dir.mkdir(parents=True)

    def tearDown(self):
        shutil.rmtree(self.test_dir)

    def test_scan_includes_small_but_valid_files(self):
        # Create a valid VOB file that is small (e.g. 5KB) but larger than 1KB
        valid_vob = self.video_ts_dir / "VTS_01_1.VOB"
        with open(valid_vob, "wb") as f:
            f.write(b"0" * 5000) # 5KB

        # Create a tiny file (<= 1KB) that should be ignored
        tiny_vob = self.video_ts_dir / "VTS_01_0.VOB"
        with open(tiny_vob, "wb") as f:
            f.write(b"0" * 500) # 500 bytes

        # Create a normal large file
        large_vob = self.video_ts_dir / "VTS_01_2.VOB"
        with open(large_vob, "wb") as f:
            f.seek(1024 * 1024) # 1MB
            f.write(b"0")

        scanner = Scanner(self.test_dir)
        results = scanner.scan()

        # We expect VTS_01_1.VOB (5KB) and VTS_01_2.VOB (1MB) to be found
        # VTS_01_0.VOB (500B) should be ignored

        filenames = [r["filename"] for r in results]
        self.assertIn("VTS_01_1.VOB", filenames)
        self.assertIn("VTS_01_2.VOB", filenames)
        self.assertNotIn("VTS_01_0.VOB", filenames)

    def test_scan_ignores_non_vob(self):
        other_file = self.video_ts_dir / "VIDEO_TS.IFO"
        with open(other_file, "wb") as f:
            f.write(b"0" * 5000)

        scanner = Scanner(self.test_dir)
        results = scanner.scan()

        filenames = [r["filename"] for r in results]
        self.assertNotIn("VIDEO_TS.IFO", filenames)

if __name__ == '__main__':
    unittest.main()
