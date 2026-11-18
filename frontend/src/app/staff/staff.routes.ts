import { Routes } from '@angular/router';
import { managerOnly } from '../core/http';
import { Shell } from '../shared/shell';
import { Dashboard } from './dashboard';
import { MyClaims, Queue } from './lists';
import { Workbench } from './workbench';

export default [
  {
    path: '',
    component: Shell,
    children: [
      { path: 'queue', component: Queue, title: 'Queue - Claims Admin' },
      { path: 'my-claims', component: MyClaims, title: 'My claims - Claims Admin' },
      { path: 'claims/:id', component: Workbench, title: 'Claim - Claims Admin' },
      {
        path: 'dashboard',
        component: Dashboard,
        canActivate: [managerOnly],
        title: 'Dashboard - Claims Admin',
      },
      { path: '', pathMatch: 'full', redirectTo: 'queue' },
    ],
  },
] satisfies Routes;
