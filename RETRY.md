# Two-Pass Batch Processing Architecture (NostalgiaPipe)

This document outlines the architectural changes and implementation details for the "Two-Pass Batch Processing" feature request, which aims to optimize the workflow for archiving DVD rips using Google Gemini.

## 1. Core Concept: Two-Pass Workflow

The original single-pass workflow (Scan -> Local Scene Detect -> Upload Thumbnails -> Transcode) is replaced by a decoupled two-pass system:

1.  **Phase 1: `submit` (The "Night" Shift)**
    *   **Goal:** Prepare media and offload intelligence gathering to the cloud.
    *   **Process:**
        1.  Scan the input directory for VOB files.
        2.  Register each file in a `jobs.json` database (managed by `JobManager`).
        3.  **Proxy Generation:** Create highly compressed "proxy" videos (360p, 5fps, mono audio) instead of extracting static thumbnails. This gives the AI temporal context (motion, audio).
        4.  **Upload:** Upload these lightweight proxies to the Google Gemini File API.
        5.  **Analyze:** Send a prompt to Gemini (using the `google-genai` SDK) to analyze the video.
        6.  **AI Output:** Gemini returns a JSON object containing:
            *   Global Year & Location.
            *   List of **Scenes**, each with:
                *   Start/End timestamps (replacing local `scenedetect`).
                *   Title & Description.
                *   People & Location tags.
    *   **Result:** A populated `jobs.json` with analysis data for every file.

2.  **Phase 2: `finalize` (The "Day" Shift)**
    *   **Goal:** Perform the heavy lifting (transcoding) based on approved AI data.
    *   **Process:**
        1.  Load `jobs.json`.
        2.  Iterate through "Analyzed" jobs.
        3.  **Transcode:** Use FFmpeg to cut and transcode the *original* high-quality VOB files based on the *exact timestamps* returned by Gemini.
        4.  **Metadata:** Embed rich metadata (Title, Year, Description) directly into the MP4 container.
        5.  **Sidecar:** Generate a `.json` sidecar file for every output video containing all raw AI data.
        6.  **Naming:** Rename output files using the pattern: `{Year} - {Title}.mp4`.

## 2. Component Updates

### `src/job_manager.py` (New)
*   **Purpose:** persistent state management.
*   **Key Methods:** `add_job`, `update_job_status`, `get_pending_proxies`, `get_ready_to_finalize`.
*   **Storage:** `jobs.json` in the output directory.

### `src/orchestrator.py` (Refactored)
*   **Removed:** Old `run()` loop, dependency on `NostalgiaFilter`.
*   **Added:** `batch_submit()` and `batch_finalize()` methods.
*   **Logic:**
    *   `batch_submit`: Scan -> Proxy -> Upload -> Analyze (loop).
    *   `batch_finalize`: Load Jobs -> Transcode (loop).

### `src/transcoder.py` (Updated)
*   **`create_proxy(input, output)`:**
    *   `vf='scale=-1:360'`: Downscale for speed/size.
    *   `r=5`: Low framerate (5fps) is sufficient for context.
    *   `ac=1`, `bitrate='32k'`: Mono, low-bitrate audio.
*   **`transcode_segment(..., metadata=...)`:**
    *   Updated to accept a `metadata` dictionary.
    *   Injects FFmpeg metadata flags: `-metadata title="..."`, `-metadata comment="..."`, `-metadata date="..."`.

### `src/visionary.py` (Updated)
*   **SDK Switch:** Updated from `google-generativeai` (legacy) to `google-genai` (v1).
*   **`upload_video(path)`:** Uploads to Gemini File API and polls for `ACTIVE` state.
*   **`analyze_video(file_obj)`:**
    *   Uses `gemini-1.5-flash` model.
    *   **Prompt:** Updated to request specific JSON schema with `scenes` array, timestamps, and metadata.

### `main.py` (Updated)
*   Updated to accept CLI arguments: `python main.py submit` or `python main.py finalize`.

## 3. Benefits

1.  **Contextual Intelligence:** AI sees the video (audio/motion), not just static frames.
2.  **Efficiency:** Heavy transcoding happens only *after* we know what we want (and can be done on a powerful machine while the "submit" step can run on a laptop).
3.  **Rich Metadata:** Embedded tags and sidecar files ensure the "archive" is searchable and future-proof.
4.  **Robustness:** `JobManager` allows the process to be paused/resumed without losing state.
