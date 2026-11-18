import { Injectable } from '@angular/core';
import { NativeDateAdapter } from '@angular/material/core';

/**
 * Reads typed dates as dd/mm/yyyy. The native adapter hands text to Date.parse, which reads
 * month first, so "16/11/2026" failed and "01/12/2026" became 12 January. Impossible dates
 * such as 31/02/2026 are refused instead of rolling over into March.
 */
@Injectable()
export class DayMonthYearAdapter extends NativeDateAdapter {
  override parse(value: unknown): Date | null {
    if (typeof value !== 'string' || value.trim() === '') {
      return null;
    }
    const parts = /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/.exec(value.trim());
    if (!parts) {
      return this.invalid();
    }
    const [day, month, year] = [Number(parts[1]), Number(parts[2]), Number(parts[3])];
    const date = new Date(year, month - 1, day);
    return date.getDate() === day && date.getMonth() === month - 1 ? date : this.invalid();
  }
}
