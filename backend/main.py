import os, re, uuid, time, json, subprocess, shutil
from dataclasses import dataclass, field
from typing import List, Dict, Optional
from enum import Enum
import asyncio
import cv2
import threading

from fastapi import FastAPI, HTTPException, Request, File, UploadFile, Form, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from pydantic import BaseModel
import logging

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("backend_core")

app = FastAPI(title="Clipz-Stream Backend (3-Tier Architecture)")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_credentials=True, allow_methods=["*"], allow_headers=["*"])

# ── Storage Configuration ──────────────────────────────────────────────────
RAW_UPLOADS_DIR = "raw_uploads"
CLIPS_DIR = "clips"
os.makedirs(RAW_UPLOADS_DIR, exist_ok=True)
os.makedirs(CLIPS_DIR, exist_ok=True)

def cleanup_expired_files(directories: List[str], max_age_seconds: float = 1800):
    now = time.time()
    for directory in directories:
        if not os.path.exists(directory): continue
        for filename in os.listdir(directory):
            file_path = os.path.join(directory, filename)
            if os.path.isdir(file_path): continue
            try:
                if now - os.path.getmtime(file_path) > max_age_seconds:
                    os.remove(file_path)
            except Exception as e:
                logger.error(f"Cleanup error on {file_path}: {e}")

def worker_loop(directories, max_age, interval):
    while True:
        cleanup_expired_files(directories, max_age)
        time.sleep(interval)

@app.on_event("startup")
async def startup_event():
    threading.Thread(target=worker_loop, args=([CLIPS_DIR, RAW_UPLOADS_DIR], 1800, 300), daemon=True).start()
    get_model() # Pre-load AI model

# ============================================================================
# TIER 1: INGESTION & STORAGE LAYER
# ============================================================================
class ProcessRequest(BaseModel):
    url: str
    num_clips: int = 3

def download_video_ingest(url: str, out_dir: str) -> str:
    """Ingests a video from a remote URL into the raw storage layer"""
    tpl = os.path.join(out_dir, f"video_{uuid.uuid4().hex[:8]}.%(ext)s")
    for cmd in [
        ["yt-dlp", "-f", "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best",
         "--merge-output-format", "mp4", "--no-warnings", "--quiet", "--geo-bypass", "-o", tpl, url],
        ["yt-dlp", "-f", "worst[ext=mp4]/worst", "--no-warnings", "--quiet", "-o", tpl, url],
    ]:
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=300)
        if r.returncode == 0:
            for f in os.listdir(out_dir):
                if f.startswith("video_") and f.endswith(".mp4"):
                    p = os.path.join(out_dir, f)
                    if os.path.getmtime(p) > time.time() - 300 and os.path.getsize(p) > 100_000:
                        return p
    raise Exception("Ingestion failed: URL unreachable or geo-restricted.")

# ============================================================================
# TIER 2: AI PROCESSING ENGINE
# ============================================================================
WHISPER_MODEL = os.getenv("WHISPER_MODEL", "tiny")
_MODEL = None

def get_model():
    global _MODEL
    if _MODEL is None:
        import whisper
        logger.info(f"Loading AI Engine: Whisper ({WHISPER_MODEL})...")
        _MODEL = whisper.load_model(WHISPER_MODEL)
    return _MODEL

class Hook(str, Enum):
    SECRET = "Secret"; REVELATION = "Revelation"; MONEY = "Money"; WARNING = "Warning"
    CURIOSITY = "Curiosity"; STORY = "Story"; TUTORIAL = "Tutorial"; CONTRARIAN = "Contrarian"
    EMOTIONAL = "Emotional"; RETENTION = "Retention"; GENERAL = "General"

@dataclass
class Chunk:
    start: float; end: float; text: str; words: List[dict]; score: int = 0
    hook: str = "General"; reasons: List[str] = field(default_factory=list); viable: bool = False
    @property
    def duration(self): return self.end - self.start
    def title(self) -> str:
        raw = " ".join(w["word"] for w in self.words[:6])
        return re.sub(r"[^\w\s]", "", raw).strip().capitalize()

def transcribe(video_path: str) -> List[dict]:
    result = get_model().transcribe(video_path, word_timestamps=True, verbose=False, condition_on_previous_text=True)
    out = []
    for seg in result.get("segments", []):
        for w in seg.get("words", []):
            t = w.get("word", "").strip()
            if t: out.append({"word": t, "startMs": int(w["start"] * 1000), "endMs": int(w["end"] * 1000)})
    return out

def semantic_chunks(words: List[dict], max_gap_ms: int = 1800, min_words: int = 10, max_dur_ms: int = 90_000) -> List[Chunk]:
    if not words: return []
    SHIFTS = {"however","but","moving on","next","now","speaking of","anyway","alright","so anyway"}
    chunks: List[Chunk] = []; buf: List[dict] = []; c_start = words[0]["startMs"]; last_end = words[0]["endMs"]
    def flush():
        if len(buf) >= min_words:
            chunks.append(Chunk(start=buf[0]["startMs"]/1000, end=buf[-1]["endMs"]/1000, text=" ".join(w["word"] for w in buf), words=list(buf)))
    for w in words:
        gap = w["startMs"] - last_end; dur = w["startMs"] - c_start; wl = w["word"].lower().strip(".,!?")
        if ((gap > max_gap_ms or (len(buf) > 6 and buf[-1]["word"].endswith((".", "!", "?"))) or wl in SHIFTS or dur > max_dur_ms) and len(buf) >= min_words):
            flush(); buf = [w]; c_start = w["startMs"]
        else: buf.append(w)
        last_end = w["endMs"]
    flush()
    return chunks

def score(c: Chunk) -> Chunk:
    tl = c.text.lower(); dur = c.duration; pts = 40; reasons = []; hook = Hook.GENERAL
    if not (15 <= dur <= 90):
        c.score = 0; c.viable = False; return c
    
    T1 = [(r"nobody (talks about|tells you)", Hook.SECRET, 35), (r"the (real|hidden) truth", Hook.REVELATION, 33)]
    for pat, h, p in T1:
        if re.search(pat, tl): pts += p; hook = h; reasons.append(f"Strong hook — {h.value}"); break
        
    c.score = min(100, max(0, pts)); c.hook = hook.value; c.reasons = reasons; c.viable = c.score >= 55
    return c

def select(scored: List[Chunk], n: int) -> List[Chunk]:
    pool = sorted([c for c in scored if c.viable], key=lambda x: -x.score)
    if not pool: pool = sorted(scored, key=lambda x: -x.score)
    sel: List[Chunk] = []; used: List[tuple] = []
    for c in pool:
        if len(sel) >= n: break
        if any(not (c.end+5 < s or c.start-5 > e) for s, e in used): continue
        sel.append(c); used.append((c.start, c.end))
    sel.sort(key=lambda x: x.start)
    return sel

def calculate_smart_crop_x(video_path: str, target_aspect: float = 9/16) -> int:
    """Computer Vision: Analyzes frames to track facial/subject center for dynamic cropping."""
    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened(): return -1
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    crop_w = int(height * target_aspect)
    
    if width <= crop_w:
        cap.release()
        return 0

    face_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + 'haarcascade_frontalface_default.xml')
    centers = []; frames_checked = 0
    
    while frames_checked < 60: # Sample first 2 seconds
        ret, frame = cap.read()
        if not ret: break
        if frames_checked % 5 == 0:
            gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
            faces = face_cascade.detectMultiScale(gray, 1.3, 5)
            for (x,y,w,h) in faces:
                centers.append(x + w//2)
        frames_checked += 1
    cap.release()
    
    if centers:
        avg_center = int(sum(centers) / len(centers))
        crop_x = avg_center - (crop_w // 2)
    else:
        crop_x = (width - crop_w) // 2
        
    return max(0, min(crop_x, width - crop_w))

# ============================================================================
# TIER 3: DELIVERY LAYER (Transcoding & CDN)
# ============================================================================
def transcode_and_deliver(src: str, start: float, end: float, out: str, crop_x: int):
    """Background delivery task using FFmpeg for transcoding"""
    dur = end - start
    vf = f"crop=ih*9/16:ih:{crop_x}:0,scale=1080:1920,fps=30" if crop_x >= 0 else "crop=ih*9/16:ih,scale=1080:1920,fps=30"
    
    cmd = [
        "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
        "-ss", str(start), "-i", src, "-t", str(dur),
        "-vf", vf, "-c:v", "libx264", "-preset", "ultrafast", "-crf", "28",
        "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", "-pix_fmt", "yuv420p", out
    ]
    subprocess.run(cmd, capture_output=True, text=True)

# ── Endpoints ─────────────────────────────────────────────────────────────
async def run_pipeline(vpath: str, url_or_name: str, n: int, base: str, background_tasks: BackgroundTasks) -> dict:
    logger.info("Starting AI Engine analysis...")
    words = transcribe(vpath)
    if not words: raise HTTPException(500, "No speech detected")
    
    chunks = semantic_chunks(words)
    scored = [score(c) for c in chunks]
    chosen = select(scored, n)

    logger.info("Computing Smart Crop via OpenCV...")
    crop_x = calculate_smart_crop_x(vpath)

    clips_out = []
    for i, chunk in enumerate(chosen):
        fname = f"clip_{uuid.uuid4().hex[:8]}_{i}.mp4"
        fpath = os.path.join(CLIPS_DIR, fname)
        clip_url = f"{base}/clips/{fname}"
        
        # Enqueue transcoding to Delivery Layer asynchronously
        background_tasks.add_task(transcode_and_deliver, vpath, chunk.start, chunk.end, fpath, crop_x)

        off = chunk.words[0]["startMs"]
        captions = [{"word": w["word"], "startMs": w["startMs"] - off, "endMs": w["endMs"] - off} for w in chunk.words]
        
        clips_out.append({
            "title": f"🔥 {chunk.title()}...", "startSec": int(chunk.start), "endSec": int(chunk.end),
            "viralScore": chunk.score, "viralReason": chunk.reasons[0] if chunk.reasons else "High-engagement",
            "captions": captions, "clipUrl": clip_url, "clip_url": clip_url,
            "hookType": chunk.hook, "allReasons": chunk.reasons, "durationSec": round(chunk.duration, 1),
        })
        
    return {"url": url_or_name, "duration": words[-1]["endMs"]/1000 if words else 0, "clips": clips_out}

@app.post("/api/process")
async def process_video_api(body: ProcessRequest, request: Request, background_tasks: BackgroundTasks):
    if not body.url.strip(): raise HTTPException(400, "URL cannot be empty")
    base = str(request.base_url).rstrip("/")
    try:
        vpath = download_video_ingest(body.url.strip(), RAW_UPLOADS_DIR)
        return await run_pipeline(vpath, body.url.strip(), max(1, min(body.num_clips, 8)), base, background_tasks)
    except Exception as e:
        logger.error(f"Process failed: {e}")
        raise HTTPException(500, str(e))

@app.post("/api/upload")
async def upload_video_api(request: Request, background_tasks: BackgroundTasks, file: UploadFile = File(...), num_clips: int = Form(3)):
    base = str(request.base_url).rstrip("/")
    ext = os.path.splitext(file.filename)[1] or ".mp4"
    vpath = os.path.join(RAW_UPLOADS_DIR, f"uploaded_{uuid.uuid4().hex[:8]}{ext}")
    try:
        with open(vpath, "wb") as buffer:
            shutil.copyfileobj(file.file, buffer)
        return await run_pipeline(vpath, file.filename, max(1, min(num_clips, 8)), base, background_tasks)
    except Exception as e:
        logger.error(f"Upload failed: {e}")
        raise HTTPException(500, str(e))

@app.get("/clips/{filename}")
async def serve_clip(filename: str):
    path = os.path.join(CLIPS_DIR, filename)
    if not os.path.exists(path):
        raise HTTPException(404, "Clip not found or processing not complete")
    return FileResponse(path, media_type="video/mp4")

@app.get("/health")
def health():
    return {"status": "ok", "architecture": "3-Tier (Ingestion, AI Core, Delivery)"}

@app.get("/")
def root():
    return {"api": "POST /api/process, POST /api/upload", "health": "GET /health"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=int(os.getenv("PORT", 8000)))
