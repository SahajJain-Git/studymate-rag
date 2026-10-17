/**
 * StudyMate API Client
 *
 * Concepts for Beginners:
 * - Fetch API: The standard browser API for making asynchronous HTTP network requests.
 * - FormData: Used to send multipart file uploads (like PDF binaries) without manually
 *   setting the Content-Type header (the browser automatically adds multipart boundaries).
 * - Error Handling: When the backend returns a 4xx or 5xx status code, fetch() does NOT
 *   reject its promise. We must check response.ok and extract the server's ErrorResponse JSON.
 */
const ApiClient = {

    /**
     * Diagnostic health check to verify backend connectivity.
     * GET /api/health
     */
    async checkHealth() {
        try {
            const response = await fetch(`${APP_CONFIG.API_BASE_URL}/api/health`, {
                method: 'GET',
                headers: { 'Accept': 'application/json' }
            });
            return await this._parseResponse(response);
        } catch (error) {
            throw this._normalizeNetworkError(error);
        }
    },

    /**
     * Uploads and extracts text from a PDF file.
     * POST /api/documents/upload
     *
     * @param {File} file The selected PDF file object from input or drag-and-drop
     */
    async uploadPdf(file) {
        try {
            const formData = new FormData();
            formData.append('file', file);

            const response = await fetch(`${APP_CONFIG.API_BASE_URL}/api/documents/upload`, {
                method: 'POST',
                body: formData
                // Note: Do NOT set 'Content-Type': 'multipart/form-data'.
                // The browser automatically sets it with the proper multipart boundary string.
            });
            return await this._parseResponse(response);
        } catch (error) {
            throw this._normalizeNetworkError(error);
        }
    },

    /**
     * Triggers vector indexing of the uploaded document.
     * POST /api/documents/index
     */
    async indexDocument() {
        try {
            const response = await fetch(`${APP_CONFIG.API_BASE_URL}/api/documents/index`, {
                method: 'POST',
                headers: {
                    'Accept': 'application/json'
                }
            });
            return await this._parseResponse(response);
        } catch (error) {
            throw this._normalizeNetworkError(error);
        }
    },

    /**
     * Inspects chunks generated from the currently active document.
     * GET /api/documents/chunks
     */
    async getChunks() {
        try {
            const response = await fetch(`${APP_CONFIG.API_BASE_URL}/api/documents/chunks`, {
                method: 'GET',
                headers: { 'Accept': 'application/json' }
            });
            return await this._parseResponse(response);
        } catch (error) {
            throw this._normalizeNetworkError(error);
        }
    },

    /**
     * Submits a student question for RAG answer generation.
     * POST /api/query
     *
     * @param {string} question The student's question text
     * @param {number} topK Maximum relevant passages to retrieve
     * @param {number} minThreshold Minimum similarity score filter
     */
    async askQuestion(question, topK = 4, minThreshold = 0.0) {
        try {
            const payload = {
                question: question,
                topK: topK,
                minThreshold: minThreshold
            };

            const response = await fetch(`${APP_CONFIG.API_BASE_URL}/api/query`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                },
                body: JSON.stringify(payload)
            });
            return await this._parseResponse(response);
        } catch (error) {
            throw this._normalizeNetworkError(error);
        }
    },

    /**
     * Internal helper to parse responses and unpack Spring Boot ErrorResponse on failure.
     */
    async _parseResponse(response) {
        let responseData = null;
        const contentType = response.headers.get('content-type');

        if (contentType && contentType.includes('application/json')) {
            try {
                responseData = await response.json();
            } catch (jsonErr) {
                responseData = null;
            }
        }

        if (!response.ok) {
            const status = response.status;
            let errorMessage = `Server error (${status}): ${response.statusText}`;

            if (responseData && responseData.message) {
                errorMessage = responseData.message;
            }

            const error = new Error(errorMessage);
            error.status = status;
            error.data = responseData;
            throw error;
        }

        return responseData;
    },

    /**
     * Internal helper to detect connection failure (e.g. backend down or CORS blocked).
     */
    _normalizeNetworkError(error) {
        if (error instanceof TypeError && error.message.includes('fetch')) {
            const netErr = new Error(
                `Unable to connect to the StudyMate backend at ${APP_CONFIG.API_BASE_URL}. ` +
                `Please verify that the Spring Boot server is running and CORS is enabled.`
            );
            netErr.status = 0;
            return netErr;
        }
        return error;
    }
};
