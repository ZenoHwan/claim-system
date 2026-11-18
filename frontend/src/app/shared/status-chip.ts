import { Component, computed, input } from '@angular/core';
import { ClaimStatus } from '../api/types';

const LABELS: Record<ClaimStatus, string> = {
  SUBMITTED: 'Submitted',
  UNDER_REVIEW: 'Under review',
  INFO_REQUESTED: 'Information requested',
  ASSESSED: 'Assessed',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  SETTLED: 'Settled',
};

export function statusLabel(status: ClaimStatus | string | undefined): string {
  return LABELS[status as ClaimStatus] ?? status ?? '';
}

@Component({
  selector: 'app-status-chip',
  template: `<span class="chip" [attr.data-status]="status()">{{ label() }}</span>`,
  styles: `
    .chip {
      display: inline-block;
      padding: 2px 10px;
      border-radius: 999px;
      font: var(--mat-sys-label-medium);
      white-space: nowrap;
      background: var(--mat-sys-surface-container-highest);
      color: var(--mat-sys-on-surface);
    }
    .chip[data-status='INFO_REQUESTED'] {
      background: #fff3cd;
      color: #5c4400;
    }
    .chip[data-status='APPROVED'],
    .chip[data-status='SETTLED'] {
      background: #d7f0df;
      color: #0f5132;
    }
    .chip[data-status='REJECTED'] {
      background: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
    }
  `,
})
export class StatusChip {
  readonly status = input.required<ClaimStatus>();
  protected readonly label = computed(() => statusLabel(this.status()));
}
