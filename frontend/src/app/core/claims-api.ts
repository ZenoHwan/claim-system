import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import {
  ClaimDetail,
  ClaimListItem,
  DocumentView,
  FollowUp,
  PaymentMethod,
  Policy,
  PolicyDetail,
  SubmitClaimRequest,
} from '../api/types';

/** Thin, typed calls to claims-service. No state. */
@Injectable({ providedIn: 'root' })
export class ClaimsApi {
  private readonly http = inject(HttpClient);

  policies() {
    return this.http.get<Policy[]>('/api/policies');
  }
  policy(id: string) {
    return this.http.get<PolicyDetail>(`/api/policies/${id}`);
  }
  /** Claimants get the questions waiting for their reply; staff get those they asked and the replies. */
  followUps(staff: boolean) {
    return this.http.get<FollowUp[]>(staff ? '/api/staff/claims/follow-ups' : '/api/claims/follow-ups');
  }
  submit(request: SubmitClaimRequest) {
    return this.http.post<ClaimDetail>('/api/claims', request);
  }
  myClaims() {
    return this.http.get<ClaimListItem[]>('/api/claims');
  }
  claim(id: string) {
    return this.http.get<ClaimDetail>(`/api/claims/${id}`);
  }

  respond(claimId: string, requestId: string, responseText: string) {
    return this.http.post<ClaimDetail>(
      `/api/claims/${claimId}/information-requests/${requestId}/response`,
      { responseText },
    );
  }

  upload(claimId: string, file: File, informationRequestId?: string) {
    const form = new FormData();
    form.append('file', file);
    if (informationRequestId) {
      form.append('informationRequestId', informationRequestId);
    }
    return this.http.post<DocumentView>(`/api/claims/${claimId}/documents`, form);
  }

  /** As a blob, because the download needs the bearer header a plain link cannot send. */
  documentContent(claimId: string, documentId: string) {
    return this.http.get(`/api/claims/${claimId}/documents/${documentId}/content`, {
      responseType: 'blob',
    });
  }

  unassigned() {
    return this.http.get<ClaimListItem[]>('/api/staff/claims/unassigned');
  }
  assignedToMe() {
    return this.http.get<ClaimListItem[]>('/api/staff/claims/mine');
  }
  pickUp(id: string) {
    return this.http.post<ClaimDetail>(`/api/staff/claims/${id}/assign`, {});
  }
  requestInformation(id: string, question: string) {
    return this.http.post<ClaimDetail>(`/api/staff/claims/${id}/information-requests`, {
      question,
    });
  }
  assess(id: string, findings: string, recommendedAmount: number) {
    return this.http.post<ClaimDetail>(`/api/staff/claims/${id}/assessment`, {
      findings,
      recommendedAmount,
    });
  }
  approve(id: string, approvedAmount: number) {
    return this.http.post<ClaimDetail>(`/api/staff/claims/${id}/decision`, {
      outcome: 'APPROVED',
      approvedAmount,
    });
  }
  reject(id: string, reason: string) {
    return this.http.post<ClaimDetail>(`/api/staff/claims/${id}/decision`, {
      outcome: 'REJECTED',
      reason,
    });
  }
  recordPayment(id: string, method: PaymentMethod, reference: string) {
    return this.http.post<ClaimDetail>(`/api/staff/claims/${id}/payment`, { method, reference });
  }
}

export const ALLOWED_DOCUMENT_TYPES = ['application/pdf', 'image/jpeg', 'image/png'];
export const MAX_DOCUMENT_BYTES = 10 * 1024 * 1024;

/** Client-side courtesy check; the server re-checks type (by content) and size. Returns a message or null. */
export function documentProblem(file: File): string | null {
  if (!ALLOWED_DOCUMENT_TYPES.includes(file.type)) {
    return `${file.name}: only PDF, JPEG and PNG files are accepted`;
  }
  return file.size > MAX_DOCUMENT_BYTES ? `${file.name}: larger than 10 MB` : null;
}
