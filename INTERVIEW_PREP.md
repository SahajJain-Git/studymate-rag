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

*(This document will expand after each stage to cover chunking trade-offs, vector similarity metrics, prompt injection mitigation, and RAG evaluation).*
