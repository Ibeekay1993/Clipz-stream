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
            }
        }
    } catch (e) {
        console.warn("Capabilities check skipped:", e);
    }
}

function goToWizardStep(stepNum) {
    if (stepNum === 2) {
        const urlInput = document.getElementById('yt-url-input');
        const url = urlInput ? urlInput.value.trim() : '';

        if (url) {
            try {
                onUrlInputChange(url);
            } catch (e) {
                console.warn(e);
            }
        } else if (selectedFile) {
            document.getElementById('video-preview-title').innerText = selectedFile.name;
            document.getElementById('video-preview-author').innerText = `File Upload • ${(selectedFile.size / (1024*1024)).toFixed(1)}MB`;
            document.getElementById('video-preview-thumb').src = "https://images.unsplash.com/photo-1574717024653-61fd2cf4d44d?auto=format&fit=crop&w=400&q=80";
        } else {
            alert("Please paste a YouTube link or select a video file to proceed!");
            return;
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
            panel.style.display = 'block';
        } else {
            panel.classList.remove('active');
            panel.style.display = 'none';
        }
    });

    const clipper = document.getElementById('clipper');
    if (clipper) clipper.scrollIntoView({ behavior: 'smooth' });
}

function submitWizardJob() {
    goToWizardStep(3);
    const urlInput = document.getElementById('yt-url-input');
    const url = urlInput ? urlInput.value.trim() : '';

    if (url) {
        const fakeEvent = { preventDefault: () => {} };
        handleYoutubeSubmit(fakeEvent);
    } else if (selectedFile) {
        handleFileUploadSubmit();
    } else {
        showError("Please provide a valid YouTube link or video file.");
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
        if (capabilities.youtube_link_import_enabled === false) {
            if (note) note.style.display = 'flex';
        } else {
            if (note) note.style.display = 'none';
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
    const url = typeof event === 'string' ? event : (event && event.target ? event.target.value.trim() : '');
    if (!url) return;
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
    const input = document.getElementById('yt-url-input');
    if (input) {
        input.value = url;
        document.getElementById('video-preview-thumb').src = "https://img.youtube.com/vi/AaMdXZMvT3w/hqdefault.jpg";
        document.getElementById('video-preview-title').innerText = "Lex Fridman AI Podcast";
        document.getElementById('video-preview-author').innerText = "Lex Fridman • Verified Media Stream";
        const card = document.getElementById('video-ingest-card');
        if (card) card.style.display = 'flex';
        goToWizardStep(2);
    }
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
    if (text.includes("Sign in to confirm") || text.includes("bot check")) {
        return "YouTube media stream requires cookies. Please try another video link or use Upload Video File.";
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
            const job = await response.json();

            if (job.status === 'completed') {
                clearInterval(interval);
                hideProgress();
                renderResults(job.result);
            } else if (job.status === 'failed') {
                clearInterval(interval);
                showError(normalizeErrorMessage(job.error) || "Job processing failed.");
            } else {
                showProgress(job.current_step || "Processing...", job.progress || 10);
            }
        } catch (e) {
            console.warn("Polling error:", e);
        }
    }, 2500);
}

// Render Clips Gallery Results (Vizard.ai Card Design)
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
        const scoreVizard = (viralScore / 10).toFixed(1);

        const card = document.createElement('div');
        card.className = 'clip-card';
        card.innerHTML = `
            <div class="clip-thumb" onclick="openVideoModal('${clipUrl}', '${escapeHtml(clipTitle)}', '${duration}s • 9:16 Vertical format')">
                <video src="${clipUrl}" playsinline preload="metadata" style="width:100%; height:100%; object-fit:cover; border-radius:12px;"></video>
                <div class="score-badge-vizard" style="position:absolute; top:8px; left:8px; background:rgba(120,40,230,0.9); color:#FFF; font-weight:800; font-size:0.75rem; padding:4px 8px; border-radius:14px;">✦ ${scoreVizard}</div>
                <div class="duration-badge-vizard" style="position:absolute; bottom:8px; right:8px; background:rgba(0,0,0,0.8); color:#FFF; font-weight:700; font-size:0.7rem; padding:2px 6px; border-radius:4px;">00:${duration < 10 ? '0' + duration : duration}</div>
            </div>
            <div class="clip-info" style="padding:10px;">
                <h3 class="clip-title" style="font-size:0.88rem; font-weight:700; line-height:1.3; color:#FFF; margin-bottom:8px;">#${index + 1} ${escapeHtml(clipTitle)}</h3>
                <div class="clip-actions">
                    <a href="${clipUrl}" download target="_blank" class="btn-primary full-width" style="padding:8px 12px; font-size:0.8rem; border-radius:8px;">
                        <i class="fa-solid fa-download"></i> Save MP4
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
