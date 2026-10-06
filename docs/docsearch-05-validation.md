# DOCSEARCH-05 validation

## API and retrieval implementation

The endpoint is `POST /api/v1/search` with JSON request fields:

```json
{
  "query": "Which extension adds vector similarity search to PostgreSQL?",
  "topK": 5
}
```

`topK` defaults to 5 and is bounded to 20. Blank queries, queries longer than 2,000 characters, non-positive `topK` and `topK` above 20 return HTTP 400 with a deterministic error code. An empty READY set returns HTTP 200 with an empty result array.

The implementation uses Spring AI 2.0.1 `VectorStore.similaritySearch(SearchRequest)` against the existing PgVectorStore. `SearchRequest.similarityThresholdAll()` is used as the least restrictive baseline. The service first reads `READY` document IDs from `documents`, then creates a Spring AI `FilterExpressionBuilder.in("docsearch.document_id", ...)` expression. No custom raw-vector SQL path or second vector store is used.

Each response result contains `documentId`, `source`, 1-based `pageNumber`, page-local `chunkIndex`, chunk `content` and `score`. PgVectorStore 2.0.1 maps cosine distance to `Document.getScore()` as `1 - distance`; this is a retrieval similarity score, not answer confidence or evidence sufficiency. No ChatClient, generation, query rewriting, reranking or RAG call is made.

No database schema change was required.

## Deterministic CI validation

`SemanticSearchIntegrationTest` uses Testcontainers PostgreSQL/pgvector and the deterministic 1024-dimensional test embedding model. Synthetic database and bird vectors make the nearest-neighbor order explicit without relying on hash collisions or Ollama. It verifies blank/overlong/invalid input, empty READY index, topK, metadata and score mapping, deterministic ordering, and exclusion of a FAILED document even when its vector exists.

The full backend suite runs 12 tests with 0 failures and 0 errors. CI does not require Ollama and no generation call is made.

## Real BGE-M3 qualification

The existing DOCSEARCH-04 model profile was reused without change:

- Ollama: `0.35.1`
- model: `bge-m3:latest`
- digest: `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`
- dimension: `1024`

The project-owned three-page fixture `docsearch-05-search-topics.pdf` was uploaded successfully as `READY` with three indexed chunks. The semantic smoke queries returned the expected page as rank 1:

| Query | Expected source/page | Returned rank | Score | Internal search time | HTTP time |
|---|---|---:|---:|---:|---:|
| Which extension adds vector similarity search to PostgreSQL? | `docsearch-05-search-topics.pdf`, page 1 | 1 | 0.6374863386 | 2650 ms | 3137 ms |
| How do birds travel between seasonal habitats? | `docsearch-05-search-topics.pdf`, page 2 | 1 | 0.7345747650 | 166 ms | 271 ms |
| Which planet is third from the Sun and has liquid water? | `docsearch-05-search-topics.pdf`, page 3 | 1 | 0.6279563904 | 219 ms | 317 ms |

The qualified document remained `READY` throughout. This is a technical smoke qualification, not a formal retrieval benchmark or tuning exercise.

A normal startup returned health HTTP 200 without performing model inference; inference occurred only for the upload and explicit search requests.
