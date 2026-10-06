# DocSearch AI

Local document search and grounded question answering, built with Spring AI, Angular and PostgreSQL/pgvector.

**Status: DOCSEARCH-06 implemented and locally qualified.** The backend accepts text-based PDF uploads, stores one extracted page per Spring AI `Document`, splits each page independently with `TokenTextSplitter`, indexes chunks in pgvector with BGE-M3, exposes semantic search and provides grounded answers with backend-validated citations. Formal retrieval and answer evaluation remain later roadmap items. The current UI displays the backend connection state and a library placeholder. A successful health response does not certify search or answer quality.

## Stack

| Component | Version / choice |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.1 |
| Spring AI | 2.0.1 |
| Maven | 3.9.9, wrapper 3.3.4 |
| Angular | 22.2.x, exact dependencies in package-lock.json |
| Node | 24.19.0 |
| PostgreSQL / pgvector | PostgreSQL 16 / pgvector 0.8.1 |
| LLM candidate | Ollama, Qwen3.5-9B Q4_K_M |
| Embedding candidate | Ollama, BGE-M3 |

## Quick start

Prerequisites: Docker Desktop/Engine and Docker Compose. Java and Node installations are unnecessary for the container build.

```bash
cp .env.example .env
docker compose config --quiet
docker compose up --build -d
docker compose logs -f backend
```

Open http://localhost:4200. The Docker-exposed backend API is available at http://localhost:8082/api/v1/system; Docker-exposed database health is at http://localhost:8082/actuator/health. Ports are bound to localhost. Default database credentials are for local development only.

The foundation starts without Ollama inference. No model download or inference runs at startup unless the optional smoke or an upload is enabled. Flyway installs pgvector and creates the DOCSEARCH-04 vector table at the qualified BGE-M3 dimension.

Stop with `docker compose down`. Database data remains in the named volume. Changing PostgreSQL credentials in `.env` does not change credentials inside an existing initialized volume.

## PDF upload (DOCSEARCH-03)

Upload a text-based PDF with the `file` multipart field:

```bash
curl -F "file=@./example.pdf" http://localhost:8082/api/v1/documents
```

PowerShell:

```powershell
curl.exe -F "file=@$PWD\example.pdf" http://localhost:8082/api/v1/documents
```

The upload limit is 20 MB per file and 21 MB per request. The response contains `documentId`, the normalized original `filename`, `pageCount` and the `READY` status. Extraction preserves one page per Spring AI `Document`; durable `document_pages` rows contain the extracted page text and its 1-based `page_number`.

The application-owned page metadata contract is `docsearch.document_id`, `docsearch.source` (the normalized original filename) and `docsearch.page_number` (starting at 1). It is independent of incidental metadata keys emitted by the PDF reader.

Malformed PDFs and PDFs with no extractable text are recorded as failed `Document`/`IngestionJob` lifecycles and return a deterministic 422 response. Empty uploads return 400; unsupported media types and non-PDF content return 415; oversized requests return 413. DOCSEARCH-03 accepts text PDFs only: it does not perform OCR, chunking, embeddings, vector indexing, search or RAG. A PDF with some blank pages is retained page-for-page when at least one page contains text. PDFs with no extractable text, including scanned/image-only PDFs without a text layer, are rejected because OCR is outside V1.

## Page-aware chunking and indexing (DOCSEARCH-04)

After page persistence, each page is split independently with Spring AI `TokenTextSplitter`; a chunk never crosses a PDF page boundary. The reproducible baseline is `chunkSize=800`, `minChunkSizeChars=200`, `minChunkLengthToEmbed=10`, `maxNumChunks=1000` and `keepSeparator=true`. Each vector row preserves `docsearch.document_id`, `docsearch.source`, 1-based `docsearch.page_number` and page-local zero-based `docsearch.chunk_index`.

The qualified production embedding is Ollama `bge-m3:latest`, digest `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`, dimension 1024. Flyway owns the `docsearch_vector_store` table; Spring AI PgVectorStore schema auto-initialization is disabled. A document becomes `READY` only after all chunks are indexed. Indexing failures mark both lifecycle records `FAILED` and remove vectors already written for that document. Changing the embedding model or dimension requires rebuilding the vector index.

DOCSEARCH-04 does not implement OCR, retrieval ranking, reranking, question answering or RAG. See [DOCSEARCH-04 validation](docs/docsearch-04-validation.md) for the indexing qualification record and [DOCSEARCH-05 validation](docs/docsearch-05-validation.md) for semantic-search qualification.

## Semantic search (DOCSEARCH-05)

Search indexed READY documents with the JSON endpoint:

```bash
curl -X POST http://localhost:8082/api/v1/search \
  -H "Content-Type: application/json" \
  -d '{"query":"Which extension adds vector similarity search to PostgreSQL?","topK":5}'
```

PowerShell:

```powershell
curl.exe -X POST http://localhost:8082/api/v1/search `
  -H "Content-Type: application/json" `
  -d '{"query":"Which extension adds vector similarity search to PostgreSQL?","topK":5}'
```

`topK` defaults to 5 and is bounded to 20. Blank, overlong or invalid queries return a deterministic 400 response. Results contain `documentId`, `source`, 1-based `pageNumber`, page-local `chunkIndex`, chunk `content` and the Spring AI score when available. The score is the PgVectorStore cosine similarity (`1 - distance`), not an answer-confidence or evidence-sufficiency judgment. Search filters vector retrieval to document IDs whose relational lifecycle is `READY`.

DOCSEARCH-05 uses the qualified BGE-M3 profile and does not add generation, ChatClient calls, query rewriting, reranking, OCR, hybrid search, citations generated by an LLM or an Angular search UI.

## Grounded answers (DOCSEARCH-06)

Ask a grounded question with a fixed bounded retrieval context:

```bash
curl -X POST http://localhost:8082/api/v1/qa \
  -H "Content-Type: application/json" \
  -d '{"question":"Which PostgreSQL extension provides vector similarity search?"}'
```

PowerShell:

```powershell
curl.exe -X POST http://localhost:8082/api/v1/qa `
  -H "Content-Type: application/json" `
  -d '{"question":"Which PostgreSQL extension provides vector similarity search?"}'
```

The Q&A endpoint uses `QA_TOP_K=5` and accepts questions up to 2,000 characters. An `ANSWERED` response contains model text with temporary request-local markers such as `[S1]` and citations resolved by the backend to the supplied document/source/page/chunk metadata. The model cannot provide trusted filenames, page numbers or document IDs directly. An `INSUFFICIENT_EVIDENCE` response uses deterministic application text and has no citations.

The production chat profile is Ollama `qwen3.5:9b`, while retrieval reuses the qualified BGE-M3 embedding profile. Retrieved text is delimited as untrusted evidence; instructions inside documents cannot override the grounding policy. The backend makes at most one chat-generation call per request and rejects malformed output, missing citations, fabricated source IDs and generation failures with distinct 5xx errors. Structural citation validity does not prove that the cited text semantically supports the answer; formal evaluation belongs to DOCSEARCH-08. DOCSEARCH-06 does not add conversation memory, reranking, query rewriting, tools, Internet search, OCR or an Angular Q&A UI.

## Development

Requirements: JDK 21, Node 24.19.0 and Docker.

```bash
docker compose up -d postgres
cd backend
./mvnw verify
./mvnw spring-boot:run
```

In a second terminal:

```bash
cd frontend
npm ci
npm start
```

The Angular development server proxies API requests to port 8082 when using Docker. The container still listens on port 8080 internally. On Windows, use `mvnw.cmd` instead of `./mvnw`. The first wrapper execution downloads Maven; dependency and image downloads also require Internet access.

## Optional real-model smoke

This is a connectivity test, not a RAG quality benchmark. Install Ollama on the development host and ensure the models used by the smoke are available:

```bash
ollama pull qwen3.5:9b
ollama pull embeddinggemma:300m
ollama list
```

DOCSEARCH-02 used the already-installed `embeddinggemma:300m` only as a provisional connectivity/smoke model. DOCSEARCH-04 separately qualified and uses `bge-m3:latest` for the vector index; the two models must not be mixed in one index. The command below remains the DOCSEARCH-02 smoke and does not replace the BGE-M3 qualification.

With PostgreSQL running, launch the backend directly on the same host as Ollama:

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.arguments="--docsearch.smoke.enabled=true --spring.ai.ollama.chat.model=qwen3.5:9b --spring.ai.ollama.embedding.model=embeddinggemma:300m"
```

Expected logs include embedding dimensions and elapsed time, plus non-empty chat output and elapsed time. An empty/non-finite vector causes startup to fail. Record the actual embedding dimension, model digests, Ollama version, RAM and elapsed time before implementing the vector schema.

Default inference settings use a bounded 8K chat context, 512 output tokens, no automatic model pulls, no retries and unloading after each request. These are initial settings, not a validated hardware profile. The exact marker smoke can fail even when the model is reachable; inspect the logs before concluding there is a transport failure.

For container access to Ollama, `.env` uses `host.docker.internal`. Loopback-only Ollama may not be reachable from a container, particularly with WSL/Linux. Run the smoke directly on the host first; do not expose Ollama publicly to solve a connectivity problem.

## Repository

- `backend/`: application, native Spring AI clients, Flyway migration, Testcontainers integration test.
- `frontend/`: Angular application and same-origin proxy.
- `compose.yaml`: local services and persistent PostgreSQL volume.
- `.github/workflows/ci.yml`: backend integration test, frontend production build and container builds.
- `docs/`: architecture decisions and validation instructions.

## Validation

Backend Maven verification and its PostgreSQL/Testcontainers integration tests pass, including deterministic vector indexing and semantic search without Ollama. The frontend production build and configuration syntax checks pass, and the affected backend/container builds pass. GitHub CI runs the backend, frontend and container checks. DOCSEARCH-02 separately qualified the explicit real-model Ollama smoke, while DOCSEARCH-04 qualified real BGE-M3 indexing and DOCSEARCH-05 qualified real BGE-M3 search; DOCSEARCH-03/04/05 CI tests do not require Ollama. No formal retrieval-quality, RAG or application-performance claim is made.

CI requires Docker and deliberately fails when the integration test cannot start its database; it does not silently skip it. CI does not download models. See [foundation checks](docs/foundation-checks.md).

## Planned MVP

Text PDF import → page-preserving extraction → page-aware chunking → dense embeddings → pgvector retrieval → grounded answers → document/page citations. Single-user local application; no OCR, web search, autonomous tools or conversation memory in V1. Only redistributable documents may be included in the demonstration corpus.

## Third-party notices

The Maven Wrapper scripts originate from Apache Maven Wrapper 3.3.4. Their Apache license and notice are retained under `backend/.mvn/wrapper/`. A project-wide license must be selected before publishing a release; no license or rights are inferred for code that may later be reused from other projects.
