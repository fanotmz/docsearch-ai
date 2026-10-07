# DOCSEARCH-08 baseline-v1

Run ID: `2026-10-06T23-31-28-677Z`

This is a frozen-system baseline. No production retrieval, chunking, prompt, model, topK or answer-policy tuning was performed after benchmark definition.

## Corpus and fingerprint

- Documents/pages: 5/15
- Cases: 24 (16 answerable, 8 unanswerable)
- Application SHA: `cca96cbfe4b887a7ebb502cafe3f249b19ff7a3f`
- Ollama: `0.35.1`
- Embedding: `bge-m3:latest`, digest `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`, dimension 1024
- Chat: `qwen3.5:9b`, digest `6488c96fa5faab64bb65cbd30d4289e20e6130ef535a93ef9a49f42eda893ea7`
- QA_TOP_K: 5; search topK: 5
- Corpus manifest SHA-256: `f5c7cba32d36b8f55f31f7855db4de7a132fcc177a85f015b766afb866464d60`; cases SHA-256: `d8c37932c532eff83dd686403058bdc0657b40f650b6fe87d2398f3147bda47e`

## Retrieval

| Metric | Result | Gate |
|---|---:|---:|
| Hit@1 | 100.0% | >= 75.0% |
| Hit@3 | 100.0% | not gated |
| Hit@5 | 100.0% | >= 90.0% |
| MRR@5 | 100.0% | >= 75.0% |
| Gold evidence recall@5 | 100.0% | not separately gated |
| Complete evidence@5 | 100.0% | >= 80.0% |

## Q&A structural metrics

| Metric | Result | Gate |
|---|---:|---:|
| Expected-status accuracy | 91.7% | >= 80.0% |
| Answerable status accuracy | 100.0% | not separately gated |
| Unanswerable abstention accuracy | 75.0% | >= 75.0% |
| Citation precision | 100.0% | >= 90.0% |
| Citation recall | 100.0% | >= 80.0% |
| Citation-complete rate | 100.0% | not separately gated |

## Latency

- Upload: {"count":5,"min":342,"median":501,"p95":14332,"max":14332}
- Search: {"count":16,"min":219,"median":27135,"p95":38853,"max":38853}
- Q&A: {"count":24,"min":45758,"median":65620,"p95":90314,"max":96307}

## Independent human semantic review

- Semantic correctness: 24/24 = 100.0%.
- Completeness: 24/24 = 100.0%.
- Material-claim support: 24/24 = 100.0%.
- Fully-correct rate under the frozen expected-status labels: 22/24 = 91.7%.

U02 and U03 are benchmark-label defects, not production-model defects. The
corpus directly supports both answers, so those cases are not genuinely
unanswerable. Benchmark-v1 remains immutable; the corrected cases belong to
benchmark-v2.

## Execution

- Uploaded documents: 5; all reached READY.
- Exactly one Q&A request was sent for each of the 24 cases.
- No application data outside the isolated evaluation database was accessed.
