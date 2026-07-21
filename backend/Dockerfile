FROM python:3.11-slim

RUN apt-get update && apt-get install -y \
    ffmpeg \
    git \
    wget \
    && rm -rf /var/lib/apt/lists/*

RUN pip install yt-dlp

RUN pip install --upgrade pip && \
    pip install --no-cache-dir setuptools wheel

WORKDIR /app
COPY requirements.txt .

# Pre-install openai-whisper with --no-build-isolation to use globally available setuptools
RUN pip install --no-cache-dir --no-build-isolation openai-whisper==20231117

# Install the remaining requirements
RUN pip install --no-cache-dir -r requirements.txt

RUN python -c "import whisper; whisper.load_model('tiny')"

COPY main.py .

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
