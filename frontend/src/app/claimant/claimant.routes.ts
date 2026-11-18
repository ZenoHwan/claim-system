import { Routes } from '@angular/router';
import { Shell } from '../shared/shell';
import { ClaimDetailPage } from './claim-detail';
import { ClaimsList } from './claims-list';
import { NewClaim } from './new-claim';

export default [
  {
    path: '',
    component: Shell,
    children: [
      { path: '', component: ClaimsList, title: 'My claims - Claims Portal' },
      { path: 'new', component: NewClaim, title: 'New claim - Claims Portal' },
      { path: ':id', component: ClaimDetailPage, title: 'Claim - Claims Portal' },
    ],
  },
] satisfies Routes;
