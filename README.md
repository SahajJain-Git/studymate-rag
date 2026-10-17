# StudyMate: Beginner-Friendly AI Study Assistant (RAG)

Welcome to **StudyMate**! StudyMate is an educational, production-style AI study companion built with **Java 21/25**, **Spring Boot 3.3.5**, **Apache PDFBox**, and **Spring AI**.

The core mission of StudyMate is to allow students to upload course materials or textbook chapters in PDF format, index the concepts using vector embeddings, and ask natural-language questions to receive accurate, context-aware answers grounded strictly in the source material (avoiding hallucinations).

---

## 🏛️ High-Level System Architecture

StudyMate follows a **Layered Monolith Architecture**. We intentionally avoid microservices and distributed complexity so that every step of the Retrieval-Augmented Generation (RAG) lifecycle is clear, transparent, and debuggable in pure Java.

```
+-------------------------------------------------------------------------+
|                          Web / Browser Client                           |
|      (Vanilla HTML5, CSS3, JavaScript Fetch API - No Node/Webpack)      |
+------------------------------------+------------------------------------+
                                     |  HTTP REST
                                     v
+-------------------------------------------------------------------------+
|                           Presentation Layer                            |
|  - DocumentController (PDF Upload)                                      |
|  - QueryController    (Question Answering)                              |
|  - HealthController   (System Status & Diagnostics)                     |
+------------------------------------+------------------------------------+
                                     |  DTOs / Method Calls
                                     v
+-------------------------------------------------------------------------+
|                             Service Layer                               |
|  - PdfService         (Parses & sanitizes raw PDF text via PDFBox)      |
|  - ChunkingService    (Splits text into overlapping semantic windows)   |
|  - RagService         (Coordinates retrieval and LLM prompt synthesis)  |
+------------------------------------+------------------------------------+
                                     |  Domain Objects
                                     v
+-------------------------------------------------------------------------+
|                     AI & Vector Store Integration                       |
|  - SimpleVectorStore  (In-memory cosine similarity vector index)        |
|  - EmbeddingModel     (Generates dense mathematical text embeddings)    |
|  - ChatModel          (Interacts with LLM for context-aware generation) |
+-------------------------------------------------------------------------+
```

---

## 🔄 Core RAG Lifecycle

StudyMate executes two primary data workflows:

### 1. Ingestion Pipeline (Upload -> Index)
1. **PDF Upload**: User uploads a document via `POST /api/documents/upload`.
2. **Text Extraction**: `PdfService` parses the document using Apache PDFBox 3.
3. **Chunking**: `ChunkingService` breaks continuous text into uniform chunks (e.g., 500 characters with a 50-character overlap) to preserve contextual boundaries.
4. **Vector Embedding**: Each chunk is transformed into a dense numerical vector via the `EmbeddingModel`.
5. **Vector Indexing**: Chunks and their vectors are stored in memory using `SimpleVectorStore`.

### 2. Retrieval & Generation Pipeline (Query -> Answer)
1. **User Query**: Student submits a question via `POST /api/query`.
2. **Query Embedding**: The user's question is converted into a vector in the same semantic space.
3. **Similarity Search**: Cosine similarity matches the top $k$ most relevant text chunks from the vector store.
4. **Prompt Augmentation**: A structured prompt is synthesized containing:
   - System instructions ("Answer strictly using the provided context").
   - Retrieved context chunks.
   - Student's question.
5. **Generation**: The `ChatModel` generates a grounded response, citing relevant context.

---

## 📋 Implementation Roadmap

| Stage | Focus Area | Status | Description |
|---|---|---|---|
| **Stage 1** | **Spring Boot Foundation** | **Completed** | Maven setup, application configuration, diagnostic health endpoint, initial tests. |
| **Stage 2** | **PDF Upload & Text Extraction** | **Completed** | Multipart upload, Apache PDFBox 3 integration, magic byte validation, page-level text extraction. |
| **Stage 3** | **Text Chunking Strategy** | *Next* | Configurable token/character windowing with sliding overlap. |
| **Stage 4** | **Embeddings & Vector Storage** | *Upcoming* | Spring AI VectorStore integration, embedding generation, top-k similarity search. |
| **Stage 5** | **RAG Question Answering** | *Upcoming* | Augmented prompt templating, LLM interaction, hallucination mitigation. |
| **Stage 6** | **Simple Frontend** | *Upcoming* | Clean UI (drag-and-drop PDF upload, question console, source citation drawer). |
| **Stage 7** | **Testing & Integration** | *Upcoming* | End-to-end integration tests, error handling, mock vs live API testing. |
| **Stage 8** | **Interview Preparation Guide** | *Upcoming* | Deep architectural Q&A, trade-off analysis, resume-ready bullet points. |

---

## 🚀 Running & Testing the Application

### Prerequisites
- **Java 21+** (or Java 25 LTS)
- Maven (or bundled `./mvnw` / `mvnw.cmd`)

### Running the Full Test Suite
```bash
./mvnw clean test
```

### Starting the Server Locally
```bash
./mvnw spring-boot:run
```

---

## 📡 API Endpoints (Stages 1 & 2)

### 1. Diagnostic Health Check
- **Endpoint:** `GET /api/health`
- **Response:** `200 OK`
```json
{
  "status": "UP",
  "appName": "studymate",
  "timestamp": "2026-10-17T14:48:00.000Z",
  "stage": "Stage 1 - Spring Boot Foundation"
}
```

### 2. Document Upload & Text Extraction
- **Endpoint:** `POST /api/documents/upload`
- **Content-Type:** `multipart/form-data`
- **Parameter:** `file` (a valid `.pdf` binary)

#### cURL Example:
```bash
curl -X POST http://localhost:8080/api/documents/upload \
  -F "file=@/path/to/lecture-notes.pdf"
```

#### Success Response (`200 OK`):
```json
{
  "filename": "lecture-notes.pdf",
  "totalPages": 2,
  "totalCharacters": 1540,
  "pages": [
    {
      "pageNumber": 1,
      "characterCount": 820,
      "preview": "Chapter 1: Object-Oriented Principles. Encapsulation hide internal state..."
    },
    {
      "pageNumber": 2,
      "characterCount": 720,
      "preview": "Polymorphism allows subclasses to define customized behavior while..."
    }
  ],
  "message": "Successfully parsed 2 pages (1540 characters). Ready for chunking."
}
```

#### Error Responses:
- **`400 Bad Request`**: Missing file, non-PDF extension, or magic byte header mismatch (disguised text files).
  ```json
  {
    "timestamp": "2026-10-17T14:48:21.000Z",
    "status": 400,
    "error": "Bad Request",
    "message": "File header is not a valid PDF document (magic byte signature mismatch).",
    "path": "/api/documents/upload"
  }
  ```
- **`413 Payload Too Large`**: Uploaded file exceeds the 20MB limit configured in `application.yml`.
- **`422 Unprocessable Entity`**: Corrupted PDF binary stream, password-protected PDF, or textless scanned/image-only PDF.
  ```json
  {
    "timestamp": "2026-10-17T14:48:21.000Z",
    "status": 422,
    "error": "Unprocessable Entity",
    "message": "The uploaded PDF does not contain extractable text. Scanned image-only PDFs without an OCR text layer are not supported.",
    "path": "/api/documents/upload"
  }
  ```

