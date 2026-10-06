# DOCSEARCH-07 validation

## Delivered UI

The Angular standalone application uses a shared shell with RouterLink navigation and three lazy-loaded views:

- `/library` — PDF upload and persistent document summaries;
- `/search` — semantic search with selectable topK 5, 10 or 20;
- `/qa` — grounded Q&A with backend-resolved citations.

The root route redirects to `/library`. The shell keeps the local-space identity and `/api/v1/system` online/offline state visible. The frontend API service centralizes the system, library, upload, search and Q&A calls. No new UI framework or state-management dependency was introduced.

## Backend support

`GET /api/v1/documents` returns a newest-first array of application-owned summaries:

```json
{
  "documentId": "…",
  "filename": "example.pdf",
  "pageCount": 3,
  "status": "READY",
  "createdAt": "2026-10-07T00:31:00Z"
}
```

PROCESSING, READY and FAILED records are included. Ordering is `created_at DESC, id DESC`; no page text, embeddings or database internals are exposed. `/api/v1/system` now reports `phase=LOCAL_MVP` and `documentSearchAvailable=true`.

## UX and safety behavior

The library accepts PDF files up to 20 MB, prevents duplicate uploads, refreshes from the backend after success and states that OCR is outside V1. Upload errors map EMPTY_FILE, FILE_TOO_LARGE, unsupported media, NOT_PDF, NO_EXTRACTABLE_TEXT, MALFORMED_PDF and network/server failures to concise French messages.

Search uses `POST /api/v1/search` with a default topK of 5 and displays source, 1-based page, page-local chunk, content and similarity score. The score is explicitly labelled as retrieval similarity and is not presented as confidence.

Q&A uses `POST /api/v1/qa` without exposing retrieval topK. ANSWERED responses display plain-text answer content and only the citation objects returned by the backend. INSUFFICIENT_EVIDENCE displays the application-owned abstention without sources. INVALID_MODEL_OUTPUT and GENERATION_FAILED have distinct messages. The Q&A page disables duplicate requests and explains that local generation can take several tens of seconds.

Filenames, queries, document text and model text use normal Angular interpolation. No `[innerHTML]`, markdown renderer or security bypass is used. Forms have labels, visible focus states and disabled busy controls. The layout was checked at the available wide and narrow browser viewports; the navigation and upload/search/Q&A cards remain usable without horizontal content overflow.

## Local end-to-end qualification

The local Compose stack ran PostgreSQL/pgvector, backend and Angular frontend against the existing Ollama service. The project-owned fixture `backend/src/test/resources/fixtures/docsearch-05-search-topics.pdf` was uploaded successfully through the local API because the embedded browser's file chooser was not exposed to the available automation surface; its persisted result was then verified through the UI.

Observed workflow:

1. Library loaded persisted `docsearch-05-search-topics.pdf` as READY with 3 pages.
2. Browser reload retained the document in the library.
3. Semantic search for `Which PostgreSQL extension provides vector similarity search?` displayed the PostgreSQL passage on page 1; the observed top result similarity was approximately `0.645`.
4. Grounded Q&A returned ANSWERED with `The pgvector extension adds vector columns and similarity search to PostgreSQL. [S1]`; the citation resolved to `docsearch-05-search-topics.pdf`, page 1, chunk 0.
5. `Who won the 2022 FIFA World Cup?` returned INSUFFICIENT_EVIDENCE with the deterministic abstention text and no citations.

The local production profile used BGE-M3 for retrieval and `qwen3.5:9b` for generation. The observed answerable Q&A request took approximately 76 seconds end to end, reflecting local generation time; this is a qualification observation, not a performance target. No additional model call is caused by the frontend.

## Qualification status and limitations

- backend Maven/Testcontainers: PASS, 25 tests, 0 failures, 0 errors;
- Angular `npm ci`: PASS;
- Angular production build: PASS;
- Docker Compose config: PASS;
- backend and frontend image builds: PASS;
- browser UI smoke: PASS for library persistence, search, answerable Q&A and insufficient-evidence Q&A.

DOCSEARCH-07 remains a local single-user UI. It does not provide deletion, PDF viewing, OCR, authentication, conversation history, retrieval tuning, answer-quality evaluation or public deployment. Formal retrieval and answer evaluation remain DOCSEARCH-08.
