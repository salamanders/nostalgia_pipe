import json
import os
from pathlib import Path
from typing import Dict, List, Optional
from datetime import datetime

class JobManager:
    def __init__(self, jobs_file: str = "jobs.json"):
        self.jobs_file = Path(jobs_file)
        self.jobs: Dict[str, Dict] = self._load_jobs()

    def _load_jobs(self) -> Dict[str, Dict]:
        if self.jobs_file.exists():
            try:
                with open(self.jobs_file, 'r') as f:
                    return json.load(f)
            except json.JSONDecodeError:
                return {}
        return {}

    def save_jobs(self):
        with open(self.jobs_file, 'w') as f:
            json.dump(self.jobs, f, indent=2)

    def add_job(self, file_path: str, context: str):
        # Use file path as key, but maybe relative to keep it clean?
        # For now, absolute path string.
        if file_path not in self.jobs:
            self.jobs[file_path] = {
                "status": "pending", # pending, proxy_created, uploaded, analyzed, complete
                "context": context,
                "created_at": datetime.now().isoformat(),
                "original_path": file_path,
                "proxy_path": None,
                "gemini_file_uri": None,
                "analysis_result": None
            }
            self.save_jobs()

    def get_job(self, file_path: str) -> Optional[Dict]:
        return self.jobs.get(file_path)

    def update_job_status(self, file_path: str, status: str, **kwargs):
        if file_path in self.jobs:
            self.jobs[file_path]["status"] = status
            for k, v in kwargs.items():
                self.jobs[file_path][k] = v
            self.jobs[file_path]["updated_at"] = datetime.now().isoformat()
            self.save_jobs()

    def get_pending_proxies(self) -> List[Dict]:
        return [j for j in self.jobs.values() if j["status"] == "pending"]

    def get_pending_uploads(self) -> List[Dict]:
        return [j for j in self.jobs.values() if j["status"] == "proxy_created"]

    def get_pending_analysis(self) -> List[Dict]:
        return [j for j in self.jobs.values() if j["status"] == "uploaded"]

    def get_ready_to_finalize(self) -> List[Dict]:
        return [j for j in self.jobs.values() if j["status"] == "analyzed"]
