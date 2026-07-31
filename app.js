// Clipz-Stream Frontend Logic — Connected to Modal T4 GPU Engine
const MODAL_BASE_URL = "https://ibeekay1993--clipz-stream-fastapi-app.modal.run";

let selectedFile = null;
let pollingInterval = null;

// Tab Switching
function switchTab(tabName) {
    document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
    document.querySelectorAll('.tab-panel').forEach(panel => panel.classList.remove('active'));

    if (tabName === 'youtube') {
        document.getElementById('tab-youtube-btn').classList.add('active');
        document.getElementById('panel-youtube').classList.add('active');
    } else {
        document.getElementById('tab-file-btn').classList.add('active');
        document.getElementById('panel-file').classList.add('active');
    }
}

function fillSample(url) {
    document.getElementById('yt-url-input').value = url;
}

// File Dropzone Handling
function triggerFileInput() {
    document.getElementById('file-input').click();
}

function handleFileSelect(event) {
    const files = event.target.files;
    if (files && files.length > 0) {
        setFile(files[0]);
    }
}

function handleDragOver(event) {
    event.preventDefault();
    document.getElementById('dropzone').classList.add('dragover');
}

function handleDragLeave(event) {
    event.preventDefault();
    document.getElementById('dropzone').classList.remove('dragover');
}

function handleFileDrop(event) {
    event.preventDefault();
    document.getElementById('dropzone').classList.remove('dragover');
    const files = event.dataTransfer.files;
    if (files && files.length > 0) {
        setFile(files[0]);
    }
}

function setFile(file) {
    selectedFile = file;
    document.getElementById('dropzone-title').innerText = file.name;
    document.getElementById('dropzone-subtitle').innerText = `Size: ${(file.size / (1024 * 1024)).toFixed(1)} MB`;
    document.getElementById('file-action-container').style.display = 'block';
}

// Progress & Error Utilities
function showProgress(stepMsg, pct) {
    document.getElementById('progress-card').style.display = 'flex';
    document.getElementById('error-card').style.display = 'none';
    document.getElementById('progress-step').innerText = stepMsg;
    document.getElementById('progress-pct').innerText = `${pct}%`;
    document.getElementById('progress-bar-fill').style.width = `${pct}%`;
}

function hideProgress() {
    document.getElementById('progress-card').style.display = 'none';
}

function showError(msg) {
    hideProgress();
    document.getElementById('error-card').style.display = 'flex';
    document.getElementById('error-message').innerText = msg;
}

function dismissError() {
    document.getElementById('error-card').style.display = 'none';
}

// YouTube Form Submission
async function handleYoutubeSubmit(event) {
    event.preventDefault();
    dismissError();
    const url = document.getElementById('yt-url-input').value.trim();
    const numClips = parseInt(document.getElementById('clips-count').value) || 3;

    if (!url) return;

    showProgress("Connecting to Modal T4 Cloud GPU...", 5);

    try {
        const response = await fetch(`${MODAL_BASE_URL}/api/jobs/create`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ url: url, num_clips: numClips })
        });

        if (!response.ok) {
            const errText = await response.text();
            throw new Error(`Cloud server response error: ${errText}`);
        }

        const data = await response.json();
        const jobId = data.job_id;

        if (!jobId) {
            throw new Error("Invalid job response from cloud engine.");
        }

        // Start polling status loop
        pollJobStatus(jobId);

    } catch (err) {
        showError(err.message || "Failed to connect to Cloud AI Engine.");
    }
}

// File Upload Form Submission
async function handleFileUploadSubmit() {
    if (!selectedFile) return;
    dismissError();
    const numClips = parseInt(document.getElementById('clips-count').value) || 3;

    showProgress("Uploading video file to Cloud GPU...", 10);

    try {
        const formData = new FormData();
        formData.append("file", selectedFile);
        formData.append("num_clips", numClips);

        const response = await fetch(`${MODAL_BASE_URL}/api/upload`, {
            method: 'POST',
            body: formData
        });

        if (!response.ok) {
            const errText = await response.text();
            throw new Error(`Upload failed: ${errText}`);
        }

        const resultData = await response.json();
        hideProgress();
        renderResults(resultData);

    } catch (err) {
        showError(err.message || "Video upload failed.");
    }
}

// Poll Job Status Loop
function pollJobStatus(jobId) {
    if (pollingInterval) clearInterval(pollingInterval);

    pollingInterval = setInterval(async () => {
        try {
            const response = await fetch(`${MODAL_BASE_URL}/api/jobs/status/${jobId}`);
            if (!response.ok) return;

            const statusData = await response.json();

            if (statusData.status === 'processing') {
                const prog = statusData.progress || 10;
                const step = statusData.current_step || "WhisperX & Llama-3 AI Processing...";
                showProgress(step, prog);
            } else if (statusData.status === 'completed') {
                clearInterval(pollingInterval);
                hideProgress();
                renderResults(statusData.result);
            } else if (statusData.status === 'failed') {
                clearInterval(pollingInterval);
                showError(statusData.error || "Video clipping pipeline failed.");
            }
        } catch (err) {
            console.warn("Polling error:", err);
        }
    }, 2000);
}

// Render Results Grid
function renderResults(resultData) {
    const resultsSection = document.getElementById('results-section');
    const clipsGrid = document.getElementById('clips-grid');
    clipsGrid.innerHTML = '';

    if (!resultData || !resultData.clips || resultData.clips.length === 0) {
        showError("No valid clips were generated for this video.");
        return;
    }

    document.getElementById('clips-total-badge').innerText = `${resultData.clips.length} Clips Ready`;

    resultData.clips.forEach((clip, index) => {
        const clipUrl = clip.clipUrl ? (clip.clipUrl.startsWith('/') ? `${MODAL_BASE_URL}${clip.clipUrl}` : clip.clipUrl) : '';
        const viralScore = clip.viralScore || 95;
        const clipTitle = clip.title || `Viral Clip #${index + 1}`;
        const duration = Math.round((clip.endSec - clip.startSec) || 30);

        const card = document.createElement('div');
        card.className = 'clip-card';
        card.innerHTML = `
            <div class="clip-thumb" onclick="openVideoModal('${clipUrl}', '${escapeHtml(clipTitle)}', '${escapeHtml(clip.viralReason || '')}')">
                <video src="${clipUrl}#t=0.1" preload="metadata"></video>
                <div class="play-overlay"><i class="fa-solid fa-play"></i></div>
                <div class="score-badge">🔥 ${viralScore}/100</div>
            </div>
            <div class="clip-info">
                <h3 class="clip-title">${escapeHtml(clipTitle)}</h3>
                <p class="clip-meta"><i class="fa-regular fa-clock"></i> ${duration}s • 9:16 Vertical format</p>
                <div class="clip-actions">
                    <a href="${clipUrl}" download class="btn-primary full-width">
                        <i class="fa-solid fa-download"></i> Download MP4
                    </a>
                </div>
            </div>
        `;
        clipsGrid.appendChild(card);
    });

    resultsSection.style.display = 'block';
    resultsSection.scrollIntoView({ behavior: 'smooth' });
}

function escapeHtml(text) {
    return String(text).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#039;');
}

// Modal Video Preview
function openVideoModal(url, title, desc) {
    const modal = document.getElementById('video-modal');
    const player = document.getElementById('modal-video-player');
    player.src = url;
    document.getElementById('modal-clip-title').innerText = title;
    document.getElementById('modal-clip-desc').innerText = desc || "High retention short segment";
    document.getElementById('modal-download-link').href = url;
    modal.classList.add('active');
}

function closeVideoModal(event) {
    if (event.target.id === 'video-modal') {
        closeVideoModalForce();
    }
}

function closeVideoModalForce() {
    const modal = document.getElementById('video-modal');
    const player = document.getElementById('modal-video-player');
    player.pause();
    player.src = "";
    modal.classList.remove('active');
}
