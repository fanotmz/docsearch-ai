# DOCSEARCH-08 baseline-v2

Run ID: `2026-10-07T08-47-43-896Z`

This is a frozen-system baseline. No production retrieval, chunking, prompt, model, topK or answer-policy tuning was performed after benchmark definition.

## Corpus and fingerprint

- Documents/pages: 5/15
- Cases: 24 (16 answerable, 8 unanswerable)
- Evaluated repository SHA: `0df852f3154ed788da26c47c576b3efc9e3fd909`
- Ollama: `0.35.1`
- Embedding: `bge-m3:latest`, digest `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`, dimension 1024
- Chat: `qwen3.5:9b`, digest `6488c96fa5faab64bb65cbd30d4289e20e6130ef535a93ef9a49f42eda893ea7`
- QA_TOP_K: 5; search topK: 5
- Corpus manifest SHA-256: `513ea7f36c1a64a7b2dbb05273da8bde1faf7e92d1819a8ed3e673afbc87d7d0`; cases SHA-256: `c8c1dd903f444ce62bd77fef0ad8d06c28f0f5722f8904c5135d2ddc02b21118`

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
| Expected-status accuracy | 100.0% | >= 80.0% |
| Answerable status accuracy | 100.0% | not separately gated |
| Unanswerable abstention accuracy | 100.0% | >= 75.0% |
| Citation precision | 100.0% | >= 90.0% |
| Citation recall | 100.0% | >= 80.0% |
| Citation-complete rate | 100.0% | not separately gated |

## Latency

- Upload: {"count":5,"min":990,"median":1670,"p95":32515,"max":32515}
- Search: {"count":16,"min":300,"median":25459,"p95":36466,"max":36466}
- Q&A: {"count":24,"min":49850,"median":65924,"p95":87141,"max":138021}

## Final human semantic review

- Semantic correctness: 24/24 = 100.0%.
- Completeness: 24/24 = 100.0%.
- Material-claim support: 24/24 = 100.0%.
- Fully-correct rate: 24/24 = 100.0%.
- Human qualification gate: >= 75.0% — PASS.

U02 and U03 were correctly abstained because the corpus does not identify
who produces the yellow dawn tags or the total mission duration of Solace.

## Final qualification

DOCSEARCH-08 = PASS. Benchmark-v2 passes every pre-registered retrieval,
Q&A structural and human semantic gate for this frozen V1 evaluation.
These project-owned results are not a universal quality guarantee.

## Execution

- Uploaded documents: 5; all reached READY.
- Exactly one Q&A request was sent for each of the 24 cases.
- No application data outside the isolated evaluation database was accessed.
