# DocSearch AI 1.0.0

DocSearch AI 1.0.0 is the qualified local V1 release candidate for document
search and grounded question answering.

## What is included

- text-based PDF import with one persisted page record per extracted page;
- page-local chunking and BGE-M3 vector indexing in PostgreSQL/pgvector;
- semantic search with source, page and chunk metadata;
- grounded Q&A through Qwen with backend-validated temporary citations;
- deterministic `INSUFFICIENT_EVIDENCE` handling;
- Angular Library, Search and Q&A screens;
- reproducible DOCSEARCH-08 benchmark artifacts and human review.

## Qualification

The frozen V1 passed the project-owned benchmark-v2: retrieval, expected
status, abstention, citation and independent human semantic gates were all
100%. The benchmark is deliberately small and does not guarantee general-world
retrieval or answer quality. Search and Q&A latency remains hardware-dependent
and non-gated.

## Runtime requirements

- Docker Desktop/Engine with Compose;
- Ollama 0.35.1 with `bge-m3:latest` and `qwen3.5:9b` already installed;
- a host configuration that permits the backend container to reach
  `http://host.docker.internal:11434` without exposing Ollama publicly.

Automatic model pulling is disabled. See [the demo guide](demo-guide.md) for
the complete local walkthrough.

## Known V1 boundaries

V1 does not include OCR, PDF viewing, deletion, authentication, accounts,
conversation memory, multi-turn chat, web search, agents, reranking, query
rewriting, formal retrieval tuning or public hosting.
