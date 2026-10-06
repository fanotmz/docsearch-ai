# DOCSEARCH-02 validation

Status: PARTIAL PASS. Host validation passed; Dockerized AI validation failed because the running Ollama listener is not reachable through Docker Desktop's `host.docker.internal`. This document records this local validation only; it does not validate retrieval or RAG quality.

## Scope and model selection

- Generation baseline: `qwen3.5:9b`.
- Embedding candidate: BGE-M3 was the initial architectural candidate, but it was not installed during this validation.
- `embeddinggemma:300m` was already present and is used only as a provisional local validation model, avoiding an unnecessary model download.
- DOCSEARCH-04 must use a vector schema dimension compatible with the embedding model ultimately qualified. Changing embedding models later requires re-indexing.

## Result vocabulary

- PASS: the command or acceptance criterion completed successfully.
- FAIL: it was attempted and did not complete successfully.
- NOT RUN: it was not attempted or the environment prevented a meaningful attempt.

## Host and repository inspection

| Check | Result |
|---|---|
| Project root | `C:\Users\fanoa\Documents\Projets\docsearch-ai` |
| Local branch at start | `1-docsearch-02-validate-local-ai-integration-with-ollama` |
| Initial status | Clean; HEAD `5ff58ad`, tracking `origin/1-docsearch-02-validate-local-ai-integration-with-ollama` |
| AGENTS.md | NOT FOUND |
| Fixed vector dimension in migrations | PASS: no vector table/column or fixed dimension; V1 only enables the `vector` extension |

## Ollama checks

Commands:

```powershell
ollama --version
ollama list
Invoke-RestMethod http://172.28.112.1:11434/api/version
Invoke-RestMethod http://172.28.112.1:11434/api/tags
```

Observed results:

- Client: `ollama version is 0.35.1` — PASS.
- Server: `{"version":"0.35.1"}` from `http://172.28.112.1:11434/api/version` — PASS.
- `http://localhost:11434` was unreachable because the running server was bound to `172.28.112.1:11434` — FAIL for localhost URL, PASS for the actual bound host address.
- `qwen3.5:9b` — ID prefix `6488c96fa5fa`, full digest `6488c96fa5faab64bb65cbd30d4289e20e6130ef535a93ef9a49f42eda893ea7`, size `6594474711` bytes.
- `embeddinggemma:300m` — ID prefix `85462619ee72`, full digest `85462619ee721b466c5927d109d4cb765861907d5417b9109caebc4e614679f1`, size `621875917` bytes.
- BGE-M3 was not installed and was not downloaded — PASS.

## Spring AI smoke results

Host command:

```powershell
cd backend
.\mvnw.cmd -B -ntp spring-boot:run "-Dspring-boot.run.arguments=--server.port=18080 --docsearch.smoke.enabled=true --spring.datasource.url=jdbc:postgresql://localhost:5433/docsearch --spring.datasource.username=docsearch --spring.datasource.password=docsearch-local --spring.ai.ollama.base-url=http://172.28.112.1:11434 --spring.ai.ollama.chat.model=qwen3.5:9b --spring.ai.ollama.embedding.model=embeddinggemma:300m"
```

| Check | Result |
|---|---|
| Host embedding | PASS |
| Host embedding dimension | PASS: `768` |
| Host finite-value validation | PASS: `true` |
| Host embedding elapsed time | PASS: `28291 ms` |
| Host generation | PASS: non-empty text |
| Host generation elapsed time | PASS: `81517 ms` |
| Docker -> `host.docker.internal` HTTP | FAIL: connection refused; Docker resolved `192.168.65.254`, while Ollama was bound to `172.28.112.1` |
| Dockerized embedding/generation | FAIL: not run after the basic HTTP connectivity failure |
| Normal startup without inference | PASS: recreated backend reached `Started DocSearchApplication`; no smoke log entries occurred |

The smoke remains opt-in through `docsearch.smoke.enabled=true`. It reports the configured model identifiers, vector dimension, finite-value validation, and elapsed times; generation acceptance is non-empty text.

The first host smoke attempt on port 8080 failed because that port was already occupied by another local process. The same validation passed on port 18080 without stopping the unrelated process.

## Docker and schema commands

```powershell
docker compose config --quiet
docker compose up -d postgres
docker exec docsearch-ai-backend-1 wget -S -O - --timeout=10 http://host.docker.internal:11434/api/version
docker compose build backend
docker compose run --rm -e DOCSEARCH_SMOKE_ENABLED=true -e OLLAMA_CHAT_MODEL=qwen3.5:9b -e OLLAMA_EMBEDDING_MODEL=embeddinggemma:300m backend
docker compose up -d --build backend
docker compose logs backend
docker compose down
```

Observed Docker results: `docker compose config --quiet` — PASS; backend image build — PASS; current compose mapping `127.0.0.1:8082:8080` — PASS; HTTP connectivity — FAIL with exit code 4 (`Connection refused`). Ollama was not publicly exposed. Vector-dimension compatibility is NOT YET APPLICABLE because no fixed vector schema exists yet.

## Limitations

This validation does not claim PDF extraction, chunking, indexing, retrieval quality, grounded-answer quality, citations, abstention quality, or RAG quality.
