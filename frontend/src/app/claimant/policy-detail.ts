import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { PolicyDetail } from '../api/types';
import { ClaimsApi } from '../core/claims-api';
import { problemMessage } from '../core/http';
import { ClaimTable } from '../shared/claim-table';

/** One of the claimant's policies: what it covers, for how long, and every claim made on it. */
@Component({
  selector: 'app-policy-detail',
  imports: [CurrencyPipe, DatePipe, RouterLink, MatButtonModule, MatCardModule, ClaimTable],
  template: `
    <main class="page">
      @if (policy(); as p) {
        <div class="page-head">
          <div class="row">
            <a matButton routerLink="/claims/policies">My policies</a>
            <h1>{{ p.policyNumber }}</h1>
          </div>
          @if (!openClaim()) {
            <a matButton="filled" routerLink="/claims/new" [queryParams]="{ policy: p.id }">Make a claim</a>
          }
        </div>

        <mat-card appearance="outlined">
          <mat-card-header><mat-card-title>Policy details</mat-card-title></mat-card-header>
          <mat-card-content>
            <dl>
              <dt>Type</dt>
              <dd>{{ p.lineOfBusiness === 'MOTOR' ? 'Motor' : 'Property' }}</dd>
              <dt>Status</dt>
              <dd>{{ active() ? 'Active' : 'Not in force' }}</dd>
              <dt>Cover</dt>
              <dd>{{ p.effectiveFrom | date: 'd MMM y' }} to {{ p.effectiveTo | date: 'd MMM y' }}</dd>
              <dt>Sum insured</dt>
              <dd>{{ p.sumInsured | currency: p.currency : 'code' }}</dd>
              <dt>Market</dt>
              <dd>{{ p.marketCode === 'MY' ? 'Malaysia' : p.marketCode === 'SG' ? 'Singapore' : p.marketCode }}</dd>
              <dt>Claims made</dt>
              <dd>{{ p.claims.length }}</dd>
            </dl>
          </mat-card-content>
        </mat-card>

        <h2>Claims on this policy</h2>
        <app-claim-table
          [claims]="p.claims"
          [columns]="columns"
          linkBase="/claims"
          emptyText="No claims have been made on this policy."
        />
      } @else if (error()) {
        <p class="error-text" role="alert">{{ error() }}</p>
      }
    </main>
  `,
  styles: `
    h1 {
      margin: 0;
    }
    h2 {
      font: var(--mat-sys-title-medium);
      margin: 24px 0 8px;
    }
    dl {
      display: grid;
      grid-template-columns: max-content 1fr;
      gap: 4px 16px;
    }
    dt {
      color: var(--mat-sys-on-surface-variant);
    }
    dd {
      margin: 0;
    }
  `,
})
export class PolicyDetailPage {
  private readonly api = inject(ClaimsApi);

  /** Bound from the route. */
  readonly id = input.required<string>();

  protected readonly policy = signal<PolicyDetail | null>(null);
  protected readonly error = signal('');
  protected readonly columns = ['claimNumber', 'incidentDate', 'claimed', 'status', 'updated'];
  /** One claim at a time per policy, so a new one is offered only when none is in progress. */
  protected readonly openClaim = computed(() =>
    this.policy()?.claims.some((c) => c.status !== 'SETTLED' && c.status !== 'REJECTED'),
  );
  protected readonly active = computed(() => {
    const p = this.policy();
    const today = new Date().toISOString().slice(0, 10);
    return !!p && p.effectiveFrom <= today && today <= p.effectiveTo;
  });

  constructor() {
    effect(() => {
      const id = this.id();
      untracked(() =>
        this.api.policy(id).subscribe({
          next: (policy) => this.policy.set(policy),
          error: (error) => this.error.set(problemMessage(error)),
        }),
      );
    });
  }
}
