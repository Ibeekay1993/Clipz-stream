FROM python:3.11-slim

RUN apt-get update && apt-get install -y \
    ffmpeg \
    git \
    wget \
    && rm -rf /var/lib/apt/lists/*

RUN pip install --upgrade pip && \
    pip install --no-cache-dir setuptools wheel

WORKDIR /app
COPY requirements.txt .

# Install requirements, then force yt-dlp to the latest release at build time.
# YouTube's anti-bot signals change often enough that a stale, cached yt-dlp
# version is one of the most common causes of "Sign in to confirm you're not
# a bot" failures on redeploys.
RUN pip install --no-cache-dir -r requirements.txt && \
    pip install --no-cache-dir --upgrade yt-dlp

COPY main.py .

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
