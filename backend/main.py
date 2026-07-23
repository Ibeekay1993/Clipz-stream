import os, re, uuid, time, subprocess, shutil
from dataclasses import dataclass, field
from typing import List
from enum import Enum
import threading
import logging

from fastapi import FastAPI, HTTPException, Request, File, UploadFile, Form
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from pydantic import BaseModel
from dotenv import load_dotenv

load_dotenv()

# External APIs
from groq import Groq
from supabase import create_client, Client

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("backend_core")

app = FastAPI(title="Clipz-Stream Backend (Groq + Supabase Architecture)")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_credentials=True, allow_methods=["*"], allow_headers=["*"])

# ── Storage Configuration ──────────────────────────────────────────────────
RAW_UPLOADS_DIR = "raw_uploads"
CLIPS_DIR = "clips"
os.makedirs(RAW_UPLOADS_DIR, exist_ok=True)
os.makedirs(CLIPS_DIR, exist_ok=True)

# Supabase Initialization
SUPABASE_URL = os.getenv("SUPABASE_URL")
SUPABASE_KEY = os.getenv("SUPABASE_KEY")
supabase: Client = create_client(SUPABASE_URL, SUPABASE_KEY) if SUPABASE_URL and SUPABASE_KEY else None

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
    if not supabase:
        logger.warning("Supabase credentials not found. Clips will be served locally instead of CDN.")

# ============================================================================
# TIER 1: INGESTION & STORAGE LAYER
# ============================================================================
class ProcessRequest(BaseModel):
    url: str
    num_clips: int = 3

def download_video_ingest(url: str, out_dir: str) -> str:
    tpl = os.path.join(out_dir, f"video_{uuid.uuid4().hex[:8]}.%(ext)s")
    for cmd in [
        ["yt-dlp", "-f", "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best",
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
# TIER 2: AI PROCESSING ENGINE (GROQ)
# ============================================================================
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
    # 1. Extract audio
    audio_path = video_path + ".mp3"
    subprocess.run(["ffmpeg", "-y", "-i", video_path, "-vn", "-c:a", "libmp3lame", "-q:a", "5", audio_path], check=True, capture_output=True)
    
    # 2. Call Groq
    client = Groq(api_key=os.getenv("GROQ_API_KEY"))
    with open(audio_path, "rb") as f:
        resp = client.audio.transcriptions.create(
            file=("audio.mp3", f.read()),
            model="whisper-large-v3",
            response_format="verbose_json"
        )
    os.remove(audio_path)
    
    # 3. Parse words
    out = []
    data = resp.model_dump()
    for seg in data.get("segments", []):
        text = seg.get("text", "").strip()
        start = seg.get("start", 0.0)
        end = seg.get("end", 0.0)
        words = text.split()
        if not words: continue
        duration_per_word = (end - start) / len(words)
        for i, w in enumerate(words):
            w_start = start + (i * duration_per_word)
            w_end = w_start + duration_per_word
            out.append({"word": w, "startMs": int(w_start * 1000), "endMs": int(w_end * 1000)})
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
    tl = c.text.lower(); dur = c.duration; pts = 70; reasons = ["High-retention speech hook"]; hook = Hook.GENERAL
    if not (10 <= dur <= 90):
        c.score = 0; c.viable = False; return c
    
    T1 = [(r"nobody (talks about|tells you)", Hook.SECRET, 25), (r"the (real|hidden) truth", Hook.REVELATION, 23)]
    for pat, h, p in T1:
        if re.search(pat, tl): pts += p; hook = h; reasons.append(f"Strong hook — {h.value}"); break
        
    c.score = min(99, max(50, pts)); c.hook = hook.value; c.reasons = reasons; c.viable = True
    return c

def select(scored: List[Chunk], n: int) -> List[Chunk]:
    pool = sorted([c for c in scored if c.viable], key=lambda x: -x.score)
    if not pool: pool = sorted(scored, key=lambda x: -x.score)
    sel: List[Chunk] = []; used: List[tuple] = []
    for c in pool:
        if len(sel) >= n: break
        if any(not (c.end+3 < s or c.start-3 > e) for s, e in used): continue
        sel.append(c); used.append((c.start, c.end))
    sel.sort(key=lambda x: x.start)
    return sel

# ============================================================================
# TIER 3: DELIVERY LAYER (FFmpeg Safe Crop & Supabase)
# ============================================================================
def transcode_and_upload(src: str, start: float, end: float, out: str) -> str:
    """Safe FFmpeg crop + Supabase CDN Upload"""
    dur = end - start
    
    # 720p 9:16 Crop: Fast encoding, clean vertical fit
    vf = "scale=720:1280:force_original_aspect_ratio=increase,crop=720:1280,fps=30"
    
    cmd = [
        "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
        "-ss", str(start), "-i", src, "-t", str(dur),
        "-vf", vf, "-c:v", "libx264", "-preset", "ultrafast", "-crf", "26",
        "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", "-pix_fmt", "yuv420p", out
    ]
    subprocess.run(cmd, capture_output=True, text=True, check=True)

    # Upload to Supabase Storage
    if supabase:
        fname = os.path.basename(out)
        with open(out, 'rb') as f:
            res = supabase.storage.from_("clips").upload(file=f, path=fname, file_options={"content-type": "video/mp4"})
            logger.info(f"Supabase upload response: {res}")
        public_url = supabase.storage.from_("clips").get_public_url(fname)
        return public_url
    else:
        return f"/clips/{os.path.basename(out)}"

# ── Endpoints ─────────────────────────────────────────────────────────────
async def run_pipeline(vpath: str, url_or_name: str, n: int, base: str) -> dict:
    logger.info("Starting Groq AI Engine analysis...")
    words = transcribe(vpath)
    if not words: raise HTTPException(500, "No speech detected")
    
    chunks = semantic_chunks(words)
    scored = [score(c) for c in chunks]
    chosen = select(scored, n)

    logger.info("Transcoding and uploading clips...")
    clips_out = []
    
    # We do this synchronously so the API returns the final Supabase URLs instantly!
    for i, chunk in enumerate(chosen):
        fname = f"clip_{uuid.uuid4().hex[:8]}_{i}.mp4"
        fpath = os.path.join(CLIPS_DIR, fname)
        
        try:
            clip_url = transcode_and_upload(vpath, chunk.start, chunk.end, fpath)
            
            # If it's a relative URL, prepend the base (Fallback)
            if clip_url.startswith("/clips/"):
                clip_url = f"{base}{clip_url}"
                
            off = chunk.words[0]["startMs"]
            captions = [{"word": w["word"], "startMs": w["startMs"] - off, "endMs": w["endMs"] - off} for w in chunk.words]
            
            clips_out.append({
                "title": f"🔥 {chunk.title()}...", "startSec": int(chunk.start), "endSec": int(chunk.end),
                "viralScore": chunk.score, "viralReason": chunk.reasons[0] if chunk.reasons else "High-engagement",
                "captions": captions, "clipUrl": clip_url, "clip_url": clip_url,
                "hookType": chunk.hook, "allReasons": chunk.reasons, "durationSec": round(chunk.duration, 1),
            })
        except Exception as e:
            logger.error(f"Failed to transcode chunk {i}: {e}")

    return {"url": url_or_name, "duration": words[-1]["endMs"]/1000 if words else 0, "clips": clips_out}

@app.post("/api/process")
async def process_video_api(body: ProcessRequest, request: Request):
    if not body.url.strip(): raise HTTPException(400, "URL cannot be empty")
    base = str(request.base_url).rstrip("/")
    try:
        vpath = download_video_ingest(body.url.strip(), RAW_UPLOADS_DIR)
        return await run_pipeline(vpath, body.url.strip(), max(1, min(body.num_clips, 8)), base)
    except Exception as e:
        logger.error(f"Process failed: {e}")
        raise HTTPException(500, str(e))

@app.post("/api/upload")
async def upload_video_api(request: Request, file: UploadFile = File(...), num_clips: int = Form(3)):
    base = str(request.base_url).rstrip("/")
    ext = os.path.splitext(file.filename)[1] or ".mp4"
    vpath = os.path.join(RAW_UPLOADS_DIR, f"uploaded_{uuid.uuid4().hex[:8]}{ext}")
    try:
        with open(vpath, "wb") as buffer:
            shutil.copyfileobj(file.file, buffer)
        return await run_pipeline(vpath, file.filename, max(1, min(num_clips, 8)), base)
    except Exception as e:
        logger.error(f"Upload failed: {e}")
        raise HTTPException(500, str(e))

@app.get("/clips/{filename}")
async def serve_clip(filename: str):
    path = os.path.join(CLIPS_DIR, filename)
    if not os.path.exists(path):
        raise HTTPException(404, "Clip not found")
    return FileResponse(path, media_type="video/mp4")

@app.get("/health")
def health():
    return {"status": "ok", "architecture": "Groq + Supabase Serverless"}

@app.get("/")
def root():
    return {"api": "POST /api/process, POST /api/upload", "health": "GET /health"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=int(os.getenv("PORT", 8000)))
