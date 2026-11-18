import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { ClaimDetail, DocumentView } from '../api/types';
import { ClaimsApi } from '../core/claims-api';
import { statusLabel } from './status-chip';

/**
 * Everything about a claim that both a claimant and staff read: facts, incident details, documents,
 * questions and answers so far, decision, payment and the timeline. Staff-only figures (reserve,
 * assessment) appear only when the server sent them.
 */
@Component({
  selector: 'app-claim-core',
  imports: [CurrencyPipe, DatePipe, MatButtonModule, MatCardModule, MatIconModule],
  template: `
    @let c = claim();
    <div class="grid">
      <mat-card appearance="outlined">
        <mat-card-header><mat-card-title>Claim</mat-card-title></mat-card-header>
        <mat-card-content>
          <dl>
            <dt>Claimant</dt>
            <dd>{{ c.claimantName }}</dd>
            <dt>Policy</dt>
            <dd>{{ c.policyNumber }}</dd>
            <dt>Sum insured</dt>
            <dd class="amount">{{ c.sumInsured | currency: c.currency : 'code' }}</dd>
            <dt>Incident date</dt>
            <dd>{{ c.incidentDate | date: 'd MMM y' }}</dd>
            <dt>Claimed</dt>
            <dd class="amount">
              @if (c.claimedAmount != null) {
                {{ c.claimedAmount | currency: c.currency : 'code' }}
              } @else {
                Not stated
              }
            </dd>
            @if (c.currentReserve !== undefined) {
              <dt>Reserve</dt>
              <dd class="amount">{{ c.currentReserve | currency: c.currency : 'code' }}</dd>
            }
            <dt>Handled by</dt>
            <dd>{{ c.assignedOfficerName ?? 'Not yet assigned' }}</dd>
          </dl>
          <p class="description">{{ c.incidentDescription }}</p>
          @if (c.motor; as motor) {
            <dl>
              <dt>Vehicle</dt>
              <dd>{{ motor.vehicleRegistration }}</dd>
              <dt>Driver</dt>
              <dd>{{ motor.driverName }}</dd>
              <dt>Location</dt>
              <dd>{{ motor.accidentLocation }}</dd>
              <dt>Third party</dt>
              <dd>{{ motor.thirdPartyInvolved ? 'Yes' : 'No' }}</dd>
            </dl>
          }
          @if (c.property; as property) {
            <dl>
              <dt>Property</dt>
              <dd>{{ property.propertyAddress }}</dd>
              <dt>Cause of loss</dt>
              <dd>{{ property.lossCause }}</dd>
            </dl>
          }
        </mat-card-content>
      </mat-card>

      <mat-card appearance="outlined">
        <mat-card-header><mat-card-title>Timeline</mat-card-title></mat-card-header>
        <mat-card-content>
          <ol class="timeline">
            @for (entry of c.history.slice().reverse(); track entry.changedAt) {
              <li>
                <strong>{{ label(entry.toStatus) }}</strong>
                <span class="muted">
                  · {{ entry.changedAt | date: 'd MMM, HH:mm' }} · {{ entry.actorName }}</span
                >
                @if (entry.reason) {
                  <div>{{ entry.reason }}</div>
                }
              </li>
            }
          </ol>
        </mat-card-content>
      </mat-card>

      <mat-card appearance="outlined">
        <mat-card-header><mat-card-title>Documents</mat-card-title></mat-card-header>
        <mat-card-content>
          @for (document of c.documents; track document.id) {
            <div class="row document">
              <mat-icon aria-hidden="true">description</mat-icon>
              <span class="name">
                {{ document.fileName }}
                <span class="muted meta"
                  >{{ kilobytes(document.sizeBytes) }} ·
                  {{ document.uploadedAt | date: 'd MMM, HH:mm' }}</span
                >
              </span>
              <button matButton (click)="download(document)">Download</button>
            </div>
          } @empty {
            <p class="muted">No documents attached.</p>
          }
        </mat-card-content>
      </mat-card>

      @if (c.informationRequests.length > 0) {
        <mat-card appearance="outlined">
          <mat-card-header><mat-card-title>Information requests</mat-card-title></mat-card-header>
          <mat-card-content class="stack">
            @for (request of c.informationRequests; track request.id) {
              <div>
                <div>
                  <strong>{{ request.question }}</strong>
                </div>
                <div class="muted">
                  {{ request.requestedByName }} · {{ request.requestedAt | date: 'd MMM, HH:mm' }}
                </div>
                @if (request.status === 'ANSWERED') {
                  <div class="answer">{{ request.responseText }}</div>
                  <div class="muted">Answered {{ request.answeredAt | date: 'd MMM, HH:mm' }}</div>
                } @else {
                  <div class="muted">Waiting for the claimant</div>
                }
              </div>
            }
          </mat-card-content>
        </mat-card>
      }

      @if (c.assessment; as assessment) {
        <mat-card appearance="outlined">
          <mat-card-header><mat-card-title>Assessment</mat-card-title></mat-card-header>
          <mat-card-content>
            <p>{{ assessment.findings }}</p>
            <dl>
              <dt>Recommended</dt>
              <dd class="amount">
                {{ assessment.recommendedAmount | currency: c.currency : 'code' }}
              </dd>
              <dt>By</dt>
              <dd>
                {{ assessment.assessedByName }}, {{ assessment.assessedAt | date: 'd MMM, HH:mm' }}
              </dd>
            </dl>
          </mat-card-content>
        </mat-card>
      }

      @if (c.decision; as decision) {
        <mat-card appearance="outlined">
          <mat-card-header><mat-card-title>Decision</mat-card-title></mat-card-header>
          <mat-card-content>
            <dl>
              <dt>Outcome</dt>
              <dd>{{ decision.outcome === 'APPROVED' ? 'Approved' : 'Rejected' }}</dd>
              @if (decision.approvedAmount !== undefined) {
                <dt>Approved amount</dt>
                <dd class="amount">
                  {{ decision.approvedAmount | currency: c.currency : 'code' }}
                </dd>
              }
              @if (decision.reason) {
                <dt>Reason</dt>
                <dd>{{ decision.reason }}</dd>
              }
              <dt>Decided</dt>
              <dd>{{ decision.decidedAt | date: 'd MMM y, HH:mm' }}</dd>
              @if (c.payment; as payment) {
                <dt>Payment</dt>
                <dd>
                  {{ payment.status === 'PAID' ? 'Paid' : 'Requested' }}
                  @if (payment.paidAt) {
                    on {{ payment.paidAt | date: 'd MMM y' }}
                  }
                  to {{ payment.payeeName }}
                  @if (payment.reference) {
                    (ref {{ payment.reference }})
                  }
                </dd>
              }
            </dl>
          </mat-card-content>
        </mat-card>
      }
    </div>
  `,
  styles: `
    .grid {
      display: grid;
      gap: 16px;
      grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
      align-items: start;
    }
    dl {
      display: grid;
      grid-template-columns: max-content 1fr;
      gap: 4px 16px;
      margin: 8px 0;
    }
    dt {
      color: var(--mat-sys-on-surface-variant);
    }
    dd {
      margin: 0;
    }
    .description,
    .answer {
      white-space: pre-wrap;
    }
    .timeline {
      list-style: none;
      margin: 8px 0 0;
      padding: 0 0 0 12px;
      border-left: 2px solid var(--mat-sys-outline-variant);
    }
    .timeline li {
      padding: 0 0 12px 8px;
    }
    .document {
      flex-wrap: nowrap;
    }
    .document .name {
      flex: 1;
      min-width: 0;
      overflow-wrap: anywhere;
    }
    .document .meta {
      display: block;
      font: var(--mat-sys-body-small);
    }
  `,
})
export class ClaimCore {
  private readonly api = inject(ClaimsApi);

  readonly claim = input.required<ClaimDetail>();
  protected readonly label = statusLabel;

  protected kilobytes(bytes: number) {
    return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  }

  protected download(document: DocumentView) {
    this.api.documentContent(this.claim().id, document.id).subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      const link = window.document.createElement('a');
      link.href = url;
      link.download = document.fileName;
      link.click();
      URL.revokeObjectURL(url);
    });
  }
}
