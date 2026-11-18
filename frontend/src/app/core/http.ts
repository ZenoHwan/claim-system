import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AbstractControl } from '@angular/forms';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { Session } from './session';

/** Adds the bearer token; any 401 (expired or invalid token) signs the user out. */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const session = inject(Session);
  const token = session.current()?.token;
  const authorised = token
    ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : request;
  return next(authorised).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && !request.url.endsWith('/api/auth/login')) {
        session.signOut();
      }
      return throwError(() => error);
    }),
  );
};

/** The server enforces the same rules; these guards only keep people out of screens that would be empty. */
export const claimantOnly: CanActivateFn = () => {
  const session = inject(Session);
  return session.current()?.role === 'CLAIMANT' || inject(Router).parseUrl('/login');
};

export const staffOnly: CanActivateFn = () => {
  const session = inject(Session);
  return session.isStaff() || inject(Router).parseUrl('/admin/login');
};

export const managerOnly: CanActivateFn = () => {
  const session = inject(Session);
  return session.isManager() || inject(Router).parseUrl('/admin/queue');
};

interface Problem {
  detail?: string;
  errors?: { field: string; message: string }[];
}

/** One readable sentence from a problem-details error response. */
export function problemMessage(error: unknown): string {
  const status = (error as HttpErrorResponse)?.status;
  // 0: no connection; 502-504: the service behind the proxy is down or restarting
  if (status === 0 || status === 502 || status === 503 || status === 504) {
    return 'The service is not reachable right now. Please wait a moment and try again.';
  }
  const problem = (error as HttpErrorResponse)?.error as Problem | undefined;
  const fields = problem?.errors?.map((e) => `${e.field}: ${e.message}`).join('; ');
  return fields || problem?.detail || 'Something went wrong. Please try again.';
}

/** Puts each per-field server message on the matching form control (dotted paths reach nested groups). */
export function applyFieldErrors(form: AbstractControl, error: unknown): void {
  const problem = (error as HttpErrorResponse)?.error as Problem | undefined;
  for (const { field, message } of problem?.errors ?? []) {
    form.get(field)?.setErrors({ server: message });
  }
}
