import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { basename, dirname, join, resolve } from "node:path";
import { performance } from "node:perf_hooks";
import { fileURLToPath } from "node:url";
import { aggregateQa, aggregateRetrieval, latencySummary, scoreQaCase, scoreRetrievalCase } from "./scoring.mjs";

const evaluationDir = dirname(fileURLToPath(import.meta.url));
const benchmarkDir = join(evaluationDir, "benchmark-v1");
const resultsDir = join(evaluationDir, "results");
const baseUrl = (process.env.DOCSEARCH_EVAL_BASE_URL ?? "http://127.0.0.1:18082").replace(/\/$/, "");
const requestTimeoutMs = Number(process.env.DOCSEARCH_EVAL_TIMEOUT_MS ?? 600000);
const topK = 5;

const manifest = JSON.parse(await readFile(join(benchmarkDir, "manifest.json"), "utf8"));
const cases = JSON.parse(await readFile(join(benchmarkDir, "cases.json"), "utf8"));

const sha256 = (buffer) => createHash("sha256").update(buffer).digest("hex");
const nowRunId = new Date().toISOString().replace(/[:.]/g, "-");

async function verifyBenchmarkDefinition() {
  if (manifest.documents.length !== 5 || manifest.corpusPages !== 15 || cases.length !== 24) {
    throw new Error("Benchmark definition must contain exactly 5 documents, 15 pages and 24 cases");
  }
  const ids = cases.map((benchmarkCase) => benchmarkCase.id);
  if (new Set(ids).size !== ids.length) throw new Error("Benchmark case IDs must be unique");
  const casesHash = sha256(await readFile(join(benchmarkDir, "cases.json")));
  if (casesHash !== manifest.casesSha256) throw new Error("cases.json SHA-256 does not match manifest.json");
  for (const document of manifest.documents) {
    const sourceBytes = await readFile(join(benchmarkDir, "corpus-sources", document.sourceFile));
    const pdfBytes = await readFile(join(benchmarkDir, "corpus", document.pdf));
    if (sha256(sourceBytes) !== document.sourceSha256 || sha256(pdfBytes) !== document.pdfSha256) {
      throw new Error(`Corpus hash mismatch for ${document.pdf}`);
    }
  }
}

async function requestJson(path, options = {}) {
  const started = performance.now();
  let response;
  try {
    response = await fetch(`${baseUrl}${path}`, { ...options, signal: AbortSignal.timeout(requestTimeoutMs) });
  }
  catch (error) {
    throw new Error(`Infrastructure request failed for ${path}: ${error.message}`, { cause: error });
  }
  const elapsedMs = Math.round(performance.now() - started);
  const text = await response.text();
  let body;
  try {
    body = text ? JSON.parse(text) : null;
  }
  catch {
    body = { raw: text };
  }
  return { status: response.status, elapsedMs, body };
}

const jsonOptions = (body) => ({ method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });

async function waitForReadyDocuments(expectedNames) {
  const deadline = Date.now() + 180000;
  while (Date.now() < deadline) {
    const response = await requestJson("/api/v1/documents");
    if (response.status !== 200 || !Array.isArray(response.body)) {
      throw new Error(`Document listing failed while waiting: HTTP ${response.status}`);
    }
    const matching = response.body.filter((document) => expectedNames.has(document.filename));
    if (matching.length === expectedNames.size && matching.every((document) => document.status === "READY")) {
      return matching;
    }
    await new Promise((resolvePromise) => setTimeout(resolvePromise, 1000));
  }
  throw new Error("Timed out waiting for benchmark documents to become READY");
}

async function uploadPdf(pdfName) {
  const bytes = await readFile(join(benchmarkDir, "corpus", pdfName));
  const form = new FormData();
  form.append("file", new Blob([bytes], { type: "application/pdf" }), pdfName);
  const started = performance.now();
  let response;
  try {
    response = await fetch(`${baseUrl}/api/v1/documents`, { method: "POST", body: form, signal: AbortSignal.timeout(requestTimeoutMs) });
  }
  catch (error) {
    throw new Error(`Infrastructure upload failed for ${pdfName}: ${error.message}`, { cause: error });
  }
  const elapsedMs = Math.round(performance.now() - started);
  const text = await response.text();
  const body = text ? JSON.parse(text) : null;
  if (response.status < 200 || response.status >= 300) {
    throw new Error(`Benchmark upload failed for ${pdfName}: HTTP ${response.status}`);
  }
  return { pdfName, elapsedMs, status: response.status, body };
}

function reviewTemplate(searchResults, qaResults) {
  const byId = new Map(qaResults.map((result) => [result.id, result]));
  return cases.map((benchmarkCase) => {
    const actual = byId.get(benchmarkCase.id);
    return {
      caseId: benchmarkCase.id,
      expectedStatus: benchmarkCase.expectedStatus,
      question: benchmarkCase.question,
      requiredFacts: benchmarkCase.requiredFacts,
      goldEvidence: benchmarkCase.goldEvidence,
      actualStatus: actual?.status ?? null,
      answer: actual?.answer ?? null,
      citations: actual?.citations ?? [],
      semanticCorrect: null,
      complete: null,
      allMaterialClaimsSupported: null,
      reviewerNotes: "",
    };
  });
}

const formatMetric = (value) => value === null ? "n/a" : `${(value * 100).toFixed(1)}%`;

function markdownReport({ fingerprint, retrieval, qa, latency, uploads, runId }) {
  return `# DOCSEARCH-08 baseline-v1\n\n` +
    `Run ID: \`${runId}\`\n\n` +
    `This is a frozen-system baseline. No production retrieval, chunking, prompt, model, topK or answer-policy tuning was performed after benchmark definition.\n\n` +
    `## Corpus and fingerprint\n\n` +
    `- Documents/pages: ${manifest.documents.length}/${manifest.corpusPages}\n` +
    `- Cases: ${cases.length} (${cases.filter((item) => item.expectedStatus === "ANSWERED").length} answerable, ${cases.filter((item) => item.expectedStatus !== "ANSWERED").length} unanswerable)\n` +
    `- Application SHA: \`${fingerprint.applicationSha}\`\n` +
    `- Ollama: \`${fingerprint.ollamaVersion}\`\n` +
    `- Embedding: \`${fingerprint.embeddingModel}\`, digest \`${fingerprint.embeddingDigest}\`, dimension ${fingerprint.embeddingDimension}\n` +
    `- Chat: \`${fingerprint.chatModel}\`, digest \`${fingerprint.chatDigest}\`\n` +
    `- QA_TOP_K: ${fingerprint.qaTopK}; search topK: ${fingerprint.searchTopK}\n` +
    `- Corpus manifest SHA-256: \`${fingerprint.manifestSha256}\`; cases SHA-256: \`${fingerprint.casesSha256}\`\n\n` +
    `## Retrieval\n\n` +
    `| Metric | Result | Gate |\n|---|---:|---:|\n` +
    `| Hit@1 | ${formatMetric(retrieval.hitAt1)} | >= 75.0% |\n` +
    `| Hit@3 | ${formatMetric(retrieval.hitAt3)} | not gated |\n` +
    `| Hit@5 | ${formatMetric(retrieval.hitAt5)} | >= 90.0% |\n` +
    `| MRR@5 | ${formatMetric(retrieval.mrrAt5)} | >= 75.0% |\n` +
    `| Gold evidence recall@5 | ${formatMetric(retrieval.goldPageRecallAt5)} | not separately gated |\n` +
    `| Complete evidence@5 | ${formatMetric(retrieval.completeEvidenceAt5)} | >= 80.0% |\n\n` +
    `## Q&A structural metrics\n\n` +
    `| Metric | Result | Gate |\n|---|---:|---:|\n` +
    `| Expected-status accuracy | ${formatMetric(qa.expectedStatusAccuracy)} | >= 80.0% |\n` +
    `| Answerable status accuracy | ${formatMetric(qa.answerableStatusAccuracy)} | not separately gated |\n` +
    `| Unanswerable abstention accuracy | ${formatMetric(qa.abstentionAccuracy)} | >= 75.0% |\n` +
    `| Citation precision | ${formatMetric(qa.citationPrecision)} | >= 90.0% |\n` +
    `| Citation recall | ${formatMetric(qa.citationRecall)} | >= 80.0% |\n` +
    `| Citation-complete rate | ${formatMetric(qa.citationCompleteRate)} | not separately gated |\n\n` +
    `## Latency\n\n` +
    `- Upload: ${JSON.stringify(latency.upload)}\n` +
    `- Search: ${JSON.stringify(latency.search)}\n` +
    `- Q&A: ${JSON.stringify(latency.qa)}\n\n` +
    `## Human semantic review\n\n` +
    `PENDING INDEPENDENT REVIEW. Automated citation validity is not semantic correctness. The committed review artifact leaves semanticCorrect, complete and allMaterialClaimsSupported null for every case.\n\n` +
    `## Execution\n\n` +
    `- Uploaded documents: ${uploads.length}; all reached READY.\n` +
    `- Exactly one Q&A request was sent for each of the ${cases.length} cases.\n` +
    `- No application data outside the isolated evaluation database was accessed.\n`;
}

async function main() {
  await verifyBenchmarkDefinition();
  const system = await requestJson("/api/v1/system");
  if (system.status !== 200 || system.body?.documentSearchAvailable !== true) {
    throw new Error(`Backend system check failed: HTTP ${system.status}`);
  }
  const initialLibrary = await requestJson("/api/v1/documents");
  if (initialLibrary.status !== 200 || !Array.isArray(initialLibrary.body)) {
    throw new Error(`Initial document listing failed: HTTP ${initialLibrary.status}`);
  }
  if (initialLibrary.body.length !== 0) {
    throw new Error(`Evaluation aborted: isolated library is not empty (${initialLibrary.body.length} documents)`);
  }

  const uploads = [];
  for (const document of manifest.documents) uploads.push(await uploadPdf(document.pdf));
  const readyDocuments = await waitForReadyDocuments(new Set(manifest.documents.map((document) => document.pdf)));

  const searchResults = [];
  const qaResults = [];
  const retrievalScores = [];
  const qaScores = [];
  const searchDurations = [];
  const qaDurations = [];

  for (const benchmarkCase of cases) {
    let search = null;
    if (benchmarkCase.expectedStatus === "ANSWERED") {
      const response = await requestJson("/api/v1/search", jsonOptions({ query: benchmarkCase.question, topK }));
      search = { status: response.status, elapsedMs: response.elapsedMs, body: response.body };
      searchDurations.push(response.elapsedMs);
      if (response.status !== 200 || !Array.isArray(response.body?.results)) {
        throw new Error(`Search protocol failure for ${benchmarkCase.id}: HTTP ${response.status}`);
      }
      retrievalScores.push(scoreRetrievalCase(benchmarkCase, response.body.results));
    }
    searchResults.push({ id: benchmarkCase.id, request: { query: benchmarkCase.question, topK }, response: search });

    const qaResponse = await requestJson("/api/v1/qa", jsonOptions({ question: benchmarkCase.question }));
    const qa = { status: qaResponse.status, elapsedMs: qaResponse.elapsedMs, body: qaResponse.body };
    qaDurations.push(qaResponse.elapsedMs);
    if (qaResponse.status === 200) {
      const answer = {
        status: qaResponse.body?.status,
        answer: qaResponse.body?.answer ?? null,
        citations: qaResponse.body?.citations ?? [],
      };
      qaScores.push(scoreQaCase(benchmarkCase, answer));
      qaResults.push({ id: benchmarkCase.id, status: answer.status, answer: answer.answer, citations: answer.citations, elapsedMs: qaResponse.elapsedMs, httpStatus: qaResponse.status });
    }
    else {
      qaResults.push({ id: benchmarkCase.id, status: null, answer: null, citations: [], elapsedMs: qaResponse.elapsedMs, httpStatus: qaResponse.status, error: qaResponse.body });
    }
  }

  const manifestBytes = await readFile(join(benchmarkDir, "manifest.json"));
  const casesBytes = await readFile(join(benchmarkDir, "cases.json"));
  const fingerprint = {
    applicationSha: process.env.DOCSEARCH_EVAL_APP_SHA ?? "unknown",
    ollamaVersion: process.env.DOCSEARCH_EVAL_OLLAMA_VERSION ?? "0.35.1",
    embeddingModel: "bge-m3:latest",
    embeddingDigest: "7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab",
    embeddingDimension: 1024,
    chatModel: "qwen3.5:9b",
    chatDigest: "6488c96fa5faab64bb65cbd30d4289e20e6130ef535a93ef9a49f42eda893ea7",
    chatTemperature: 0,
    chatNumCtx: 8192,
    chatNumPredict: 512,
    qaTopK: 5,
    searchTopK: 5,
    splitter: { chunkSize: 800, minChunkSizeChars: 200, minChunkLengthToEmbed: 10, maxNumChunks: 1000, keepSeparator: true },
    manifestSha256: sha256(manifestBytes),
    casesSha256: sha256(casesBytes),
  };
  const retrieval = aggregateRetrieval(retrievalScores);
  const qa = aggregateQa(qaScores);
  const latency = { upload: latencySummary(uploads.map((item) => item.elapsedMs)), search: latencySummary(searchDurations), qa: latencySummary(qaDurations) };
  const run = { runId: nowRunId, baseUrl, fingerprint, readyDocuments, uploads, searchResults, qaResults, retrieval, qa, latency };
  await mkdir(resultsDir, { recursive: true });
  await writeFile(join(resultsDir, "baseline-v1.raw.json"), JSON.stringify(run, null, 2) + "\n");
  await writeFile(join(resultsDir, "baseline-v1.metrics.json"), JSON.stringify({ runId: nowRunId, fingerprint, retrieval, qa, latency }, null, 2) + "\n");
  await writeFile(join(resultsDir, "baseline-v1.review.json"), JSON.stringify(reviewTemplate(searchResults, qaResults), null, 2) + "\n");
  await writeFile(join(resultsDir, "baseline-v1.md"), markdownReport({ fingerprint, retrieval, qa, latency, uploads, runId: nowRunId }));
  console.log(JSON.stringify({ runId: nowRunId, retrieval, qa, latency, resultFiles: ["baseline-v1.raw.json", "baseline-v1.metrics.json", "baseline-v1.review.json", "baseline-v1.md"] }, null, 2));
}

try {
  await main();
}
catch (error) {
  console.error(error.stack ?? error.message);
  process.exitCode = 1;
}
