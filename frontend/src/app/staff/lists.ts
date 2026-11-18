import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { ClaimListItem } from '../api/types';
import { ClaimsApi } from '../core/claims-api';
import { problemMessage } from '../core/http';
import { Session } from '../core/session';
import { ClaimTable } from '../shared/claim-table';

/** Unassigned claims in the staff member's market, oldest first. Picking one up opens its workbench. */
@Component({
  selector: 'app-queue',
  imports: [MatButtonModule, ClaimTable],
  template: `
    <main class="page">
      <div class="page-head">
        <h1>Unassigned claims · {{ session.current()?.market }}</h1>
        <button matButton (click)="load()">Refresh</button>
      </div>
      @if (message()) {
        <p class="error-text" role="alert">{{ message() }}</p>
      }
      <app-claim-table
        [claims]="claims()"
        [columns]="columns"
        linkBase="/admin/claims"
        (pickUp)="pickUp($event)"
        emptyText="Nothing waiting to be picked up."
      />
    </main>
  `,
})
export class Queue {
  private readonly api = inject(ClaimsApi);
  private readonly router = inject(Router);
  protected readonly session = inject(Session);

  protected readonly claims = signal<ClaimListItem[]>([]);
  protected readonly message = signal('');
  protected readonly columns = [
    'claimNumber',
    'line',
    'claimant',
    'claimed',
    'submitted',
    'action',
  ];

  constructor() {
    this.load();
  }

  protected load() {
    this.api.unassigned().subscribe((claims) => this.claims.set(claims));
  }

  protected pickUp(claim: ClaimListItem) {
    this.message.set('');
    this.api.pickUp(claim.id).subscribe({
      next: () => void this.router.navigate(['/admin/claims', claim.id]),
      error: (error: HttpErrorResponse) => {
        // 409: another officer picked it up between this list loading and the click.
        this.message.set(
          error.status === 409
            ? `${claim.claimNumber} was already picked up by someone else.`
            : problemMessage(error),
        );
        this.load();
      },
    });
  }
}

@Component({
  selector: 'app-my-claims',
  imports: [ClaimTable],
  template: `
    <main class="page">
      <div class="page-head"><h1>My claims</h1></div>
      <app-claim-table
        [claims]="claims()"
        [columns]="columns"
        linkBase="/admin/claims"
        emptyText="No claims are assigned to you."
      />
    </main>
  `,
})
export class MyClaims {
  protected readonly claims = signal<ClaimListItem[]>([]);
  protected readonly columns = ['claimNumber', 'line', 'claimant', 'claimed', 'status', 'updated'];

  constructor() {
    inject(ClaimsApi)
      .assignedToMe()
      .subscribe((claims) => this.claims.set(claims));
  }
}
