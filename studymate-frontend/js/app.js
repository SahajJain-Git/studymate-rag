/**
 * StudyMate Dashboard Application Controller
 *
 * Concepts for Beginners:
 * - State Machine: The `state` object tracks the document lifecycle
 *   (selected file -> uploaded -> indexed -> querying).
 * - Safe DOM Manipulation: We use `document.createElement()` and `element.textContent`
 *   to render text received from the backend, preventing Cross-Site Scripting (XSS).
 * - Accessible Feedback: ARIA live regions and status badges inform the user
 *   about backend connectivity, upload progress, and indexing state.
 */
document.addEventListener('DOMContentLoaded', () => {

    // ==========================================
    // 1. Application State
    // ==========================================
    const state = {
        selectedFile: null,
        activeDocument: null, // Holds DocumentUploadResponse from backend
        isIndexed: false,
        isUploading: false,
        isIndexing: false,
        isQuerying: false,
        lastAnswerText: ''
    };

    // ==========================================
    // 2. DOM Elements Cache
    // ==========================================
    const el = {
        // Backend Status
        backendStatusBadge: document.getElementById('backendStatusBadge'),
        backendStatusText: document.getElementById('backendStatusText'),

        // Upload Zone (Step 1)
        dropzone: document.getElementById('dropzone'),
        fileInput: document.getElementById('fileInput'),
        selectedFileBar: document.getElementById('selectedFileBar'),
        selectedFileName: document.getElementById('selectedFileName'),
        selectedFileSize: document.getElementById('selectedFileSize'),
        removeFileBtn: document.getElementById('removeFileBtn'),
        uploadBtn: document.getElementById('uploadBtn'),
        uploadAlert: document.getElementById('uploadAlert'),

        // Active Document Status & Indexing (Step 2)
        docStatusCard: document.getElementById('docStatusCard'),
        indexStatusBadge: document.getElementById('indexStatusBadge'),
        metaTotalPages: document.getElementById('metaTotalPages'),
        metaTotalChars: document.getElementById('metaTotalChars'),
        togglePreviewsBtn: document.getElementById('togglePreviewsBtn'),
        pagePreviewsList: document.getElementById('pagePreviewsList'),
        indexBtn: document.getElementById('indexBtn'),
        indexAlert: document.getElementById('indexAlert'),

        // Question Console (Step 3)
        questionForm: document.getElementById('questionForm'),
        questionInput: document.getElementById('questionInput'),
        topKSelect: document.getElementById('topKSelect'),
        askBtn: document.getElementById('askBtn'),
        clearBtn: document.getElementById('clearBtn'),
        ragReadyIndicator: document.getElementById('ragReadyIndicator'),
        queryAlert: document.getElementById('queryAlert'),

        // Answer & Citations
        insufficientContextBanner: document.getElementById('insufficientContextBanner'),
        answerContent: document.getElementById('answerContent'),
        copyAnswerBtn: document.getElementById('copyAnswerBtn'),
        citationsContainer: document.getElementById('citationsContainer'),
        citationsCountBadge: document.getElementById('citationsCountBadge')
    };

    // ==========================================
    // 3. Initialization & Health Check
    // ==========================================
    init();

    async function init() {
        bindEvents();
        await verifyBackendConnectivity();
    }

    async function verifyBackendConnectivity() {
        try {
            el.backendStatusBadge.className = 'backend-status-badge';
            el.backendStatusText.textContent = 'Checking backend...';

            const health = await ApiClient.checkHealth();
            if (health && health.status === 'UP') {
                el.backendStatusBadge.className = 'backend-status-badge connected';
                el.backendStatusText.textContent = '● Backend Connected';
            } else {
                throw new Error('Backend returned non-UP status');
            }
        } catch (error) {
            el.backendStatusBadge.className = 'backend-status-badge disconnected';
            el.backendStatusText.textContent = '○ Backend Offline';
            showAlert(el.uploadAlert, 'error', error.message);
        }
    }

    // ==========================================
    // 4. Event Listeners
    // ==========================================
    function bindEvents() {
        // Dropzone drag & drop
        el.dropzone.addEventListener('click', () => el.fileInput.click());
        el.dropzone.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                el.fileInput.click();
            }
        });

        ['dragenter', 'dragover'].forEach(eventType => {
            el.dropzone.addEventListener(eventType, (e) => {
                e.preventDefault();
                e.stopPropagation();
                el.dropzone.classList.add('dragover');
            });
        });

        ['dragleave', 'drop'].forEach(eventType => {
            el.dropzone.addEventListener(eventType, (e) => {
                e.preventDefault();
                e.stopPropagation();
                el.dropzone.classList.remove('dragover');
            });
        });

        el.dropzone.addEventListener('drop', (e) => {
            const files = e.dataTransfer.files;
            if (files && files.length > 0) {
                handleFileSelection(files[0]);
            }
        });

        el.fileInput.addEventListener('change', () => {
            if (el.fileInput.files && el.fileInput.files.length > 0) {
                handleFileSelection(el.fileInput.files[0]);
            }
        });

        el.removeFileBtn.addEventListener('click', () => {
            clearSelectedFile();
        });

        // Step 1: Upload button
        el.uploadBtn.addEventListener('click', onUploadClick);

        // Step 2: Indexing button & Previews toggle
        el.indexBtn.addEventListener('click', onIndexClick);
        el.togglePreviewsBtn.addEventListener('click', togglePagePreviews);

        // Step 3: Question Form
        el.questionForm.addEventListener('submit', onQuestionSubmit);
        el.clearBtn.addEventListener('click', onClearQuestion);
        el.copyAnswerBtn.addEventListener('click', onCopyAnswer);
    }

    // ==========================================
    // 5. File Selection Handling
    // ==========================================
    function handleFileSelection(file) {
        hideAlert(el.uploadAlert);

        // Validation 1: Must be a PDF
        if (!file.name.toLowerCase().endsWith('.pdf') && file.type !== 'application/pdf') {
            showAlert(el.uploadAlert, 'error', 'Only PDF files are supported. Please select a file ending in .pdf');
            clearSelectedFile();
            return;
        }

        // Validation 2: Size Limit (20MB)
        const maxSizeBytes = 20 * 1024 * 1024;
        if (file.size > maxSizeBytes) {
            showAlert(el.uploadAlert, 'error', `File size (${formatFileSize(file.size)}) exceeds the maximum allowed limit of 20MB.`);
            clearSelectedFile();
            return;
        }

        state.selectedFile = file;
        el.selectedFileName.textContent = file.name;
        el.selectedFileSize.textContent = formatFileSize(file.size);
        el.selectedFileBar.style.display = 'flex';
        el.uploadBtn.disabled = false;
    }

    function clearSelectedFile() {
        state.selectedFile = null;
        el.fileInput.value = '';
        el.selectedFileBar.style.display = 'none';
        el.uploadBtn.disabled = true;
    }

    // ==========================================
    // 6. Step 1: PDF Upload Workflow
    // ==========================================
    async function onUploadClick() {
        if (!state.selectedFile || state.isUploading) return;

        try {
            state.isUploading = true;
            hideAlert(el.uploadAlert);
            setButtonLoading(el.uploadBtn, true, 'Extracting text with PDFBox...');

            // Call POST /api/documents/upload
            const response = await ApiClient.uploadPdf(state.selectedFile);

            state.activeDocument = response;
            state.isIndexed = false;

            // Update Step 2 Card
            el.metaTotalPages.textContent = response.totalPages;
            el.metaTotalChars.textContent = response.totalCharacters.toLocaleString();
            renderPagePreviews(response.pages);

            el.docStatusCard.style.display = 'block';
            updateIndexBadge('not-indexed');
            el.indexBtn.disabled = false;
            hideAlert(el.indexAlert);

            // Reset Question Console since a new document replaces the previous one
            disableQuestionConsole('Please click "Index Document" above before asking questions.');
            resetAnswerAndCitations();

            showAlert(el.uploadAlert, 'success', `Successfully parsed "${response.filename}" (${response.totalPages} pages). Now click "Index Document" below.`);
        } catch (error) {
            showAlert(el.uploadAlert, 'error', error.message);
        } finally {
            state.isUploading = false;
            setButtonLoading(el.uploadBtn, false, 'Extract & Upload PDF');
        }
    }

    function renderPagePreviews(pages) {
        el.pagePreviewsList.innerHTML = '';
        if (!pages || pages.length === 0) return;

        pages.forEach(p => {
            const item = document.createElement('div');
            item.className = 'page-preview-item';

            const header = document.createElement('div');
            header.className = 'page-preview-header';

            const pageNum = document.createElement('span');
            pageNum.textContent = `Page ${p.pageNumber}`;

            const charCount = document.createElement('span');
            charCount.textContent = `${p.characterCount} chars`;

            header.appendChild(pageNum);
            header.appendChild(charCount);

            const text = document.createElement('div');
            text.className = 'page-preview-text';
            text.textContent = p.preview || '(Blank text)';

            item.appendChild(header);
            item.appendChild(text);
            el.pagePreviewsList.appendChild(item);
        });
    }

    function togglePagePreviews() {
        const isHidden = el.pagePreviewsList.style.display === 'none';
        el.pagePreviewsList.style.display = isHidden ? 'block' : 'none';
        el.togglePreviewsBtn.querySelector('span').textContent = isHidden
            ? 'Hide Page Previews ▲'
            : 'Show Page Previews ▼';
    }

    // ==========================================
    // 7. Step 2: Vector Indexing Workflow
    // ==========================================
    async function onIndexClick() {
        if (!state.activeDocument || state.isIndexing) return;

        try {
            state.isIndexing = true;
            hideAlert(el.indexAlert);
            updateIndexBadge('indexing');
            setButtonLoading(el.indexBtn, true, 'Chunking & Vectorizing...');

            // Call POST /api/documents/index
            const response = await ApiClient.indexDocument();

            state.isIndexed = true;
            updateIndexBadge('indexed');
            el.indexBtn.disabled = true;

            showAlert(el.indexAlert, 'success', `Indexed ${response.totalChunksIndexed} chunks into VectorStore. Ready for search!`);

            // Enable Question Console
            enableQuestionConsole();
        } catch (error) {
            updateIndexBadge('not-indexed');
            showAlert(el.indexAlert, 'error', error.message);
        } finally {
            state.isIndexing = false;
            setButtonLoading(el.indexBtn, false, '⚡ Index Document for Search');
        }
    }

    function updateIndexBadge(status) {
        if (status === 'indexed') {
            el.indexStatusBadge.className = 'status-pill indexed';
            el.indexStatusBadge.textContent = '✓ Indexed & Searchable';
        } else if (status === 'indexing') {
            el.indexStatusBadge.className = 'status-pill indexing';
            el.indexStatusBadge.textContent = '⏳ Indexing Chunks...';
        } else {
            el.indexStatusBadge.className = 'status-pill not-indexed';
            el.indexStatusBadge.textContent = 'Not Indexed';
        }
    }

    // ==========================================
    // 8. Step 3: Question Answering Workflow
    // ==========================================
    function enableQuestionConsole() {
        el.questionInput.disabled = false;
        el.askBtn.disabled = false;
        el.ragReadyIndicator.textContent = '✓ Ready to answer questions';
        el.ragReadyIndicator.style.color = 'var(--success)';
        el.questionInput.focus();
    }

    function disableQuestionConsole(reasonText) {
        el.questionInput.disabled = true;
        el.askBtn.disabled = true;
        el.ragReadyIndicator.textContent = reasonText;
        el.ragReadyIndicator.style.color = 'var(--text-dim)';
    }

    async function onQuestionSubmit(e) {
        e.preventDefault();
        const question = el.questionInput.value.trim();

        if (!question) {
            showAlert(el.queryAlert, 'error', 'Please enter a question.');
            el.questionInput.focus();
            return;
        }

        if (!state.isIndexed) {
            showAlert(el.queryAlert, 'error', 'Please upload and index a document first.');
            return;
        }

        try {
            state.isQuerying = true;
            hideAlert(el.queryAlert);
            setButtonLoading(el.askBtn, true, 'Searching & synthesizing...');
            showAnswerLoading();

            const topK = parseInt(el.topKSelect.value, 10) || 4;

            // Call POST /api/query
            const response = await ApiClient.askQuestion(question, topK, 0.0);

            renderAnswerAndCitations(response);
        } catch (error) {
            showAlert(el.queryAlert, 'error', error.message);
            renderAnswerError(error.message);
        } finally {
            state.isQuerying = false;
            setButtonLoading(el.askBtn, false, '🔍 Ask StudyMate');
        }
    }

    function onClearQuestion() {
        el.questionInput.value = '';
        hideAlert(el.queryAlert);
        el.questionInput.focus();
    }

    function showAnswerLoading() {
        el.answerContent.innerHTML = '';
        const loadingBox = document.createElement('div');
        loadingBox.className = 'empty-state';

        const spinner = document.createElement('span');
        spinner.className = 'spinner spinner-dark';
        spinner.style.width = '24px';
        spinner.style.height = '24px';
        spinner.style.marginBottom = '0.75rem';

        const text = document.createElement('p');
        text.className = 'empty-state-text';
        text.textContent = 'Searching vector store and synthesizing grounded answer...';

        loadingBox.appendChild(spinner);
        loadingBox.appendChild(text);
        el.answerContent.appendChild(loadingBox);

        el.insufficientContextBanner.style.display = 'none';
        el.copyAnswerBtn.disabled = true;
    }

    // Safe DOM Rendering for Answer & Citations
    function renderAnswerAndCitations(response) {
        state.lastAnswerText = response.answer || '';

        // 1. Render Answer Text safely
        el.answerContent.innerHTML = '';
        const answerTextNode = document.createElement('p');
        answerTextNode.textContent = response.answer;
        el.answerContent.appendChild(answerTextNode);
        el.copyAnswerBtn.disabled = false;

        // 2. Insufficient Context Banner
        if (response.hasSufficientContext === false) {
            el.insufficientContextBanner.style.display = 'flex';
        } else {
            el.insufficientContextBanner.style.display = 'none';
        }

        // 3. Render Citations safely
        renderCitations(response.citations);
    }

    function renderCitations(citations) {
        el.citationsContainer.innerHTML = '';
        const count = citations ? citations.length : 0;
        el.citationsCountBadge.textContent = count;

        if (count === 0) {
            const empty = document.createElement('div');
            empty.className = 'empty-state';

            const icon = document.createElement('span');
            icon.className = 'empty-state-icon';
            icon.textContent = '📑';

            const text = document.createElement('p');
            text.className = 'empty-state-text';
            text.textContent = 'No relevant passages retrieved for this question.';

            empty.appendChild(icon);
            empty.appendChild(text);
            el.citationsContainer.appendChild(empty);
            return;
        }

        const list = document.createElement('div');
        list.className = 'citations-list';

        citations.forEach(c => {
            const card = document.createElement('div');
            card.className = 'citation-card';

            const header = document.createElement('div');
            header.className = 'citation-header';

            const pageBadge = document.createElement('span');
            pageBadge.className = 'citation-page-badge';
            pageBadge.textContent = `[Page ${c.pageNumber}]`;

            const scoreBadge = document.createElement('span');
            scoreBadge.className = 'citation-score-badge';
            const scoreVal = (typeof c.score === 'number') ? c.score.toFixed(4) : '0.0000';
            scoreBadge.textContent = `Score: ${scoreVal}`;

            header.appendChild(pageBadge);
            header.appendChild(scoreBadge);

            const source = document.createElement('div');
            source.className = 'citation-source';
            source.textContent = c.sourceFilename || 'document.pdf';

            const snippet = document.createElement('div');
            snippet.className = 'citation-snippet';
            snippet.textContent = `"${c.textPreview || ''}"`;

            const chunkId = document.createElement('div');
            chunkId.className = 'citation-chunk-id';
            chunkId.textContent = `Chunk: ${c.chunkId}`;

            card.appendChild(header);
            card.appendChild(source);
            card.appendChild(snippet);
            card.appendChild(chunkId);

            list.appendChild(card);
        });

        el.citationsContainer.appendChild(list);
    }

    function renderAnswerError(errorMessage) {
        el.answerContent.innerHTML = '';
        const errBox = document.createElement('div');
        errBox.className = 'alert alert-error';
        errBox.textContent = `Error: ${errorMessage}`;
        el.answerContent.appendChild(errBox);
        el.copyAnswerBtn.disabled = true;
    }

    function resetAnswerAndCitations() {
        el.answerContent.innerHTML = `
            <div class="empty-state">
                <span class="empty-state-icon" aria-hidden="true">💡</span>
                <p class="empty-state-text">Your grounded answer will appear here once you ask a question.</p>
            </div>
        `;
        el.citationsContainer.innerHTML = `
            <div class="empty-state">
                <span class="empty-state-icon" aria-hidden="true">📑</span>
                <p class="empty-state-text">No citations retrieved yet. Verified source passages will be displayed here.</p>
            </div>
        `;
        el.citationsCountBadge.textContent = '0';
        el.insufficientContextBanner.style.display = 'none';
        el.copyAnswerBtn.disabled = true;
    }

    async function onCopyAnswer() {
        if (!state.lastAnswerText) return;
        try {
            await navigator.clipboard.writeText(state.lastAnswerText);
            const originalText = el.copyAnswerBtn.querySelector('span').textContent;
            el.copyAnswerBtn.querySelector('span').textContent = '✓ Copied!';
            setTimeout(() => {
                el.copyAnswerBtn.querySelector('span').textContent = originalText;
            }, 1800);
        } catch (err) {
            console.error('Failed to copy to clipboard', err);
        }
    }

    // ==========================================
    // 9. UI Utility Helpers
    // ==========================================
    function showAlert(element, type, message) {
        element.className = `alert alert-${type}`;
        element.textContent = message;
        element.style.display = 'block';
    }

    function hideAlert(element) {
        element.style.display = 'none';
        element.textContent = '';
    }

    function setButtonLoading(button, isLoading, loadingText) {
        button.disabled = isLoading;
        const span = button.querySelector('span');
        if (isLoading) {
            button.dataset.originalText = span.textContent;
            span.innerHTML = `<span class="spinner" aria-hidden="true"></span> ${escapeHtml(loadingText)}`;
        } else {
            span.textContent = button.dataset.originalText || loadingText;
        }
    }

    function formatFileSize(bytes) {
        if (bytes < 1024) return `${bytes} B`;
        if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
        return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
    }

    function escapeHtml(str) {
        return str.replace(/[&<>"']/g, match => {
            const escapeMap = {
                '&': '&amp;',
                '<': '&lt;',
                '>': '&gt;',
                '"': '&quot;',
                "'": '&#39;'
            };
            return escapeMap[match];
        });
    }
});
