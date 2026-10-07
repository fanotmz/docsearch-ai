# DocSearch AI local demo guide

This guide runs the qualified local V1 from a clean Docker Compose stack.

## Prerequisites

- Docker Desktop/Engine and Docker Compose;
- Ollama 0.35.1;
- `bge-m3:latest` and `qwen3.5:9b` already present in Ollama;
- Docker-to-host access to `http://host.docker.internal:11434`.

Check the model inventory before starting the application:

```bash
ollama --version
ollama list
```

The application never pulls models automatically. Do not expose Ollama to the
public Internet; keep its firewall scope limited to the Docker Desktop host
network as described in the local DOCSEARCH-02 validation.

## Start

```bash
cp .env.example .env
docker compose config --quiet
docker compose up --build -d
curl http://localhost:8082/api/v1/system
```

Open the UI at <http://localhost:4200>. The backend is intentionally bound to
<http://localhost:8082> on loopback; the database is bound to localhost only.

## Demonstration flow

1. Open **Library** and import a redistributable text PDF.
2. Wait for the document to show `READY` and its page count.
3. Reload the browser and confirm the library is loaded from
   `GET /api/v1/documents`.
4. Open **Search**, search for a fact in the document and inspect the source,
   page, chunk and retrieval-similarity score.
5. Open **Q&A** and ask a question answered by the document. Confirm that the
   answer includes backend-resolved citations.
6. Ask an unrelated question. Confirm `INSUFFICIENT_EVIDENCE` and no fake
   citations.

The benchmark fixture used for engineering qualification is kept under
`evaluation/benchmark-v2/corpus/`. It is project-owned and redistributable.

## API smoke checks

```bash
curl http://localhost:8082/api/v1/documents
curl -X POST http://localhost:8082/api/v1/search \
  -H 'Content-Type: application/json' \
  -d '{"query":"Which topic is covered by this document?","topK":5}'
curl -X POST http://localhost:8082/api/v1/qa \
  -H 'Content-Type: application/json' \
  -d '{"question":"What does the document say about its main topic?"}'
```

For a deterministic engineering walkthrough, use the benchmark runner only
against an empty temporary database; never point it at a user's development
volume.

## Troubleshooting

- `OFFLINE` in the UI: verify the backend health endpoint and retry.
- Docker model connection failure: verify Ollama's host bind and the scoped
  Windows Firewall rule; do not hardcode a transient WSL address.
- `NO_EXTRACTABLE_TEXT`: V1 accepts text PDFs only and does not perform OCR.
- Slow Q&A: local generation can take tens of seconds; this is expected on
  hardware without a dedicated accelerator.

Stop the stack with:

```bash
docker compose down
```

The named PostgreSQL volume is intentionally preserved by this command.
