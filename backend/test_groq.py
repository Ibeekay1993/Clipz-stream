import os, json
from groq import Groq
from dotenv import load_dotenv

load_dotenv()
client = Groq(api_key=os.getenv("GROQ_API_KEY"))

with open("test_audio.mp3", "rb") as f:
    resp = client.audio.transcriptions.create(
        file=("test_audio.mp3", f.read()),
        model="whisper-large-v3",
        response_format="verbose_json"
    )
    print(resp.model_dump_json(indent=2))
