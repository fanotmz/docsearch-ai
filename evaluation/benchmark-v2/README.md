# DOCSEARCH-08 benchmark-v2 definition

This corrected benchmark is frozen before the first real request. It is not based on
`docsearch-05-search-topics.pdf`, which was used for implementation smoke
qualification.

Benchmark-v2 preserves the benchmark-v1 corpus and all 22 unchanged cases.
It replaces only U02 and U03 with genuinely unsupported questions while
keeping their stable IDs. Production behavior is unchanged.

## Dataset

- Five newly authored project-owned PDF documents.
- Three pages per document, 15 source pages total.
- Sixteen answerable cases: ten single-evidence, four multi-evidence and two
  paraphrase/distractor cases.
- Eight unanswerable cases: four near-miss cases and four unsupported cases.
- U02 asks who prints the yellow dawn pollination tags; U03 asks for the
  Solace probe's total mission duration. Neither fact is present in the corpus.
- Gold evidence is identified by PDF filename and 1-based page number only.
- `manifest.json` records SHA-256 hashes for every source and PDF.

## Frozen production profile

- Ollama `0.35.1`.
- Embedding `bge-m3:latest`, digest
  `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`,
  dimension 1024.
- Chat `qwen3.5:9b`, digest
  `6488c96fa5faab64bb65cbd30d4289e20e6130ef535a93ef9a49f42eda893ea7`.
- Search topK 5 and fixed Q&A topK 5.
- Existing TokenTextSplitter baseline: 800 tokens, 200 minimum characters,
  10 minimum embed length, 1000 maximum chunks, separator preservation on.

## Pre-registered gates

- Retrieval: Hit@1 >= 0.75, Hit@5 >= 0.90, MRR@5 >= 0.75 and complete
  gold-evidence retrieval@5 >= 0.80.
- Grounded Q&A: expected-status accuracy >= 0.80, unanswerable abstention
  accuracy >= 0.75, citation precision >= 0.90 and citation recall >= 0.80.
- Human review: fully-correct rate >= 0.75.
- Latency is reported and is not a hard gate on local hardware.

The human semantic-review fields are deliberately absent from the frozen
definition and remain null in the generated review artifact until an
independent review is completed.
