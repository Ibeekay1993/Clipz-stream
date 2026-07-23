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
        "python-multipart"
    )
    .add_local_dir(
        os.path.dirname(os.path.abspath(__file__)),
        remote_path="/root/backend"
    )
)

# Define the Modal App
app = modal.App("clipz-stream")

@app.function(
    image=image,
    secrets=[modal.Secret.from_name("clipz-secrets")]
)
@modal.asgi_app()
def fastapi_app():
    import sys
    # Add backend folder to path so main.py can be imported
    sys.path.append("/root/backend")
    from main import app as fastapi_web_app
    return fastapi_web_app
