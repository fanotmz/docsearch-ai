# DOCSEARCH-02 validation

Status: PASS. Host and Dockerized local AI validation passed after restricting Ollama access to Docker Desktop's internal subnet. This document records this local validation only; it does not validate retrieval or RAG quality.

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
- Initial `http://localhost:11434` access was unavailable while Ollama was bound only to `172.28.112.1:11434`; after the approved firewall-scoped bind change, localhost, the WSL address and Docker Desktop access all passed.
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
| Docker -> `host.docker.internal` HTTP | PASS: HTTP 200 from `/api/version` after the firewall-scoped bind change |
| Dockerized embedding | PASS: dimension `768`, finite values, elapsed `3224 ms` |
| Dockerized generation | PASS: non-empty text, elapsed `21061 ms` |
| Normal startup without inference | PASS: freshly recreated backend reached `Started DocSearchApplication`; no smoke log entries occurred |

The smoke remains opt-in through `docsearch.smoke.enabled=true`. It reports the configured model identifiers, vector dimension, finite-value validation, and elapsed times; generation acceptance is non-empty text.

The first host smoke attempt on port 8080 failed because that port was already occupied by another local process. The same validation passed on port 18080 without stopping the unrelated process.

## Docker and schema commands

```powershell
docker compose config --quiet
docker compose up -d postgres
docker compose run --rm --no-deps --entrypoint sh backend -c "wget -S -O- --timeout=10 http://host.docker.internal:11434/api/version"
docker compose build backend
docker compose run --rm --no-deps -e DOCSEARCH_SMOKE_ENABLED=true -e OLLAMA_CHAT_MODEL=qwen3.5:9b -e OLLAMA_EMBEDDING_MODEL=embeddinggemma:300m backend
docker compose up -d --build --force-recreate backend
docker compose logs backend
```

Observed Docker results: `docker compose config --quiet` — PASS; backend image build — PASS; current compose mapping `127.0.0.1:8082:8080` — PASS; HTTP connectivity — PASS with HTTP 200; Dockerized embedding/generation — PASS. Ollama remains restricted by Windows Firewall to Docker Desktop's `192.168.65.0/24` source subnet and is not publicly exposed. Vector-dimension compatibility is NOT YET APPLICABLE because no fixed vector schema exists yet.

## Limitations

This validation does not claim PDF extraction, chunking, indexing, retrieval quality, grounded-answer quality, citations, abstention quality, or RAG quality.
