// Clipz Studio Frontend Logic
const MODAL_BASE_URL = "https://ibeekay1993--clipz-stream-fastapi-app.modal.run";

let selectedFile = null;
let currentWizardStep = 1;
let currentSession = null;
let burnCaptionsEnabled = true;

document.addEventListener('DOMContentLoaded', () => {
    initializeAuth();
    loadBackendCapabilities();
    switchTab('file');
    startCountdown();

    const btnNext = document.getElementById('btn-goto-step2');
    if (btnNext) {
        btnNext.addEventListener('click', (e) => {
            e.preventDefault();
            e.stopPropagation();
            goToWizardStep(2);
        });
    }
});

// Countdown
function startCountdown() {
    let timeLeft = 23 * 3600 + 57 * 60;
    const hEl = document.getElementById('cd-h');
    const mEl = document.getElementById('cd-m');
    const sEl = document.getElementById('cd-s');
    if (!hEl || !mEl || !sEl) return;

    setInterval(() => {
        if (timeLeft <= 0) return;
        timeLeft--;
        const h = Math.floor(timeLeft / 3600);
        const m = Math.floor((timeLeft % 3600) / 60);
        const s = timeLeft % 60;
        hEl.innerText = h < 10 ? '0' + h : h;
        mEl.innerText = m < 10 ? '0' + m : m;
        sEl.innerText = s < 10 ? '0' + s : s;
    }, 1000);
}

// Auth
async function initializeAuth() {
    await captureOAuthRedirectSession();
    try {
        currentSession = JSON.parse(localStorage.getItem('clipz_session') || 'null');
    } catch (_err) {
        currentSession = null;
    }
    updateAuthUi(currentSession);
}

async function captureOAuthRedirectSession() {
    const hash = new URLSearchParams(window.location.hash.replace(/^#/, ''));
    const accessToken = hash.get('access_token');
    if (!accessToken) return;
    try {
        const response = await fetch(`${MODAL_BASE_URL}/api/auth/session`, {
            headers: { Authorization: `Bearer ${accessToken}` }
        });
        const data = await response.json();
        if (!response.ok) throw new Error(data.detail || 'Could not finish Google login.');
        localStorage.setItem('clipz_session', JSON.stringify(data));
        history.replaceState(null, '', window.location.pathname + window.location.search);
    } catch (err) {
        console.warn('OAuth session capture failed:', err);
    }
}

function updateAuthUi(session) {
    const banner = document.getElementById('top-countdown-banner');
    const bannerText = document.querySelector('.countdown-text');
    const bannerButton = banner ? banner.querySelector('button') : null;
    if (!banner) return;
    if (bannerText) {
        bannerText.innerText = session
            ? `Signed in as ${session.email}. Clips are saved to your account.`
            : 'Guest projects expire after 24 hours. Sign up to save them.';
    }
    if (bannerButton) {
        bannerButton.innerText = session ? 'Sign out' : 'Sign up';
        bannerButton.onclick = session ? signOut : openAuthModal;
    }
}

function openAuthModal() {
    const modal = document.getElementById('auth-modal');
    if (modal) modal.style.display = 'flex';
}

function closeAuthModal(e) {
    if (e.target.id === 'auth-modal') closeAuthModalForce();
}

function closeAuthModalForce() {
    const modal = document.getElementById('auth-modal');
    if (modal) modal.style.display = 'none';
}

function showAuthMessage(message, isError = false) {
    const el = document.getElementById('auth-message');
    if (!el) return;
    el.style.display = 'block';
    el.style.color = isError ? 'var(--error)' : 'var(--text-muted)';
    el.innerText = message;
}

async function submitAuthForm() {
    const email = document.getElementById('auth-email')?.value.trim();
    const password = document.getElementById('auth-password')?.value;
    const button = document.getElementById('auth-submit-btn');
    if (!email || !password || password.length < 8) {
        showAuthMessage('Enter an email and a password with at least 8 characters.', true);
        return;
    }
    if (button) button.disabled = true;
    showAuthMessage('Checking your account...');
    try {
        let response = await fetch(`${MODAL_BASE_URL}/api/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password })
        });
        if (!response.ok) {
            response = await fetch(`${MODAL_BASE_URL}/api/auth/signup`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email, password })
            });
        }
        const data = await response.json();
        if (!response.ok) throw new Error(data.detail || data.message || 'Authentication failed.');
        if (data.access_token) {
            currentSession = data;
            localStorage.setItem('clipz_session', JSON.stringify(currentSession));
            updateAuthUi(currentSession);
            showAuthMessage('Signed in. Future clips will be saved.');
            setTimeout(closeAuthModalForce, 600);
        } else {
            showAuthMessage(data.message || 'Check your email to confirm your account.');
        }
    } catch (err) {
        showAuthMessage(err.message || 'Sign up/login failed.', true);
    } finally {
        if (button) button.disabled = false;
    }
}

async function signInWithGoogle() {
    const redirectTo = encodeURIComponent(window.location.origin + window.location.pathname);
    window.location.href = `${MODAL_BASE_URL}/api/auth/google?redirect_to=${redirectTo}`;
}

async function signOut() {
    currentSession = null;
    localStorage.removeItem('clipz_session');
    updateAuthUi(null);
}

function getAuthHeaders() {
    return currentSession?.access_token ? { Authorization: `Bearer ${currentSession.access_token}` } : {};
}

function getCurrentUserId() {
    return currentSession?.user_id || null;
}

// Backend capabilities
async function loadBackendCapabilities() {
    try {
        const response = await fetch(`${MODAL_BASE_URL}/api/capabilities`, { cache: "no-store" });
        if (!response.ok) return;
        const capabilities = await response.json();
        const note = document.getElementById('youtube-capability-note');
        const ytButton = document.getElementById('tab-youtube-btn');
        if (capabilities.youtube_link_import_enabled === false) {
            if (note) note.style.display = 'flex';
            if (ytButton) ytButton.classList.add('limited');
            switchTab('file');
        } else {
            if (note) note.style.display = 'none';
            if (ytButton) ytButton.classList.remove('limited');
        }
    } catch (err) {
        console.warn('Capability check failed:', err);
    }
}

function getActiveImportTab() {
    const activeTab = document.querySelector('.tab-btn.active');
    return activeTab ? activeTab.id : 'tab-file-btn';
}

// Wizard navigation
function goToWizardStep(stepNum) {
    if (stepNum === 2) {
        const activeTab = getActiveImportTab();
        const card = document.getElementById('video-ingest-card');
        if (activeTab === 'tab-file-btn') {
            if (!selectedFile) {
                showError("Please upload a video file first.");
                return;
            }
            document.getElementById('video-preview-title').innerText = selectedFile.name;
            document.getElementById('video-preview-author').innerText = `File Upload • ${(selectedFile.size / (1024 * 1024)).toFixed(1)}MB`;
            document.getElementById('video-preview-thumb').src = "https://images.unsplash.com/photo-1574717024653-61fd2cf4d44d?auto=format&fit=crop&w=400&q=80";
            if (card) card.style.display = 'flex';
        } else {
            const urlInput = document.getElementById('yt-url-input');
            const url = urlInput ? urlInput.value.trim() : '';
            if (!url) {
                showError("Please paste a YouTube URL first, or switch to Upload Video File.");
                return;
            }
            const ytId = extractYoutubeId(url);
            if (!ytId) {
                showError("Invalid YouTube URL. Please check the link or upload the video file directly.");
                return;
            }
            document.getElementById('video-preview-thumb').src = `https://img.youtube.com/vi/${ytId}/hqdefault.jpg`;
            document.getElementById('video-preview-title').innerText = "YouTube Video Stream";
            document.getElementById('video-preview-author').innerText = "Waiting for cloud import";
            if (card) card.style.display = 'flex';
            try { onUrlInputChange(url); } catch (e) {}
        }
    }

    currentWizardStep = stepNum;

    document.querySelectorAll('.wizard-step').forEach((el, idx) => {
        el.classList.remove('active', 'completed');
        if (idx + 1 < stepNum) el.classList.add('completed');
        else if (idx + 1 === stepNum) el.classList.add('active');
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
    if (header && stepNum > 1) {
        header.style.display = 'none';
    } else if (header) {
        header.style.display = 'block';
    }

    const clipper = document.getElementById('clipper');
    if (clipper) clipper.scrollIntoView({ behavior: 'smooth' });
}

function submitWizardJob() {
    if (submitWizardJob._inFlight) return;
    const activeTab = getActiveImportTab();

    if (activeTab === 'tab-file-btn') {
        if (!selectedFile) {
            showError("Please upload a video file first.");
            goToWizardStep(1);
            return;
        }
        submitWizardJob._inFlight = true;
        goToWizardStep(3);
        handleFileUploadSubmit().finally(() => { submitWizardJob._inFlight = false; });
        return;
    }

    const urlInput = document.getElementById('yt-url-input');
    const url = urlInput ? urlInput.value.trim() : '';
    if (!url) {
        showError("Please paste a YouTube URL first, or use Upload Video File.");
        goToWizardStep(1);
        return;
    }
    submitWizardJob._inFlight = true;
    goToWizardStep(3);
    handleYoutubeSubmit({ preventDefault: () => {} }).finally(() => { submitWizardJob._inFlight = false; });
}

// Tab switching
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

function switchMobileTab(tabName) {
    document.querySelectorAll('.mobile-nav-item').forEach(btn => btn.classList.remove('active'));
    if (tabName === 'import') {
        const item = document.getElementById('mobile-nav-import');
        if (item) item.classList.add('active');
        document.getElementById('clipper').scrollIntoView({ behavior: 'smooth' });
    } else if (tabName === 'clips') {
        const item = document.getElementById('mobile-nav-clips');
        if (item) item.classList.add('active');
        const res = document.getElementById('results-section');
        if (res) res.scrollIntoView({ behavior: 'smooth' });
    }
}

function extractYoutubeId(url) {
    if (!url) return null;
    const patterns = [
        /(?:youtu\.be\/|v\/|u\/\w\/|embed\/|shorts\/|watch\?v=|&v=)([A-Za-z0-9_-]{11})/,
    ];
    for (const p of patterns) {
        const m = url.match(p);
        if (m && m[1] && m[1].length === 11) return m[1];
    }
    return null;
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

// File handling
function triggerFileInput() {
    document.getElementById('file-input').click();
}

function handleFileSelect(event) {
    const files = event.target.files;
    if (files && files.length > 0) setFile(files[0]);
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
    if (files && files.length > 0) setFile(files[0]);
}

function setFile(file) {
    selectedFile = file;
    document.getElementById('dropzone-title').innerText = file.name;
    document.getElementById('dropzone-subtitle').innerText = `Size: ${(file.size / (1024 * 1024)).toFixed(1)} MB — ${file.type || 'video'}`;
    document.getElementById('file-action-container').style.display = 'block';
}

function toggleCaptions(enabled) {
    burnCaptionsEnabled = enabled;
}

// Progress & Error
function showProgress(stepMsg, pct) {
    const clampedPct = Math.max(0, Math.min(100, Number(pct) || 0));
    document.getElementById('progress-card').style.display = 'block';
    document.getElementById('error-card').style.display = 'none';
    document.getElementById('results-section').style.display = 'none';
    document.getElementById('progress-step').innerText = stepMsg;
    document.getElementById('progress-pct').innerText = `${clampedPct}%`;
    document.getElementById('progress-bar-fill').style.width = `${clampedPct}%`;

    const steps = document.querySelectorAll('#progress-steps .progress-step-item');
    const thresholds = [5, 25, 50, 70, 90];
    steps.forEach((item, idx) => {
        const level = idx + 1;
        const threshold = thresholds[idx];
        item.classList.remove('completed', 'active');
        if (clampedPct >= threshold) {
            item.classList.add('completed');
            const icon = item.querySelector('.progress-step-icon i');
            if (icon) {
                icon.className = 'fa-solid fa-circle-check';
            }
        } else if (clampedPct >= Math.max(0, threshold - 15)) {
            item.classList.add('active');
            const icon = item.querySelector('.progress-step-icon i');
            if (icon) {
                icon.className = 'fa-solid fa-circle-notch fa-spin';
            }
        } else {
            const icon = item.querySelector('.progress-step-icon i');
            if (icon) {
                icon.className = 'fa-regular fa-circle';
            }
        }
    });
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

function normalizeErrorMessage(msg) {
    const text = String(msg || "");
    if (text.includes("Sign in to confirm") || text.includes("bot check")) {
        return "YouTube media stream requires cookies. Please try another video link or use Upload Video File.";
    }
    return text;
}

// YouTube submit
async function handleYoutubeSubmit(event) {
    if (event && event.preventDefault) event.preventDefault();
    dismissError();
    const url = document.getElementById('yt-url-input').value.trim();
    const numClips = parseInt(document.getElementById('clips-count').value) || 3;
    if (!url) return;

    showProgress("Preparing your video...", 5);
    try {
        const response = await fetch(`${MODAL_BASE_URL}/api/jobs/create`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
            body: JSON.stringify({ url: url, num_clips: numClips, burn_captions: burnCaptionsEnabled, user_id: getCurrentUserId() })
        });
        if (!response.ok) {
            const errText = await response.text();
            throw new Error(errText || "Server processing error.");
        }
        const data = await response.json();
        const jobId = data.job_id;
        if (!jobId) throw new Error("Invalid response from processing engine.");
        pollJobStatus(jobId);
    } catch (err) {
        showError(err.message || "Unable to connect. Please try again.");
    }
}

// File upload submit
async function handleFileUploadSubmit() {
    if (!selectedFile) return;
    dismissError();

    const MAX_FILE_SIZE_MB = 100;
    const SOFT_RECOMMEND_MB = 500;
    if (selectedFile.size > SOFT_RECOMMEND_MB * 1024 * 1024) {
        showError(`File too large (${(selectedFile.size / (1024 * 1024)).toFixed(1)}MB). For videos over ${SOFT_RECOMMEND_MB}MB, use a YouTube link instead.`);
        return;
    }
    if (selectedFile.size > MAX_FILE_SIZE_MB * 1024 * 1024) {
        showError(`File too large (${(selectedFile.size / (1024 * 1024)).toFixed(1)}MB). The upload limit is ${MAX_FILE_SIZE_MB}MB.`);
        return;
    }

    const numClips = parseInt(document.getElementById('clips-count').value) || 3;
    showProgress("Uploading video...", 10);

    try {
        const formData = new FormData();
        formData.append("file", selectedFile);
        formData.append("num_clips", numClips);
        formData.append("burn_captions", burnCaptionsEnabled ? "true" : "false");
        if (getCurrentUserId()) formData.append("user_id", getCurrentUserId());

        const response = await fetch(`${MODAL_BASE_URL}/api/upload`, {
            method: 'POST',
            headers: getAuthHeaders(),
            body: formData
        });

        if (!response.ok) {
            const errText = await response.text();
            throw new Error(errText || "Upload failed.");
        }

        const resultData = await response.json();
        if (resultData.job_id) {
            pollJobStatus(resultData.job_id);
        } else {
            hideProgress();
            renderResults(resultData);
        }
    } catch (err) {
        if (err.message.includes("Failed to fetch")) {
            showError("Network error: connection dropped. Try a smaller file and run again.");
        } else {
            showError(err.message || "Video upload failed.");
        }
    }
}

// Reset
function resetApp() {
    if (pollingInterval) clearInterval(pollingInterval);
    const resultsSection = document.getElementById('results-section');
    resultsSection.style.opacity = '0';
    resultsSection.style.transition = 'opacity 0.3s ease';
    setTimeout(() => {
        const ytInput = document.getElementById('yt-url-input');
        if (ytInput) ytInput.value = '';
        selectedFile = null;
        document.getElementById('dropzone-title').innerText = "Drag & drop video here";
        document.getElementById('dropzone-subtitle').innerText = "MP4, MKV, or WEBM — up to 100MB";
        document.getElementById('file-action-container').style.display = 'none';
        document.getElementById('progress-card').style.display = 'none';
        resultsSection.style.display = 'none';
        resultsSection.style.opacity = '1';
        document.getElementById('clips-grid').innerHTML = '';
        goToWizardStep(1);
        dismissError();
    }, 300);
}

// Polling
let pollingInterval = null;

function pollJobStatus(jobId) {
    if (pollingInterval) clearInterval(pollingInterval);
    pollingInterval = setInterval(async () => {
        try {
            const response = await fetch(`${MODAL_BASE_URL}/api/jobs/status/${jobId}`, {
                headers: getAuthHeaders()
            });
            if (response.status === 404) {
                clearInterval(pollingInterval);
                showError("Processing interrupted: job not found on server.");
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

// Interactive Workspace
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
        const fullTranscript = (clip.captions || []).map(c => c.word).join(" ");
        const card = document.createElement('div');
        card.className = 'card';
        card.style.padding = 'var(--space-5)';
        card.innerHTML = `
            <div class="card-header" style="display: flex; justify-content: space-between; align-items: center; padding-bottom: var(--space-3); margin-bottom: var(--space-3); border-bottom: 1px solid var(--border-subtle);">
                <div>
                    <div class="card-title">Clip ${index + 1}</div>
                    <div class="text-caption" style="margin-top: 2px;">${escapeHtml(clip.title || 'Untitled')}</div>
                </div>
                <span style="display: inline-flex; align-items: center; gap: 4px; font-size: 0.75rem; font-weight: 700; color: var(--accent); background: var(--accent-muted); padding: 4px 10px; border-radius: 999px;">
                    <i class="fa-solid fa-fire" style="font-size: 0.65rem;"></i> ${(clip.viralScore / 10).toFixed(1)}
                </span>
            </div>
            <div style="display: flex; gap: var(--space-4); margin-bottom: var(--space-4);">
                <div style="flex: 1;">
                    <label class="control-label" style="margin-bottom: var(--space-1); display: block;">Start (seconds)</label>
                    <input type="number" step="0.1" value="${clip.startSec}" id="start-time-${index}" class="form-input">
                </div>
                <div style="flex: 1;">
                    <label class="control-label" style="margin-bottom: var(--space-1); display: block;">End (seconds)</label>
                    <input type="number" step="0.1" value="${clip.endSec}" id="end-time-${index}" class="form-input">
                </div>
            </div>
            <div>
                <label class="control-label" style="margin-bottom: var(--space-1); display: block;">Transcript</label>
                <textarea id="transcript-${index}" class="form-input" style="height: 80px; resize: vertical;">${escapeHtml(fullTranscript)}</textarea>
            </div>
        `;
        list.appendChild(card);
    });
}

async function submitRenderJob() {
    if (!currentWorkspaceJob) return;
    if (submitRenderJob._inFlight) return;
    submitRenderJob._inFlight = true;

    currentWorkspaceJob.clips.forEach((clip, index) => {
        const start = parseFloat(document.getElementById(`start-time-${index}`).value);
        const end = parseFloat(document.getElementById(`end-time-${index}`).value);
        const transcriptText = document.getElementById(`transcript-${index}`).value;
        clip.startSec = start;
        clip.endSec = end;
        const newWords = transcriptText.trim().split(/\s+/).filter(Boolean);
        const duration = Math.max(0.1, end - start);
        const timePerWord = (duration * 1000) / Math.max(1, newWords.length);
        const newCaptions = newWords.map((w, i) => {
            const offsetMs = start * 1000;
            return {
                word: w,
                startMs: Math.round(offsetMs + i * timePerWord),
                endMs: Math.round(offsetMs + (i + 1) * timePerWord)
            };
        });
        clip.captions = newCaptions;
    });

    document.getElementById('interactive-workspace').style.display = 'none';
    showProgress("Rendering clips...", 0);

    try {
        const response = await fetch(`${MODAL_BASE_URL}/api/jobs/render`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
            body: JSON.stringify({ url: currentWorkspaceJob.url, clips: currentWorkspaceJob.clips, user_id: getCurrentUserId() })
        });
        if (!response.ok) {
            const errText = await response.text();
            throw new Error(errText || "Render start failed.");
        }
        const data = await response.json();
        if (data.job_id) {
            pollJobStatus(data.job_id);
        } else {
            throw new Error("Invalid response from render engine.");
        }
    } catch (err) {
        showError(err.message || "Failed to start rendering job.");
    } finally {
        submitRenderJob._inFlight = false;
    }
}

// Results
function renderResults(resultData) {
    const resultsSection = document.getElementById('results-section');
    const clipsGrid = document.getElementById('clips-grid');
    clipsGrid.innerHTML = '';

    if (!resultData || !resultData.clips || resultData.clips.length === 0) {
        showError("No valid clips were generated for this video.");
        return;
    }

    const playableClips = resultData.clips.filter((clip) => Boolean(clip.clipUrl || clip.clip_url));
    const pendingClips = resultData.clips.length - playableClips.length;

    if (playableClips.length === 0 && resultData.status === 'needs_review') {
        renderInteractiveWorkspace(resultData);
        return;
    }

    if (playableClips.length === 0) {
        showError("Clips were generated but no downloadable file is ready yet.");
        return;
    }

    document.getElementById('clips-total-badge').innerText = pendingClips > 0
        ? `${playableClips.length} Ready / ${pendingClips} Processing`
        : `${playableClips.length} Clips`;
    window.currentClips = playableClips;

    playableClips.forEach((clip, index) => {
        const rawClipUrl = clip.clipUrl || clip.clip_url || '';
        const clipUrl = rawClipUrl ? (rawClipUrl.startsWith('/') ? `${MODAL_BASE_URL}${rawClipUrl}` : rawClipUrl) : '';
        const duration = Math.round((clip.endSec - clip.startSec) || 30);
        const clipTitle = clip.title || `Clip ${index + 1}`;
        const durationLabel = formatClipDuration(duration);
        const safeClipUrl = escapeHtml(clipUrl);
        const autoCaption = `${escapeHtml(clip.title || '')} ${escapeHtml(clip.viralReason || '')}`.trim();

        const card = document.createElement('div');
        card.className = 'clip-card';
        card.innerHTML = `
            <div class="clip-thumb" onclick="openVideoModal(${index})">
                <video src="${safeClipUrl}" playsinline preload="metadata"></video>
                <div class="clip-duration">${durationLabel}</div>
            </div>
            <div class="clip-body">
                <div class="clip-title">#${index + 1} ${escapeHtml(clipTitle)}</div>
                <div class="clip-reason">${escapeHtml(clip.viralReason || 'High-engagement segment')}</div>
                <div style="display: flex; gap: var(--space-2); font-size: 0.8125rem; color: var(--text-muted); align-items: center;">
                    <span style="display: inline-flex; align-items: center; gap: 4px;">
                        <i class="fa-solid fa-closed-captioning" style="font-size: 0.75rem;"></i>
                        ${burnCaptionsEnabled ? 'Captions on' : 'No captions'}
                    </span>
                </div>
                <div class="clip-actions">
                    <button type="button" class="btn btn-secondary" onclick="shareClip('${escapeHtml(clipTitle)}', '${safeClipUrl}')" style="font-size: 0.8125rem;">
                        <i class="fa-solid fa-share-nodes"></i> Share
                    </button>
                    <a href="${safeClipUrl}" download target="_blank" class="btn btn-primary" style="font-size: 0.8125rem; text-align: center;">
                        <i class="fa-solid fa-download"></i> Download
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
    if (text === null || text === undefined) return '';
    return String(text).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#039;');
}

function formatClipDuration(totalSec) {
    const sec = Math.max(0, Math.round(Number(totalSec) || 0));
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return `${m}:${s < 10 ? '0' + s : s}`;
}

// Video Modal
function openVideoModal(clipIndex) {
    const clip = window.currentClips[clipIndex];
    if (!clip) return;
    const rawClipUrl = clip.clipUrl || clip.clip_url || '';
    const url = rawClipUrl ? (rawClipUrl.startsWith('/') ? `${MODAL_BASE_URL}${rawClipUrl}` : rawClipUrl) : '';
    const title = clip.hookType ? `${clip.hookType} Hook` : `Clip ${clipIndex + 1}`;
    const duration = clip.durationSec ? Math.round(clip.durationSec) : 30;
    const desc = `${formatClipDuration(duration)} • 9:16`;

    const modal = document.getElementById('video-modal');
    const player = document.getElementById('modal-video-player');
    player.src = url;
    document.getElementById('modal-clip-title').innerText = title;
    document.getElementById('modal-clip-desc').innerText = desc;
    window.currentClipUrl = url;
    window.currentClipTitle = title;

    document.getElementById('timeline-duration').innerText = formatClipDuration(duration);

    const transcriptContainer = document.getElementById('transcript-container');
    if (clip.captions && clip.captions.length > 0) {
        transcriptContainer.innerHTML = '';
        clip.captions.forEach((caption, idx) => {
            const span = document.createElement('span');
            span.innerText = caption.word + ' ';
            span.className = 'transcript-word';
            const rawStart = (caption.startMs != null) ? caption.startMs / 1000 : Number(caption.start || 0);
            const rawEnd = (caption.endMs != null) ? caption.endMs / 1000 : Number(caption.end || 0);
            span.dataset.start = rawStart;
            span.dataset.end = rawEnd;
            span.onclick = () => {
                player.currentTime = rawStart;
                player.play();
            };
            transcriptContainer.appendChild(span);
        });
    } else {
        transcriptContainer.innerHTML = '<p style="text-align: center; color: var(--text-muted);">No transcript available.</p>';
    }

    player.ontimeupdate = () => {
        const currentTime = player.currentTime;
        const words = transcriptContainer.querySelectorAll('.transcript-word');
        words.forEach(word => {
            const start = parseFloat(word.dataset.start);
            const end = parseFloat(word.dataset.end);
            if (currentTime >= start && currentTime <= end) {
                word.style.color = 'var(--text-primary)';
                word.style.background = 'var(--accent-muted)';
                word.style.borderRadius = '4px';
            } else {
                word.style.color = 'var(--text-secondary)';
                word.style.background = 'transparent';
            }
        });
    };

    modal.classList.add('active');
}

function closeVideoModal(event) {
    if (event.target.id === 'video-modal') closeVideoModalForce();
}

function closeVideoModalForce() {
    const modal = document.getElementById('video-modal');
    const player = document.getElementById('modal-video-player');
    player.pause();
    player.removeAttribute('src');
    player.load();
    player.ontimeupdate = null;
    modal.classList.remove('active');
    window.currentClipUrl = '';
    window.currentClipTitle = '';
}

// Download
async function downloadClip() {
    if (!window.currentClipUrl) return;
    const btn = document.getElementById('modal-download-btn');
    const originalText = btn.innerHTML;
    btn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Downloading...';
    btn.disabled = true;

    try {
        const response = await fetch(window.currentClipUrl);
        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        const safeTitle = (window.currentClipTitle || "clipz-video").replace(/[^a-z0-9]/gi, '_').toLowerCase();
        a.download = `${safeTitle}.mp4`;
        document.body.appendChild(a);
        a.click();
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

// Social
let currentPublishUrl = '';

function shareClip(title, url) {
    const fullUrl = url && url.startsWith('http') ? url : window.location.href;
    currentPublishUrl = fullUrl;
    if (navigator.share && /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent)) {
        navigator.share({
            title: title,
            text: `Check out this clip: ${title}`,
            url: fullUrl
        }).catch(() => openPublishModal());
    } else {
        openPublishModal();
    }
}

function openPublishModal() {
    document.getElementById('publish-modal').style.display = 'flex';
}

function closePublishModal(e) {
    if (e.target.id === 'publish-modal') closePublishModalForce();
}

function closePublishModalForce() {
    document.getElementById('publish-modal').style.display = 'none';
}

function mockSocialPublish(platform) {
    alert(`To publish directly to ${platform}, connect your account in Settings. For now, download the video or copy the link.`);
}

function copyPublishLinkFallback() {
    navigator.clipboard.writeText(currentPublishUrl).then(() => {
        alert('Link copied to clipboard! Paste it on your social media.');
        closePublishModalForce();
    }).catch(() => {
        alert('Unable to copy link to clipboard.');
    });
}
