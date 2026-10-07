# DOCSEARCH-08 frozen benchmark

This directory contains the project-owned frozen benchmark definitions and the
reproducible scorer/runner for DOCSEARCH-08. The production system is frozen
for this evaluation: do not change retrieval, chunking, prompts, models,
topK, vector storage or answer policy after observing results.

## Definition

Each benchmark version contains five newly authored three-page PDFs, their human-
readable source text, SHA-256 manifest and exactly 24 cases: 16 answerable
and 8 unanswerable. Gold evidence is page-level (`source` plus `page`), not
chunk-level.

The PDFs are generated from the source text with:

```powershell
python evaluation/build-corpus-pdfs.py
```

The generated PDFs are committed. Regeneration changes their hashes and must
create a new benchmark version rather than silently changing `benchmark-v1`.

## Deterministic scorer tests

These tests never call Ollama:

```powershell
node --test evaluation/tests/scoring.test.mjs
```

## Real baseline runner

Run only against an isolated temporary PostgreSQL/pgvector database and a
dedicated local backend port. The runner refuses to start if the document
library is not empty, uploads all five PDFs, waits for READY, sends one
semantic-search request for each answerable case and exactly one Q&A request
for every case.

```powershell
$env:DOCSEARCH_EVAL_BASE_URL = "http://127.0.0.1:18082"
$env:DOCSEARCH_EVAL_APP_SHA = (git rev-parse HEAD)
$env:DOCSEARCH_EVAL_OLLAMA_VERSION = "0.35.1"
$env:DOCSEARCH_EVAL_BENCHMARK = "v1"
$env:DOCSEARCH_EVAL_OUTPUT_PREFIX = "baseline-v1"
node evaluation/run-benchmark.mjs
```

Set `DOCSEARCH_EVAL_BENCHMARK` to `v2` and the output prefix to
`baseline-v2` for the corrected dataset. The runner writes versioned
artifacts such as `results/baseline-v1.raw.json`,
`results/baseline-v1.metrics.json`, `results/baseline-v1.md` and
`results/baseline-v1.review.json`. Human semantic-review fields remain null.

Latency is reported but is not a qualification gate. Automated structural
metrics do not establish semantic correctness; that requires independent
review of the committed raw outputs.
