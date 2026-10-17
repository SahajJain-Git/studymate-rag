package com.studymate.controller;

import com.studymate.dto.QuestionRequest;
import com.studymate.dto.QuestionResponse;
import com.studymate.service.RagService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller providing the student question answering endpoint for StudyMate.
 *
 * Concepts for Beginners:
 * - Thin Controller Pattern: This controller contains NO vector search logic, prompt templates,
 *   or AI parsing algorithms. Its sole job is to accept the HTTP request, trigger input validation,
 *   delegate execution to RagService, and return the QuestionResponse DTO.
 * - @Valid: Automatically triggers Bean Validation on the incoming QuestionRequest body.
 *   If the student sends a blank or missing question string, Spring immediately rejects the
 *   request with HTTP 400 Bad Request via GlobalExceptionHandler before RagService is even called.
 */
@RestController
@RequestMapping("/api/query")
public class QueryController {

    private final RagService ragService;

    public QueryController(RagService ragService) {
        this.ragService = ragService;
    }

    /**
     * Answers a natural-language question grounded in the currently indexed document.
     * POST /api/query
     *
     * @param request Validated QuestionRequest payload containing question and optional parameters
     * @return ResponseEntity containing QuestionResponse with answer and page citations
     */
    @PostMapping
    public ResponseEntity<QuestionResponse> askQuestion(@Valid @RequestBody QuestionRequest request) {
        QuestionResponse response = ragService.answerQuestion(request);
        return ResponseEntity.ok(response);
    }
}
