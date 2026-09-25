import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

export interface KnowledgeDocument {
  id: string;
  title: string;
  embeddingModel: string;
  chunkCount: number;
  createdBy: string;
  createdAt: string;
}

export interface DocumentPage {
  items: KnowledgeDocument[];
  total: number;
  page: number;
  size: number;
}

export interface DocumentDetail {
  document: KnowledgeDocument;
  chunks: { ordinal: number; content: string }[];
}

export interface SearchHit {
  documentId: string;
  documentTitle: string;
  chunkOrdinal: number;
  content: string;
  similarity: number;
}

@Injectable({ providedIn: 'root' })
export class KnowledgeApiService {
  private readonly http = inject(HttpClient);

  list(authorization: string, page: number) {
    return this.http.get<DocumentPage>('/api/v1/documents', {
      headers: this.headers(authorization), params: { page, size: 10 }
    });
  }

  get(authorization: string, id: string) {
    return this.http.get<DocumentDetail>(`/api/v1/documents/${id}`, { headers: this.headers(authorization) });
  }

  ingest(authorization: string, title: string, content: string) {
    return this.http.post<KnowledgeDocument>('/api/v1/documents', { title, content }, {
      headers: this.headers(authorization)
    });
  }

  delete(authorization: string, id: string) {
    return this.http.delete<void>(`/api/v1/documents/${id}`, { headers: this.headers(authorization) });
  }

  search(authorization: string, query: string, topK: number) {
    return this.http.post<SearchHit[]>('/api/v1/knowledge/search', { query, topK }, {
      headers: this.headers(authorization)
    });
  }

  private headers(authorization: string) { return new HttpHeaders({ Authorization: authorization }); }
}
