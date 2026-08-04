// Clipz-Stream Frontend Logic — Connected to Modal T4 GPU Engine
const MODAL_BASE_URL = "https://ibeekay1993--clipz-stream-fastapi-app.modal.run";

let selectedFile = null;
let currentWizardStep = 1;

document.addEventListener('DOMContentLoaded', () => {
    checkCapabilities();
});

async function checkCapabilities() {
    try {
        const resp = await fetch(`${MODAL_BASE_URL}/api/capabilities`);
        if (resp.ok) {
            const data = await resp.json();
            if (!data.youtube_link_import_enabled) {
                const note = document.getElementById('youtube-capability-note');
                if (note) note.style.display = 'flex';
                switchTab('file');
            }
        }
    } catch (e) {
        console.warn("Capabilities check skipped:", e);
    }
}

function goToWizardStep(stepNum) {
    if (stepNum === 2) {
        const activeTab = document.querySelector('.tab-btn.active') ? document.querySelector('.tab-btn.active').id : 'tab-youtube-btn';
        if (activeTab === 'tab-youtube-btn') {
            const url = document.getElementById('yt-url-input').value.trim();
            if (!url) {
                alert("Please paste a valid YouTube URL first!");
                return;
            }
            onUrlInputChange(url);
        } else if (activeTab === 'tab-file-btn') {
            if (!selectedFile) {
                alert("Please select or drop a video file first!");
                return;
            }
            document.getElementById('video-preview-title').innerText = selectedFile.name;
            document.getElementById('video-preview-author').innerText = `File Upload • ${(selectedFile.size / (1024*1024)).toFixed(1)}MB`;
            document.getElementById('video-preview-thumb').src = "https://images.unsplash.com/photo-1574717024653-61fd2cf4d44d?auto=format&fit=crop&w=400&q=80";
        }
    }

    currentWizardStep = stepNum;

    document.querySelectorAll('.wizard-step').forEach((el, idx) => {
        if (idx + 1 <= stepNum) {
            el.classList.add('active');
        } else {
            el.classList.remove('active');
        }
    });

    document.querySelectorAll('.wizard-panel').forEach((panel, idx) => {
        if (idx + 1 === stepNum) {
            panel.classList.add('active');
        } else {
            panel.classList.remove('active');
        }
    });
}

function submitWizardJob() {
    goToWizardStep(3);
    const activeTab = document.querySelector('.tab-btn.active') ? document.querySelector('.tab-btn.active').id : 'tab-youtube-btn';
    if (activeTab === 'tab-youtube-btn') {
        const fakeEvent = { preventDefault: () => {} };
        handleYoutubeSubmit(fakeEvent);
    } else {
        handleFileUploadSubmit();
    }
}

let pollingInterval = null;
let currentCaptionStyle = "opus";
let currentAspectRatio = "9:16";

function selectCaptionStyle(btn, styleName) {
    document.querySelectorAll('#caption-style-chips .chip-option').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    currentCaptionStyle = styleName;
}

function selectAspectRatio(btn, ratioName) {
    document.querySelectorAll('#aspect-ratio-chips .chip-option').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    currentAspectRatio = ratioName;
}

function switchMobileTab(tabName) {
    document.querySelectorAll('.mobile-nav-item').forEach(btn => btn.classList.remove('active'));
    
    if (tabName === 'import') {
        const item = document.getElementById('mobile-nav-import');
        if (item) item.classList.add('active');
        document.getElementById('clipper').scrollIntoView({ behavior: 'smooth' });
    } else if (tabName === 'presets') {
        const item = document.getElementById('mobile-nav-presets');
        if (item) item.classList.add('active');
        const grid = document.querySelector('.preset-controls-grid');
        if (grid) grid.scrollIntoView({ behavior: 'smooth' });
    } else if (tabName === 'history') {
        const item = document.getElementById('mobile-nav-history');
        if (item) item.classList.add('active');
        const res = document.getElementById('results-section');
        if (res) res.scrollIntoView({ behavior: 'smooth' });
    }
}

async function loadBackendCapabilities() {
    try {
        const response = await fetch(`${MODAL_BASE_URL}/api/capabilities`, { cache: "no-store" });
        if (!response.ok) return;
        const capabilities = await response.json();
        const note = document.getElementById('youtube-capability-note');
        const samples = document.getElementById('youtube-sample-links');
        const ytButton = document.getElementById('tab-youtube-btn');

        if (capabilities.youtube_link_import_enabled === false) {
            if (note) note.style.display = 'flex';
            if (samples) samples.style.display = 'none';
            if (ytButton) ytButton.classList.add('limited');
            switchTab('file');
        } else {
            if (note) note.style.display = 'none';
            if (samples) samples.style.display = 'flex';
            if (ytButton) ytButton.classList.remove('limited');
        }
    } catch (err) {
        console.warn('Capability check failed:', err);
    }
}

window.addEventListener('DOMContentLoaded', loadBackendCapabilities);
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

function extractYoutubeId(url) {
    const regExp = /^.*(youtu.be\/|v\/|u\/\w\/|embed\/|watch\?v=|\&v=)([^#\&\?]*).*/;
    const match = url.match(regExp);
    return (match && match[2].length === 11) ? match[2] : null;
}

async function onUrlInputChange(event) {
    const url = typeof event === 'string' ? event : event.target.value.trim();
    const ytId = extractYoutubeId(url);
    const card = document.getElementById('video-ingest-card');

    if (!ytId) {
        if (card) card.style.display = 'none';
        return;
    }

    try {
        const thumbUrl = `https://img.youtube.com/vi/${ytId}/hqdefault.jpg`;
        document.getElementById('video-preview-thumb').src = thumbUrl;

        // Fetch oEmbed Title & Author
        const oembedUrl = `https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=${ytId}&format=json`;
        const resp = await fetch(oembedUrl);
        if (resp.ok) {
            const data = await resp.json();
            document.getElementById('video-preview-title').innerText = data.title || "YouTube Video";
            document.getElementById('video-preview-author').innerText = `Channel • ${data.author_name || "Verified"}`;
        } else {
            document.getElementById('video-preview-title').innerText = "YouTube Video Ready";
            document.getElementById('video-preview-author').innerText = "Verified Media Stream";
        }

        if (card) card.style.display = 'flex';
    } catch (err) {
        if (card) card.style.display = 'flex';
    }
}

function fillSample(url) {
    document.getElementById('yt-url-input').value = url;
    onUrlInputChange(url);
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

function normalizeErrorMessage(msg) {
    const text = String(msg || "");
    if (text.includes("YouTube blocked cloud ingestion") || text.includes("YTDLP_COOKIES_CONTENT") || text.includes("YTDLP_PROXY")) {
        return "YouTube is currently blocking cloud link imports for this video. Please use Upload Video File and generate clips from the MP4 directly.";
    }
    return text;
}

let burnCaptionsEnabled = true;

function toggleCaptions(enabled) {
    burnCaptionsEnabled = enabled;
    const btnOn = document.getElementById('btn-captions-on');
    const btnOff = document.getElementById('btn-captions-off');
    if (enabled) {
        if (btnOn) btnOn.classList.add('active');
        if (btnOff) btnOff.classList.remove('active');
    } else {
        if (btnOn) btnOn.classList.remove('active');
        if (btnOff) btnOff.classList.add('active');
    }
}

// Progress & Error Utilities
function showProgress(stepMsg, pct) {
    document.getElementById('progress-card').style.display = 'block';
    document.getElementById('error-card').style.display = 'none';
    document.getElementById('results-section').style.display = 'none';
    document.getElementById('progress-step').innerText = stepMsg;
    document.getElementById('progress-pct').innerText = `${pct}%`;
    document.getElementById('progress-bar-fill').style.width = `${pct}%`;

    // Dynamically update Vizard level indicators
    const lvl1 = document.getElementById('vizard-lvl-1');
    const lvl2 = document.getElementById('vizard-lvl-2');
    const lvl3 = document.getElementById('vizard-lvl-3');

    if (pct < 35) {
        if (lvl1) lvl1.className = "vizard-level-item active";
        if (lvl2) lvl2.className = "vizard-level-item";
        if (lvl3) lvl3.className = "vizard-level-item";
    } else if (pct < 65) {
        if (lvl1) lvl1.className = "vizard-level-item active";
        if (lvl2) lvl2.className = "vizard-level-item active";
        if (lvl3) lvl3.className = "vizard-level-item";
    } else {
        if (lvl1) lvl1.className = "vizard-level-item active";
        if (lvl2) lvl2.className = "vizard-level-item active";
        if (lvl3) lvl3.className = "vizard-level-item active";
    }
}

function hideProgress() {
    document.getElementById('progress-card').style.display = 'none';
}

function showError(msg) {
    hideProgress();
    document.getElementById('error-card').style.display = 'flex';
    document.getElementById('error-message').innerText = normalizeErrorMessage(msg);
}

function dismissError() {
    document.getElementById('error-card').style.display = 'none';
}

// YouTube Form Submission
async function handleYoutubeSubmit(event) {
    if (event && event.preventDefault) event.preventDefault();
    dismissError();
    const url = document.getElementById('yt-url-input').value.trim();
    const numClips = parseInt(document.getElementById('clips-count').value) || 3;

    if (!url) return;

    showProgress("Connecting to High-Speed AI Processing Engine...", 5);

    try {
        const response = await fetch(`${MODAL_BASE_URL}/api/jobs/create`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ url: url, num_clips: numClips, burn_captions: burnCaptionsEnabled })
        });

        if (!response.ok) {
            const errText = await response.text();
            throw new Error(`Server processing error: ${errText}`);
        }

        const data = await response.json();
        const jobId = data.job_id;

        if (!jobId) {
            throw new Error("Invalid response from processing engine.");
        }

        // Start polling status loop
        pollJobStatus(jobId);

    } catch (err) {
        showError(err.message || "Unable to connect to AI Processing Engine.");
    }
}

// File Upload Form Submission
async function handleFileUploadSubmit() {
    if (!selectedFile) return;
    dismissError();
    const numClips = parseInt(document.getElementById('clips-count').value) || 3;

    showProgress("Uploading video file to Studio Engine...", 10);

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
        const rawClipUrl = clip.clipUrl || clip.clip_url || '';
        const clipUrl = rawClipUrl ? (rawClipUrl.startsWith('/') ? `${MODAL_BASE_URL}${rawClipUrl}` : rawClipUrl) : '';
        const viralScore = clip.viralScore || 95;
        const clipTitle = clip.title || `Viral Clip #${index + 1}`;
        const duration = Math.round((clip.endSec - clip.startSec) || 30);

        const card = document.createElement('div');
        card.className = 'clip-card';
        card.innerHTML = `
            <div class="clip-thumb">
                <video src="${clipUrl}" playsinline controls preload="metadata" style="width:100%; height:100%; object-fit:cover; border-radius:12px;"></video>
                <div class="score-badge">🔥 ${viralScore}/100</div>
            </div>
            <div class="clip-info">
                <h3 class="clip-title">${escapeHtml(clipTitle)}</h3>
                <p class="clip-meta"><i class="fa-regular fa-clock"></i> ${duration}s • 9:16 Vertical format</p>
                <div class="clip-actions">
                    <a href="${clipUrl}" download target="_blank" class="btn-primary full-width">
                        <i class="fa-solid fa-download"></i> Download Video MP4
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
