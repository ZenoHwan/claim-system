import { Routes } from '@angular/router';
import { FollowUpsPage } from '../shared/follow-ups-page';
import { Shell } from '../shared/shell';
import { PolicyDetailPage } from './policy-detail';
import { ClaimDetailPage } from './claim-detail';
import { ClaimsList } from './claims-list';
import { MyPolicies } from './my-policies';
import { NewClaim } from './new-claim';

export default [
  {
    path: '',
    component: Shell,
    children: [
      { path: '', component: ClaimsList, title: 'My claims - Claims Portal' },
      { path: 'new', component: NewClaim, title: 'New claim - Claims Portal' },
      { path: 'policies', component: MyPolicies, title: 'My policies - Claims Portal' },
      { path: 'policies/:id', component: PolicyDetailPage, title: 'Policy - Claims Portal' },
      { path: 'follow-ups', component: FollowUpsPage, title: 'Follow-ups - Claims Portal' },
      { path: ':id', component: ClaimDetailPage, title: 'Claim - Claims Portal' },
    ],
  },
] satisfies Routes;
