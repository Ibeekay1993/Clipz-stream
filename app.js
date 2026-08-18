// Clipz-Stream Frontend Logic — Connected to Modal T4 GPU Engine
const MODAL_BASE_URL = "https://ibeekay1993--clipz-stream-fastapi-app.modal.run";

let selectedFile = null;
let currentWizardStep = 1;

document.addEventListener('DOMContentLoaded', () => {
    checkCapabilities();

    const btnNext = document.getElementById('btn-goto-step2');
    if (btnNext) {
        btnNext.addEventListener('click', (e) => {
            e.preventDefault();
            e.stopPropagation();
            goToWizardStep(2);
        });
    }

    const sampleBtn = document.getElementById('btn-sample-lex');
    if (sampleBtn) {
        sampleBtn.addEventListener('click', (e) => {
            e.preventDefault();
            e.stopPropagation();
            fillSample('https://www.youtube.com/watch?v=AaMdXZMvT3w');
        });
    }
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
        let url = urlInput ? urlInput.value.trim() : '';

        if (!url && !selectedFile) {
            showError("Please enter a valid YouTube URL or upload a file.");
            return;
        }

        if (url) {
            const ytId = extractYoutubeId(url);
            if (!ytId) {
                showError("Invalid YouTube URL. Please check the link and try again.");
                return;
            }
            document.getElementById('video-preview-thumb').src = `https://img.youtube.com/vi/${ytId}/hqdefault.jpg`;
            document.getElementById('video-preview-title').innerText = "YouTube Video Stream";
            document.getElementById('video-preview-author').innerText = "Verified Media Channel";
            const card = document.getElementById('video-ingest-card');
            if (card) card.style.display = 'flex';
            try { onUrlInputChange(url); } catch (e) {}
        } else if (selectedFile) {
            document.getElementById('video-preview-title').innerText = selectedFile.name;
            document.getElementById('video-preview-author').innerText = `File Upload • ${(selectedFile.size / (1024*1024)).toFixed(1)}MB`;
            document.getElementById('video-preview-thumb').src = "https://images.unsplash.com/photo-1574717024653-61fd2cf4d44d?auto=format&fit=crop&w=400&q=80";
            const card = document.getElementById('video-ingest-card');
            if (card) card.style.display = 'flex';
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

    const header = document.querySelector('.hero-header');
    if (header) {
        header.style.display = stepNum > 1 ? 'none' : 'block';
    }

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
    
    // Check file size (100MB limit for Modal HTTP ingress)
    const MAX_FILE_SIZE_MB = 100;
    if (selectedFile.size > MAX_FILE_SIZE_MB * 1024 * 1024) {
        showError(`File too large (${(selectedFile.size / (1024*1024)).toFixed(1)}MB). The cloud proxy limit is ${MAX_FILE_SIZE_MB}MB. Please use the YouTube Link option instead for large videos!`);
        return;
    }
    
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
        
        if (resultData.job_id) {
            // Backend returned a job ID (async processing)
            pollJobStatus(resultData.job_id);
        } else {
            // Fallback for older synchronous behavior
            hideProgress();
            renderResults(resultData);
        }

    } catch (err) {
        if (err.message.includes("Failed to fetch")) {
            showError("Network Error: Connection to cloud dropped. Your video may be too large or the server timed out. Try using a YouTube link!");
        } else {
            showError(err.message || "Video upload failed.");
        }
    }
}

// Clip Another Video Workflow
function resetApp() {
    if (pollingInterval) clearInterval(pollingInterval);
    
    const resultsSection = document.getElementById('results-section');
    resultsSection.style.opacity = '0';
    resultsSection.style.transition = 'opacity 0.3s ease';
    
    setTimeout(() => {
        document.getElementById('url-input').value = '';
        selectedFile = null;
        document.getElementById('dropzone-title').innerText = "Drag & Drop video file here";
        document.getElementById('dropzone-subtitle').innerText = "Supports MP4, MKV, or WEBM up to 100MB";
        document.getElementById('dropzone-browse-btn').style.display = "inline-block";
        document.getElementById('file-action-container').style.display = "none";
        
        document.getElementById('progress-card').style.display = 'none';
        resultsSection.style.display = 'none';
        resultsSection.style.opacity = '1';
        document.getElementById('clips-grid').innerHTML = '';
        
        goToWizardStep(1);
        dismissError();
    }, 300);
}

// Poll Job Status Loop
function pollJobStatus(jobId) {
    if (pollingInterval) clearInterval(pollingInterval);

    pollingInterval = setInterval(async () => {
        try {
            const response = await fetch(`${MODAL_BASE_URL}/api/jobs/status/${jobId}`);
            
            if (response.status === 404) {
                clearInterval(pollingInterval);
                showError("Processing interrupted: Job not found on server.");
                return;
            }
            if (!response.ok) return;

            const job = await response.json();

            if (job.status === 'completed') {
                clearInterval(pollingInterval);
                hideProgress();
                if (job.result && job.result.status === 'needs_review') {
                    renderInteractiveWorkspace(job.result);
                } else {
                    renderResults(job.result);
                }
            } else if (job.status === 'failed') {
                clearInterval(pollingInterval);
                showError(normalizeErrorMessage(job.error) || "Job processing failed.");
            } else {
                showProgress(job.current_step || "Processing...", job.progress || 10);
            }
        } catch (e) {
            console.warn("Polling error:", e);
        }
    }, 2500);
}

// Interactive Editing Workspace
let currentWorkspaceJob = null;

function renderInteractiveWorkspace(resultData) {
    currentWorkspaceJob = resultData;
    document.getElementById('interactive-workspace').style.display = 'block';
    
    const list = document.getElementById('editor-clips-list');
    list.innerHTML = '';
    
    if (!resultData || !resultData.clips || resultData.clips.length === 0) {
        showError("No valid clips were generated for this video.");
        return;
    }
    
    resultData.clips.forEach((clip, index) => {
        const card = document.createElement('div');
        card.className = 'clip-card';
        card.style = 'flex-direction: column; background: #11141A; padding: 20px; border-radius: 12px; border: 1px solid rgba(255, 255, 255, 0.05); gap: 16px; margin-bottom: 24px; display: flex;';
        
        const fullTranscript = clip.captions.map(c => c.word).join(" ");
        
        card.innerHTML = `
            <div style="display: flex; justify-content: space-between; align-items: center;">
                <h3 style="margin: 0; font-size: 1.1rem; color: #FFF;">Clip ${index + 1}: ${escapeHtml(clip.title)}</h3>
                <span class="score-badge"><i class="fa-solid fa-fire"></i> ${(clip.viralScore / 10).toFixed(1)}</span>
            </div>
            <div style="display: flex; gap: 16px;">
                <div style="flex: 1;">
                    <label style="display: block; font-size: 0.85rem; color: #888; margin-bottom: 6px;">Start Time (seconds)</label>
                    <input type="number" step="0.1" value="${clip.startSec}" id="start-time-${index}" class="form-input" style="width: 100%; box-sizing: border-box;">
                </div>
                <div style="flex: 1;">
                    <label style="display: block; font-size: 0.85rem; color: #888; margin-bottom: 6px;">End Time (seconds)</label>
                    <input type="number" step="0.1" value="${clip.endSec}" id="end-time-${index}" class="form-input" style="width: 100%; box-sizing: border-box;">
                </div>
            </div>
            <div>
                <label style="display: block; font-size: 0.85rem; color: #888; margin-bottom: 6px;">Transcript (Edit to fix typos)</label>
                <textarea id="transcript-${index}" class="form-input" style="width: 100%; height: 80px; resize: vertical; box-sizing: border-box;">${escapeHtml(fullTranscript)}</textarea>
            </div>
        `;
        list.appendChild(card);
    });
}

async function submitRenderJob() {
    if (!currentWorkspaceJob) return;
    
    // Collect the edited values
    currentWorkspaceJob.clips.forEach((clip, index) => {
        const start = parseFloat(document.getElementById(`start-time-${index}`).value);
        const end = parseFloat(document.getElementById(`end-time-${index}`).value);
        const transcriptText = document.getElementById(`transcript-${index}`).value;
        
        clip.startSec = start;
        clip.endSec = end;
        
        const newWords = transcriptText.trim().split(/\\s+/);
        const durationMs = Math.max(1, (end - start) * 1000);
        const timePerWord = durationMs / Math.max(1, newWords.length);
        
        const newCaptions = newWords.map((w, i) => ({
            word: w,
            startMs: Math.round(i * timePerWord),
            endMs: Math.round((i + 1) * timePerWord)
        }));
        
        clip.captions = newCaptions;
    });
    
    document.getElementById('interactive-workspace').style.display = 'none';
    showProgress("Initializing Render Engine...", 0);
    
    try {
        const response = await fetch(`${MODAL_BASE_URL}/api/jobs/render`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ url: currentWorkspaceJob.url, clips: currentWorkspaceJob.clips })
        });
        
        if (!response.ok) {
            const errText = await response.text();
            throw new Error(`Render start failed: ${errText}`);
        }
        
        const data = await response.json();
        if (data.job_id) {
            pollJobStatus(data.job_id);
        } else {
            throw new Error("Invalid response from render engine.");
        }
    } catch (err) {
        showError(err.message || "Failed to start rendering job.");
    }
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
    window.currentClips = resultData.clips;

    resultData.clips.forEach((clip, index) => {
        const rawClipUrl = clip.clipUrl || clip.clip_url || '';
        const clipUrl = rawClipUrl ? (rawClipUrl.startsWith('/') ? `${MODAL_BASE_URL}${rawClipUrl}` : rawClipUrl) : '';
        const viralScore = clip.viralScore || 95;
        const clipTitle = clip.title || `Viral Clip #${index + 1}`;
        const duration = Math.round((clip.endSec - clip.startSec) || 30);
        const scoreVizard = (viralScore / 10).toFixed(1);

        const autoCaption = `Wait until you hear this! 🤯 ${escapeHtml(clipTitle)}... This is absolutely crazy. \n\n#viral #fyp #podcast #clips`;

        const brollTag = clip.brollQuery ? `
            <div class="auto-caption-box" style="background: rgba(40,120,255,0.1); padding: 8px; border-radius: 8px; margin-bottom: 12px;">
                <div style="font-size: 0.7rem; color: #4FA8FF; font-weight: 700; margin-bottom: 4px; display:flex; justify-content:space-between;">
                    <span><i class="fa-solid fa-images"></i> AI B-Roll Overlay</span>
                </div>
                <p style="font-size: 0.75rem; color: #CCC; margin: 0; line-height: 1.4;">Context: "${escapeHtml(clip.brollQuery)}"</p>
            </div>
        ` : '';

        const card = document.createElement('div');
        card.className = 'clip-card';
        card.innerHTML = `
            <div class="clip-thumb" onclick="openVideoModal(${index})">
                <video src="${clipUrl}" playsinline preload="metadata" style="width:100%; height:100%; object-fit:cover; border-radius:12px;"></video>
                <div class="score-badge-vizard" style="position:absolute; top:8px; left:8px; background:rgba(120,40,230,0.9); color:#FFF; font-weight:800; font-size:0.75rem; padding:4px 8px; border-radius:14px;">✦ ${scoreVizard}</div>
                <div class="duration-badge-vizard" style="position:absolute; bottom:8px; right:8px; background:rgba(0,0,0,0.8); color:#FFF; font-weight:700; font-size:0.7rem; padding:2px 6px; border-radius:4px;">00:${duration < 10 ? '0' + duration : duration}</div>
            </div>
            <div class="clip-info" style="padding:10px;">
                <h3 class="clip-title" style="font-size:0.88rem; font-weight:700; line-height:1.3; color:#FFF; margin-bottom:8px;">#${index + 1} ${escapeHtml(clipTitle)}</h3>
                
                ${brollTag}

                <div class="auto-caption-box" style="background: rgba(255,255,255,0.05); padding: 8px; border-radius: 8px; margin-bottom: 12px; cursor: pointer;" onclick="navigator.clipboard.writeText('${autoCaption.replace(/\n/g, ' ')}'); alert('Caption copied!');">
                    <div style="font-size: 0.7rem; color: var(--primary-neon); font-weight: 700; margin-bottom: 4px; display:flex; justify-content:space-between;">
                        <span><i class="fa-solid fa-wand-magic-sparkles"></i> AI Caption</span>
                        <i class="fa-regular fa-copy"></i>
                    </div>
                    <p style="font-size: 0.75rem; color: #CCC; margin: 0; line-height: 1.4; white-space: pre-wrap;">${autoCaption}</p>
                </div>

                <div class="clip-actions" style="display:flex; gap:8px;">
                    <a href="${clipUrl}" download target="_blank" class="btn-primary" style="flex:1; padding:8px; font-size:0.8rem; border-radius:8px; justify-content:center;">
                        <i class="fa-solid fa-download"></i> Save
                    </a>
                    <button type="button" class="btn-secondary" style="flex:1; padding:8px; font-size:0.8rem; border-radius:8px; justify-content:center;" onclick="shareClip('${escapeHtml(clipTitle)}', '${clipUrl}')">
                        <i class="fa-brands fa-tiktok"></i> Share
                    </button>
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
function openVideoModal(clipIndex) {
    const clip = window.currentClips[clipIndex];
    const url = clip.clipUrl ? (clip.clipUrl.startsWith('/') ? `${MODAL_BASE_URL}${clip.clipUrl}` : clip.clipUrl) : '';
    const title = clip.hookType ? `${clip.hookType} Hook` : `Clip ${clipIndex + 1}`;
    const duration = clip.duration ? Math.round(clip.duration) : 30;
    const desc = `${duration}s • 9:16 Vertical format`;

    const modal = document.getElementById('video-modal');
    const player = document.getElementById('modal-video-player');
    player.src = url;
    document.getElementById('modal-clip-title').innerText = title;
    document.getElementById('modal-clip-desc').innerText = desc;
    window.currentClipUrl = url; // Store url for download logic
    window.currentClipTitle = title;
    
    // Update Timeline Component
    document.getElementById('timeline-duration').innerText = `00:${duration < 10 ? '0'+duration : duration}`;

    // Render Transcript
    const transcriptContainer = document.getElementById('transcript-container');
    if (clip.captions && clip.captions.length > 0) {
        transcriptContainer.innerHTML = '';
        clip.captions.forEach((caption, idx) => {
            const span = document.createElement('span');
            span.innerText = caption.word + ' ';
            span.className = 'transcript-word';
            span.dataset.start = caption.start;
            span.dataset.end = caption.end;
            span.onclick = () => {
                player.currentTime = caption.start;
                player.play();
            };
            transcriptContainer.appendChild(span);
        });
    } else {
        transcriptContainer.innerHTML = '<p style="text-align:center; color:#666;">No transcript available.</p>';
    }

    // Highlighting Logic
    player.ontimeupdate = () => {
        const currentTime = player.currentTime;
        const words = transcriptContainer.querySelectorAll('.transcript-word');
        words.forEach(word => {
            const start = parseFloat(word.dataset.start);
            const end = parseFloat(word.dataset.end);
            if (currentTime >= start && currentTime <= end) {
                word.style.color = '#FFF';
                word.style.background = 'rgba(255,255,255,0.1)';
                word.style.borderRadius = '4px';
                // Auto-scroll
                const containerHeight = transcriptContainer.clientHeight;
                const scrollPos = word.offsetTop - containerHeight / 2;
                transcriptContainer.scrollTo({ top: scrollPos, behavior: 'smooth' });
            } else {
                word.style.color = '#888';
                word.style.background = 'transparent';
            }
        });
    };

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

// Download Logic to bypass Cross-Origin limits
async function downloadClip() {
    if (!window.currentClipUrl) return;
    
    const btn = document.getElementById('modal-download-btn');
    const originalText = btn.innerHTML;
    btn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Downloading...';
    btn.disabled = true;

    try {
        const response = await fetch(window.currentClipUrl);
        const blob = await response.blob();
        
        // Create an object URL from the Blob
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        // Clean up title for filename
        const safeTitle = (window.currentClipTitle || "LClipz-Video").replace(/[^a-z0-9]/gi, '_').toLowerCase();
        a.download = `${safeTitle}.mp4`;
        document.body.appendChild(a);
        a.click();
        
        // Cleanup
        a.remove();
        window.URL.revokeObjectURL(url);
    } catch (e) {
        console.error("Download failed:", e);
        alert("Download failed. Please check your connection or try again.");
    } finally {
        btn.innerHTML = originalText;
        btn.disabled = false;
    }
}

// Social Share Logic
function shareClip(title, url) {
    if (navigator.share) {
        navigator.share({
            title: title,
            text: `Check out this viral clip: ${title}\n\n#viral #fyp #podcast`,
            url: url
        }).catch(err => console.warn('Share failed:', err));
    } else {
        navigator.clipboard.writeText(url);
        alert('Share link copied to clipboard! Paste it on your social media.');
    }
}
