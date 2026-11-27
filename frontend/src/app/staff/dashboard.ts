import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { forkJoin } from 'rxjs';
import { Exposure, Performance, Workload } from '../api/types';
import { problemMessage } from '../core/http';
import { ReportsApi } from '../core/reports-api';
import { statusLabel } from '../shared/status-chip';

const OPEN_STATUSES = ['SUBMITTED', 'UNDER_REVIEW', 'INFO_REQUESTED', 'ASSESSED', 'APPROVED'];

/** Manager view: workload, performance and outstanding exposure, built from claim events. */
@Component({
  selector: 'app-dashboard',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, MatButtonModule, MatCardModule, MatTableModule],
  template: `
    <main class="page">
      <div class="page-head">
        <div>
          <h1>Dashboard · {{ workload()?.market }}</h1>
          <!-- Figures come from events and can trail the claims service by a few seconds. -->
          <p class="muted">
            As of {{ asOf() ? (asOf() | date: 'd MMM y, HH:mm:ss') : 'no events yet' }}
          </p>
        </div>
        <button matButton (click)="load()">Refresh</button>
      </div>
      @if (error()) {
        <p class="error-text" role="alert">{{ error() }}</p>
      }

      @if (workload(); as w) {
        <div class="tiles">
          <mat-card appearance="outlined"
            ><mat-card-content
              ><div class="figure">{{ w.openClaims }}</div>
              Open claims</mat-card-content
            ></mat-card
          >
          <mat-card appearance="outlined"
            ><mat-card-content
              ><div class="figure">{{ w.unassigned }}</div>
              Unassigned</mat-card-content
            ></mat-card
          >
          <mat-card appearance="outlined"
            ><mat-card-content
              ><div class="figure">{{ performance()?.decidedLast30Days }}</div>
              Decided in 30 days</mat-card-content
            ></mat-card
          >
          <mat-card appearance="outlined">
            <mat-card-content>
              <div class="figure">
                {{
                  performance()?.averageDaysToDecision != null
                    ? (performance()!.averageDaysToDecision | number: '1.0-1')
                    : '–'
                }}
              </div>
              Avg days to decision
            </mat-card-content>
          </mat-card>
        </div>

        <div class="panels">
          <mat-card appearance="outlined">
            <mat-card-header><mat-card-title>Workload by officer</mat-card-title></mat-card-header>
            <mat-card-content class="table-wrap">
              @if (w.officers.length === 0) {
                <p class="muted">No assigned open claims.</p>
              } @else {
                <table class="plain">
                  <thead>
                    <tr>
                      <th>Officer</th>
                      <th class="num">Open</th>
                      @for (status of statuses; track status) {
                        <th class="num">{{ label(status) }}</th>
                      }
                    </tr>
                  </thead>
                  <tbody>
                    @for (officer of w.officers; track officer.officerId) {
                      <tr>
                        <td>{{ officer.officerName }}</td>
                        <td class="num">{{ officer.open }}</td>
                        @for (status of statuses; track status) {
                          <td class="num">{{ officer.byStatus[status] ?? 0 }}</td>
                        }
                      </tr>
                    }
                  </tbody>
                </table>
              }
            </mat-card-content>
          </mat-card>

          <mat-card appearance="outlined">
            <mat-card-header
              ><mat-card-title>Ageing of open claims</mat-card-title></mat-card-header
            >
            <mat-card-content>
              <table class="plain">
                <tbody>
                  <tr>
                    <td>0–2 days</td>
                    <td class="num">{{ w.ageing.days0to2 }}</td>
                  </tr>
                  <tr>
                    <td>3–7 days</td>
                    <td class="num">{{ w.ageing.days3to7 }}</td>
                  </tr>
                  <tr>
                    <td>8+ days</td>
                    <td class="num">{{ w.ageing.days8plus }}</td>
                  </tr>
                </tbody>
              </table>
            </mat-card-content>
          </mat-card>

          <mat-card appearance="outlined">
            <mat-card-header><mat-card-title>Done today</mat-card-title></mat-card-header>
            <mat-card-content class="table-wrap">
              @if (!performance()?.today?.length) {
                <p class="muted">Nothing decided or settled yet today.</p>
              } @else {
                <table class="plain">
                  <thead>
                    <tr>
                      <th>Officer</th>
                      <th class="num">Approved</th>
                      <th class="num">Rejected</th>
                      <th class="num">Settled</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (officer of performance()!.today; track officer.officerId) {
                      <tr>
                        <td>{{ officer.officerName }}</td>
                        <td class="num">{{ officer.approved }}</td>
                        <td class="num">{{ officer.rejected }}</td>
                        <td class="num">{{ officer.settled }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
              }
            </mat-card-content>
          </mat-card>
        </div>
      }

      @if (exposure(); as e) {
        <mat-card appearance="outlined" class="exposure">
          <mat-card-header>
            <mat-card-title>Outstanding exposure</mat-card-title>
            <mat-card-subtitle
              >Current reserve on open claims, in each market's own currency</mat-card-subtitle
            >
          </mat-card-header>
          <mat-card-content class="table-wrap">
            @if (e.rows.length === 0) {
              <p class="muted">No open claims.</p>
            } @else {
              <table class="plain">
                <thead>
                  <tr>
                    <th>Market</th>
                    <th>Line</th>
                    <th class="num">Open claims</th>
                    <th class="num">Outstanding reserve</th>
                  </tr>
                </thead>
                <tbody>
                  @for (row of e.rows; track row.marketCode + row.lineOfBusiness + row.currency) {
                    <tr>
                      <td>{{ row.marketCode }}</td>
                      <td>{{ row.lineOfBusiness === 'MOTOR' ? 'Motor' : 'Property' }}</td>
                      <td class="num">{{ row.openClaims }}</td>
                      <td class="num">
                        {{ row.outstandingReserve | currency: row.currency : 'code' }}
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            }
          </mat-card-content>
        </mat-card>
      }
    </main>
  `,
  styles: `
    .tiles {
      display: grid;
      gap: 16px;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      margin-bottom: 16px;
    }
    .figure {
      font: var(--mat-sys-display-small);
    }
    .panels {
      display: grid;
      gap: 16px;
      grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
      align-items: start;
      margin-bottom: 16px;
    }
    table.plain {
      width: 100%;
      border-collapse: collapse;
    }
    table.plain th,
    table.plain td {
      text-align: left;
      padding: 8px 12px 8px 0;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }
    table.plain th {
      font: var(--mat-sys-label-medium);
      color: var(--mat-sys-on-surface-variant);
    }
    table.plain .num {
      text-align: right;
      font-variant-numeric: tabular-nums;
    }
  `,
})
export class Dashboard {
  private readonly api = inject(ReportsApi);

  protected readonly workload = signal<Workload | null>(null);
  protected readonly performance = signal<Performance | null>(null);
  protected readonly exposure = signal<Exposure | null>(null);
  protected readonly asOf = computed(() => this.exposure()?.asOf ?? this.workload()?.asOf);
  protected readonly error = signal('');
  protected readonly statuses = OPEN_STATUSES;
  protected readonly label = statusLabel;

  constructor() {
    this.load();
  }

  protected load() {
    this.error.set('');
    forkJoin({
      workload: this.api.workload(),
      performance: this.api.performance(),
      exposure: this.api.exposure(),
    }).subscribe({
      next: ({ workload, performance, exposure }) => {
        this.workload.set(workload);
        this.performance.set(performance);
        this.exposure.set(exposure);
      },
      error: (error) => this.error.set(problemMessage(error)),
    });
  }
}
