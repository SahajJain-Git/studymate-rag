package com.studymate.controller;

import com.studymate.dto.ErrorResponse;
import com.studymate.exception.EmptyPdfException;
import com.studymate.exception.InvalidFileException;
import com.studymate.exception.NoDocumentUploadedException;
import com.studymate.exception.PdfProcessingException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.Instant;

/**
 * Centralized exception interceptor for all REST controllers.
 *
 * Concepts for Beginners:
 * - @RestControllerAdvice: Intercepts exceptions thrown by any @RestController across the application.
 *   Acts as an AOP (Aspect-Oriented Programming) advice, preventing try-catch clutter inside controllers.
 * - Uniform Response Structure: Regardless of what fails (validation, corrupted PDF, file size limit),
 *   the client always receives an ErrorResponse DTO with the same consistent schema and appropriate HTTP status.
 * - Security: Internal stack traces are logged on the server for debugging, but never leaked in the HTTP response.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles requests for document chunks or queries when no document has been uploaded yet.
     * HTTP 404 Not Found
     */
    @ExceptionHandler(NoDocumentUploadedException.class)
    public ResponseEntity<ErrorResponse> handleNoDocumentUploaded(
            NoDocumentUploadedException ex, HttpServletRequest request) {

        log.warn("No active document on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    /**
     * Handles invalid client input (missing files, bad file extensions, magic byte mismatch).
     * HTTP 400 Bad Request
     */
    @ExceptionHandler(InvalidFileException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFile(
            InvalidFileException ex, HttpServletRequest request) {

        log.warn("Invalid file request on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /**
     * Handles illegal arguments (e.g. invalid chunking configuration).
     * HTTP 400 Bad Request
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {

        log.warn("Illegal argument on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /**
     * Handles PDFs that are syntactically valid but contain zero extractable text (e.g. scanned image-only PDFs).
     * HTTP 422 Unprocessable Entity
     */
    @ExceptionHandler(EmptyPdfException.class)
    public ResponseEntity<ErrorResponse> handleEmptyPdf(
            EmptyPdfException ex, HttpServletRequest request) {

        log.warn("Unprocessable empty PDF on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);
    }

    /**
     * Handles PDFBox parsing errors, decryption issues, or corrupted PDF streams.
     * HTTP 422 Unprocessable Entity
     */
    @ExceptionHandler(PdfProcessingException.class)
    public ResponseEntity<ErrorResponse> handlePdfProcessing(
            PdfProcessingException ex, HttpServletRequest request) {

        log.error("PDF processing error on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);
    }

    /**
     * Handles requests exceeding Spring's configured multipart upload limits.
     * HTTP 413 Payload Too Large
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {

        log.warn("Upload size limit exceeded on {}: {}", request.getRequestURI(), ex.getMessage());
        String message = "File size exceeds the configured maximum allowed upload limit (20MB).";
        return buildErrorResponse(HttpStatus.PAYLOAD_TOO_LARGE, message, request);
    }

    /**
     * Handles missing 'file' multipart parameter in upload requests.
     * HTTP 400 Bad Request
     */
    @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleMissingRequestPart(
            Exception ex, HttpServletRequest request) {

        log.warn("Missing required request parameter on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Required request parameter 'file' is missing.", request);
    }

    /**
     * Fallback handler for any uncaught unexpected exceptions.
     * HTTP 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {

        log.error("Unhandled exception on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        String safeMessage = "An unexpected server error occurred while processing your request.";
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, safeMessage, request);
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(
            HttpStatus status, String message, HttpServletRequest request) {

        ErrorResponse errorResponse = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(errorResponse);
    }
}
