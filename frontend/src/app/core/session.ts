import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs';
import { Door, LoginResponse } from '../api/types';

const STORAGE_KEY = 'claims.session';

/** Who is signed in. Kept in sessionStorage so each browser tab can hold a different user. */
@Injectable({ providedIn: 'root' })
export class Session {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  readonly current = signal<LoginResponse | null>(
    JSON.parse(sessionStorage.getItem(STORAGE_KEY) ?? 'null'),
  );
  readonly isStaff = computed(() => !!this.current() && this.current()!.role !== 'CLAIMANT');
  readonly isManager = computed(() => this.current()?.role === 'MANAGER');

  signIn(email: string, password: string, door: Door) {
    return this.http.post<LoginResponse>('/api/auth/login', { email, password, door }).pipe(
      tap((session) => {
        sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
        this.current.set(session);
      }),
    );
  }

  /** Clears the session and returns the user to the door they came in through. */
  signOut() {
    if (!this.current()) {
      return; // several requests can fail with 401 at once; only the first one signs out
    }
    const door = this.isStaff() ? '/admin/login' : '/login';
    sessionStorage.removeItem(STORAGE_KEY);
    this.current.set(null);
    void this.router.navigateByUrl(door);
  }
}
