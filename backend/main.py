import os, re, uuid, time, subprocess, shutil, json
from dataclasses import dataclass, field
from typing import List
from enum import Enum
import threading
import logging
import yt_dlp

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
        for fname in os.listdir(directory):
            fpath = os.path.join(directory, fname)
            try:
                if os.path.isfile(fpath) and (now - os.path.getmtime(fpath)) > max_age_seconds:
                    os.remove(fpath)
            except Exception as e:
                logger.warning(f"Failed to delete {fpath}: {e}")

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
    out_file = os.path.join(out_dir, f"video_{uuid.uuid4().hex[:8]}.mp4")
    
    # Industry Best Practice: YouTube Session Cookie Authentication
    cookie_path = os.path.join(out_dir, "yt_cookies.txt")
    cookies_content = os.getenv("YOUTUBE_COOKIES", "")
    if cookies_content and not os.path.exists(cookie_path):
        try:
            with open(cookie_path, "w", encoding="utf-8") as f:
                f.write(cookies_content)
        except Exception as e:
            logger.warning(f"Failed to write cookies file: {e}")
            
    has_cookies = os.path.exists(cookie_path) and os.path.getsize(cookie_path) > 10
    
    clients_attempts = [
        ['mweb', 'ios', 'android'],
        ['tv_embedded', 'web_creator'],
        ['web', 'mweb']
    ]
    
    for client_list in clients_attempts:
        ydl_opts = {
            'format': 'bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[height<=720][ext=mp4]/best',
            'outtmpl': out_file,
            'quiet': True,
            'no_warnings': True,
            'geo_bypass': True,
            'merge_output_format': 'mp4',
            'extractor_args': {
                'youtube': {
                    'player_client': client_list
                }
            }
        }
        if has_cookies:
            ydl_opts['cookiefile'] = cookie_path
            
        try:
            logger.info(f"Downloading video via yt_dlp ({client_list}, cookies={has_cookies}): {url}")
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                ydl.download([url])
            if os.path.exists(out_file) and os.path.getsize(out_file) > 100_000:
                return out_file
        except Exception as e:
            logger.warning(f"Client list {client_list} failed: {e}")
            
    # Check if file was saved under slightly different extension
    for f in os.listdir(out_dir):
        if f.startswith("video_"):
            p = os.path.join(out_dir, f)
            if os.path.getmtime(p) > time.time() - 300 and os.path.getsize(p) > 100_000:
                return p

    raise Exception(f"Ingestion failed for {url}. Datacenter bot restriction. Please use the Upload Video tab.")

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
        # Smart Deduplication: ensure minimum 5.0 second gap between clips
        if any(not (c.end + 5.0 < s or c.start - 5.0 > e) for s, e in used): continue
        sel.append(c); used.append((c.start, c.end))
    sel.sort(key=lambda x: x.start)
    return sel

def generate_ass_file(words: List[dict], clip_start_sec: float, ass_out_path: str):
    """Generates an ASS subtitle file with kinetic word highlighting and bold stroke outline"""
    header = """[Script Info]
ScriptType: v4.00+
PlayResX: 720
PlayResY: 1280

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,Arial,36,&H00FFFFFF,&H0000FFFF,&H00000000,&H80000000,-1,0,0,0,100,100,0,0,1,4,0,2,20,20,160,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
"""
    lines_events = []
    group = []
    clip_words = [w for w in words if (w.get("startMs", 0)/1000.0) >= clip_start_sec - 0.5]
    
    for i, w in enumerate(clip_words):
        group.append(w)
        if len(group) >= 4 or i == len(clip_words) - 1:
            line_start_sec = max(0, (group[0]["startMs"] / 1000.0) - clip_start_sec)
            line_end_sec = max(line_start_sec + 0.4, (group[-1]["endMs"] / 1000.0) - clip_start_sec)
            
            def format_time(sec):
                hrs = int(sec // 3600)
                mins = int((sec % 3600) // 60)
                secs = int(sec % 60)
                cs = int((sec % 1) * 100)
                return f"{hrs}:{mins:02d}:{secs:02d}.{cs:02d}"
            
            start_str = format_time(line_start_sec)
            end_str = format_time(line_end_sec)
            line_text = " ".join(str(w.get("word", "")).upper() for w in group)
            event_line = f"Dialogue: 0,{start_str},{end_str},Default,,0,0,0,,{{\\c&H0000FFFF&}}{line_text}"
            lines_events.append(event_line)
            group = []
            
    with open(ass_out_path, "w", encoding="utf-8") as f:
        f.write(header + "\n".join(lines_events))

# ============================================================================
# TIER 3: DELIVERY LAYER (FFmpeg Safe Crop & Supabase)
# ============================================================================
def transcode_and_upload(src: str, start: float, end: float, out: str, words: List[dict] = None) -> str:
    """Safe FFmpeg crop + ASS Kinetic Burned-In Subtitles + Supabase CDN Upload"""
    dur = end - start
    
    # Generate ASS Subtitle File if words provided
    ass_path = out + ".ass"
    use_subtitles = False
    if words:
        try:
            generate_ass_file(words, start, ass_path)
            use_subtitles = os.path.exists(ass_path)
        except Exception as e:
            logger.warning(f"ASS subtitle generation failed: {e}")
            
    if use_subtitles:
        escaped_ass = ass_path.replace("\\", "/").replace(":", "\\:")
        vf = f"scale=720:1280:force_original_aspect_ratio=increase,crop=720:1280,subtitles='{escaped_ass}',fps=30"
    else:
        vf = "scale=720:1280:force_original_aspect_ratio=increase,crop=720:1280,fps=30"
        
    cmd = [
        "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
        "-ss", str(start), "-i", src, "-t", str(dur),
        "-vf", vf, "-c:v", "libx264", "-preset", "ultrafast", "-crf", "26",
        "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", "-pix_fmt", "yuv420p", out
    ]
    subprocess.run(cmd, capture_output=True, text=True, check=True)
    if os.path.exists(ass_path):
        try: os.remove(ass_path)
        except: pass

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

def llama_analyze_chunks(chunks: List[Chunk], n: int) -> List[dict]:
    """Call Groq Llama-3-70B to score clips, write viral titles & identify hooks"""
    client = Groq(api_key=os.getenv("GROQ_API_KEY"))
    
    payload_chunks = []
    for idx, c in enumerate(chunks):
        payload_chunks.append({
            "id": idx,
            "start": round(c.start, 1),
            "end": round(c.end, 1),
            "duration": round(c.duration, 1),
            "text": c.text
        })
        
    prompt = f"""
You are an elite viral video editor for TikTok, Instagram Reels, and YouTube Shorts (like Opus Clip & Wayin).
Analyze the following transcript chunks from a video and select the top {n} most engaging, high-retention segments.

TRANSCRIPT CHUNKS:
{json.dumps(payload_chunks, indent=2)}

Return ONLY a JSON object with key "clips". Each clip must have:
- "chunk_id": int (the id of the selected chunk)
- "title": str (catchy headline with emoji, max 6 words)
- "viralScore": int (82 to 99)
- "hookType": str (one of: Secret, Revelation, Story, Contrarian, Tutorial, Curiosity, Emotional, Warning)
- "viralReason": str (1 crisp sentence explaining why this clip will perform well)
"""
    try:
        resp = client.chat.completions.create(
            model="llama-3.3-70b-versatile",
            messages=[{"role": "user", "content": prompt}],
            response_format={"type": "json_object"}
        )
        data = json.loads(resp.choices[0].message.content)
        return data.get("clips", [])
    except Exception as e:
        logger.error(f"Groq Llama-3 70B analysis error: {e}")
        return []

# ── Endpoints ─────────────────────────────────────────────────────────────
JOBS = {}

async def run_pipeline(vpath: str, url_or_name: str, n: int, base: str, job_id: str = None) -> dict:
    def update_job(prog: int, step: str):
        if job_id and job_id in JOBS:
            JOBS[job_id]["progress"] = prog
            JOBS[job_id]["current_step"] = step

    update_job(10, "Extracting audio & Groq WhisperX transcription...")
    logger.info("Starting Groq Whisper AI Engine analysis...")
    words = transcribe(vpath)
    if not words: raise HTTPException(500, "No speech detected in video")
    
    update_job(35, "Groq Llama-3-70B Viral Hook & Retention Analysis...")
    chunks = semantic_chunks(words)
    if not chunks: raise HTTPException(500, "Could not form speech segments")

    # Call Llama-3 70B AI Engine
    llama_analysis = llama_analyze_chunks(chunks, n)
    
    chosen_chunks = []
    if llama_analysis:
        for la in llama_analysis:
            cid = la.get("chunk_id", 0)
            if 0 <= cid < len(chunks):
                c = chunks[cid]
                c.score = la.get("viralScore", 85)
                c.hook = la.get("hookType", "General")
                c.reasons = [la.get("viralReason", "High-engagement viral hook")]
                c.text = la.get("title", c.title()) # Override title
                chosen_chunks.append((c, la.get("title", c.title())))
    
    if not chosen_chunks:
        scored = [score(c) for c in chunks]
        chosen = select(scored, n)
        chosen_chunks = [(c, c.title()) for c in chosen]

    update_job(60, "FFmpeg 9:16 Safe Crop & Supabase CDN Transcoding...")
    logger.info("Transcoding and uploading clips...")
    clips_out = []
    
    total = len(chosen_chunks)
    for i, (chunk, title_text) in enumerate(chosen_chunks):
        prog_inc = int(60 + ((i + 1) / max(1, total)) * 35)
        update_job(prog_inc, f"Transcoding clip {i+1} of {total}...")
        
        fname = f"clip_{uuid.uuid4().hex[:8]}_{i}.mp4"
        fpath = os.path.join(CLIPS_DIR, fname)
        
        try:
            clip_url = transcode_and_upload(vpath, chunk.start, chunk.end, fpath, words=chunk.words)
            if clip_url.startswith("/clips/"):
                clip_url = f"{base}{clip_url}"
                
            off = chunk.words[0]["startMs"]
            captions = [{"word": w["word"], "startMs": w["startMs"] - off, "endMs": w["endMs"] - off} for w in chunk.words]
            
            t_clean = title_text if title_text.startswith("🔥") or title_text.startswith("⚡") else f"🔥 {title_text}"
            clips_out.append({
                "title": t_clean, "startSec": int(chunk.start), "endSec": int(chunk.end),
                "viralScore": chunk.score, "viralReason": chunk.reasons[0] if chunk.reasons else "High-engagement clip",
                "captions": captions, "clipUrl": clip_url, "clip_url": clip_url,
                "hookType": chunk.hook, "allReasons": chunk.reasons, "durationSec": round(chunk.duration, 1),
            })
        except Exception as e:
            logger.error(f"Failed to transcode chunk {i}: {e}")

    result = {"url": url_or_name, "duration": words[-1]["endMs"]/1000 if words else 0, "clips": clips_out}
    update_job(100, "Complete")
    return result

def execute_job_bg(job_id: str, vpath: str, url_or_name: str, n: int, base: str):
    try:
        import asyncio
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        res = loop.run_until_complete(run_pipeline(vpath, url_or_name, n, base, job_id))
        JOBS[job_id]["status"] = "completed"
        JOBS[job_id]["progress"] = 100
        JOBS[job_id]["result"] = res
    except Exception as e:
        logger.error(f"Job {job_id} failed: {e}")
        JOBS[job_id]["status"] = "failed"
        JOBS[job_id]["error"] = str(e)

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

@app.post("/api/jobs/create")
async def create_job_api(body: ProcessRequest, request: Request):
    if not body.url.strip(): raise HTTPException(400, "URL cannot be empty")
    base = str(request.base_url).rstrip("/")
    job_id = uuid.uuid4().hex[:12]
    JOBS[job_id] = {
        "job_id": job_id, "status": "processing", "progress": 2, 
        "current_step": "Ingesting video & validating media streams...", "result": None, "error": None
    }
    
    def worker():
        try:
            vpath = download_video_ingest(body.url.strip(), RAW_UPLOADS_DIR)
            execute_job_bg(job_id, vpath, body.url.strip(), max(1, min(body.num_clips, 8)), base)
        except Exception as e:
            JOBS[job_id]["status"] = "failed"
            JOBS[job_id]["error"] = str(e)

    threading.Thread(target=worker, daemon=True).start()
    return {"job_id": job_id, "status": "processing"}

@app.get("/api/jobs/status/{job_id}")
async def get_job_status(job_id: str):
    if job_id not in JOBS:
        raise HTTPException(404, "Job not found")
    return JOBS[job_id]

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
    return {"status": "ok", "architecture": "Groq Llama-3-70B + Supabase Serverless"}

@app.get("/")
def root():
    return {"api": "POST /api/process, POST /api/jobs/create, GET /api/jobs/status/{job_id}", "health": "GET /health"}

# Modal Cloud Deployment Handler
try:
    import modal
    image = (
        modal.Image.debian_slim(python_version="3.11")
        .apt_install("ffmpeg")
        .pip_install("fastapi", "yt-dlp", "groq", "requests", "supabase", "python-dotenv", "python-multipart")
    )
    modal_app = modal.App("clipz-stream-fastapi-app")
    @modal_app.function(image=image, timeout=600, cpu=2.0)
    @modal.asgi_app()
    def fastapi_app():
        return app
except Exception:
    pass

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=int(os.getenv("PORT", 8000)))
