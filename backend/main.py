import os
import re
import tempfile
import whisper
import subprocess
import uuid
from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from pydantic import BaseModel
from services.clipper import generate_clip

app = FastAPI(
    title="WayinVideo Backend",
    description="Physical AI YouTube Shorts subtitle generator and MoviePy segmentation backend."
)

# Enable CORS for cross-platform integration
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Ensure persistent physical clips directory exists on the server to hold outputs
CLIPS_DIR = "clips"
os.makedirs(CLIPS_DIR, exist_ok=True)

# Global model container loaded at startup/first call
MODEL_INSTANCE = None

def get_whisper_model():
    global MODEL_INSTANCE
    if MODEL_INSTANCE is None:
        print("Loading Whisper model into memory...")
        MODEL_INSTANCE = whisper.load_model("base")
    return MODEL_INSTANCE

class ProcessRequest(BaseModel):
    url: str
    num_clips: int = 3

@app.get("/health")
def health_check():
    return {
        "status": "ok", 
        "mode": "FREE - No paid API keys required",
        "transcription_engine": "Whisper (Local CPU/GPU)",
        "clipper_engine": "MoviePy with FFmpeg"
    }

@app.get("/clips/{filename}")
async def get_clip(filename: str):
    """
    Directly streams physically generated MP4 files back to clients/Android devices.
    """
    path = os.path.join(CLIPS_DIR, filename)
    if os.path.exists(path):
        return FileResponse(path, media_type="video/mp4")
    raise HTTPException(status_code=404, detail="Requested clip file not found on server.")

def download_video(url: str, out_dir: str) -> str:
    """
    Downloads combined high-quality video/audio MP4 from YouTube using yt-dlp.
    """
    output_template = os.path.join(out_dir, "video.%(ext)s")
    # Request standard mp4 format for effortless local decoding and seamless cropping
    cmd = [
        "yt-dlp",
        "-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best",
        "--merge-output-format", "mp4",
        "-o", output_template,
        url
    ]
    print(f"[*] Running yt-dlp video download: {' '.join(cmd)}")
    result = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if result.returncode != 0:
        raise Exception(f"yt-dlp video download failed: {result.stderr}")
    
    for f in os.listdir(out_dir):
        if f.startswith("video.") and f.endswith(".mp4"):
            return os.path.join(out_dir, f)
            
    # Fallback to any file starting with video
    for f in os.listdir(out_dir):
        if f.startswith("video."):
            return os.path.join(out_dir, f)
            
    raise Exception("Video download completed successfully but output file could not be resolved.")

@app.post("/api/process")
async def process_video(request_body: ProcessRequest, request: Request):
    yt_url = request_body.url.strip()
    num_clips = request_body.num_clips
    
    if not yt_url:
        raise HTTPException(status_code=400, detail="Invalid request: URL parameter cannot be empty.")
    
    with tempfile.TemporaryDirectory() as tmp_dir:
        try:
            print(f"[*] Initializing video ingest for: {yt_url}")
            video_path = download_video(yt_url, tmp_dir)
            
            print("[*] Beginning automatic local video speech-transcription via OpenAI Whisper...")
            model = get_whisper_model()
            result = model.transcribe(video_path, word_timestamps=True)
            
            segments = result.get("segments", [])
            all_words = []
            
            for seg in segments:
                for w in seg.get("words", []):
                    word_str = w.get("word", "").strip()
                    if word_str:
                        all_words.append({
                            "word": word_str,
                            "startMs": int(w.get("start", 0.0) * 1000),
                            "endMs": int(w.get("end", 0.0) * 1000)
                        })
            
            if not all_words:
                raise HTTPException(status_code=500, detail="Transcription completed but no speech words were extracted.")
            
            video_duration_ms = all_words[-1]["endMs"]
            total_duration_sec = video_duration_ms / 1000.0
            
            print(f"[+] Completed speech mapping. Resolved {len(all_words)} words over {total_duration_sec:.2f} seconds.")
            
            clips = []
            clip_dur_ms = 30000  # 30-second clips
            interval_ms = video_duration_ms / max(1, num_clips + 1)
            hooks = ["secret", "must", "important", "best", "why", "how", "future", "learn", "hacks", "tips", "mindset", "goal"]
            
            # Resolve system base URL dynamically for client resource parsing
            base_url = str(request.base_url)
            
            for i in range(num_clips):
                target_start_ms = (i + 0.5) * interval_ms
                target_end_ms = target_start_ms + clip_dur_ms
                
                # Extract words fitting the interval
                clip_words = [w for w in all_words if w["startMs"] >= target_start_ms and w["endMs"] <= target_end_ms]
                if not clip_words:
                    clip_words = [w for w in all_words if abs(w["startMs"] - target_start_ms) < (clip_dur_ms * 1.5)]
                
                if not clip_words:
                    continue
                
                start_ms = clip_words[0]["startMs"]
                end_ms = clip_words[-1]["endMs"]
                
                start_sec = start_ms / 1000.0
                end_sec = end_ms / 1000.0
                
                # Check bounds safety
                if end_sec <= start_sec:
                    end_sec = start_sec + 5.0
                
                # Render clean title text
                four_words = " ".join([w["word"] for w in clip_words[:4]])
                clean_title = re.sub(r'[^\w\s]', '', four_words).strip().capitalize()
                
                combined_text = " ".join([w["word"] for w in clip_words])
                
                # Calculate highlight virality score
                base_score = 83 + (i * 3)
                lower_text = combined_text.lower()
                for hook in hooks:
                    if hook in lower_text:
                        base_score += 2
                
                viral_score = min(max(base_score, 50), 99)
                reason = f"High speech-rate capture highlights valuable arguments: '{clean_title}'. Optimal vertical 9:16 layout formatting."
                
                # Generate a unique video clip filename
                clip_filename = f"clip_{uuid.uuid4().hex[:8]}_{i}.mp4"
                clip_filepath = os.path.join(CLIPS_DIR, clip_filename)
                
                # Run MoviePy to physically slice and write video clip file
                try:
                    generate_clip(video_path, start_sec, end_sec, clip_filepath)
                    # Produce paths compatible with standard client protocols
                    full_clip_url = f"{base_url.rstrip('/')}/clips/{clip_filename}"
                except Exception as moviepy_err:
                    print(f"[-] MoviePy generation failed for clip {i}: {moviepy_err}. Falling back to source stream link.")
                    full_clip_url = yt_url
                    
                clips.append({
                    "title": f"🔥 {clean_title}...",
                    "startSec": int(start_sec),
                    "endSec": int(end_sec),
                    "viralScore": viral_score,
                    "viralReason": reason,
                    "captions": clip_words,
                    "clipUrl": full_clip_url,
                    "clip_url": full_clip_url
                })
            
            return {
                "url": yt_url,
                "duration": total_duration_sec,
                "clips": clips
            }
            
        except Exception as e:
            print(f"[-] Exception in service processing logic: {str(e)}")
            raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    # Warm up Whisper model on startup so client requests don't lag or timeout on first load
    get_whisper_model()
    uvicorn.run(app, host="0.0.0.0", port=8000)
