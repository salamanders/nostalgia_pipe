import unittest
from unittest.mock import MagicMock, patch
from pathlib import Path
from src.transcoder import Transcoder
from src.visionary import Visionary

class TestTranscoder(unittest.TestCase):
    @patch('src.transcoder.ffmpeg')
    def test_create_proxy(self, mock_ffmpeg):
        transcoder = Transcoder()
        mock_ffmpeg.input.return_value.output.return_value.overwrite_output.return_value.run.return_value = None

        result = transcoder.create_proxy(Path("dummy.vob"), Path("proxy.mp4"))
        self.assertTrue(result)

    @patch('src.transcoder.subprocess.run')
    @patch('src.transcoder.ffmpeg')
    def test_transcode_segment(self, mock_ffmpeg, mock_subprocess):
        transcoder = Transcoder()

        # Mock ffmpeg.probe
        mock_ffmpeg.probe.return_value = {'streams': [{'codec_type': 'audio', 'codec_name': 'ac3'}]}

        # Mock ffmpeg.compile
        # It needs to return a list of args, with the output path at the end
        mock_ffmpeg.compile.return_value = ['ffmpeg', '-i', 'in', 'out.mp4']

        # Mock input().output() chain
        # input() returns stream, stream.output() returns output_node
        stream_mock = MagicMock()
        mock_ffmpeg.input.return_value = stream_mock
        output_node_mock = MagicMock()
        stream_mock.output.return_value = output_node_mock

        result = transcoder.transcode_segment(
            Path("dummy.vob"),
            Path("out.mp4"),
            0.0,
            10.0,
            metadata={"title": "Test Title", "year": "2020"}
        )
        self.assertTrue(result)

        # Check if subprocess was called
        mock_subprocess.assert_called()
        args, kwargs = mock_subprocess.call_args
        cmd_list = args[0]

        # Check metadata injection
        self.assertIn("-metadata", cmd_list)
        self.assertIn("title=Test Title", cmd_list)
        self.assertIn("date=2020", cmd_list)

class TestVisionary(unittest.TestCase):
    @patch('src.visionary.genai')
    def test_analyze_video(self, mock_genai):
        visionary = Visionary("fake_key")

        # Mock Client
        mock_client = MagicMock()
        visionary.client = mock_client

        # Mock models.generate_content response
        mock_response = MagicMock()
        mock_response.text = '{"scenes": []}'
        mock_client.models.generate_content.return_value = mock_response

        # Mock video file
        mock_video_file = MagicMock()
        mock_video_file.display_name = "test_video"

        result = visionary.analyze_video(mock_video_file)
        self.assertIsInstance(result, dict)
        self.assertIn("scenes", result)
        mock_client.models.generate_content.assert_called()

if __name__ == '__main__':
    unittest.main()
