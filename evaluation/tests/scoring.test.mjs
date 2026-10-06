import test from "node:test";
import assert from "node:assert/strict";
import { aggregateQa, aggregateRetrieval, latencySummary, scoreQaCase, scoreRetrievalCase } from "../scoring.mjs";

const answerable = (goldEvidence = [{ source: "a.pdf", page: 1 }]) => ({
  id: "A01",
  expectedStatus: "ANSWERED",
  requiredFacts: ["fact"],
  goldEvidence,
});

test("retrieval scores Hit@1, Hit@3, Hit@5, MRR and multi-page completeness", () => {
  const score = scoreRetrievalCase(answerable([
    { source: "a.pdf", page: 1 },
    { source: "a.pdf", page: 2 },
  ]), [
    { source: "d.pdf", pageNumber: 9 },
    { source: "a.pdf", pageNumber: 1 },
    { source: "a.pdf", pageNumber: 2 },
  ]);
  assert.equal(score.hitAt1, 0);
  assert.equal(score.hitAt3, 1);
  assert.equal(score.hitAt5, 1);
  assert.equal(score.reciprocalRankAt5, 0.5);
  assert.equal(score.goldPageRecallAt5, 1);
  assert.equal(score.completeEvidenceAt5, 1);
});

test("retrieval aggregate excludes unanswerable cases", () => {
  const score = scoreRetrievalCase({ id: "U01", expectedStatus: "INSUFFICIENT_EVIDENCE", requiredFacts: [], goldEvidence: [] }, []);
  assert.deepEqual(aggregateRetrieval([score]), { cases: 0, hitAt1: null, hitAt3: null, hitAt5: null, mrrAt5: null, goldPageRecallAt5: null, completeEvidenceAt5: null });
});

test("QA scoring handles status, citation precision/recall and completeness", () => {
  const score = scoreQaCase(answerable([
    { source: "a.pdf", page: 1 },
    { source: "a.pdf", page: 2 },
  ]), {
    status: "ANSWERED",
    citations: [{ source: "a.pdf", pageNumber: 1 }, { source: "wrong.pdf", pageNumber: 4 }],
  });
  assert.equal(score.expectedStatusCorrect, 1);
  assert.equal(score.citationPrecision, 0.5);
  assert.equal(score.citationRecall, 0.5);
  assert.equal(score.citationComplete, 0);
});

test("QA aggregate reports answerable and abstention accuracy", () => {
  const answered = scoreQaCase(answerable(), { status: "ANSWERED", citations: [{ source: "a.pdf", pageNumber: 1 }] });
  const abstained = scoreQaCase({ id: "U01", expectedStatus: "INSUFFICIENT_EVIDENCE", requiredFacts: [], goldEvidence: [] }, { status: "INSUFFICIENT_EVIDENCE", citations: [] });
  const metrics = aggregateQa([answered, abstained]);
  assert.equal(metrics.expectedStatusAccuracy, 1);
  assert.equal(metrics.answerableStatusAccuracy, 1);
  assert.equal(metrics.abstentionAccuracy, 1);
  assert.equal(metrics.citationPrecision, 1);
  assert.equal(metrics.citationRecall, 1);
});

test("zero denominators are null", () => {
  assert.equal(aggregateQa([]).citationPrecision, null);
  assert.equal(latencySummary([]).p95, null);
});

test("malformed benchmark and result data is rejected", () => {
  assert.throws(() => scoreRetrievalCase({}, []), /Malformed benchmark case/);
  assert.throws(() => scoreRetrievalCase(answerable(), [{ source: "a.pdf" }]), /Malformed search result list/);
  assert.throws(() => scoreQaCase(answerable(), { status: "BROKEN", citations: [] }), /Malformed QA result/);
  assert.throws(() => scoreQaCase(answerable(), { status: "ANSWERED", citations: [{ source: "a.pdf" }] }), /Malformed citation list/);
});
