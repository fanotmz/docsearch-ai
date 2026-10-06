# Architecture — V1

## Scope

Local single-user application for text PDF search and independent grounded questions. Public source repository; no public upload service in V1.

## Application boundaries

One Spring Boot backend, one Angular frontend and PostgreSQL. Ollama runs on the host by default. Backend packages will be organized by feature: documents, ingestion, search, qa and configuration. The initial system endpoint only describes the current application phase.

## Native Spring AI integration

Use PagePdfDocumentReader with one page per Document, TokenTextSplitter, EmbeddingModel, PgVectorStore, VectorStoreDocumentRetriever, RetrievalAugmentationAdvisor and ChatClient. Keep page metadata through the entire pipeline. Start with dense search and a bounded context; evaluate retrieval before adding query rewriting or reranking.

## Data and lifecycle

DOCSEARCH-03 introduces `documents`, `ingestion_jobs` and `document_pages`. A document starts as PROCESSING, has one ingestion job, and becomes READY only after page extraction and page persistence complete; parser or text-availability failures leave both records in a FAILED state. `document_pages` is durable source text, not a chunk or vector index: it preserves one row per extracted PDF page so DOCSEARCH-04 can chunk later without requiring the source PDF to be uploaded again. No PDF binary is retained.

Every persisted page has an application-owned document identity, source label and 1-based page number. The corresponding Spring AI `Document` metadata contract is `docsearch.document_id`, `docsearch.source` and `docsearch.page_number`; later stages must not depend on incidental PDF reader metadata keys. Chunk content, metadata and embeddings belong to a single PgVectorStore-compatible table in a later migration. Do not create that table before measuring the embedding dimension. Never mix models in one embedding profile. Reindex all documents when the embedding model changes.

Only READY documents are searchable. Failed ingestion must clean partial vectors. Ingestion is serialized and no database transaction remains open during inference.

## Answers

No retrieved context means deterministic abstention before generation. A model answer references temporary source IDs; the backend resolves them only against the supplied chunks. Structural reference validation does not certify semantic support. Human evaluation must separately check correctness, coverage and abstention.

## Foundation decisions

- Spring Boot 4.1.1 and Spring AI 2.0.1, no snapshots.
- PostgreSQL 16 and pgvector 0.8.1 as the initial test/container baseline.
- Angular 22.2.x with npm lockfile and Node 24.19.0.
- Native Ollama auto-configuration, without an automatically initialized vector store.
- No model calls at normal startup; real-model smoke is explicit and opt-in.
- DOCSEARCH-03 persists extracted text pages but does not retain source PDF binaries.
- DOCSEARCH-03 does not provide OCR, chunking, embeddings, vector indexing, search or RAG.
- No imported code from previous projects in this foundation.

Container tags are explicit but do not pin immutable image digests. Record and pin those digests during qualification, together with model digests. Performance and accelerator compatibility remain unqualified.

## Official references

- https://docs.spring.io/spring-ai/reference/getting-started.html
- https://docs.spring.io/spring-ai/reference/api/etl-pipeline.html
- https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html
- https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html
- https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html
- https://angular.dev/reference/versions
