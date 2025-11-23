
import ffmpeg
from pathlib import Path

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
