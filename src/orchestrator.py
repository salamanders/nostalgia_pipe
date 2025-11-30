import os
import json
from pathlib import Path
from dotenv import load_dotenv
from rich.console import Console
from rich.progress import Progress

from .scanner import Scanner
from .visionary import Visionary
from .transcoder import Transcoder
from .job_manager import JobManager

console = Console()

class Orchestrator:
    def __init__(self, visionary=None, job_manager=None):
        load_dotenv()
        self.input_path = os.getenv("INPUT_PATH")
        self.output_path = os.getenv("OUTPUT_PATH")
        self.api_key = os.getenv("GOOGLE_API_KEY")

        if not self.input_path or not self.output_path:
            console.print("[bold red]Error:[/bold red] INPUT_PATH and OUTPUT_PATH must be set in .env file.")
            # For testing purposes, we might want to avoid exit if injected
            if not visionary and not job_manager:
                 exit(1)

        self.scanner = Scanner(self.input_path)

        # Dependency Injection or Default
        self.visionary = visionary if visionary else Visionary(self.api_key)
        self.transcoder = Transcoder()

        # Initialize Job Manager
        if job_manager:
            self.job_manager = job_manager
        else:
            self.job_manager = JobManager(os.path.join(self.output_path, "jobs.json"))

        # Ensure output directory exists
        Path(self.output_path).mkdir(parents=True, exist_ok=True)
        # Directory for proxies
        self.proxy_dir = Path(self.output_path) / "proxies"
        self.proxy_dir.mkdir(exist_ok=True)

    def scan_and_register(self):
        """Scans for new files and registers them in the job manager."""
        console.print("[cyan]Scanning for VOB files...[/cyan]")
        vob_files = self.scanner.scan()
        console.print(f"Found {len(vob_files)} valid VOB files.")

        for vob in vob_files:
            self.job_manager.add_job(str(vob["path"]), vob["context"])

    def batch_submit(self):
        """Pass 1: Create proxies, upload to Gemini, and Analyze."""
        self.scan_and_register()

        # 1. Create Proxies
        pending_proxies = self.job_manager.get_pending_proxies()
        if pending_proxies:
            console.print(f"[bold yellow]Creating proxies for {len(pending_proxies)} files...[/bold yellow]")
            with Progress() as progress:
                task = progress.add_task("Creating Proxies...", total=len(pending_proxies))
                for job in pending_proxies:
                    original_path = Path(job["original_path"])
                    proxy_filename = f"{original_path.stem}_proxy.mp4"
                    proxy_path = self.proxy_dir / proxy_filename

                    if self.transcoder.create_proxy(original_path, proxy_path):
                        self.job_manager.update_job_status(
                            job["original_path"],
                            "proxy_created",
                            proxy_path=str(proxy_path)
                        )
                    progress.advance(task)

        # 2. Upload and Analyze
        pending_uploads = self.job_manager.get_pending_uploads() # status=proxy_created

        # Process pending uploads
        if pending_uploads:
            console.print(f"[bold yellow]Uploading {len(pending_uploads)} files...[/bold yellow]")
            for job in pending_uploads:
                proxy_path = Path(job["proxy_path"])
                console.print(f"Uploading [blue]{proxy_path.name}[/blue]...")
                video_file = self.visionary.upload_video(proxy_path)
                if video_file:
                    self.job_manager.update_job_status(
                        job["original_path"],
                        "uploaded",
                        gemini_file_uri=video_file.uri,
                        gemini_file_name=video_file.name # Store name for retrieval
                    )

        # Refetch pending analysis (some might have just been added)
        pending_analysis = self.job_manager.get_pending_analysis()

        if pending_analysis:
            console.print(f"[bold yellow]Analyzing {len(pending_analysis)} files...[/bold yellow]")
            for job in pending_analysis:
                console.print(f"Analyzing [blue]{job['original_path']}[/blue]...")

                # We need the file object
                file_name = job.get("gemini_file_name")
                if not file_name:
                    console.print("[red]Missing gemini_file_name, re-uploading required (not implemented).[/red]")
                    continue

                try:
                    video_file = self.visionary.get_file(file_name)

                    result = self.visionary.analyze_video(video_file)
                    if result:
                        self.job_manager.update_job_status(
                            job["original_path"],
                            "analyzed",
                            analysis_result=result
                        )
                        console.print("[green]Analysis complete.[/green]")
                    else:
                        console.print("[red]Analysis failed.[/red]")
                except Exception as e:
                     console.print(f"[red]Error during analysis: {e}[/red]")

    def batch_finalize(self):
        """Pass 2: Transcode final clips based on Gemini analysis."""
        ready_jobs = self.job_manager.get_ready_to_finalize()
        if not ready_jobs:
            console.print("[yellow]No jobs ready to finalize. Run 'submit' first?[/yellow]")
            return

        console.print(f"[bold green]Finalizing {len(ready_jobs)} jobs...[/bold green]")

        with Progress() as progress:
            task = progress.add_task("Transcoding Scenes...", total=len(ready_jobs))

            for job in ready_jobs:
                original_path = Path(job["original_path"])
                analysis = job["analysis_result"]
                context = job["context"]

                if not analysis or "scenes" not in analysis:
                    console.print(f"[red]Invalid analysis for {original_path.name}[/red]")
                    progress.advance(task)
                    continue

                global_year = analysis.get("global_year")
                global_location = analysis.get("global_location")

                scenes = analysis.get("scenes", [])
                console.print(f"  Found {len(scenes)} scenes in {original_path.name}")

                scene_idx = 1
                for scene in scenes:
                    start = scene.get("start_time", 0)
                    end = scene.get("end_time", 0)
                    title = scene.get("title", "Unknown Scene")
                    desc = scene.get("description", "")
                    year = scene.get("year") or global_year or "Unknown Year"
                    location = scene.get("location") or global_location or "Unknown Location"

                    if end - start < 1.0:
                        continue # Skip tiny scenes

                    # Filename: {Year} {Title}.mp4 (handle collisions)
                    safe_title = "".join([c for c in title if c.isalnum() or c in " -_"]).strip()
                    safe_year = "".join([c for c in str(year) if c.isdigit()])
                    if not safe_year: safe_year = "0000"

                    # Ensure unique filename
                    base_filename = f"{safe_year} - {safe_title}"
                    output_filename = f"{base_filename}.mp4"
                    output_file = Path(self.output_path) / output_filename

                    counter = 1
                    while output_file.exists():
                        output_filename = f"{base_filename} ({counter}).mp4"
                        output_file = Path(self.output_path) / output_filename
                        counter += 1

                    # Metadata dict
                    metadata = {
                        "title": title,
                        "description": desc,
                        "year": str(year),
                        "location": location,
                        "people": ", ".join(scene.get("people", []))
                    }

                    # Sidecar JSON
                    sidecar_path = output_file.with_suffix('.json')
                    with open(sidecar_path, 'w') as f:
                        json.dump(metadata, f, indent=2)

                    # Transcode
                    progress.console.print(f"    Transcoding: {output_filename}")
                    self.transcoder.transcode_segment(original_path, output_file, start, end, metadata)
                    scene_idx += 1

                self.job_manager.update_job_status(job["original_path"], "complete")
                progress.advance(task)
