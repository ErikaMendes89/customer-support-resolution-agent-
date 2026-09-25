import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

export type CaseStatus = 'OPEN' | 'IN_PROGRESS' | 'NEEDS_INFORMATION' | 'RESOLVED' | 'CLOSED';

export interface SupportCase {
  id: string;
  title: string;
  description: string;
  status: CaseStatus;
  allowedTransitions: CaseStatus[];
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface CaseEvent {
  id: number;
  eventType: 'CREATED' | 'STATUS_CHANGED';
  fromStatus: CaseStatus | null;
  toStatus: CaseStatus;
  note: string | null;
  actor: string;
  occurredAt: string;
}

export interface CaseDetail {
  supportCase: SupportCase;
  events: CaseEvent[];
}

export interface CasePage {
  items: SupportCase[];
  total: number;
  page: number;
  size: number;
}

export interface ProposalSource {
  key: string;
  documentId: string;
  documentTitle: string;
  chunkOrdinal: number;
  content: string;
  similarity: number;
}

export interface ResolutionProposal {
  id: string;
  caseId: string;
  status: 'READY_FOR_REVIEW' | 'INSUFFICIENT_EVIDENCE';
  answer: string;
  generationModel: string;
  embeddingModel: string;
  createdBy: string;
  createdAt: string;
  sources: ProposalSource[];
}

@Injectable({ providedIn: 'root' })
export class CaseApiService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/v1/cases';

  list(authorization: string, page: number) {
    return this.http.get<CasePage>(this.url, {
      headers: this.headers(authorization), params: { page, size: 10 }
    });
  }

  get(authorization: string, id: string) {
    return this.http.get<CaseDetail>(`${this.url}/${id}`, { headers: this.headers(authorization) });
  }

  create(authorization: string, title: string, description: string) {
    return this.http.post<SupportCase>(this.url, { title, description }, {
      headers: this.headers(authorization)
    });
  }

  changeStatus(authorization: string, id: string, status: CaseStatus, note: string) {
    return this.http.patch<CaseDetail>(`${this.url}/${id}/status`, { status, note }, {
      headers: this.headers(authorization)
    });
  }

  proposals(authorization: string, caseId: string) {
    return this.http.get<ResolutionProposal[]>(`${this.url}/${caseId}/proposals`, {
      headers: this.headers(authorization)
    });
  }

  generateProposal(authorization: string, caseId: string) {
    return this.http.post<ResolutionProposal>(`${this.url}/${caseId}/proposals`, {}, {
      headers: this.headers(authorization)
    });
  }

  private headers(authorization: string) {
    return new HttpHeaders({ Authorization: authorization });
  }
}
