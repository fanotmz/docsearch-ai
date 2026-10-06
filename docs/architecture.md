# Architecture — V1

## Scope

Local single-user application for text PDF search and independent grounded questions. Public source repository; no public upload service in V1.

## Application boundaries

One Spring Boot backend, one Angular frontend and PostgreSQL. Ollama runs on the host by default. Backend packages will be organized by feature: documents, ingestion, search, qa and configuration. The initial system endpoint only describes the current application phase.

## Native Spring AI integration

Use PagePdfDocumentReader with one page per Document, TokenTextSplitter, EmbeddingModel, PgVectorStore, VectorStoreDocumentRetriever, RetrievalAugmentationAdvisor and ChatClient. Keep page metadata through the entire pipeline. Start with dense search and a bounded context; evaluate retrieval before adding query rewriting or reranking.

## Data and lifecycle

DOCSEARCH-03 introduces `documents`, `ingestion_jobs` and `document_pages`. DOCSEARCH-04 adds the Flyway-owned `docsearch_vector_store` table. A document starts as PROCESSING, has one ingestion job, and becomes READY only after page extraction, page persistence, page-aware chunking and vector indexing complete; parser, text-availability or indexing failures leave both records in a FAILED state. `document_pages` is durable source text: it preserves one row per extracted PDF page so chunking does not require the source PDF to be uploaded again. No PDF binary is retained.

Every persisted page has an application-owned document identity, source label and 1-based page number. Each page is split independently with Spring AI `TokenTextSplitter`, so chunks never cross page boundaries. The vector metadata contract is `docsearch.document_id`, `docsearch.source`, `docsearch.page_number` and page-local zero-based `docsearch.chunk_index`; later stages must not depend on incidental PDF reader metadata keys. Chunk content, metadata and embeddings live together in `docsearch_vector_store`, compatible with Spring AI 2.0.1 PgVectorStore and dimension 1024 for the qualified BGE-M3 profile. Spring AI schema auto-initialization is disabled; Flyway owns the table. Never mix models in one embedding profile. Reindex all documents when the embedding model or dimension changes.

Only READY documents are searchable. DOCSEARCH-05 reads READY document IDs from the relational lifecycle table and passes them as an `IN` metadata filter to Spring AI `VectorStore.similaritySearch(SearchRequest)`, so stale or partial vectors cannot leak into search results. Failed indexing removes vectors already written for the affected document while retaining source pages for diagnostics/reindexing. Ingestion is serialized in local V1 and no database transaction remains open during embedding inference.

## Answers

DOCSEARCH-06 exposes grounded Q&A through an explicit retrieval-and-generation service. It deliberately does not use `RetrievalAugmentationAdvisor` for the answer call: Spring AI 2.0.1 advisors can augment a prompt, but the explicit path keeps the exact bounded evidence set available for temporary source IDs and structural citation validation. The service reuses `SemanticSearchService` with fixed `QA_TOP_K=5`, so READY filtering remains centralized.

Retrieved chunks receive request-local IDs `S1`, `S2`, ... in retrieval order. The system policy treats evidence as data, requires the model to answer only from supplied evidence, and requires `[S<number>]` markers in an `ANSWERED` response. Spring AI 2.0.1 `ChatClient.Builder` performs one `.call().content()` generation call; `BeanOutputConverter<GroundedModelOutput>` parses the model-owned `status` and `answer` fields. The backend validates status, non-empty answer, citation presence and source-ID membership, then resolves citations from its own evidence map. Model filenames, page numbers and document IDs are never trusted.

No retrieved context returns deterministic `INSUFFICIENT_EVIDENCE` before ChatClient invocation. Model-requested abstention is normalized to the same application-owned response. Invalid structured output and unknown citations return `INVALID_MODEL_OUTPUT`; chat transport failures return `GENERATION_FAILED`. Structural citation validity does not certify semantic support. Human evaluation must separately check correctness, coverage and abstention.

## Foundation decisions

- Spring Boot 4.1.1 and Spring AI 2.0.1, no snapshots.
- PostgreSQL 16 and pgvector 0.8.1 as the initial test/container baseline.
- Angular 22.2.x with npm lockfile and Node 24.19.0.
- Native Ollama auto-configuration, with a manually configured PgVectorStore and Flyway-owned schema; Spring AI schema auto-initialization remains disabled.
- No model calls at normal startup; real-model smoke is explicit and opt-in.
- DOCSEARCH-03 persists extracted text pages but does not retain source PDF binaries.
- DOCSEARCH-03 does not provide OCR, chunking, embeddings, vector indexing, search or RAG. DOCSEARCH-04 adds page-aware chunking and vector indexing. DOCSEARCH-05 adds semantic search only; generation, reranking and RAG remain later stages.
- No imported code from previous projects in this foundation.

Container tags are explicit but do not pin immutable image digests. Record and pin those digests during qualification, together with model digests. Performance and accelerator compatibility remain unqualified.

## Official references

- https://docs.spring.io/spring-ai/reference/getting-started.html
- https://docs.spring.io/spring-ai/reference/api/etl-pipeline.html
- https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html
- https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html
- https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html
- https://angular.dev/reference/versions
