# Changelog

All notable changes to DocSearch AI are documented here.

## [1.0.0] - 2026-10-07

### Added

- Text-based PDF upload with page-preserving extraction.
- Page-aware chunking, BGE-M3 embeddings and pgvector indexing.
- Semantic search with source/page/chunk metadata.
- Grounded Q&A with backend-validated citations and deterministic abstention.
- Angular Library, Search and Q&A views for local use.
- Frozen V1 benchmark harness and independent semantic review.

### Qualified

- DOCSEARCH-08 benchmark-v2 passed every pre-registered retrieval,
  structural-Q&A and human semantic gate on the project-owned corpus.

### Limitations

- V1 is a local, single-user application.
- OCR, deletion, authentication, conversation memory, reranking, web search
  and public deployment remain out of scope.
- Local model latency depends on available hardware and is reported separately
  from the benchmark gates.
