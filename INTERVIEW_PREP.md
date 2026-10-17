# StudyMate: Technical Interview Preparation Guide

This guide is designed to help you confidently explain the architecture, design choices, and implementation details of **StudyMate** in technical interviews for Java Developer, Backend Engineer, and AI/RAG Engineer roles.

---

## 🎯 High-Level Pitch (The "30-Second Elevator Pitch")

> *"I designed and built **StudyMate**, a Spring Boot-based Retrieval-Augmented Generation (RAG) assistant that allows users to upload PDF study documents and ask natural-language questions grounded strictly in their text. The architecture uses a layered monolith design with Apache PDFBox for extraction, a sliding-window chunking strategy with semantic overlap, vector embeddings via Spring AI, an in-memory vector store for sub-millisecond similarity search, and an augmented prompt pipeline sent to an LLM to prevent hallucinations. The system runs independently without mandatory external database infrastructure and includes clean separation of concerns with thin controllers, robust error handling, and test-driven validation."*

---

## 💡 Stage 1 Architectural Interview Questions & Answers

### 1. Why a Layered Monolith instead of Microservices?
**Interviewer:** *"Why didn't you split this into a PDF Service, an Embedding Service, and a Query Service?"*

**Answer:**
> *"For StudyMate, a layered monolith is the optimal architectural choice because:*
> 1. *It eliminates unnecessary network latency and serialization overhead between the text extractor, chunker, and vector store.*
> 2. *It avoids distributed transaction challenges and infrastructure complexity (service discovery, API gateways, multiple deployments) when our primary objective is high internal coherence and clear data flow.*
> 3. *Our internal modules (`PdfService`, `ChunkingService`, `RagService`) have well-defined interfaces. If traffic scales unevenly in the future (for example, if document ingestion becomes CPU-heavy), we can easily extract `PdfService` or the vector indexing module into an asynchronous worker or separate service with minimal refactoring."*

---

### 2. How does Spring Boot's Auto-Configuration work under the hood?
**Interviewer:** *"What happens when `@SpringBootApplication` is placed on the main class?"*

**Answer:**
> *"The `@SpringBootApplication` annotation is a composite meta-annotation that brings together three core capabilities:*
> 1. `@SpringBootConfiguration`: *Specifies that the class provides Spring configuration (declares `@Bean` definitions).*
> 2. `@ComponentScan`: *Directs Spring to recursively scan the current package and subpackages for `@Component`, `@Service`, `@Repository`, and `@RestController` classes and instantiate them as Spring Beans in the ApplicationContext.*
> 3. `@EnableAutoConfiguration`: *Triggers Spring Boot's auto-configuration mechanism. Spring inspects the JARs on the classpath (for instance, seeing `tomcat-embed-core` and `spring-webmvc`) and uses conditional annotations (such as `@ConditionalOnClass` and `@ConditionalOnMissingBean`) to automatically instantiate and wire the necessary beans (such as the embedded Tomcat server and `DispatcherServlet`) without requiring boilerplate XML or manual Java configurations."*

---

### 3. What is a Java `record`, and why use it for DTOs?
**Interviewer:** *"Why did you use a `record` for `HealthResponse` instead of a regular class or Lombok `@Data`?"*

**Answer:**
> *"Java records (introduced as a standard feature in Java 16) are purpose-built as transparent, immutable carriers for nominal data:*
> - *They automatically generate `final` private fields, canonical constructors, component accessors (`fieldName()`), `equals()`, `hashCode()`, and `toString()`.*
> - *Unlike Lombok, records are built directly into the Java language and runtime specification, requiring no third-party annotation processors or bytecode manipulation.*
> - *Immutability is critical for DTOs in concurrent web applications: once instantiated by Jackson or a service, a DTO should never be mutated by accident across threads."*

---

### 4. What is the role of `MockMvc` in Spring Boot testing?
**Interviewer:** *"Why use `MockMvc` instead of sending real HTTP requests via `RestTemplate` or `WebTestClient`?"*

**Answer:**
> *"With `@AutoConfigureMockMvc`, Spring test framework boots the `DispatcherServlet` and full application context in-memory without binding to a physical network port. This gives us several advantages:*
> 1. **Speed & Resource Efficiency:** *Tests run in milliseconds without port conflicts or socket lifecycle overhead.*
> 2. **Complete MVC Verification:** *We can test URL routing, serialization, HTTP status codes, filters, and validation annotations exactly as they would behave in production.*
> 3. **Determinism:** *Tests are immune to host machine firewall issues or port collisions."*

---

### 5. Why is a placeholder API key an anti-pattern, and how do you achieve true offline capability?
**Interviewer:** *"Can't we just set `spring.ai.openai.api-key: fake-key` in `application.yml` for local development?"*

**Answer:**
> *"Setting a dummy string as an API key is a fragile anti-pattern for three reasons:*
> 1. **It is not a mock:** *The underlying OpenAI HTTP client still attempts to connect to remote endpoints during execution and fails with runtime HTTP 401 Unauthorized errors.*
> 2. **Premature Auto-Configuration Coupling:** *Adding `spring-ai-openai-spring-boot-starter` prematurely forces Spring Boot to load `OpenAiAutoConfiguration`, which requires API keys for chat, embeddings, and image models upon application context initialization.*
> 3. **Clean Decoupling:** *In enterprise software design, stages or modules that do not need remote external services (such as PDF extraction in Stage 2 and text chunking in Stage 3) should only declare core domain dependencies (like `spring-ai-core`). When we introduce vector storage and AI in Stage 4/5, we provide a deterministic in-memory `MockEmbeddingModel` / mock chat service alongside production providers, enabling 100% offline testing without network calls or dummy credentials."*

---

## 💡 Stage 2 Document Ingestion & PDF Processing Questions & Answers

### 6. Why can't we trust MIME types or file extensions alone for PDF validation?
**Interviewer:** *"Why did you check magic bytes instead of just inspecting `file.getContentType()` or checking if the filename ends with `.pdf`?"*

**Answer:**
> *"Both filename and MIME type are completely client-controlled and easily forged:*
> 1. *A client or attacker can rename an executable, script, or plain text file to `notes.pdf` and set the HTTP `Content-Type: application/pdf` header.*
> 2. *Passing disguised files to a PDF parser can cause memory exhaustion, buffer issues, or unexpected parser crashes.*
> 3. *True content inspection requires validating the file's **magic byte header**. Every compliant PDF begins with the 5-byte sequence `%PDF-` (`0x25, 0x50, 0x44, 0x46, 0x2D`). In `PdfService`, we inspect the initial bytes directly from the binary stream before invoking PDFBox, guaranteeing defense-in-depth."*

---

### 7. What is new in Apache PDFBox 3.0 compared to PDFBox 2.0 regarding resource management?
**Interviewer:** *"How did you load the PDF document using PDFBox, and what memory considerations did you account for?"*

**Answer:**
> *"In Apache PDFBox 3.0.x, the legacy static method `PDDocument.load(...)` was deprecated and replaced with `Loader.loadPDF(...)`:*
> - *We wrap `Loader.loadPDF(fileBytes)` inside a Java **`try-with-resources`** block. `PDDocument` implements `AutoCloseable`, ensuring that native off-heap memory, scratch buffers, and internal font caches are deterministically freed when parsing finishes or if an exception is thrown.*
> - *We avoid saving uploaded files to permanent disk storage, processing the stream in memory to prevent lingering temporary files, disk exhaustion, or concurrent I/O bottlenecks."*

---

### 8. Why is it essential to preserve page numbers in a RAG ingestion pipeline?
**Interviewer:** *"Why didn't you just concatenate all pages into one large `String` during PDF extraction?"*

**Answer:**
> *"Preserving page numbers (`ExtractedPage(pageNumber, text)`) is vital for trustworthy, enterprise-grade RAG systems:*
> 1. **Explainability & Grounding:** *When an LLM answers a student's question, it must provide verifiable citations (e.g., 'See Chapter 2, Page 4'). Without tracking source page numbers during extraction and chunking, attributing answers back to specific textbook pages becomes impossible.*
> 2. **Evaluation & Verification:** *In automated RAG evaluation (e.g. Ragas framework), we test Context Relevance and Groundedness. Having exact page metadata allows automated evaluation metrics to assert whether the retrieved chunk originated from the correct textbook section."*

---

### 9. How do Thin Controllers and `@RestControllerAdvice` work together?
**Interviewer:** *"What is the architectural benefit of using `@RestControllerAdvice` instead of `try/catch` blocks inside your controller methods?"*

**Answer:**
> *"Using `@RestControllerAdvice` adheres to the Single Responsibility Principle and the Thin Controller pattern:*
> - **Separation of Concerns:** *Controllers focus purely on HTTP routing, request deserialization, and HTTP response mapping. Business exceptions (`InvalidFileException`, `EmptyPdfException`, `PdfProcessingException`) bubble up naturally.*
> - **Centralized Error Policy:** *The advice acts as an interceptor across all controllers. If we decide to adjust our error payload schema or HTTP status mappings, we update a single method in `GlobalExceptionHandler` rather than dozens of controller endpoints.*
> - **Consistent Client Contract:** *The client is guaranteed a uniform JSON schema (`ErrorResponse`) with standard timestamp, status, error, and message attributes for every 4xx and 5xx condition."*

---

*(This document will expand after each stage to cover chunking trade-offs, vector similarity metrics, prompt injection mitigation, and RAG evaluation).*


