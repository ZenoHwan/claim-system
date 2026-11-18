import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { Policy } from '../api/types';
import { ClaimsApi } from '../core/claims-api';

/** The claimant's policies, with the claim in progress on each and a shortcut to report a new one. */
@Component({
  selector: 'app-my-policies',
  imports: [CurrencyPipe, DatePipe, RouterLink, MatButtonModule, MatTableModule],
  template: `
    <main class="page">
      <div class="page-head">
        <h1>My policies</h1>
      </div>
      @if (policies().length === 0) {
        <p class="muted">You have no policies with us.</p>
      } @else {
        <div class="table-wrap">
          <table mat-table [dataSource]="policies()">
            <ng-container matColumnDef="policyNumber">
              <th mat-header-cell *matHeaderCellDef>Policy no.</th>
              <td mat-cell *matCellDef="let p">
                <a [routerLink]="['/claims/policies', p.id]">{{ p.policyNumber }}</a>
              </td>
            </ng-container>
            <ng-container matColumnDef="line">
              <th mat-header-cell *matHeaderCellDef>Type</th>
              <td mat-cell *matCellDef="let p">{{ p.lineOfBusiness === 'MOTOR' ? 'Motor' : 'Property' }}</td>
            </ng-container>
            <ng-container matColumnDef="cover">
              <th mat-header-cell *matHeaderCellDef>Cover</th>
              <td mat-cell *matCellDef="let p">
                {{ p.effectiveFrom | date: 'd MMM y' }} to {{ p.effectiveTo | date: 'd MMM y' }}
              </td>
            </ng-container>
            <ng-container matColumnDef="sumInsured">
              <th mat-header-cell *matHeaderCellDef>Sum insured</th>
              <td mat-cell *matCellDef="let p" class="amount">{{ p.sumInsured | currency: p.currency : 'code' }}</td>
            </ng-container>
            <ng-container matColumnDef="claim">
              <th mat-header-cell *matHeaderCellDef>Claim in progress</th>
              <td mat-cell *matCellDef="let p">
                @if (p.openClaimId) {
                  <a [routerLink]="['/claims', p.openClaimId]">{{ p.openClaimNumber }}</a>
                } @else {
                  <span class="muted">None</span>
                }
              </td>
            </ng-container>
            <ng-container matColumnDef="action">
              <th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let p">
                <!-- one claim at a time per policy; the server enforces this too -->
                @if (!p.openClaimId) {
                  <a matButton="tonal" routerLink="/claims/new" [queryParams]="{ policy: p.id }">Make a claim</a>
                }
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns"></tr>
          </table>
        </div>
      }
    </main>
  `,
})
export class MyPolicies {
  protected readonly policies = signal<Policy[]>([]);
  protected readonly columns = ['policyNumber', 'line', 'cover', 'sumInsured', 'claim', 'action'];

  constructor() {
    inject(ClaimsApi)
      .policies()
      .subscribe((policies) => this.policies.set(policies));
  }
}
