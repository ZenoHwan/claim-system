import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Observable } from 'rxjs';
import { ClaimDetail, PaymentMethod } from '../api/types';
import { ClaimsApi } from '../core/claims-api';
import { problemMessage } from '../core/http';
import { Notifications } from '../core/notifications';
import { Session } from '../core/session';
import { ClaimCore } from '../shared/claim-core';
import { StatusChip } from '../shared/status-chip';

/**
 * Where staff work a claim. Only the action that the claim's status allows is shown, and only to the
 * staff member it is assigned to; the server enforces both again.
 */
@Component({
  selector: 'app-workbench',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    ClaimCore,
    StatusChip,
  ],
  template: `
    <main class="page">
      @if (claim(); as c) {
        <div class="page-head">
          <div class="row">
            <a matButton routerLink="/admin/my-claims">My claims</a>
            <h1>{{ c.claimNumber }}</h1>
            <span class="muted"
              >{{ c.lineOfBusiness === 'MOTOR' ? 'Motor' : 'Property' }} · {{ c.marketCode }}</span
            >
            <app-status-chip [status]="c.status" />
          </div>
        </div>

        @if (error()) {
          <p class="error-text" role="alert">{{ error() }}</p>
        }

        @if (!c.assignedOfficerId) {
          <div class="row">
            <p class="muted">Not picked up yet. You can read it here, or take it on.</p>
            <button matButton="filled" type="button" [disabled]="busy()" (click)="pickUp()">
              Pick up
            </button>
          </div>
        } @else if (!mine()) {
          <p class="muted">Assigned to {{ c.assignedOfficerName }}. Read only.</p>
        } @else if (c.status === 'UNDER_REVIEW') {
          <div class="actions">
            <mat-card appearance="outlined">
              <mat-card-header
                ><mat-card-title>Request information</mat-card-title></mat-card-header
              >
              <mat-card-content class="stack" [formGroup]="request">
                <mat-form-field>
                  <mat-label>Question for the claimant</mat-label>
                  <textarea matInput rows="3" formControlName="question"></textarea>
                </mat-form-field>
                <div class="row">
                  <button
                    matButton="outlined"
                    type="button"
                    [disabled]="request.invalid || busy()"
                    (click)="requestInformation()"
                  >
                    Send request
                  </button>
                </div>
              </mat-card-content>
            </mat-card>
            <mat-card appearance="outlined">
              <mat-card-header><mat-card-title>Assessment</mat-card-title></mat-card-header>
              <mat-card-content class="stack" [formGroup]="assessment">
                <mat-form-field>
                  <mat-label>Findings</mat-label>
                  <textarea matInput rows="3" formControlName="findings"></textarea>
                </mat-form-field>
                <mat-form-field>
                  <mat-label>Recommended amount ({{ c.currency }})</mat-label>
                  <input
                    matInput
                    type="number"
                    min="0"
                    step="0.01"
                    formControlName="recommendedAmount"
                  />
                </mat-form-field>
                <div class="row">
                  <button
                    matButton="filled"
                    type="button"
                    [disabled]="assessment.invalid || busy()"
                    (click)="assess()"
                  >
                    Save assessment
                  </button>
                </div>
              </mat-card-content>
            </mat-card>
          </div>
        } @else if (c.status === 'INFO_REQUESTED') {
          <p class="muted">Waiting for the claimant to respond. This page updates when they do.</p>
        } @else if (c.status === 'ASSESSED') {
          <div class="actions">
            <mat-card appearance="outlined">
              <mat-card-header><mat-card-title>Approve</mat-card-title></mat-card-header>
              <mat-card-content class="stack" [formGroup]="approval">
                <mat-form-field>
                  <mat-label>Approved amount ({{ c.currency }})</mat-label>
                  <input
                    matInput
                    type="number"
                    min="0.01"
                    step="0.01"
                    formControlName="approvedAmount"
                  />
                  <mat-hint>Sum insured {{ c.sumInsured }} {{ c.currency }}</mat-hint>
                </mat-form-field>
                <div class="row">
                  <button
                    matButton="filled"
                    type="button"
                    [disabled]="approval.invalid || busy()"
                    (click)="approve()"
                  >
                    Approve
                  </button>
                </div>
              </mat-card-content>
            </mat-card>
            <mat-card appearance="outlined">
              <mat-card-header><mat-card-title>Reject</mat-card-title></mat-card-header>
              <mat-card-content class="stack" [formGroup]="rejection">
                <mat-form-field>
                  <mat-label>Reason (shown to the claimant)</mat-label>
                  <textarea matInput rows="3" formControlName="reason"></textarea>
                </mat-form-field>
                <div class="row">
                  <button
                    matButton="outlined"
                    type="button"
                    [disabled]="rejection.invalid || busy()"
                    (click)="reject()"
                  >
                    Reject
                  </button>
                </div>
              </mat-card-content>
            </mat-card>
          </div>
        } @else if (c.status === 'APPROVED') {
          <div class="actions">
            <mat-card appearance="outlined">
              <mat-card-header>
                <mat-card-title>Record payment</mat-card-title>
                <mat-card-subtitle
                  >{{ c.payment?.amount }} {{ c.currency }} to
                  {{ c.payment?.payeeName }}</mat-card-subtitle
                >
              </mat-card-header>
              <mat-card-content class="stack" [formGroup]="payment">
                <mat-form-field>
                  <mat-label>Method</mat-label>
                  <mat-select formControlName="method">
                    <mat-option value="BANK_TRANSFER">Bank transfer</mat-option>
                    <mat-option value="CHEQUE">Cheque</mat-option>
                  </mat-select>
                </mat-form-field>
                <mat-form-field>
                  <mat-label>Payment reference</mat-label>
                  <input matInput formControlName="reference" />
                </mat-form-field>
                <div class="row">
                  <button
                    matButton="filled"
                    type="button"
                    [disabled]="payment.invalid || busy()"
                    (click)="recordPayment()"
                  >
                    Record payment and settle
                  </button>
                </div>
              </mat-card-content>
            </mat-card>
          </div>
        }

        <app-claim-core [claim]="c" />
      } @else if (error()) {
        <p class="error-text" role="alert">{{ error() }}</p>
      }
    </main>
  `,
  styles: `
    .actions {
      display: grid;
      gap: 16px;
      grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
      align-items: start;
      margin-bottom: 16px;
    }
    mat-card-content {
      padding-top: 8px;
    }
  `,
})
export class Workbench {
  private readonly api = inject(ClaimsApi);
  private readonly session = inject(Session);
  private readonly notifications = inject(Notifications);
  private readonly fb = inject(FormBuilder).nonNullable;

  /** Bound from the route. */
  readonly id = input.required<string>();

  protected readonly claim = signal<ClaimDetail | null>(null);
  protected readonly mine = computed(
    () => this.claim()?.assignedOfficerId === this.session.current()?.userId,
  );
  protected readonly error = signal('');
  protected readonly busy = signal(false);

  protected readonly request = this.fb.group({ question: ['', Validators.required] });
  protected readonly assessment = this.fb.group({
    findings: ['', Validators.required],
    recommendedAmount: [null as number | null, [Validators.required, Validators.min(0)]],
  });
  protected readonly approval = this.fb.group({
    approvedAmount: [null as number | null, [Validators.required, Validators.min(0.01)]],
  });
  protected readonly rejection = this.fb.group({ reason: ['', Validators.required] });
  protected readonly payment = this.fb.group({
    method: ['BANK_TRANSFER' as PaymentMethod, Validators.required],
    reference: ['', Validators.required],
  });

  constructor() {
    effect(() => {
      const id = this.id();
      untracked(() => this.load(id));
    });
    // The claimant responded (or anything else changed this claim): reload without a refresh.
    effect(() => {
      if (this.notifications.lastPushed()?.claimId === this.id()) {
        untracked(() => this.load(this.id()));
      }
    });
  }

  private load(id: string) {
    this.api.claim(id).subscribe({
      next: (claim) => this.show(claim),
      error: (error) => this.error.set(problemMessage(error)),
    });
  }

  private show(claim: ClaimDetail) {
    this.claim.set(claim);
    // Sensible starting point: approve what the assessment recommended.
    if (claim.status === 'ASSESSED' && this.approval.pristine) {
      this.approval.setValue({ approvedAmount: claim.assessment?.recommendedAmount ?? null });
    }
  }

  /** If a colleague got there first the server answers 409; the message is shown and the claim reloads read-only. */
  protected pickUp() {
    this.run(this.api.pickUp(this.id()));
  }

  protected requestInformation() {
    this.run(this.api.requestInformation(this.id(), this.request.getRawValue().question), () =>
      this.request.reset(),
    );
  }

  protected assess() {
    const { findings, recommendedAmount } = this.assessment.getRawValue();
    this.run(this.api.assess(this.id(), findings, recommendedAmount!));
  }

  protected approve() {
    this.run(this.api.approve(this.id(), this.approval.getRawValue().approvedAmount!));
  }

  protected reject() {
    this.run(this.api.reject(this.id(), this.rejection.getRawValue().reason));
  }

  protected recordPayment() {
    const { method, reference } = this.payment.getRawValue();
    this.run(this.api.recordPayment(this.id(), method, reference));
  }

  /** Every action returns the updated claim; a refusal (409, 400, 403) is shown and the claim reloaded. */
  private run(action: Observable<ClaimDetail>, onSuccess?: () => void) {
    this.busy.set(true);
    this.error.set('');
    action.subscribe({
      next: (claim) => {
        this.busy.set(false);
        onSuccess?.();
        this.show(claim);
      },
      error: (error) => {
        this.busy.set(false);
        this.error.set(problemMessage(error));
        this.load(this.id());
      },
    });
  }
}
