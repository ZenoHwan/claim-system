import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { FollowUp } from '../api/types';
import { FollowUps } from '../core/follow-ups';
import { Session } from '../core/session';

/**
 * Pending follow-ups. A claimant sees the questions waiting for their reply. Staff see replies they
 * have not acted on yet, then the questions they sent that the claimant has not answered.
 */
@Component({
  selector: 'app-follow-ups-page',
  imports: [DatePipe, NgTemplateOutlet, RouterLink, MatButtonModule, MatCardModule],
  template: `
    <main class="page">
      <div class="page-head">
        <h1>Follow-ups</h1>
      </div>
      @if (session.isStaff()) {
        <h2>Replies to review ({{ followUps.replies().length }})</h2>
        <ng-container *ngTemplateOutlet="list; context: { $implicit: followUps.replies(), empty: 'No replies waiting for you.' }" />
        <h2>Waiting for the claimant ({{ followUps.waiting().length }})</h2>
        <ng-container *ngTemplateOutlet="list; context: { $implicit: followUps.waiting(), empty: 'No questions outstanding.' }" />
      } @else {
        <p class="muted">Questions from our claims team that need your reply.</p>
        <ng-container *ngTemplateOutlet="list; context: { $implicit: followUps.waiting(), empty: 'Nothing needs your reply right now.' }" />
      }

      <ng-template #list let-items let-empty="empty">
        <div class="stack">
          @for (item of asFollowUps(items); track item.requestId) {
            <mat-card appearance="outlined">
              <mat-card-header>
                <mat-card-title>
                  <a [routerLink]="[claimBase(), item.claimId]">{{ item.claimNumber }}</a>
                </mat-card-title>
                <mat-card-subtitle>
                  {{ item.requestedByName }} asked on {{ item.requestedAt | date: 'd MMM, HH:mm' }}
                </mat-card-subtitle>
              </mat-card-header>
              <mat-card-content>
                <p>{{ item.question }}</p>
                @if (item.status === 'ANSWERED') {
                  <p class="muted">Replied {{ item.answeredAt | date: 'd MMM, HH:mm' }}: {{ item.responseText }}</p>
                }
              </mat-card-content>
              <mat-card-actions>
                <a matButton="tonal" [routerLink]="[claimBase(), item.claimId]">
                  {{ session.isStaff() ? 'Open claim' : 'Reply' }}
                </a>
              </mat-card-actions>
            </mat-card>
          } @empty {
            <p class="muted">{{ empty }}</p>
          }
        </div>
      </ng-template>
    </main>
  `,
  styles: `
    h2 {
      font: var(--mat-sys-title-medium);
      margin: 24px 0 8px;
    }
  `,
})
export class FollowUpsPage {
  protected readonly session = inject(Session);
  protected readonly followUps = inject(FollowUps);

  constructor() {
    this.followUps.load();
  }

  protected claimBase() {
    return this.session.isStaff() ? '/admin/claims' : '/claims';
  }

  /** Template context values are untyped; this restores the type for the list. */
  protected asFollowUps(items: unknown) {
    return items as FollowUp[];
  }
}
