import ffmpeg
from pathlib import Path
from typing import List, Tuple, Dict, Any
from scenedetect import detect, ContentDetector

class Transcoder:
    def get_audio_settings(self, input_path: Path) -> Dict[str, Any]:
        """
        Determines audio settings based on the input file's audio codec.
        If the audio is AC3, we copy it. Otherwise, we re-encode to AAC.
        If no audio, returns empty dict or None?
        The ffmpeg-python .output(**kwargs) unpacks. If empty, no audio flags?
        But if input has no audio, we shouldn't add audio flags if we aren't mapping audio.
        """
        try:
            probe = ffmpeg.probe(str(input_path))
            audio_stream = next((stream for stream in probe['streams'] if stream['codec_type'] == 'audio'), None)

            if not audio_stream:
                 return {}

            if audio_stream['codec_name'] == 'ac3':
                return {'acodec': 'copy'}
            else:
                return {'acodec': 'aac', 'audio_bitrate': '256k'}
        except Exception as e:
            print(f"Error probing audio for {input_path}: {e}")
            # Fallback to safe AAC encoding
            return {'acodec': 'aac', 'audio_bitrate': '256k'}

    def transcode(self, input_path: Path, output_path: Path):
        """
        Transcodes the VOB file to MP4 using specific archival settings.
        """
        audio_settings = self.get_audio_settings(input_path)

        try:
            (
                ffmpeg
                .input(str(input_path))
                .output(
                    str(output_path),
                    vcodec='libx265',
                    crf=18,
                    preset='slower',
                    vf='bwdif=mode=1,format=yuv420p10le',
                    pix_fmt='yuv420p10le',
                    movflags='+faststart',
                    **audio_settings
                )
                .overwrite_output()
                .run(quiet=True)
            )
            return True
        except ffmpeg.Error as e:
            print(f"Error transcoding {input_path}: {e.stderr.decode('utf8')}")
            return False
        except Exception as e:
            print(f"Error transcoding {input_path}: {e}")
            return False

    def detect_scenes(self, input_path: Path) -> List[Tuple[float, float]]:
        """
        Detects scenes in the video file using PySceneDetect.
        Returns a list of (start_time, end_time) tuples in seconds.
        """
        try:
            # ContentDetector finds areas where the difference between two subsequent
            # frames exceeds the threshold value.
            scene_list = detect(str(input_path), ContentDetector())

            # Convert FrameTimecode to seconds (float)
            scenes = []
            for scene in scene_list:
                start_time = scene[0].get_seconds()
                end_time = scene[1].get_seconds()
                scenes.append((start_time, end_time))

            return scenes
        except Exception as e:
            print(f"Error detecting scenes in {input_path}: {e}")
            # Return empty list to indicate failure or no scenes found (fallback to full video)
            return []

    def transcode_segment(self, input_path: Path, output_path: Path, start: float, end: float):
        """
        Transcodes a specific segment of the VOB file to MP4.
        """
        audio_settings = self.get_audio_settings(input_path)

        try:
            # Calculate duration
            duration = end - start

            (
                ffmpeg
                .input(str(input_path), ss=start, t=duration)
                .output(
                    str(output_path),
                    vcodec='libx265',
                    crf=18,
                    preset='slower',
                    vf='bwdif=mode=1,format=yuv420p10le',
                    pix_fmt='yuv420p10le',
                    movflags='+faststart',
                    **audio_settings
                )
                .overwrite_output()
                .run(quiet=True)
            )
            return True
        except ffmpeg.Error as e:
            print(f"Error transcoding segment {input_path} ({start}-{end}): {e.stderr.decode('utf8')}")
            return False
        except Exception as e:
            print(f"Error transcoding segment {input_path}: {e}")
            return False

    def create_vfr_proxy(self, input_path: Path, output_path: Path, timestamps: List[float]) -> bool:
        """
        Creates a low-resolution, variable-frame-rate (VFR) proxy video.
        It includes the full original audio track (if present) but only the video frames
        at the specified timestamps.
        """
        if not timestamps:
            return False

        try:
            probe = ffmpeg.probe(str(input_path))
            audio_stream_info = next((stream for stream in probe['streams'] if stream['codec_type'] == 'audio'), None)

            select_filter = "+".join([f"between(t,{ts},{ts}+0.001)" for ts in timestamps])

            streams = []

            video_stream = (
                ffmpeg.input(str(input_path))
                .video.filter('select', select_filter)
                .filter('setpts', 'N/FRAME_RATE/TB')
                .filter('scale', -1, 360) # 360p proxy
            )
            streams.append(video_stream)

            audio_params = {}
            if audio_stream_info:
                audio_stream = ffmpeg.input(str(input_path)).audio
                streams.append(audio_stream)
                audio_params = {
                    'acodec': 'aac',
                    'audio_bitrate': '64k'
                }

            (
                ffmpeg.output(
                    *streams,
                    str(output_path),
                    vcodec='libx264',
                    crf=28,
                    preset='fast',
                    movflags='+faststart',
                    **audio_params
                )
                .overwrite_output()
                .run(quiet=True)
            )
            return True
        except ffmpeg.Error as e:
            print(f"Error creating VFR proxy for {input_path}: {e.stderr.decode('utf8')}")
            return False
        except Exception as e:
            print(f"Error creating VFR proxy for {input_path}: {e}")
            return False
