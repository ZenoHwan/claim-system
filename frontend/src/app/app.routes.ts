import { Routes } from '@angular/router';
import { claimantOnly, staffOnly } from './core/http';

// Two doors into one application. Each area is lazy-loaded, so a claimant never downloads the staff screens.
export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./login/login').then((m) => m.Login),
    data: { door: 'CLAIMANT' },
    title: 'Sign in - Claims Portal',
  },
  {
    path: 'admin/login',
    loadComponent: () => import('./login/login').then((m) => m.Login),
    data: { door: 'STAFF' },
    title: 'Staff sign in - Claims Admin',
  },
  {
    path: 'claims',
    canActivate: [claimantOnly],
    loadChildren: () => import('./claimant/claimant.routes'),
  },
  { path: 'admin', canActivate: [staffOnly], loadChildren: () => import('./staff/staff.routes') },
  { path: '', pathMatch: 'full', redirectTo: 'claims' },
  { path: '**', redirectTo: 'claims' },
];
