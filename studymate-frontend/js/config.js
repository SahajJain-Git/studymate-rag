/**
 * StudyMate Frontend Configuration
 *
 * Concepts for Beginners:
 * - Centralized Configuration: By keeping API_BASE_URL in this single file,
 *   you only need to change it here if your Spring Boot backend runs on a different
 *   port or host.
 */
const APP_CONFIG = {
    // Default URL for the local Spring Boot backend server
    API_BASE_URL: 'http://localhost:8080'
};
