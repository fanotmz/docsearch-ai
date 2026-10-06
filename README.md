# DocSearch AI

Local document search and grounded question answering, built with Spring AI, Angular and PostgreSQL/pgvector.

**Status: foundation prepared.** PDF ingestion and RAG are planned; they are not available yet. The current UI displays the backend connection state and a library placeholder. A successful health response does not certify that Ollama or document search is working.

## Stack

| Component | Version / choice |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.1 |
| Spring AI | 2.0.1 |
| Maven | 3.9.9, wrapper 3.3.4 |
| Angular | 22.2.x, exact dependencies in package-lock.json |
| Node | 24.19.0 |
| PostgreSQL / pgvector | PostgreSQL 16 / pgvector 0.8.1 |
| LLM candidate | Ollama, Qwen3.5-9B Q4_K_M |
| Embedding candidate | Ollama, BGE-M3 |

## Quick start

Prerequisites: Docker Desktop/Engine and Docker Compose. Java and Node installations are unnecessary for the container build.

```bash
cp .env.example .env
docker compose config --quiet
docker compose up --build -d
docker compose logs -f backend
```

Open http://localhost:4200. The backend is available at http://localhost:8080/api/v1/system; database health is exposed at http://localhost:8080/actuator/health. Ports are bound to localhost. Default database credentials are for local development only.

The foundation starts without Ollama. No model download or inference runs at startup unless the optional smoke is enabled. The vector extension is installed by Flyway; the vector table will be added after the real embedding dimension is measured.

Stop with `docker compose down`. Database data remains in the named volume. Changing PostgreSQL credentials in `.env` does not change credentials inside an existing initialized volume.

## Development

Requirements: JDK 21, Node 24.19.0 and Docker.

```bash
docker compose up -d postgres
cd backend
./mvnw verify
./mvnw spring-boot:run
```

In a second terminal:

```bash
cd frontend
npm ci
npm start
```

The Angular development server proxies API requests to port 8082 when using Docker. The container still listens on port 8080 internally. On Windows, use `mvnw.cmd` instead of `./mvnw`. The first wrapper execution downloads Maven; dependency and image downloads also require Internet access.

## Optional real-model smoke

This is a connectivity test, not a RAG quality benchmark. Install Ollama on the development host and ensure the models used by the smoke are available:

```bash
ollama pull qwen3.5:9b
ollama pull embeddinggemma:300m
ollama list
```

BGE-M3 remains the architectural embedding candidate for the future vector index, but it was not downloaded or qualified in DOCSEARCH-02. The validation used the already-installed `embeddinggemma:300m` only as a provisional local smoke model; the command below does not qualify BGE-M3.

With PostgreSQL running, launch the backend directly on the same host as Ollama:

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.arguments="--docsearch.smoke.enabled=true --spring.ai.ollama.chat.model=qwen3.5:9b --spring.ai.ollama.embedding.model=embeddinggemma:300m"
```

Expected logs include embedding dimensions and elapsed time, plus non-empty chat output and elapsed time. An empty/non-finite vector causes startup to fail. Record the actual embedding dimension, model digests, Ollama version, RAM and elapsed time before implementing the vector schema.

Default inference settings use a bounded 8K chat context, 512 output tokens, no automatic model pulls, no retries and unloading after each request. These are initial settings, not a validated hardware profile. The exact marker smoke can fail even when the model is reachable; inspect the logs before concluding there is a transport failure.

For container access to Ollama, `.env` uses `host.docker.internal`. Loopback-only Ollama may not be reachable from a container, particularly with WSL/Linux. Run the smoke directly on the host first; do not expose Ollama publicly to solve a connectivity problem.

## Repository

- `backend/`: application, native Spring AI clients, Flyway migration, Testcontainers integration test.
- `frontend/`: Angular application and same-origin proxy.
- `compose.yaml`: local services and persistent PostgreSQL volume.
- `.github/workflows/ci.yml`: backend integration test, frontend production build and container builds.
- `docs/`: architecture decisions and validation instructions.

## Validation

Frontend production build has been executed successfully. XML/YAML/JSON configuration and Unix wrapper syntax have been checked. Backend compilation, Testcontainers, container startup and real Ollama smoke remain to be executed in a Java 21/Docker environment. No RAG performance or accuracy result is claimed.

CI requires Docker and deliberately fails when the integration test cannot start its database; it does not silently skip it. CI does not download models. See [foundation checks](docs/foundation-checks.md).

## Planned MVP

Text PDF import → page-preserving chunks → dense embeddings → pgvector retrieval → Spring AI RAG → document/page citations. Single-user local application; no OCR, web search, autonomous tools or conversation memory in V1. Only redistributable documents may be included in the demonstration corpus.

## Third-party notices

The Maven Wrapper scripts originate from Apache Maven Wrapper 3.3.4. Their Apache license and notice are retained under `backend/.mvn/wrapper/`. A project-wide license must be selected before publishing a release; no license or rights are inferred for code that may later be reused from other projects.
