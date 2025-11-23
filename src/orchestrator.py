
import os
from pathlib import Path
from dotenv import load_dotenv
from rich.console import Console
from rich.progress import Progress
from .scanner import Scanner
from .visionary import Visionary
from .transcoder import Transcoder

console = Console()

class Orchestrator:
    def __init__(self):
        load_dotenv()
        self.input_path = os.getenv("INPUT_PATH")
        self.output_path = os.getenv("OUTPUT_PATH")
        self.api_key = os.getenv("GOOGLE_API_KEY")

        if not self.input_path or not self.output_path:
            console.print("[bold red]Error:[/bold red] INPUT_PATH and OUTPUT_PATH must be set in .env file.")
            exit(1)

        self.scanner = Scanner(self.input_path)
        self.visionary = Visionary(self.api_key)
        self.transcoder = Transcoder()

        Path(self.output_path).mkdir(parents=True, exist_ok=True)

    def run(self):
        console.print("[bold green]Starting NostalgiaPipe...[/bold green]")

        # Stage A: Scanner
        console.print("[cyan]Scanning for VOB files...[/cyan]")
        vob_files = self.scanner.scan()
        console.print(f"Found {len(vob_files)} valid VOB files.")

        with Progress() as progress:
            task = progress.add_task("[green]Processing...", total=len(vob_files))

            for vob in vob_files:
                input_file = vob["path"]
                context = vob["context"]
                original_filename = vob["filename"] # e.g. VTS_01_1.VOB

                # Extract Chapter Number from filename if possible (VTS_01_1 -> Ch01)
                # Or VTS_01_1 -> Ch1-1?
                # The requirements say: Name Construction: Combine the Folder Context + Chapter Number + AI Description.
                # Example: Christmas 2004 - Ch01 - Kids Opening Presents.mp4
                # VOB names are usually VTS_XX_Y.VOB.
                # Let's try to parse XX and Y.

                parts = input_file.stem.split('_')
                chapter_str = "ChXX"
                if len(parts) >= 3 and parts[0] == "VTS":
                    # VTS_01_1 -> parts = ['VTS', '01', '1']
                    try:
                        title_set = parts[1]
                        title_num = parts[2]
                        chapter_str = f"Ch{title_set}-{title_num}"
                    except:
                        pass

                progress.update(task, description=f"Processing {context} - {original_filename}")

                # Stage B: Visionary
                temp_image_path = Path(self.output_path) / "temp_frame.jpg"
                if self.visionary.extract_frame(input_file, temp_image_path):
                    description = self.visionary.get_description(temp_image_path)
                    # Clean description
                    description = "".join([c for c in description if c.isalnum() or c in " -_"]).strip()
                else:
                    description = f"{original_filename}"

                # Construct new filename
                new_filename = f"{context} - {chapter_str} - {description}.mp4"
                output_file = Path(self.output_path) / new_filename

                # Stage C: Transcoder
                progress.update(task, description=f"Transcoding to {new_filename}")
                if self.transcoder.transcode(input_file, output_file):
                    console.print(f"[bold blue]Completed:[/bold blue] {new_filename}")
                else:
                    console.print(f"[bold red]Failed:[/bold red] {input_file}")

                # Cleanup temp image
                if temp_image_path.exists():
                    temp_image_path.unlink()

                progress.advance(task)

        console.print("[bold green]All Done![/bold green]")

if __name__ == "__main__":
    app = Orchestrator()
    app.run()
