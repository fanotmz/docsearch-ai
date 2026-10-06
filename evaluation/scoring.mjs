const goldEvidenceKey = (evidence) => `${evidence.source}:${evidence.page}`;
const resultEvidenceKey = (evidence) => `${evidence.source}:${evidence.pageNumber}`;

const requireCase = (benchmarkCase) => {
  if (!benchmarkCase || typeof benchmarkCase.id !== "string" || !Array.isArray(benchmarkCase.goldEvidence)) {
    throw new TypeError("Malformed benchmark case");
  }
  if (!Array.isArray(benchmarkCase.requiredFacts) || !["ANSWERED", "INSUFFICIENT_EVIDENCE"].includes(benchmarkCase.expectedStatus)) {
    throw new TypeError(`Malformed benchmark case ${benchmarkCase.id}`);
  }
};

const requireSearchResults = (results) => {
  if (!Array.isArray(results) || results.some((result) => !result || typeof result.source !== "string" || !Number.isInteger(result.pageNumber))) {
    throw new TypeError("Malformed search result list");
  }
};

const uniqueKeys = (items, keyFunction = goldEvidenceKey) => [...new Set(items.map(keyFunction))];

export function scoreRetrievalCase(benchmarkCase, searchResults) {
  requireCase(benchmarkCase);
  requireSearchResults(searchResults);
  if (benchmarkCase.expectedStatus !== "ANSWERED") {
    return { id: benchmarkCase.id, excluded: true };
  }
  const gold = uniqueKeys(benchmarkCase.goldEvidence, goldEvidenceKey);
  const returned = searchResults.slice(0, 5).map(resultEvidenceKey);
  const firstRelevantIndex = returned.findIndex((key) => gold.includes(key));
  const hit = (limit) => returned.slice(0, limit).some((key) => gold.includes(key)) ? 1 : 0;
  const found = new Set(returned.filter((key) => gold.includes(key)));
  return {
    id: benchmarkCase.id,
    excluded: false,
    hitAt1: hit(1),
    hitAt3: hit(3),
    hitAt5: hit(5),
    reciprocalRankAt5: firstRelevantIndex < 0 ? 0 : 1 / (firstRelevantIndex + 1),
    goldPageRecallAt5: gold.length === 0 ? null : found.size / gold.length,
    completeEvidenceAt5: gold.length > 0 && found.size === gold.length ? 1 : 0,
    firstRelevantRankAt5: firstRelevantIndex < 0 ? null : firstRelevantIndex + 1,
  };
}

export function aggregateRetrieval(scores) {
  const included = scores.filter((score) => !score.excluded);
  if (included.length === 0) {
    return { cases: 0, hitAt1: null, hitAt3: null, hitAt5: null, mrrAt5: null, goldPageRecallAt5: null, completeEvidenceAt5: null };
  }
  const average = (field) => included.reduce((sum, score) => sum + score[field], 0) / included.length;
  return {
    cases: included.length,
    hitAt1: average("hitAt1"),
    hitAt3: average("hitAt3"),
    hitAt5: average("hitAt5"),
    mrrAt5: average("reciprocalRankAt5"),
    goldPageRecallAt5: average("goldPageRecallAt5"),
    completeEvidenceAt5: average("completeEvidenceAt5"),
  };
}

const citationKeys = (citations) => {
  if (!Array.isArray(citations) || citations.some((citation) => !citation || typeof citation.source !== "string" || !Number.isInteger(citation.pageNumber))) {
    throw new TypeError("Malformed citation list");
  }
  return uniqueKeys(citations, resultEvidenceKey);
};

export function scoreQaCase(benchmarkCase, answer) {
  requireCase(benchmarkCase);
  if (!answer || !["ANSWERED", "INSUFFICIENT_EVIDENCE"].includes(answer.status)) {
    throw new TypeError(`Malformed QA result for ${benchmarkCase.id}`);
  }
  const actualCitations = citationKeys(answer.citations ?? []);
  const gold = uniqueKeys(benchmarkCase.goldEvidence, goldEvidenceKey);
  const goldSet = new Set(gold);
  const actualSet = new Set(actualCitations);
  const expectedStatusCorrect = benchmarkCase.expectedStatus === answer.status ? 1 : 0;
  const answerable = benchmarkCase.expectedStatus === "ANSWERED";
  const precision = answerable && actualCitations.length > 0
    ? actualCitations.filter((key) => goldSet.has(key)).length / actualCitations.length
    : answerable ? 0 : null;
  const recall = answerable && gold.length > 0
    ? gold.filter((key) => actualSet.has(key)).length / gold.length
    : answerable ? 0 : null;
  return {
    id: benchmarkCase.id,
    expectedStatus: benchmarkCase.expectedStatus,
    actualStatus: answer.status,
    expectedStatusCorrect,
    answerable,
    citationPrecision: precision,
    citationRecall: recall,
    citationComplete: answerable && gold.length > 0 && gold.every((key) => actualSet.has(key)) ? 1 : answerable ? 0 : null,
    abstentionCorrect: benchmarkCase.expectedStatus === "INSUFFICIENT_EVIDENCE" ? expectedStatusCorrect : null,
  };
}

export function aggregateQa(scores) {
  const average = (items, field) => {
    const values = items.map((item) => item[field]).filter((value) => value !== null && value !== undefined);
    return values.length === 0 ? null : values.reduce((sum, value) => sum + value, 0) / values.length;
  };
  const answerable = scores.filter((score) => score.answerable);
  const unanswerable = scores.filter((score) => !score.answerable);
  return {
    cases: scores.length,
    expectedStatusAccuracy: average(scores, "expectedStatusCorrect"),
    answerableStatusAccuracy: average(answerable, "expectedStatusCorrect"),
    abstentionAccuracy: average(unanswerable, "abstentionCorrect"),
    citationPrecision: average(answerable, "citationPrecision"),
    citationRecall: average(answerable, "citationRecall"),
    citationCompleteRate: average(answerable, "citationComplete"),
  };
}

export function percentile(values, percentileValue) {
  if (!Array.isArray(values) || values.length === 0) return null;
  const sorted = [...values].sort((a, b) => a - b);
  const rank = Math.max(1, Math.ceil((percentileValue / 100) * sorted.length));
  return sorted[rank - 1];
}

export function latencySummary(values) {
  if (!Array.isArray(values) || values.length === 0) return { count: 0, min: null, median: null, p95: null, max: null };
  const sorted = [...values].sort((a, b) => a - b);
  return { count: values.length, min: sorted[0], median: percentile(values, 50), p95: percentile(values, 95), max: sorted.at(-1) };
}
