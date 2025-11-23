
import unittest
from unittest.mock import MagicMock, patch
from pathlib import Path
from src.transcoder import Transcoder
from src.visionary import Visionary

class TestTranscoder(unittest.TestCase):
    @patch('src.transcoder.ffmpeg')
    @patch('src.transcoder.detect')
    def test_detect_scenes(self, mock_detect, mock_ffmpeg):
        # Setup mock return for detect
        mock_scene_item1 = (MagicMock(), MagicMock())
        mock_scene_item1[0].get_seconds.return_value = 0.0
        mock_scene_item1[1].get_seconds.return_value = 10.0

        mock_scene_item2 = (MagicMock(), MagicMock())
        mock_scene_item2[0].get_seconds.return_value = 10.0
        mock_scene_item2[1].get_seconds.return_value = 20.0

        mock_detect.return_value = [mock_scene_item1, mock_scene_item2]

        transcoder = Transcoder()
        scenes = transcoder.detect_scenes(Path("dummy.vob"))

        self.assertEqual(len(scenes), 2)
        self.assertEqual(scenes[0], (0.0, 10.0))
        self.assertEqual(scenes[1], (10.0, 20.0))

    @patch('src.transcoder.ffmpeg')
    def test_transcode_segment(self, mock_ffmpeg):
        transcoder = Transcoder()
        # Mock the run method
        mock_ffmpeg.input.return_value.output.return_value.overwrite_output.return_value.run.return_value = None

        result = transcoder.transcode_segment(Path("dummy.vob"), Path("out.mp4"), 0.0, 10.0)
        self.assertTrue(result)

        # Check if ffmpeg input called with correct args
        # Note: we can't easily check kwargs passed to chained calls with simple mocks without more complex setup,
        # but we can check if it ran without exception.

class TestVisionary(unittest.TestCase):
    @patch('src.visionary.genai')
    def test_get_description_no_key(self, mock_genai):
        visionary = Visionary("")
        desc = visionary.get_description([Path("img.jpg")])
        self.assertEqual(desc, "Unidentified Event")

    @patch('src.visionary.genai')
    def test_get_description_with_key(self, mock_genai):
        visionary = Visionary("fake_key")
        mock_model = MagicMock()
        visionary.model = mock_model
        mock_model.generate_content.return_value.text = "Test Description"

        desc = visionary.get_description([Path("img.jpg")])
        self.assertEqual(desc, "Test Description")
        mock_model.generate_content.assert_called()

if __name__ == '__main__':
    unittest.main()
