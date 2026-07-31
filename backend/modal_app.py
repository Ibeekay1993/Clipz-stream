import modal
import os

# Define the container image with all necessary OS packages and Python libraries
# AND copy the local backend files inside the container directly
image = (
    modal.Image.debian_slim()
    .apt_install("ffmpeg")
    .pip_install(
        "groq",
        "supabase",
        "yt-dlp",
        "fastapi",
        "uvicorn",
        "pydantic",
        "python-dotenv",
        "requests",
        "aiohttp",
        "python-multipart",
        "opencv-python-headless"
    )
    .env({
        "BUILD_ID": "20260731_v12",
        "SUPABASE_URL": "https://oprejbzmizpsuhflagss.supabase.co",
        "SUPABASE_KEY": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im9wcmVqYnptaXpwc3VoZmxhZ3NzIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODQ3ODAxMzksImV4cCI6MjEwMDM1NjEzOX0.HQX82um7uP8-e-kdLUyK6lLybWW3ufGy8cGSqRw2_fY",
        "GROQ_API_KEY": os.getenv("GROQ_API_KEY", "")
    })
    .add_local_file(
        os.path.join(os.path.dirname(os.path.abspath(__file__)), "main.py"),
        remote_path="/root/main.py"
    )
    .add_local_dir(
        os.path.dirname(os.path.abspath(__file__)),
        remote_path="/root/backend"
    )
)

# Define shared persistent Volume for cross-container job tracking
volume = modal.Volume.from_name("clipz-shared-storage", create_if_missing=True)

# Define the Modal App
app = modal.App("clipz-stream")

@app.function(
    image=image,
    secrets=[modal.Secret.from_name("clipz-secrets")],
    timeout=600,
    gpu="T4",
    volumes={"/root/storage": volume}
)
def run_background_job(job_id: str, url: str, num_clips: int, base_url: str):
    import sys
    sys.path.insert(0, "/root/backend")
    import main as main_mod
    main_mod.modal_volume = volume
    
    try:
        vpath = main_mod.download_video_ingest(url, main_mod.RAW_UPLOADS_DIR, job_id)
        main_mod.execute_job_bg(job_id, vpath, url, num_clips, base_url)
    except Exception as e:
        main_mod.logger.error(f"Background Modal job {job_id} failed: {e}")
        main_mod.push_job_update(job_id, progress=0, current_step="Failed", status="failed", error=str(e))

@app.function(
    image=image,
    secrets=[modal.Secret.from_name("clipz-secrets")],
    timeout=600,
    cpu=2.0,
    volumes={"/root/storage": volume}
)
@modal.asgi_app()
def fastapi_app():
    import sys
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    sys.path.insert(0, "/root/backend")
    import main as main_mod
    main_mod.modal_volume = volume
    main_mod.modal_background_job_fn = run_background_job
    return main_mod.app
