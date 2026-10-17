package com.studymate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for Spring Boot startup and base health endpoint.
 *
 * Concepts for Beginners:
 * - @SpringBootTest: Boots the full Spring ApplicationContext for testing.
 * - @AutoConfigureMockMvc: Configures MockMvc, which allows testing HTTP endpoints
 *   without starting an actual network socket.
 * - MockMvc: Simulates HTTP requests directly against Spring's DispatcherServlet
 *   and asserts status code, content type, and JSON payload fields.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StudyMateApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Context loads successfully")
    void contextLoads() {
        // Verifies the Spring ApplicationContext can start without configuration errors.
    }

    @Test
    @DisplayName("GET /api/health returns 200 OK and expected diagnostic payload")
    void healthEndpointReturnsOk() throws Exception {
        mockMvc.perform(get("/api/health")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.appName").value("studymate"))
                .andExpect(jsonPath("$.stage").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
