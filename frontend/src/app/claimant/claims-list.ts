import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { ClaimListItem } from '../api/types';
import { ClaimsApi } from '../core/claims-api';
import { ClaimTable } from '../shared/claim-table';

@Component({
  selector: 'app-claims-list',
  imports: [RouterLink, MatButtonModule, ClaimTable],
  template: `
    <main class="page">
      <div class="page-head">
        <h1>My claims</h1>
        <a matButton="filled" routerLink="/claims/new">New claim</a>
      </div>
      <app-claim-table
        [claims]="claims()"
        [columns]="columns"
        linkBase="/claims"
        emptyText="You have not reported any claims yet."
      />
    </main>
  `,
})
export class ClaimsList {
  protected readonly claims = signal<ClaimListItem[]>([]);
  protected readonly columns = [
    'claimNumber',
    'line',
    'incidentDate',
    'claimed',
    'status',
    'updated',
  ];

  constructor() {
    inject(ClaimsApi)
      .myClaims()
      .subscribe((claims) => this.claims.set(claims));
  }
}
