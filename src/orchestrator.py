import os
import ffmpeg
from pathlib import Path
from dotenv import load_dotenv
from rich.console import Console
from rich.progress import Progress
from .scanner import Scanner
from .visionary import Visionary
from .transcoder import Transcoder
from .nostalgia_filter import NostalgiaFilter

console = Console()

class Orchestrator:
    def __init__(self, visionary=None):
        load_dotenv()
        self.input_path = os.getenv("INPUT_PATH")
        self.output_path = os.getenv("OUTPUT_PATH")
        self.api_key = os.getenv("GOOGLE_API_KEY")

        if not self.input_path or not self.output_path:
            console.print("[bold red]Error:[/bold red] INPUT_PATH and OUTPUT_PATH must be set in .env file.")
            exit(1)

        self.scanner = Scanner(self.input_path)
        # Use injected visionary or create a new one
        self.visionary = visionary if visionary else Visionary(self.api_key)
        self.transcoder = Transcoder()
        self.nostalgia_filter = NostalgiaFilter()

        Path(self.output_path).mkdir(parents=True, exist_ok=True)

    def run(self):
        console.print("[bold green]Starting NostalgiaPipe...[/bold green]")

        # Stage A: Scanner
        console.print("[cyan]Scanning for video files...[/cyan]")
        video_files = self.scanner.scan()
        console.print(f"Found {len(video_files)} valid video files.")

        with Progress() as progress:
            task = progress.add_task("[green]Processing...", total=len(video_files))

            for video in video_files:
                input_file = video["path"]
                context = video["context"]
                original_filename = video["filename"]

                # Extract Chapter Number from filename if possible (VOB legacy logic)
                parts = input_file.stem.split('_')
                chapter_str = ""
                if input_file.suffix.lower() == '.vob' and len(parts) >= 3 and parts[0] == "VTS":
                    try:
                        title_set = parts[1]
                        title_num = parts[2]
                        chapter_str = f"Ch{title_set}-{title_num} - "
                    except:
                        pass

                progress.update(task, description=f"Analyzing scenes in {context} - {original_filename}")

                # Scene Detection
                scenes = self.transcoder.detect_scenes(input_file)
                if not scenes:
                    # Fallback to whole file if no scenes detected or error
                    try:
                        probe = ffmpeg.probe(str(input_file))
                        video_info = next(s for s in probe['streams'] if s['codec_type'] == 'video')
                        duration = float(video_info['duration'])
                        scenes = [(0.0, duration)]
                    except:
                        console.print(f"[bold red]Could not probe file {input_file}, skipping.[/bold red]")
                        progress.advance(task)
                        continue

                for i, (start, end) in enumerate(scenes):
                    scene_idx = i + 1
                    duration = end - start
                    if duration < 1.0: # Skip very short scenes
                        continue

                    # Stage B: Visionary (Thumbnails & Naming)
                    description = ""
                    # Always try to name if visionary is present (even if api_key is missing, maybe it's a mock)
                    if self.visionary:
                        # Select best frames using NostalgiaFilter
                        ts_list = self.nostalgia_filter.select_best_frames(str(input_file), start, end)

                        if ts_list:
                            proxy_path = Path(self.output_path) / f"temp_proxy_{scene_idx}.mp4"
                            if self.transcoder.create_vfr_proxy(input_file, proxy_path, ts_list):
                                desc = self.visionary.get_description(proxy_path)
                                description = "".join([c for c in desc if c.isalnum() or c in " -_"]).strip()
                                # Keep proxy for inspection as requested
                                console.print(f"[dim]Proxy kept at: {proxy_path}[/dim]")
                                # if proxy_path.exists():
                                #    proxy_path.unlink()

                    if not description:
                         description = f"Scene {scene_idx}"

                    # Construct new filename
                    if description == f"Scene {scene_idx}":
                         new_filename = f"{context} - {chapter_str}Scene {scene_idx:03d}.mp4"
                    else:
                         new_filename = f"{context} - {chapter_str}Scene {scene_idx:03d} - {description}.mp4"

                    output_file = Path(self.output_path) / new_filename

                    # Stage C: Transcode Segment
                    progress.update(task, description=f"Transcoding {new_filename}")
                    if self.transcoder.transcode_segment(input_file, output_file, start, end):
                        console.print(f"[bold blue]Completed:[/bold blue] {new_filename}")
                    else:
                        console.print(f"[bold red]Failed:[/bold red] {new_filename}")

                progress.advance(task)

        console.print("[bold green]All Done![/bold green]")

if __name__ == "__main__":
    app = Orchestrator()
    app.run()
