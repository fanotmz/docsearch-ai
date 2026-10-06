export type ConnectionState = 'loading' | 'online' | 'offline';
export type DocumentStatus = 'PROCESSING' | 'READY' | 'FAILED';

export interface SystemInfo {
  name: string;
  phase: string;
  documentSearchAvailable: boolean;
}

export interface DocumentSummary {
  documentId: string;
  filename: string;
  pageCount: number;
  status: DocumentStatus;
  createdAt: string;
}

export interface DocumentUploadResponse {
  documentId: string;
  filename: string;
  pageCount: number;
  status: DocumentStatus;
}

export interface SemanticSearchResult {
  documentId: string;
  source: string;
  pageNumber: number;
  chunkIndex: number;
  content: string;
  score: number | null;
}

export interface SemanticSearchResponse {
  query: string;
  results: SemanticSearchResult[];
}

export type QaStatus = 'ANSWERED' | 'INSUFFICIENT_EVIDENCE';

export interface GroundedCitation {
  id: string;
  documentId: string;
  source: string;
  pageNumber: number;
  chunkIndex: number;
}

export interface GroundedAnswerResponse {
  question: string;
  status: QaStatus;
  answer: string;
  citations: GroundedCitation[];
}
