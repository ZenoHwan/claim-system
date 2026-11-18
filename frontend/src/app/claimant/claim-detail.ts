import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { concat, last, of, switchMap, tap } from 'rxjs';
import { ClaimDetail } from '../api/types';
import { ClaimsApi, documentProblem } from '../core/claims-api';
import { problemMessage } from '../core/http';
import { Notifications } from '../core/notifications';
import { ClaimCore } from '../shared/claim-core';
import { StatusChip } from '../shared/status-chip';

/** A claimant's view of one claim, with the reply form when staff have asked for information. */
@Component({
  selector: 'app-claim-detail',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    ClaimCore,
    StatusChip,
  ],
  template: `
    <main class="page">
      @if (claim(); as c) {
        <div class="page-head">
          <div class="row">
            <a matButton routerLink="/claims">My claims</a>
            <h1>{{ c.claimNumber }}</h1>
            <app-status-chip [status]="c.status" />
          </div>
        </div>

        @if (openRequest(); as request) {
          <mat-card appearance="outlined" class="reply">
            <mat-card-header>
              <mat-card-title>More information needed</mat-card-title>
              <mat-card-subtitle
                >{{ request.requestedByName }} asked: {{ request.question }}</mat-card-subtitle
              >
            </mat-card-header>
            <mat-card-content class="stack">
              <mat-form-field>
                <mat-label>Your reply</mat-label>
                <textarea matInput rows="3" [formControl]="reply"></textarea>
              </mat-form-field>
              <input
                #picker
                type="file"
                multiple
                hidden
                accept="application/pdf,image/jpeg,image/png"
                (change)="addFiles(picker.files); picker.value = ''"
              />
              <div class="row">
                <button matButton="outlined" type="button" [disabled]="busy()" (click)="picker.click()">
                  Attach files
                </button>
              </div>
              @for (file of files(); track file) {
                <div class="row">
                  <mat-icon aria-hidden="true">description</mat-icon>
                  <span>{{ file.name }}</span>
                  <button matButton type="button" [disabled]="busy()" (click)="removeFile(file)">Remove</button>
                </div>
              }
              @if (error()) {
                <p class="error-text" role="alert">{{ error() }}</p>
              }
              <div class="row">
                <button
                  matButton="filled"
                  type="button"
                  [disabled]="reply.invalid || busy()"
                  (click)="send(request.id)"
                >
                  Send reply
                </button>
              </div>
            </mat-card-content>
          </mat-card>
        }

        <app-claim-core [claim]="c" />
      } @else if (error()) {
        <p class="error-text" role="alert">{{ error() }}</p>
      }
    </main>
  `,
  styles: `
    .reply {
      margin-bottom: 16px;
      border-color: var(--mat-sys-primary);
    }
  `,
})
export class ClaimDetailPage {
  private readonly api = inject(ClaimsApi);
  private readonly notifications = inject(Notifications);

  /** Bound from the route. */
  readonly id = input.required<string>();

  protected readonly claim = signal<ClaimDetail | null>(null);
  protected readonly openRequest = computed(() =>
    this.claim()?.informationRequests.find((r) => r.status === 'OPEN'),
  );
  protected readonly reply = new FormControl('', {
    nonNullable: true,
    validators: Validators.required,
  });
  protected readonly files = signal<File[]>([]);
  protected readonly error = signal('');
  protected readonly busy = signal(false);

  constructor() {
    effect(() => {
      const id = this.id();
      untracked(() => this.load(id));
    });
    // A live notification about this claim means it changed: show the new state without a refresh.
    effect(() => {
      if (this.notifications.lastPushed()?.claimId === this.id()) {
        untracked(() => this.load(this.id()));
      }
    });
  }

  private load(id: string) {
    this.api.claim(id).subscribe({
      next: (claim) => this.claim.set(claim),
      error: (error) => this.error.set(problemMessage(error)),
    });
  }

  protected addFiles(picked: FileList | null) {
    const problems = Array.from(picked ?? [])
      .map(documentProblem)
      .filter(Boolean);
    if (problems.length) {
      this.error.set(problems.join('; '));
      return;
    }
    this.error.set('');
    this.files.update((files) => [...files, ...Array.from(picked ?? [])]);
  }

  protected removeFile(file: File) {
    this.files.update((files) => files.filter((f) => f !== file));
  }

  protected send(requestId: string) {
    const claimId = this.id();
    this.busy.set(true);
    this.error.set('');
    // Files first, linked to the request, so they are already there when staff see the reply.
    // Each file leaves the list once it is uploaded, so a retry after a failure never sends it twice.
    concat(
      ...this.files().map((file) =>
        this.api.upload(claimId, file, requestId).pipe(tap(() => this.removeFile(file))),
      ),
      of(null),
    )
      .pipe(
        last(),
        switchMap(() => this.api.respond(claimId, requestId, this.reply.value)),
      )
      .subscribe({
        next: (claim) => {
          this.claim.set(claim);
          this.reply.reset();
          this.files.set([]);
          this.busy.set(false);
        },
        error: (error) => {
          this.busy.set(false);
          this.error.set(problemMessage(error));
        },
      });
  }
}
