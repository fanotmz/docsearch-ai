import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  DocumentSummary,
  DocumentUploadResponse,
  GroundedAnswerResponse,
  SemanticSearchResponse,
  SystemInfo,
} from './api.models';

@Injectable({ providedIn: 'root' })
export class DocsearchApiService {
  private readonly http = inject(HttpClient);

  getSystem(): Observable<SystemInfo> {
    return this.http.get<SystemInfo>('/api/v1/system');
  }

  listDocuments(): Observable<DocumentSummary[]> {
    return this.http.get<DocumentSummary[]>('/api/v1/documents');
  }

  uploadDocument(file: File): Observable<DocumentUploadResponse> {
    const formData = new FormData();
    formData.append('file', file, file.name);
    return this.http.post<DocumentUploadResponse>('/api/v1/documents', formData);
  }

  search(query: string, topK: number): Observable<SemanticSearchResponse> {
    return this.http.post<SemanticSearchResponse>('/api/v1/search', { query, topK });
  }

  answer(question: string): Observable<GroundedAnswerResponse> {
    return this.http.post<GroundedAnswerResponse>('/api/v1/qa', { question });
  }
}
