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
| **Stage 3** | **Text Chunking Strategy** | **Completed** | Sliding window algorithm, configurable overlap, InMemoryDocumentStore, chunk inspection endpoint. |
| **Stage 4** | **Embeddings & Vector Storage** | **Completed** | Spring AI SimpleVectorStore, deterministic local embedding baseline, top-k similarity search, stale chunk eviction. |
| **Stage 5** | **RAG Question Answering** | **Completed** | Prompt templating, RagService, QueryController, grounded answer synthesis, authoritative Java citations, insufficient-context guardrails. |
| **Stage 6** | **Simple Frontend** | **Completed** | Clean AI dashboard in vanilla HTML5/CSS3/JS (studymate-frontend), drag-and-drop PDF upload, indexing action, citation drawer, CORS enablement. |
| **Stage 7** | **Testing & Integration** | **Completed** | Comprehensive 86-test regression suite, evidence sufficiency heuristic, encrypted PDF detection, lifecycle replacement verification, automated E2E script. |
| **Stage 8** | **Interview Preparation Guide** | *Upcoming* | Deep architectural Q&A, trade-off analysis, resume-ready bullet points. |

---

## 🚀 Running & Testing the Application

### Prerequisites
- **Java 21+** (or Java 25 LTS)
- Maven (or bundled `./mvnw` / `mvnw.cmd`)
- Python 3 (or any static HTTP server for serving the frontend)

### Running the Full Automated Backend Test Suite (86 Tests)
```bash
# Windows
.\mvnw.cmd test

# Linux / macOS
./mvnw clean test
```

### Running the End-to-End Pipeline Integration Test (HTTP Lifecycle)
When the Spring Boot backend is running on `http://localhost:8080`:
```bash
python test_e2e_pipeline.py
```

### 1. Starting the Spring Boot Backend (Port 8080)
```bash
# Windows
.\mvnw.cmd spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```

### 2. Starting the Frontend Dashboard (Port 3000)
In a separate terminal window:
```bash
python -m http.server 3000 --directory studymate-frontend
```
Then open your browser and navigate to: **`http://localhost:3000`**


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

### 3. Chunk Inspection Endpoint (Stage 3)
- **Endpoint:** `GET /api/documents/chunks`
- **Description:** Returns the text chunks, window metrics, and page citations generated from the active document held in `InMemoryDocumentStore`.

#### cURL Example:
```bash
curl -X GET http://localhost:8080/api/documents/chunks
```

#### Success Response (`200 OK`):
```json
{
  "filename": "lecture-notes.pdf",
  "totalPages": 2,
  "totalChunks": 4,
  "chunkSize": 500,
  "overlap": 50,
  "chunks": [
    {
      "chunkId": "lecture-notes.pdf-p1-c0",
      "text": "Chapter 1: Principles of Object-Oriented Programming...",
      "pageNumber": 1,
      "chunkIndex": 0,
      "sourceFilename": "lecture-notes.pdf",
      "characterCount": 500
    },
    {
      "chunkId": "lecture-notes.pdf-p1-c1",
      "text": "...Programming language paradigms and encapsulation...",
      "pageNumber": 1,
      "chunkIndex": 1,
      "sourceFilename": "lecture-notes.pdf",
      "characterCount": 370
    }
  ],
  "message": "Generated 4 chunks from 2 pages with chunk-size=500 and overlap=50."
}
```

#### No Document Uploaded Response (`404 Not Found`):
```json
{
  "timestamp": "2026-10-17T16:20:00.000Z",
  "status": 404,
  "error": "Not Found",
  "message": "No document has been uploaded yet. Please upload a PDF first via POST /api/documents/upload",
  "path": "/api/documents/chunks"
}
```

### 4. Vector Store Indexing (Stage 4)
- **Endpoint:** `POST /api/documents/index`
- **Description:** Takes the active document from `InMemoryDocumentStore`, chunks it, converts chunks to Spring AI `Document` entities with citation metadata, and indexes them in `SimpleVectorStore`. If an older document was previously indexed, its vectors are automatically evicted to prevent stale results.

#### cURL Example:
```bash
curl -X POST http://localhost:8080/api/documents/index
```

#### Success Response (`200 OK`):
```json
{
  "documentId": "doc-7f1a3b8c",
  "filename": "lecture-notes.pdf",
  "totalChunksIndexed": 4,
  "message": "Successfully indexed 4 chunks for document 'lecture-notes.pdf'. Ready for vector search."
}
```

### 5. Semantic Similarity Search (Stage 4)
- **Endpoint:** `POST /api/documents/search`
- **Content-Type:** `application/json`
- **Description:** Performs top-$k$ similarity search against indexed vector chunks using the local deterministic embedding model.

#### Request Body (`SearchQueryRequest`):
```json
{
  "query": "What is encapsulation in object-oriented programming?",
  "topK": 3,
  "minThreshold": 0.0
}
```

#### Success Response (`200 OK`):
```json
{
  "query": "What is encapsulation in object-oriented programming?",
  "documentId": "doc-7f1a3b8c",
  "totalMatches": 2,
  "results": [
    {
      "chunkId": "doc-7f1a3b8c-lecture-notes_pdf-p1-c0",
      "documentId": "doc-7f1a3b8c",
      "text": "Chapter 1: Principles of Object-Oriented Programming. Encapsulation hides internal state...",
      "pageNumber": 1,
      "chunkIndex": 0,
      "sourceFilename": "lecture-notes.pdf",
      "score": 0.8421
    },
    {
      "chunkId": "doc-7f1a3b8c-lecture-notes_pdf-p1-c1",
      "documentId": "doc-7f1a3b8c",
      "text": "...Programming language paradigms and encapsulation restrict direct access...",
      "pageNumber": 1,
      "chunkIndex": 1,
      "sourceFilename": "lecture-notes.pdf",
      "score": 0.6514
    }
  ]
}
```

### 6. RAG Question Answering (Stage 5)
- **Endpoint:** `POST /api/query`
- **Content-Type:** `application/json`
- **Description:** Takes a student's natural-language question, retrieves the most relevant chunks from the indexed document via `VectorStoreService`, formats an augmented prompt with strict grounding rules, queries the `ChatModel`, and returns an authoritative answer with page citations.

#### cURL Example:
```bash
curl -X POST http://localhost:8080/api/query \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What is encapsulation in object-oriented programming?",
    "topK": 2,
    "minThreshold": 0.0
  }'
```

#### Success Response (`200 OK`):
```json
{
  "question": "What is encapsulation in object-oriented programming?",
  "answer": "Based on [Page 1], Principles of Object-Oriented Programming. Encapsulation hides internal object state.",
  "citations": [
    {
      "pageNumber": 1,
      "sourceFilename": "oop-study-guide.pdf",
      "chunkId": "doc-7f1a3b8c-oop-study-guide_pdf-p1-c0",
      "score": 0.8523,
      "textPreview": "Principles of Object-Oriented Programming. Encapsulation hides internal object state..."
    }
  ],
  "documentId": "doc-7f1a3b8c",
  "hasSufficientContext": true
}
```

#### Insufficient Context Response (`200 OK`):
When the uploaded document does not contain relevant information for the question (e.g. asking about Quantum Physics against a Cooking recipe PDF):
```json
{
  "question": "What is quantum entanglement?",
  "answer": "The uploaded study document does not contain relevant information to answer this question.",
  "citations": [],
  "documentId": "doc-7f1a3b8c",
  "hasSufficientContext": false
}
```

#### Error Responses:
- **`400 Bad Request`**: Blank or missing question string.
- **`404 Not Found`**: No document has been indexed yet.




