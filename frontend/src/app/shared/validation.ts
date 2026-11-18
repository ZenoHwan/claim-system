import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * Form rules that mirror the claims-service's request validation, so a claimant sees the problem
 * on the field before submitting. The server checks everything again.
 */

/** Trims and collapses spaces, as the server does before validating. */
export function clean(value: unknown): string {
  return typeof value === 'string' ? value.trim().replace(/\s+/g, ' ') : '';
}

/** "  wxy1234 " -> "WXY 1234", "sba1234a" -> "SBA 1234 A"; anything else is returned upper-cased. */
export function plate(value: unknown): string {
  const upper = clean(value).toUpperCase();
  const parts = /^([A-Z]{1,3})(\d{1,4})([A-Z]?)$/.exec(upper.replace(/ /g, ''));
  return parts ? [parts[1], parts[2], parts[3]].filter(Boolean).join(' ') : upper;
}

/** Fails with `message` when the cleaned value is not empty and does not match `pattern`. */
export function matches(pattern: RegExp, message: string): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = clean(control.value);
    return value && !pattern.test(value) ? { format: message } : null;
  };
}

/** Minimum length counted after trimming, so a run of spaces does not pass. */
export function minChars(length: number): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = clean(control.value);
    return value && value.length < length ? { minChars: length } : null;
  };
}

export const rules = {
  plate: matches(/^[A-Z]{1,3} \d{1,4}( [A-Z])?$/, 'Use the plate format, e.g. WXY 1234 or SBA 1234 A'),
  name: matches(/^\p{L}[\p{L} .'/@-]*$/u, "Use letters, spaces and . ' / @ - only"),
  addressLine: matches(
    /^(?=.*[\p{L}\p{N}])[\p{L}\p{N} ,.'/#()&-]+$/u,
    "Use letters, numbers, spaces and , . ' / # ( ) & - only",
  ),
  amount: (control: AbstractControl): ValidationErrors | null => {
    const value = control.value;
    if (value === null || value === '') {
      return null;
    }
    return /^\d{1,13}(\.\d{1,2})?$/.test(String(value)) && Number(value) > 0
      ? null
      : { format: 'Enter an amount above 0 with at most 2 decimal places' };
  },
};

/** Postal code length depends on the policy's market: Malaysia 5 digits, Singapore 6. */
export const POSTAL_CODE_DIGITS: Record<string, number> = { MY: 5, SG: 6 };

export function postalCode(market: () => string | undefined): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = clean(control.value);
    const digits = POSTAL_CODE_DIGITS[market() ?? ''];
    if (!value) {
      return null;
    }
    if (!/^\d+$/.test(value) || (digits ? value.length !== digits : !/^\d{5,6}$/.test(value))) {
      return { format: digits ? `Enter the ${digits}-digit postal code` : 'Enter a 5 or 6 digit postal code' };
    }
    return null;
  };
}

/** The message under a field, for whichever rule it fails first. */
export function errorText(control: AbstractControl | null): string {
  const e = control?.errors;
  if (!e) {
    return '';
  }
  // an unreadable date also leaves the value empty, so explain the format before saying "Required"
  if (e['matDatepickerParse']) return 'Enter a valid date as dd/mm/yyyy';
  if (e['required']) return 'Required';
  if (e['matDatepickerMin']) return 'This is before your policy cover started';
  if (e['matDatepickerMax']) return 'The date cannot be in the future or after your cover ended';
  if (e['minChars']) return `At least ${e['minChars']} characters`;
  if (e['maxlength']) return `At most ${e['maxlength'].requiredLength} characters`;
  if (e['format']) return e['format'];
  if (e['server']) return e['server'];
  return 'Check this field';
}

/** Blocks typed characters that can never be valid in the field (pasting is still validated). */
export function allowOnly(event: KeyboardEvent, allowed: RegExp): void {
  if (event.key.length === 1 && !event.ctrlKey && !event.metaKey && !allowed.test(event.key)) {
    event.preventDefault();
  }
}
