# DOCSEARCH-04 validation

## Qualified embedding profile

The production candidate was qualified before creating the vector schema:

- Ollama: `0.35.1`
- model: `bge-m3:latest`
- digest: `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`
- model size: `1,157,672,605` bytes (reported locally as 1.2 GB)
- embedding dimension: `1024`
- qualification input: representative French and English text
- result: non-empty, finite vectors; `2537 ms` for the successful two-input qualification after model load

`embeddinggemma:300m` remains the separate DOCSEARCH-02 provisional smoke model at dimension 768. It is not used by this vector index.

## Chunking and storage contract

Each persisted page is processed independently with Spring AI 2.0.1 `TokenTextSplitter` using:

```text
chunkSize=800
minChunkSizeChars=200
minChunkLengthToEmbed=10
maxNumChunks=1000
keepSeparator=true
```

The `docsearch_vector_store` table is created by Flyway migration V3 and is compatible with Spring AI 2.0.1 `PgVectorStore`:

```text
id UUID PRIMARY KEY
content TEXT
metadata JSON
embedding vector(1024)
```

Spring AI schema auto-initialization is disabled. Every indexed chunk carries `docsearch.document_id`, `docsearch.source`, human-readable 1-based `docsearch.page_number` and deterministic page-local zero-based `docsearch.chunk_index`.

The ingestion lock serializes local V1 processing. No database transaction is held while Ollama performs embedding inference. A document is marked `READY` only after all vectors are written. An indexing failure marks the document and job `FAILED`, records `INDEXING_FAILED`, and deletes vectors already written for that document; persisted source pages remain available for diagnostics or reindexing.

## Deterministic CI validation

`FoundationIntegrationTest` uses Testcontainers PostgreSQL/pgvector and a local deterministic 1024-dimensional `EmbeddingModel`, so CI does not need Ollama. It verifies page persistence, page-local chunking, vector dimension, metadata, `READY/SUCCEEDED` lifecycle and no-text/malformed upload failures. `DocumentIngestionFailureTest` verifies partial-vector cleanup and the `FAILED` indexing lifecycle.

Command:

```powershell
cd backend
.\mvnw.cmd verify
```

## Real local indexing qualification

On 2026-10-06, the repository-owned three-page fixture `docsearch-03-pages.pdf` was uploaded to the backend using the qualified BGE-M3 profile and local PostgreSQL/pgvector:

- extracted pages: `3`
- produced chunks: `3`
- indexed vector rows: `3`
- vector dimension: `1024`
- metadata page mapping: pages `1`, `2`, `3`, each with chunk index `0`
- lifecycle: `Document READY`, `IngestionJob SUCCEEDED`
- `VectorStore.add` elapsed time: `5460 ms`
- end-to-end upload HTTP time: `5913 ms`

A second normal backend startup returned health HTTP 200 and produced no embedding, generation or indexing log entry. No model call is required for normal startup or deterministic CI tests.

DOCSEARCH-04 does not provide a semantic-search API, retrieval ranking evaluation, OCR, chunking across pages, reranking, question answering or RAG.
