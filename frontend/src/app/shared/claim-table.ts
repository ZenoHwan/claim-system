import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { ClaimListItem } from '../api/types';
import { StatusChip } from './status-chip';

/** The claim list used by the claimant's "My claims", the staff queue and the staff "My claims". */
@Component({
  selector: 'app-claim-table',
  imports: [CurrencyPipe, DatePipe, RouterLink, MatButtonModule, MatTableModule, StatusChip],
  template: `
    @if (claims().length === 0) {
      <p class="muted">{{ emptyText() }}</p>
    } @else {
      <div class="table-wrap">
        <table mat-table [dataSource]="claims()">
          <ng-container matColumnDef="claimNumber">
            <th mat-header-cell *matHeaderCellDef>Claim no.</th>
            <td mat-cell *matCellDef="let claim">
              <a [routerLink]="[linkBase(), claim.id]">{{ claim.claimNumber }}</a>
            </td>
          </ng-container>
          <ng-container matColumnDef="line">
            <th mat-header-cell *matHeaderCellDef>Type</th>
            <td mat-cell *matCellDef="let claim">
              {{ claim.lineOfBusiness === 'MOTOR' ? 'Motor' : 'Property' }}
            </td>
          </ng-container>
          <ng-container matColumnDef="claimant">
            <th mat-header-cell *matHeaderCellDef>Claimant</th>
            <td mat-cell *matCellDef="let claim">{{ claim.claimantName }}</td>
          </ng-container>
          <ng-container matColumnDef="incidentDate">
            <th mat-header-cell *matHeaderCellDef>Incident</th>
            <td mat-cell *matCellDef="let claim">{{ claim.incidentDate | date: 'd MMM y' }}</td>
          </ng-container>
          <ng-container matColumnDef="claimed">
            <th mat-header-cell *matHeaderCellDef>Claimed</th>
            <td mat-cell *matCellDef="let claim" class="amount">
              @if (claim.claimedAmount != null) {
                {{ claim.claimedAmount | currency: claim.currency : 'code' }}
              } @else {
                Not stated
              }
            </td>
          </ng-container>
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Status</th>
            <td mat-cell *matCellDef="let claim"><app-status-chip [status]="claim.status" /></td>
          </ng-container>
          <ng-container matColumnDef="submitted">
            <th mat-header-cell *matHeaderCellDef>Submitted</th>
            <td mat-cell *matCellDef="let claim">{{ claim.submittedAt | date: 'd MMM, HH:mm' }}</td>
          </ng-container>
          <ng-container matColumnDef="updated">
            <th mat-header-cell *matHeaderCellDef>Updated</th>
            <td mat-cell *matCellDef="let claim">{{ claim.updatedAt | date: 'd MMM, HH:mm' }}</td>
          </ng-container>
          <ng-container matColumnDef="action">
            <th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let claim">
              <button matButton="tonal" (click)="pickUp.emit(claim)">Pick up</button>
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns()"></tr>
          <tr mat-row *matRowDef="let row; columns: columns()"></tr>
        </table>
      </div>
    }
  `,
})
export class ClaimTable {
  readonly claims = input.required<ClaimListItem[]>();
  readonly columns = input.required<string[]>();
  /** Route prefix for the claim link, e.g. '/claims' or '/admin/claims'. */
  readonly linkBase = input('/claims');
  readonly emptyText = input('Nothing here yet.');
  readonly pickUp = output<ClaimListItem>();
}
