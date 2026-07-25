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
    .env({"BUILD_ID": "20260725_v9"})
    .add_local_file(
        os.path.join(os.path.dirname(os.path.abspath(__file__)), "main.py"),
        remote_path="/root/main.py"
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
    secrets=[modal.Secret.from_name("clipz-secrets")],
    timeout=600,
    cpu=2.0
)
@modal.asgi_app()
def fastapi_app():
    import importlib.util
    backend_dir = os.path.dirname(os.path.abspath(__file__))
    main_path = os.path.join(backend_dir, "main.py")
    spec = importlib.util.spec_from_file_location("main_backend_module", main_path)
    main_mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(main_mod)
    return main_mod.app
