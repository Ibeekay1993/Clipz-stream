import os, re, uuid, time, json, subprocess, tempfile
from dataclasses import dataclass, field
from typing import List, Dict, Optional
from enum import Enum

from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from pydantic import BaseModel

from services.cleanup import start_background_cleanup_worker

app = FastAPI(title="Clipz-Stream Backend")

@app.on_event("startup")
async def startup_event():
    start_background_cleanup_worker("clips", max_age_seconds=1800, interval_seconds=300)

app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_credentials=True,
                   allow_methods=["*"], allow_headers=["*"])

CLIPS_DIR = "clips"
os.makedirs(CLIPS_DIR, exist_ok=True)

WHISPER_MODEL = os.getenv("WHISPER_MODEL", "base")
_MODEL = None

def get_model():
    global _MODEL
    if _MODEL is None:
        import whisper
        print(f"Loading Whisper {WHISPER_MODEL}...")
        _MODEL = whisper.load_model(WHISPER_MODEL)
    return _MODEL


# ── Hook taxonomy ────────────────────────────────────────────────────────────

class Hook(str, Enum):
    SECRET      = "Secret"
    REVELATION  = "Revelation"
    MONEY       = "Money"
    WARNING     = "Warning"
    CURIOSITY   = "Curiosity"
    STORY       = "Story"
    TUTORIAL    = "Tutorial"
    CONTRARIAN  = "Contrarian"
    EMOTIONAL   = "Emotional"
    RETENTION   = "Retention"
    GENERAL     = "General"


# ── Data ─────────────────────────────────────────────────────────────────────

@dataclass
class Chunk:
    start:   float
    end:     float
    text:    str
    words:   List[dict]
    score:   int  = 0
    hook:    str  = "General"
    reasons: List[str] = field(default_factory=list)
    viable:  bool = False

    @property
    def duration(self): return self.end - self.start

    def title(self) -> str:
        """First 6 words, cleaned — used as clip title"""
        raw = " ".join(w["word"] for w in self.words[:6])
        return re.sub(r"[^\w\s]", "", raw).strip().capitalize()


# ── Step 1: Download ──────────────────────────────────────────────────────────

def download_video(url: str, out_dir: str) -> str:
    tpl = os.path.join(out_dir, "video.%(ext)s")
    for cmd in [
        ["yt-dlp", "-f",
         "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best",
         "--merge-output-format", "mp4",
         "--no-warnings", "--quiet", "--geo-bypass", "--retries", "3",
         "-o", tpl, url],
        ["yt-dlp", "-f", "worst[ext=mp4]/worst",
         "--no-warnings", "--quiet", "-o", tpl, url],
    ]:
        print(f"Executing yt-dlp: {' '.join(cmd)}")
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=300)
        if r.returncode == 0:
            for f in os.listdir(out_dir):
                if f.startswith("video.") and f.endswith(".mp4"):
                    p = os.path.join(out_dir, f)
                    if os.path.getsize(p) > 100_000:
                        return p
    raise Exception("Download failed — URL may be private, invalid, or geo-restricted")


# ── Step 2: Transcribe (word-level) ──────────────────────────────────────────

def transcribe(video_path: str) -> List[dict]:
    """Returns flat word list: [{word, startMs, endMs}]"""
    result = get_model().transcribe(video_path, word_timestamps=True,
                                    verbose=False, condition_on_previous_text=True)
    out = []
    for seg in result.get("segments", []):
        for w in seg.get("words", []):
            t = w.get("word", "").strip()
            if t:
                out.append({"word": t,
                             "startMs": int(w["start"] * 1000),
                             "endMs":   int(w["end"]   * 1000)})
    return out


# ── Step 3: Semantic chunking ─────────────────────────────────────────────────

def semantic_chunks(words: List[dict],
                    max_gap_ms: int = 1800,
                    min_words:  int = 10,
                    max_dur_ms: int = 90_000) -> List[Chunk]:
    if not words: return []

    SHIFTS = {"however","but","moving on","next","now","speaking of",
               "anyway","alright","so anyway"}
    chunks: List[Chunk] = []
    buf: List[dict] = []
    c_start = words[0]["startMs"]
    last_end = words[0]["endMs"]

    def flush():
        if len(buf) >= min_words:
            chunks.append(Chunk(
                start = buf[0]["startMs"] / 1000,
                end   = buf[-1]["endMs"]  / 1000,
                text  = " ".join(w["word"] for w in buf),
                words = list(buf)))

    for w in words:
        gap = w["startMs"] - last_end
        dur = w["startMs"] - c_start
        wl  = w["word"].lower().strip(".,!?")
        if ((gap > max_gap_ms
             or (len(buf) > 6 and buf[-1]["word"].endswith((".", "!", "?")))
             or wl in SHIFTS
             or dur > max_dur_ms)
                and len(buf) >= min_words):
            flush(); buf = [w]; c_start = w["startMs"]
        else:
            buf.append(w)
        last_end = w["endMs"]
    flush()
    print(f"  ✓ {len(chunks)} chunks")
    return chunks


# ── Step 4: Virality scoring ──────────────────────────────────────────────────

def score(c: Chunk) -> Chunk:
    tl  = c.text.lower()
    dur = c.duration

    if not (15 <= dur <= 90):
        c.score = 0; c.viable = False; return c

    pts = 40
    reasons: List[str] = []
    hook = Hook.GENERAL

    # Tier 1 — strong hooks (+28–35)
    T1 = [
        (r"nobody (talks about|tells you|knows about|wants you to know)", Hook.SECRET,     35),
        (r"the (real|hidden|ugly|actual|shocking) (truth|reason|secret)",  Hook.REVELATION, 33),
        (r"i (lost|made|earned|saved|wasted) [\$]?\d+",                    Hook.MONEY,      33),
        (r"(stop|don't|never) (doing|making|wasting|believing)",           Hook.WARNING,    28),
        (r"what (if|happens when|nobody tells you)",                        Hook.CURIOSITY,  28),
        (r"(stay|watch|listen) (until|till) the end",                      Hook.RETENTION,  28),
        (r"most people (don't|won't|can't|never) (know|realize|do)",       Hook.CONTRARIAN, 26),
    ]
    for pat, h, p in T1:
        if re.search(pat, tl):
            pts += p; hook = h; reasons.append(f"Strong hook — {h.value}"); break

    # Tier 2 — medium hooks (+18–25)
    if hook == Hook.GENERAL:
        T2 = [
            (r"here('s| is) (why|how|the truth|the real reason)",  Hook.REVELATION, 25),
            (r"i used to (think|believe|do|feel)",                  Hook.STORY,      22),
            (r"how to ",                                             Hook.TUTORIAL,   22),
            (r"step \d+",                                            Hook.TUTORIAL,   18),
        ]
        for pat, h, p in T2:
            if re.search(pat, tl):
                pts += p; hook = h; reasons.append(f"Hook — {h.value}"); break

    # Emotion
    for p, words in [(20, ["shocking","unbelievable","insane","crazy","devastating","heartbreaking"]),
                     (12, ["amazing","incredible","frustrated","thrilled","furious"]),
                     ( 6, ["worried","hopeful","confused","surprised","grateful"])]:
        for w in words:
            if w in tl:
                pts += p; reasons.append(f"Emotion — {w}")
                if hook == Hook.GENERAL: hook = Hook.EMOTIONAL
                break

    # Story arc
    phases = {
        "setup":   ["when i was","back in","i remember","it started"],
        "problem": ["problem","struggle","failed","mistake","wrong"],
        "conflict":["but then","until","suddenly","out of nowhere"],
        "resolve": ["solution","finally","turned out","worked","now i"],
    }
    found = [p for p, kws in phases.items() if any(k in tl for k in kws)]
    if len(found) >= 3:
        pts += 25; reasons.append(f"Story arc — {' → '.join(found)}")
        if hook == Hook.GENERAL: hook = Hook.STORY
    elif len(found) == 2:
        pts += 12; reasons.append("Partial story arc")

    # Curiosity
    for pat in [r"\?", r"imagine", r"what if", r"you won't believe",
                r"here's what happened", r"the crazy part", r"the best part"]:
        if re.search(pat, tl):
            pts += 12; reasons.append("Curiosity gap"); break

    # Info density
    ws = c.text.split()
    if len(ws) > 30:            pts += 10; reasons.append("High info density")
    if any(ch.isdigit() for ch in c.text): pts += 5; reasons.append("Contains data")
    if len(set(w.lower() for w in ws)) / max(len(ws), 1) > 0.72:
        pts += 5; reasons.append("Focused content")

    # Duration sweet spot
    if   30 <= dur <= 60: pts += 15; reasons.append("Ideal duration 30-60s")
    elif 15 <= dur <  30: pts += 8;  reasons.append("Short & punchy")
    elif 60 < dur  <= 75: pts += 5;  reasons.append("Long-form potential")

    c.score   = min(100, max(0, pts))
    c.hook    = hook.value
    c.reasons = reasons
    c.viable  = c.score >= 55
    return c


# ── Step 5: Selection ─────────────────────────────────────────────────────────

def select(scored: List[Chunk], n: int) -> List[Chunk]:
    pool = sorted([c for c in scored if c.viable], key=lambda x: -x.score)
    if not pool:
        pool = sorted(scored, key=lambda x: -x.score)

    sel: List[Chunk] = []
    used: List[tuple] = []
    hooks: set = set()

    for c in pool:
        if len(sel) >= n: break
        if any(not (c.end+5 < s or c.start-5 > e) for s, e in used): continue
        if c.hook in hooks and len(sel) < n-1: continue
        sel.append(c); used.append((c.start, c.end)); hooks.add(c.hook)

    # fill without diversity if short
    if len(sel) < n:
        for c in pool:
            if len(sel) >= n: break
            if c not in sel and not any(not (c.end+5<s or c.start-5>e) for s,e in used):
                sel.append(c); used.append((c.start, c.end))

    sel.sort(key=lambda x: x.start)
    return sel


# ── Step 6: FFmpeg cut (frame-accurate, 9:16 crop baked in) ──────────────────

def cut(src: str, start: float, end: float, out: str):
    """
    Re-encodes with libx264 for frame-accurate cut + 9:16 crop.
    Replaces MoviePy — 3-5x faster, crop actually written to file.
    """
    dur = end - start
    cmd = [
        "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
        "-ss", str(start), "-i", src, "-t", str(dur),
        "-vf", "crop=ih*9/16:ih,scale=1080:1920,fps=30",
        "-c:v", "libx264", "-preset", "fast", "-crf", "23",
        "-c:a", "aac", "-b:a", "128k",
        "-movflags", "+faststart", "-pix_fmt", "yuv420p",
        out,
    ]
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0 or not os.path.exists(out) or os.path.getsize(out) < 10_000:
        # Fallback: copy-mode (keyframe-boundary only, no crop)
        cmd2 = ["ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
                "-ss", str(start), "-t", str(dur), "-i", src,
                "-c", "copy", "-avoid_negative_ts", "make_zero", out]
        r2 = subprocess.run(cmd2, capture_output=True, text=True)
        if r2.returncode != 0:
            raise Exception(f"FFmpeg cut failed: {r.stderr[:300]}")


# ── API models ────────────────────────────────────────────────────────────────

class ProcessRequest(BaseModel):
    url:       str
    num_clips: int = 3       # Android sends num_clips (snake_case)


# ── Main endpoint ─────────────────────────────────────────────────────────────

@app.post("/api/process")
async def process_video(body: ProcessRequest, request: Request):
    url  = body.url.strip()
    n    = max(1, min(body.num_clips, 8))

    if not url:
        raise HTTPException(400, "URL cannot be empty")

    base = str(request.base_url).rstrip("/")

    with tempfile.TemporaryDirectory() as tmp:
        try:
            # 1 — Download
            print(f"\n{'─'*55}")
            print(f"📥  {url}")
            vpath = download_video(url, tmp)
            print(f"  ✓ downloaded")

            # 2 — Transcribe
            print("🎙  Transcribing...")
            words = transcribe(vpath)
            if not words:
                raise HTTPException(500, "No speech detected")
            total_sec = words[-1]["endMs"] / 1000
            print(f"  ✓ {len(words)} words  ({total_sec:.0f}s)")

            # 3 — Chunk
            print("🧩  Chunking...")
            chunks = semantic_chunks(words)
            if not chunks:
                raise HTTPException(500, "Chunking failed")

            # 4 — Score
            print("🎯  Scoring...")
            scored = [score(c) for c in chunks]
            viable = sum(1 for c in scored if c.viable)
            print(f"  ✓ {viable}/{len(scored)} viable (≥55)")

            # Top 3 preview
            for c in sorted(scored, key=lambda x: -x.score)[:3]:
                print(f"    [{c.score:3d}] {c.hook:12s}  {c.text[:55]}...")

            # 5 — Select
            print(f"🏆  Selecting {n} clips...")
            chosen = select(scored, n)

            # 6 — Cut
            print("✂️   Cutting...")
            clips_out = []
            for i, chunk in enumerate(chosen):
                fname = f"clip_{uuid.uuid4().hex[:8]}_{i}.mp4"
                fpath = os.path.join(CLIPS_DIR, fname)
                try:
                    cut(vpath, chunk.start, chunk.end, fpath)
                    clip_url = f"{base}/clips/{fname}"
                except Exception as e:
                    print(f"  ✗ clip {i+1} failed: {e} — using source URL")
                    clip_url = url

                # Word timestamps remapped relative to clip start
                off = chunk.words[0]["startMs"]
                captions = [{"word":    w["word"],
                              "startMs": w["startMs"] - off,
                              "endMs":   w["endMs"]   - off}
                             for w in chunk.words]

                clips_out.append({
                    # ── exact fields Android BackendClipOutput reads ──────────
                    "title":       f"🔥 {chunk.title()}...",
                    "startSec":    int(chunk.start),
                    "endSec":      int(chunk.end),
                    "viralScore":  chunk.score,
                    "viralReason": (chunk.reasons[0] if chunk.reasons
                                    else "High-engagement moment"),
                    "captions":    captions,
                    "clipUrl":     clip_url,
                    "clip_url":    clip_url,   # legacy compat key
                    # ── richer fields (optional, safe to ignore) ─────────────
                    "hookType":    chunk.hook,
                    "allReasons":  chunk.reasons,
                    "durationSec": round(chunk.duration, 1),
                })
                print(f"  ✓ clip {i+1}  score={chunk.score}  "
                      f"hook={chunk.hook}  dur={chunk.duration:.0f}s")

            print(f"{'─'*55}\n✅  {len(clips_out)} clips done\n")
            return {"url": url, "duration": total_sec, "clips": clips_out}

        except HTTPException:
            raise
        except Exception as e:
            print(f"\n❌  {e}")
            raise HTTPException(500, str(e))


# ── File serving & health ─────────────────────────────────────────────────────

@app.get("/clips/{filename}")
async def serve_clip(filename: str):
    path = os.path.join(CLIPS_DIR, filename)
    if not os.path.exists(path):
        raise HTTPException(404, "Clip not found or expired (30-min TTL)")
    return FileResponse(path, media_type="video/mp4")


@app.get("/health")
def health():
    return {
        "status":  "ok",
        "model":   WHISPER_MODEL,
        "pipeline": ["yt-dlp", "whisper", "semantic-chunk",
                     "virality-score", "ffmpeg-9:16", "captions"],
    }


@app.get("/")
def root():
    return {"api": "POST /api/process", "health": "GET /health"}


if __name__ == "__main__":
    import uvicorn
    get_model()
    uvicorn.run(app, host="0.0.0.0", port=int(os.getenv("PORT", 8000)))
