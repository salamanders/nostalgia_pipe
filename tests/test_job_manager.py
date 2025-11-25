import unittest
import os
import json
from pathlib import Path
from src.job_manager import JobManager

class TestJobManager(unittest.TestCase):
    def setUp(self):
        self.test_file = "test_jobs.json"
        if os.path.exists(self.test_file):
            os.remove(self.test_file)
        self.manager = JobManager(self.test_file)

    def tearDown(self):
        if os.path.exists(self.test_file):
            os.remove(self.test_file)

    def test_add_job(self):
        self.manager.add_job("path/to/video.vob", "Context")
        job = self.manager.get_job("path/to/video.vob")
        self.assertIsNotNone(job)
        self.assertEqual(job["status"], "pending")
        self.assertEqual(job["context"], "Context")

    def test_update_status(self):
        self.manager.add_job("path/to/video.vob", "Context")
        self.manager.update_job_status("path/to/video.vob", "proxy_created", proxy_path="path/to/proxy.mp4")

        job = self.manager.get_job("path/to/video.vob")
        self.assertEqual(job["status"], "proxy_created")
        self.assertEqual(job["proxy_path"], "path/to/proxy.mp4")

    def test_persistence(self):
        self.manager.add_job("path/to/video.vob", "Context")

        # New instance
        new_manager = JobManager(self.test_file)
        job = new_manager.get_job("path/to/video.vob")
        self.assertIsNotNone(job)
        self.assertEqual(job["context"], "Context")

if __name__ == '__main__':
    unittest.main()
