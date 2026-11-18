import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Exposure, Performance, Workload } from '../api/types';

@Injectable({ providedIn: 'root' })
export class ReportsApi {
  private readonly http = inject(HttpClient);

  workload() {
    return this.http.get<Workload>('/api/reports/workload');
  }
  performance() {
    return this.http.get<Performance>('/api/reports/performance');
  }
  exposure() {
    return this.http.get<Exposure>('/api/reports/exposure');
  }
}
