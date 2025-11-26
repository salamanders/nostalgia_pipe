import ffmpeg
from pathlib import Path
from typing import List, Tuple, Dict, Any
from scenedetect import detect, ContentDetector

class Transcoder:
    def get_audio_settings(self, input_path: Path) -> Dict[str, Any]:
        """
        Determines audio settings based on the input file's audio codec.
        If the audio is AC3, we copy it. Otherwise, we re-encode to AAC.
        """
        try:
            probe = ffmpeg.probe(str(input_path))
            audio_stream = next((stream for stream in probe['streams'] if stream['codec_type'] == 'audio'), None)

            if audio_stream and audio_stream['codec_name'] == 'ac3':
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
        # Breakdown of the flags:
        # bwdif=mode=1: This is the magic switch.
        #   mode=0: Same framerate (30fps). Avoid.
        #   mode=1: Double framerate (60fps). Use this. It outputs one frame for every field.
        # format=yuv420p10le: This ensures the processing pipeline happens in 10-bit.
        #   Even though your DVD source is 8-bit, deinterlacing involves interpolation (creating new pixels).
        #   Doing this math in 10-bit prevents "banding" in the blue gradients of the sky.
        # -c:a copy: (If AC3) This passes the audio through untouched.
        #   DVD audio is usually AC3 or PCM, which Google Photos handles fine.
        #   Re-compressing it to AAC would only lose quality.
        # -movflags +faststart: Moves the metadata to the front of the file.
        #   This allows the video to start playing immediately on Google Photos/Web browsers before the whole file is downloaded.

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
        It includes the full original audio track but only the video frames
        at the specified timestamps.
        """
        if not timestamps:
            return False

        try:
            # Construct the select filter string. E.g., 'select=eq(n,10)+eq(n,25)+eq(n,50)'
            # This is complex because timestamps need to be converted to frame numbers.
            # A simpler way is to use a timestamp-based selection.
            # E.g., 'select='gt(t,10)*lt(t,11)+gt(t,20)*lt(t,21)''
            # Let's build the select filter string based on timestamps.
            select_filter = "+".join([f"between(t,{ts},{ts}+0.001)" for ts in timestamps])

            audio_stream = ffmpeg.input(str(input_path)).audio
            video_stream = (
                ffmpeg.input(str(input_path))
                .video.filter('select', select_filter)
                .filter('setpts', 'N/FRAME_RATE/TB')
                .filter('scale', -1, 360) # 360p proxy
            )

            (
                ffmpeg.output(
                    video_stream,
                    audio_stream,
                    str(output_path),
                    vcodec='libx264',
                    crf=28,
                    preset='fast',
                    acodec='aac',
                    audio_bitrate='64k',
                    movflags='+faststart'
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
