import ffmpeg
from pathlib import Path
from typing import List, Tuple
from scenedetect import detect, ContentDetector

class Transcoder:
    def transcode(self, input_path: Path, output_path: Path):
        """
        Transcodes the VOB file to MP4 using specific archival settings.
        """
        # Flags:
        # -c:v libx264
        # -crf 17
        # -preset veryslow
        # -vf bwdif=mode=0:parity=-1
        # -pix_fmt yuv420p
        # -c:a aac -b:a 256k

        try:
            (
                ffmpeg
                .input(str(input_path))
                .output(
                    str(output_path),
                    vcodec='libx264',
                    crf=17,
                    preset='veryslow',
                    vf='bwdif=mode=0:parity=-1',
                    pix_fmt='yuv420p',
                    acodec='aac',
                    audio_bitrate='256k'
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
        try:
            # Calculate duration
            duration = end - start

            # Note: putting -ss before -i is faster but might not be frame accurate unless we use -noaccurate_seek which we probably don't want.
            # However, since we are re-encoding, -ss before -i seeks to the nearest keyframe and then decodes until the timestamp.
            # But since we are doing frame-accurate cutting (based on scene detection), we probably want accuracy.
            # PySceneDetect returns precise timestamps.
            # Using -ss after -i is slow (decodes everything up to that point).
            # Using -ss before -i is fast.
            # If we use -ss before -i, the timestamps in filters start at 0.

            (
                ffmpeg
                .input(str(input_path), ss=start, t=duration)
                .output(
                    str(output_path),
                    vcodec='libx264',
                    crf=17,
                    preset='veryslow',
                    vf='bwdif=mode=0:parity=-1',
                    pix_fmt='yuv420p',
                    acodec='aac',
                    audio_bitrate='256k'
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
