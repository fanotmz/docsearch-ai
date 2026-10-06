# Foundation qualification

Prepared does not mean qualified. Complete these checks before beginning PDF ingestion.

1. Run `./mvnw verify` in `backend/` with JDK 21 and Docker available. The integration test must start its pgvector container, apply Flyway, check cosine distance and create the native AI clients without contacting Ollama.
2. Run `npm ci` and `npm run build` in `frontend/`.
3. Run `docker compose config --quiet` and `docker compose up --build -d` from the root. Check the backend health, system endpoint and the UI connection state.
4. Stop the backend and confirm the UI reports an unavailable service. Restart it and use the retry button.
5. Run the optional real-model smoke on the host. Record returned embedding dimension, chat marker result, Ollama version and model digests. Measure elapsed time and memory; do not treat model file sizes as total runtime RAM.
6. After the initial commit, inspect all CI jobs. Backend verification and both container builds must pass. Do not classify a skipped integration test as passing.

The production frontend build and configuration syntax checks have passed. The remaining backend, container and real-model checks require the target environment. No model or application performance claim is made until those checks are complete.
