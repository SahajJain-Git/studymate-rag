package com.studymate.exception;

/**
 * Thrown when an uploaded file fails fundamental validation.
 *
 * Examples:
 * - File is null or has 0 bytes.
 * - File does not have a .pdf extension.
 * - File fails magic byte validation (%PDF- signature is missing).
 */
public class InvalidFileException extends RuntimeException {

    public InvalidFileException(String message) {
        super(message);
    }

    public InvalidFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
