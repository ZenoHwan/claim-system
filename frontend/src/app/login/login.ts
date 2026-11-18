import { Component, computed, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Door } from '../api/types';
import { Session } from '../core/session';

/** One component, two screens: /login for claimants and /admin/login for staff. The server enforces the door. */
@Component({
  selector: 'app-login',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  template: `
    <main class="door" [class.staff]="staff()">
      <mat-card appearance="outlined">
        <mat-card-header>
          <mat-card-title>{{ staff() ? 'Claims Admin' : 'Claims Portal' }}</mat-card-title>
          <mat-card-subtitle>{{
            staff() ? 'Staff sign in' : 'Sign in to report and track your claims'
          }}</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <form class="stack" [formGroup]="form" (ngSubmit)="signIn()">
            <mat-form-field>
              <mat-label>Email</mat-label>
              <input matInput type="email" formControlName="email" autocomplete="username" />
            </mat-form-field>
            <mat-form-field>
              <mat-label>Password</mat-label>
              <input
                matInput
                type="password"
                formControlName="password"
                autocomplete="current-password"
              />
            </mat-form-field>
            @if (error()) {
              <p class="error-text" role="alert">{{ error() }}</p>
            }
            <button matButton="filled" type="submit" [disabled]="form.invalid || busy()">
              Sign in
            </button>
          </form>
        </mat-card-content>
        <mat-card-actions>
          @if (staff()) {
            <a matButton routerLink="/login">Not staff? Claimant sign in</a>
          } @else {
            <a matButton routerLink="/admin/login">Staff sign in</a>
          }
        </mat-card-actions>
      </mat-card>
    </main>
  `,
  styles: `
    .door {
      min-height: 100vh;
      display: grid;
      place-items: center;
      padding: 16px;
      background: var(--mat-sys-surface-container-low);
    }
    .door.staff {
      background: var(--mat-sys-surface-container-highest);
    }
    mat-card {
      width: min(420px, 100%);
    }
    mat-card-content {
      padding-top: 16px;
    }
  `,
})
export class Login {
  private readonly session = inject(Session);
  private readonly router = inject(Router);

  /** Bound from route data. */
  readonly door = input.required<Door>();
  protected readonly staff = computed(() => this.door() === 'STAFF');

  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });
  protected readonly error = signal('');
  protected readonly busy = signal(false);

  protected signIn() {
    const { email, password } = this.form.getRawValue();
    this.busy.set(true);
    this.error.set('');
    this.session.signIn(email, password, this.door()).subscribe({
      next: () => void this.router.navigateByUrl(this.staff() ? '/admin/queue' : '/claims'),
      error: () => {
        this.busy.set(false);
        // The server answers every failure the same way, including a valid account at the wrong door.
        this.error.set('Email or password is incorrect for this sign-in page.');
      },
    });
  }
}
