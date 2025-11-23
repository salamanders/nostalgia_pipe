# **Project Name: NostalgiaPipe \- DVD Archival Assistant**

## **1\. Objective**

You are an expert Python developer and Video Engineering specialist. Your task is to build a CLI application called nostalgia\_pipe.

**The Goal:** Scan a local hard drive for uncompressed DVD rips (VIDEO\_TS folder structures), process the individual .VOB files into high-quality, deinterlaced .mp4 files suitable for Google Photos, and use AI to intelligently rename them based on visual content.

**Philosophy:** Efficiency is *not* a priority. Quality and archival preservation are the only priorities. Assume the machine has unlimited CPU and RAM.

## **2\. Tech Stack & Dependencies**

* **Language:** Python 3.10+  
* **Core Engine:** ffmpeg (invoked via subprocess or ffmpeg-python wrapper).  
* **AI Vision:** google-generativeai (Gemini 1.5 Flash or Pro) for analyzing video frames.  
* **CLI UI:** rich library for beautiful progress bars and logs.  
* **Path Handling:** pathlib.

## **3\. Architecture & Workflow**

The application should function in a pipeline with three distinct stages:

### **Stage A: The Deep Scanner (Discovery)**

1. Recursively scan a user-provided INPUT\_DIR.  
2. Identify "DVD structure" folders (folders containing a VIDEO\_TS subdirectory).  
3. Inside VIDEO\_TS, identify valid content files (.VOB).  
   * **Target:** Usually files named VTS\_01\_1.VOB, VTS\_01\_2.VOB, etc.  
4. **Context Gathering:** Capture the directory path.  
   * *Example:* If path is F:/HomeMovies/Christmas 2004/VIDEO\_TS/VTS\_01\_1.VOB, the context is "Christmas 2004".

### **Stage B: The AI Labeler (The Creative Part)**

Before converting, we need a better name than "VTS\_01\_1".

1. **Snapshot:** Use ffmpeg to extract a single high-quality screenshot from the *middle* (50% timestamp) of the VOB file.  
2. **Vision Analysis:** Send this screenshot to the **Gemini API**.  
3. **Prompt:** "Analyze this image from a home movie. Provide a succinct, 3-5 word filename description of the event or action (e.g., 'Kids Opening Presents', 'Grandma Blowing Candles', 'Beach Volleyball'). Do not include file extensions."  
4. **Name Construction:** Combine the Folder Context \+ Chapter Number \+ AI Description.  
   * *Result:* Christmas 2004 \- Ch01 \- Kids Opening Presents.mp4

### **Stage C: The Transcoder (Archival Processing)**

Convert the VOB to a Google Photos-optimized format. Do not scrimp on CPU cycles.

**FFmpeg Command Requirements:**

* **Container:** MP4  
* **Video Codec:** libx264 (H.264 is most compatible with GPhotos).  
* **Quality:** \-crf 17 (Visually lossless).  
* **Preset:** \-preset veryslow (Maximum compression efficiency per bit).  
* **Deinterlacing:** This is critical for DVDs. Use the bwdif (Bob Weaver Deinterlacing Filter) which is superior to yadif.  
  * *Flag:* \-vf bwdif=mode=0:parity=-1 (Keep original frame rate, auto-detect parity).  
* **Pixel Format:** \-pix\_fmt yuv420p (Required for broad compatibility).  
* **Audio:** \-c:a aac \-b:a 256k (High bitrate audio preservation).  
* **Metadata:** Map original creation dates if possible, or set creation date to now.

## **4\. Implementation Details for Jules**

### **Class Structure Suggestion**

* Scanner: Handles file system traversal and filtering.  
* Visionary: Handles the extraction of the jpg frame and the API call to Gemini.  
* Transcoder: Builds and executes the FFmpeg command string.  
* Orchestrator: glues them together with rich progress bars.

### **Environment Variables**

The app should look for a .env file containing:

GOOGLE\_API\_KEY=your\_gemini\_key\_here  
INPUT\_PATH=/path/to/movies  
OUTPUT\_PATH=/path/to/processed

### **Handling Errors**

* If the AI fails or times out, fallback to the folder name \+ timestamp (e.g., Christmas 2004 \- VTS\_01\_1.mp4).  
* If a file is corrupt, log it to errors.log and continue to the next.

## **5\. Definition of Done**

1. A Python script (or set of scripts) that runs from the terminal.  
2. It successfully finds VOBs.  
3. It generates a temp image, gets a name from Gemini, converts the video, and places it in the Output Dir.  
4. The output filenames are human-readable and descriptive.  
5. The video quality looks excellent (no jagged interlace lines).

**Go wild with the implementation. Make it robust, make the logs look cool, and ensure the video quality is top-tier.**
