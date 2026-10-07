# DOCSEARCH-08 evaluation

Status: baseline-v1 reviewed; corrected benchmark-v2 pending execution and independent semantic review.

DOCSEARCH-08 evaluates the production system frozen through DOCSEARCH-07.
No retrieval, chunking, prompt, model, topK, vector-store or answer-policy
change was made after the benchmark definition was committed.

## Benchmark design

`evaluation/benchmark-v1` contains five newly authored project-owned PDFs,
three pages each, for 15 source pages. The corpus is separate from
`docsearch-05-search-topics.pdf`, which was used during implementation smoke
qualification. There are exactly 24 cases:

- 16 answerable: 10 single-evidence, 4 multi-evidence and 2 paraphrase/
  distractor cases;
- 8 unanswerable: 4 near-miss cases and 4 unsupported cases.

Gold evidence is page-level (`source` plus 1-based `page`); chunk indexes are
not used as gold identity. Source text, generated PDFs, cases and SHA-256
hashes are committed in the benchmark definition.

## Frozen system fingerprint

- Application SHA: `cca96cbfe4b887a7ebb502cafe3f249b19ff7a3f`.
- Ollama: `0.35.1`.
- Embedding: `bge-m3:latest`, digest
  `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`,
  dimension 1024.
- Chat: `qwen3.5:9b`, digest
  `6488c96fa5faab64bb65cbd30d4289e20e6130ef535a93ef9a49f42eda893ea7`.
- Chat temperature: `0`; `num-ctx=8192`; `num-predict=512`.
- Search topK: `5`; fixed Q&A topK: `5`.
- TokenTextSplitter: chunk size 800, minimum chunk size 200 characters,
  minimum embed length 10, maximum chunks 1000, separator preservation on.
- Corpus manifest SHA-256:
  `f5c7cba32d36b8f55f31f7855db4de7a132fcc177a85f015b766afb866464d60`.
- Cases SHA-256:
  `d8c37932c532eff83dd686403058bdc0657b40f650b6fe87d2398f3147bda47e`.

## Reproducibility

The run used an isolated temporary pgvector PostgreSQL instance on host port
55433 and a local backend on port 18082. The runner refused to continue until
`GET /api/v1/documents` returned an empty array, uploaded all five PDFs,
waited for every document to become READY, sent search requests for the 16
answerable cases and sent exactly one Q&A request for every case. The normal
development database and containers were not used as the evaluation store.

Deterministic scorer tests run without Ollama:

```powershell
node --test evaluation/tests/scoring.test.mjs
```

The real run is reproducible with the isolated environment and:

```powershell
$env:DOCSEARCH_EVAL_BASE_URL = "http://127.0.0.1:18082"
$env:DOCSEARCH_EVAL_APP_SHA = (git rev-parse HEAD)
node evaluation/run-benchmark.mjs
```

## Automated baseline results

| Retrieval metric | Result | Pre-registered gate |
|---|---:|---:|
| Hit@1 | 100.0% | >= 75.0% |
| Hit@3 | 100.0% | reported |
| Hit@5 | 100.0% | >= 90.0% |
| MRR@5 | 100.0% | >= 75.0% |
| Gold evidence recall@5 | 100.0% | reported |
| Complete evidence@5 | 100.0% | >= 80.0% |

| Q&A structural metric | Result | Pre-registered gate |
|---|---:|---:|
| Expected-status accuracy | 91.7% (22/24) | >= 80.0% |
| Answerable status accuracy | 100.0% (16/16) | reported |
| Unanswerable abstention accuracy | 75.0% (6/8) | >= 75.0% |
| Citation precision | 100.0% | >= 90.0% |
| Citation recall | 100.0% | >= 80.0% |
| Citation-complete rate | 100.0% | reported |

The two unanswerable cases answered by the frozen model were `U02` and
`U03`. `U02` answered that no green tag is assigned, citing page 3; `U03`
answered that Solace completes zero polar orbits, citing page 3. These are
recorded as baseline false-positive abstentions. No production tuning was
performed in response.

## Latency

All values are milliseconds and include the HTTP request duration on this
local machine. Latency is reported, not gated:

| Operation | Count | Min | Median | P95 | Max |
|---|---:|---:|---:|---:|---:|
| Upload | 5 | 342 | 501 | 14332 | 14332 |
| Search | 16 | 219 | 27135 | 38853 | 38853 |
| Q&A | 24 | 45758 | 65620 | 90314 | 96307 |

## Independent v1 human semantic review

The independent review artifact is `evaluation/results/baseline-v1.review.json`.
The review found:

- semantic correctness: 24/24 = 100.0%;
- completeness: 24/24 = 100.0%;
- material-claim support: 24/24 = 100.0%;
- fully-correct rate under frozen expected-status labels: 22/24 = 91.7%.

U02 and U03 are benchmark-label defects, not production-model defects. The
corpus explicitly states that no green tag is assigned and that Solace does
not use a polar orbit, so both frozen answers are supported. These cases are
not genuinely unanswerable. Benchmark-v1 remains immutable.

## Benchmark-v2

Benchmark-v2 preserves the v1 corpus and all corpus hashes, and replaces only
U02 and U03 with questions whose requested facts are absent: the producer of
the yellow dawn tags and the total mission duration of Solace. Their
`goldEvidence` and `requiredFacts` arrays are empty, and their IDs remain
stable. The v2 definition is frozen before its complete real run. Its human
semantic review remains pending.

The final DOCSEARCH-08 verdict remains pending the corrected v2 run and its
independent semantic review.

## Benchmark-v2 baseline results

Benchmark-v2 was executed once against the isolated evaluation database using
the frozen application at `0df852f3154ed788da26c47c576b3efc9e3fd909`. The
corpus manifest SHA-256 is
`513ea7f36c1a64a7b2dbb05273da8bde1faf7e92d1819a8ed3e673afbc87d7d0` and the
corrected cases SHA-256 is
`c8c1dd903f444ce62bd77fef0ad8d06c28f0f5722f8904c5135d2ddc02b21118`.

Automated retrieval metrics were all 100.0%: Hit@1, Hit@3, Hit@5, MRR@5,
gold-evidence recall@5 and complete-evidence@5. Automated Q&A metrics were
also all 100.0%: expected-status accuracy (24/24), answerable status accuracy
(16/16), unanswerable abstention accuracy (8/8), citation precision, citation
recall and citation-complete rate.

Search latency was 25,459 ms median and 36,466 ms p95. Q&A latency was
65,924 ms median and 87,141 ms p95. All 16 search and 24 Q&A requests
returned HTTP 200; no HTTP or model failures occurred.

The v2 review artifact remains unreviewed with all human fields null. These
automated results do not constitute the final DOCSEARCH-08 verdict.

## Result artifacts

- `evaluation/results/baseline-v1.raw.json`
- `evaluation/results/baseline-v1.metrics.json`
- `evaluation/results/baseline-v1.md`
- `evaluation/results/baseline-v1.review.json`

The raw artifact preserves case metadata, search results, model answers,
citations, HTTP outcomes and timings without machine-specific absolute paths
or model binaries.
