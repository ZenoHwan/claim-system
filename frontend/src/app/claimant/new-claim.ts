import { CurrencyPipe, DatePipe, NgTemplateOutlet } from '@angular/common';
import { Component, afterNextRender, computed, effect, inject, signal, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { DateAdapter, MAT_DATE_LOCALE, provideNativeDateAdapter } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatStepper, MatStepperModule } from '@angular/material/stepper';
import { concat, last, of, switchMap } from 'rxjs';
import { LossCause, Policy, SubmitClaimRequest } from '../api/types';
import { ClaimsApi, documentProblem } from '../core/claims-api';
import { applyFieldErrors, problemMessage } from '../core/http';
import { DayMonthYearAdapter } from '../shared/day-month-year-adapter';
import { POSTAL_CODE_DIGITS, allowOnly, errorText, minChars, plate, postalCode, rules } from '../shared/validation';

/** Report an incident in five steps: policy, incident, line-specific details, documents, review. */
@Component({
  selector: 'app-new-claim',
  // day/month/year, as claimants in these markets write dates
  providers: [
    provideNativeDateAdapter(),
    { provide: DateAdapter, useClass: DayMonthYearAdapter },
    { provide: MAT_DATE_LOCALE, useValue: 'en-GB' },
  ],
  imports: [
    CurrencyPipe,
    DatePipe,
    NgTemplateOutlet,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCheckboxModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatStepperModule,
  ],
  template: `
    <main class="page">
      <div class="page-head">
        <h1>New claim</h1>
        <a matButton routerLink="/claims" (click)="clearDraft()">Cancel</a>
      </div>

      @if (restored()) {
        <p class="muted" role="status">
          Your draft was restored. Any files you had attached need to be added again.
        </p>
      }

      <mat-stepper linear #stepper [formGroup]="form" (selectionChange)="saveDraft($event.selectedIndex)">
        <mat-step label="Policy" [stepControl]="form.controls.policyId">
          <div class="stack step">
            <mat-form-field>
              <mat-label>Which policy is this claim against?</mat-label>
              <mat-select formControlName="policyId">
                @for (p of policies(); track p.id) {
                  <!-- one claim at a time per policy; the server enforces this too -->
                  <mat-option [value]="p.id" [disabled]="!!p.openClaimNumber">
                    {{ p.policyNumber }} · {{ p.lineOfBusiness === 'MOTOR' ? 'Motor' : 'Property' }}
                    @if (p.openClaimNumber) {
                      · claim {{ p.openClaimNumber }} in progress
                    }
                  </mat-option>
                }
              </mat-select>
              <mat-hint>A policy can have one claim in progress at a time.</mat-hint>
            </mat-form-field>
            @if (policy(); as p) {
              <p class="muted">
                Cover {{ p.effectiveFrom | date: 'd MMM y' }} to
                {{ p.effectiveTo | date: 'd MMM y' }}, sum insured
                {{ p.sumInsured | currency: p.currency : 'code' }}
              </p>
            }
            <div class="row">
              <button matButton="filled" matStepperNext type="button" (click)="form.controls.policyId.markAsTouched()">Next</button>
            </div>
          </div>
        </mat-step>

        <mat-step label="Incident" [stepControl]="form.controls.incident">
          <div class="stack step" formGroupName="incident">
            <mat-form-field>
              <mat-label>Date of incident</mat-label>
              <input
                matInput
                [matDatepicker]="datePicker"
                formControlName="incidentDate"
                [min]="coverFrom()"
                [max]="latestIncidentDate()"
                placeholder="dd/mm/yyyy"
                maxlength="10"
                (keydown)="allowOnly($event, dateChars)"
              />
              <mat-datepicker-toggle matIconSuffix [for]="datePicker" />
              <mat-datepicker #datePicker />
              <mat-error>{{ errorText(form.controls.incident.controls.incidentDate) }}</mat-error>
            </mat-form-field>
            <mat-form-field>
              <mat-label>What happened?</mat-label>
              <textarea matInput rows="4" formControlName="incidentDescription" maxlength="4000"></textarea>
              <mat-hint>At least 20 characters, e.g. where you were going and what hit what.</mat-hint>
              <mat-error>{{ errorText(form.controls.incident.controls.incidentDescription) }}</mat-error>
            </mat-form-field>
            <mat-form-field>
              <mat-label>Estimated loss ({{ policy()?.currency }}), if known</mat-label>
              <input
                matInput
                inputmode="decimal"
                formControlName="claimedAmount"
                (keydown)="allowOnly($event, amountChars)"
              />
              <mat-hint>Optional. Leave blank if you do not have a quote yet.</mat-hint>
              <mat-error>{{ errorText(form.controls.incident.controls.claimedAmount) }}</mat-error>
            </mat-form-field>
            <div class="row">
              <button matButton matStepperPrevious type="button">Back</button>
              <button matButton="filled" matStepperNext type="button" (click)="form.controls.incident.markAllAsTouched()">Next</button>
            </div>
          </div>
        </mat-step>

        <mat-step
          label="Details"
          [stepControl]="isMotor() ? form.controls.motor : form.controls.property"
        >
          @if (isMotor()) {
            <div class="stack step" formGroupName="motor">
              <mat-form-field>
                <mat-label>Vehicle registration</mat-label>
                <input
                  matInput
                  formControlName="vehicleRegistration"
                  maxlength="12"
                  autocapitalize="characters"
                  (blur)="tidyPlate()"
                  (keydown)="allowOnly($event, plateChars)"
                />
                <mat-hint>For example WXY 1234 or SBA 1234 A</mat-hint>
                <mat-error>{{ errorText(form.controls.motor.controls.vehicleRegistration) }}</mat-error>
              </mat-form-field>
              <mat-form-field>
                <mat-label>Driver name</mat-label>
                <input matInput formControlName="driverName" maxlength="200" autocomplete="name" />
                <mat-error>{{ errorText(form.controls.motor.controls.driverName) }}</mat-error>
              </mat-form-field>
              <p class="label">Accident location</p>
              <ng-container
                *ngTemplateOutlet="addressFields; context: { $implicit: form.controls.motor.controls.accidentLocation }"
              />
              <mat-checkbox formControlName="thirdPartyInvolved"
                >A third party was involved</mat-checkbox
              >
            </div>
          } @else {
            <div class="stack step" formGroupName="property">
              <p class="label">Property address</p>
              <ng-container
                *ngTemplateOutlet="addressFields; context: { $implicit: form.controls.property.controls.propertyAddress }"
              />
              <mat-form-field>
                <mat-label>Cause of loss</mat-label>
                <mat-select formControlName="lossCause">
                  @for (cause of lossCauses; track cause) {
                    <mat-option [value]="cause">{{ cause }}</mat-option>
                  }
                </mat-select>
                <mat-error>{{ errorText(form.controls.property.controls.lossCause) }}</mat-error>
              </mat-form-field>
            </div>
          }
          <div class="row">
            <button matButton matStepperPrevious type="button">Back</button>
            <button matButton="filled" matStepperNext type="button" (click)="(isMotor() ? form.controls.motor : form.controls.property).markAllAsTouched()">Next</button>
          </div>
        </mat-step>

        <mat-step label="Documents" optional>
          <div class="stack step">
            <p class="muted">Photos, reports or receipts. PDF, JPEG or PNG, up to 10 MB each.</p>
            <input
              #picker
              type="file"
              multiple
              hidden
              accept="application/pdf,image/jpeg,image/png"
              (change)="addFiles(picker.files); picker.value = ''"
            />
            <div class="row">
              <button matButton="outlined" type="button" (click)="picker.click()">
                Attach files
              </button>
            </div>
            @for (file of files(); track file) {
              <div class="row">
                <mat-icon aria-hidden="true">description</mat-icon>
                <span>{{ file.name }}</span>
                <button matButton type="button" (click)="removeFile(file)">Remove</button>
              </div>
            }
            @if (fileError()) {
              <p class="error-text" role="alert">{{ fileError() }}</p>
            }
            <div class="row">
              <button matButton matStepperPrevious type="button">Back</button>
              <button matButton="filled" matStepperNext type="button">Next</button>
            </div>
          </div>
        </mat-step>

        <mat-step label="Review">
          <div class="stack step">
            @if (policy(); as p) {
              @let incident = form.controls.incident.getRawValue();
              <dl>
                <dt>Policy</dt>
                <dd>{{ p.policyNumber }}</dd>
                <dt>Incident date</dt>
                <dd>{{ incident.incidentDate | date: 'd MMM y' }}</dd>
                <dt>Estimated loss</dt>
                <dd>
                  @if (incident.claimedAmount !== null && incident.claimedAmount !== '') {
                    {{ incident.claimedAmount | currency: p.currency : 'code' }}
                  } @else {
                    Not stated
                  }
                </dd>
                <dt>What happened</dt>
                <dd>{{ incident.incidentDescription }}</dd>
                <dt>Documents</dt>
                <dd>{{ files().length }} attached</dd>
              </dl>
            }
            @if (error()) {
              <p class="error-text" role="alert">{{ error() }}</p>
            }
            <div class="row">
              <button matButton matStepperPrevious type="button">Back</button>
              <button
                matButton="filled"
                type="button"
                [disabled]="busy()"
                (click)="submit(stepper)"
              >
                Submit claim
              </button>
            </div>
          </div>
        </mat-step>
      </mat-stepper>

      <ng-template #addressFields let-group>
        <div class="stack" [formGroup]="group">
          <mat-form-field>
            <mat-label>Address line 1</mat-label>
            <input matInput formControlName="addressLine1" maxlength="300" />
            <mat-error>{{ errorText(group.controls.addressLine1) }}</mat-error>
          </mat-form-field>
          <mat-form-field>
            <mat-label>Address line 2 (optional)</mat-label>
            <input matInput formControlName="addressLine2" maxlength="300" />
            <mat-error>{{ errorText(group.controls.addressLine2) }}</mat-error>
          </mat-form-field>
          <mat-form-field>
            <mat-label>Address line 3 (optional)</mat-label>
            <input matInput formControlName="addressLine3" maxlength="300" />
            <mat-error>{{ errorText(group.controls.addressLine3) }}</mat-error>
          </mat-form-field>
          <mat-form-field class="postal">
            <mat-label>Postal code</mat-label>
            <input
              matInput
              formControlName="postalCode"
              inputmode="numeric"
              [maxlength]="postalDigits() ?? 6"
              (keydown)="allowOnly($event, digitChars)"
            />
            @if (postalDigits(); as digits) {
              <mat-hint>{{ digits }} digits</mat-hint>
            }
            <mat-error>{{ errorText(group.controls.postalCode) }}</mat-error>
          </mat-form-field>
        </div>
      </ng-template>
    </main>
  `,
  styles: `
    .label {
      margin: 8px 0 0;
      font: var(--mat-sys-title-small);
    }
    .postal {
      max-width: 160px;
    }
    .step {
      max-width: 560px;
      padding-top: 16px;
    }
    dl {
      display: grid;
      grid-template-columns: max-content 1fr;
      gap: 4px 16px;
    }
    dt {
      color: var(--mat-sys-on-surface-variant);
    }
    dd {
      margin: 0;
    }
  `,
})
export class NewClaim {
  private readonly api = inject(ClaimsApi);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder).nonNullable;

  protected readonly today = new Date();
  /** The chosen policy's market, kept apart from `policy` because the form's validators read it. */
  private readonly marketCode = signal<string | undefined>(undefined);
  protected readonly errorText = errorText;
  protected readonly allowOnly = allowOnly;
  protected readonly dateChars = /[\d/]/;
  protected readonly amountChars = /[\d.]/;
  protected readonly digitChars = /\d/;
  protected readonly plateChars = /[a-zA-Z\d ]/;
  protected readonly lossCauses: LossCause[] = ['FIRE', 'WATER', 'THEFT', 'STORM', 'OTHER'];

  protected readonly form = this.fb.group({
    policyId: ['', Validators.required],
    incident: this.fb.group({
      incidentDate: [null as Date | null, Validators.required],
      incidentDescription: ['', [Validators.required, minChars(20), Validators.maxLength(4000)]],
      claimedAmount: [null as number | string | null, rules.amount],
    }),
    motor: this.fb.group({
      vehicleRegistration: ['', [Validators.required, rules.plate]],
      driverName: ['', [Validators.required, minChars(2), rules.name]],
      accidentLocation: this.address(),
      thirdPartyInvolved: [false],
    }),
    property: this.fb.group({
      propertyAddress: this.address(),
      lossCause: ['' as LossCause | '', Validators.required],
    }),
  });

  /** Same rules as the server. The postal code's length follows the chosen policy's market. */
  private address() {
    return this.fb.group({
      addressLine1: ['', [Validators.required, minChars(3), rules.addressLine]],
      addressLine2: ['', rules.addressLine],
      addressLine3: ['', rules.addressLine],
      postalCode: ['', [Validators.required, postalCode(() => this.marketCode())]],
    });
  }

  protected readonly policies = signal<Policy[]>([]);
  private readonly policyId = toSignal(this.form.controls.policyId.valueChanges, {
    initialValue: '',
  });
  protected readonly policy = computed(() => this.policies().find((p) => p.id === this.policyId()));
  protected readonly isMotor = computed(() => this.policy()?.lineOfBusiness !== 'PROPERTY');
  protected readonly postalDigits = computed(() => POSTAL_CODE_DIGITS[this.policy()?.marketCode ?? '']);
  /** The incident must fall within the policy's cover and cannot be in the future. */
  protected readonly coverFrom = computed(() => localDate(this.policy()?.effectiveFrom));
  protected readonly latestIncidentDate = computed(() => {
    const coverTo = localDate(this.policy()?.effectiveTo);
    return coverTo && coverTo < this.today ? coverTo : this.today;
  });

  /** Shows the plate the way it will be stored, e.g. "wxy1234" becomes "WXY 1234". */
  protected tidyPlate() {
    const control = this.form.controls.motor.controls.vehicleRegistration;
    control.setValue(plate(control.value));
  }

  protected readonly files = signal<File[]>([]);
  protected readonly fileError = signal('');
  protected readonly error = signal('');
  protected readonly busy = signal(false);

  private readonly stepper = viewChild.required(MatStepper);
  private step = 0;
  protected readonly restored = signal(false);

  constructor() {
    this.api.policies().subscribe((policies) => this.policies.set(policies));
    // a different market means a different postal code length, so re-check what was typed
    effect(() => {
      this.marketCode.set(this.policy()?.marketCode);
      this.form.controls.motor.controls.accidentLocation.controls.postalCode.updateValueAndValidity();
      this.form.controls.property.controls.propertyAddress.controls.postalCode.updateValueAndValidity();
    });
    this.restoreDraft();
    // "Make a claim" on My policies links here with ?policy=<id>; an explicit choice beats the draft's.
    const linked = inject(ActivatedRoute).snapshot.queryParamMap.get('policy');
    if (linked) {
      this.form.controls.policyId.setValue(linked);
    }
    this.form.valueChanges.subscribe(() => this.saveDraft(this.step));
  }

  /**
   * A refresh must not throw away what the claimant has typed. The draft lives in sessionStorage,
   * so it ends with the tab and never outlives the sign-in. Files cannot be stored and are re-added.
   */
  protected saveDraft(step: number) {
    this.step = step;
    const value = this.form.getRawValue();
    const draft = {
      step,
      value: { ...value, incident: { ...value.incident, incidentDate: isoDate(value.incident.incidentDate) } },
    };
    sessionStorage.setItem(DRAFT_KEY, JSON.stringify(draft));
  }

  protected clearDraft() {
    sessionStorage.removeItem(DRAFT_KEY);
  }

  private restoreDraft() {
    const stored = sessionStorage.getItem(DRAFT_KEY);
    if (!stored) {
      return;
    }
    const { step, value } = JSON.parse(stored);
    const date = value.incident.incidentDate;
    this.form.patchValue({
      ...value,
      incident: { ...value.incident, incidentDate: date ? new Date(date + 'T00:00:00') : null },
    });
    this.restored.set(true);
    // A linear stepper only lets you stand on a step whose earlier steps were visited and are valid.
    afterNextRender(() => {
      const stepper = this.stepper();
      const steps = stepper.steps.toArray();
      const { policyId, incident, motor, property } = this.form.controls;
      // policy, incident, details (whichever line applies), documents (optional)
      const valid = [policyId.valid, incident.valid, motor.valid || property.valid, true];
      let reachable = 0;
      while (reachable < step && valid[reachable]) {
        steps[reachable].interacted = true;
        reachable++;
      }
      stepper.selectedIndex = reachable;
    });
  }

  protected addFiles(picked: FileList | null) {
    const problems: string[] = [];
    const accepted: File[] = [];
    for (const file of Array.from(picked ?? [])) {
      const problem = documentProblem(file);
      problem ? problems.push(problem) : accepted.push(file);
    }
    this.files.update((files) => [...files, ...accepted]);
    this.fileError.set(problems.join('; '));
  }

  protected removeFile(file: File) {
    this.files.update((files) => files.filter((f) => f !== file));
  }

  protected submit(stepper: MatStepper) {
    const { policyId, incident, motor, property } = this.form.getRawValue();
    const request: SubmitClaimRequest = {
      policyId,
      incidentDate: isoDate(incident.incidentDate)!,
      incidentDescription: incident.incidentDescription,
      claimedAmount: amount(incident.claimedAmount),
      ...(this.isMotor()
        ? { motor }
        : { property: { ...property, lossCause: property.lossCause as LossCause } }),
    };
    this.busy.set(true);
    this.error.set('');
    this.api
      .submit(request)
      .pipe(
        // The claim exists first; then each file is uploaded to it, one at a time.
        switchMap((claim) =>
          concat(...this.files().map((file) => this.api.upload(claim.id, file)), of(claim)).pipe(
            last(),
            switchMap(() => of(claim)),
          ),
        ),
      )
      .subscribe({
        next: (claim) => {
          this.clearDraft();
          void this.router.navigate(['/claims', claim.id]);
        },
        error: (error) => {
          this.busy.set(false);
          this.error.set(problemMessage(error));
          // Server field names are flat; the incident fields live in a nested group here.
          applyFieldErrors(this.form.controls.incident, error);
          if (this.form.controls.incident.invalid) {
            stepper.selectedIndex = 1;
          }
        },
      });
  }
}

const DRAFT_KEY = 'new-claim-draft';

/** yyyy-MM-dd in local time: the day the claimant picked, not shifted by a UTC conversion. */
function isoDate(date: Date | null): string | null {
  if (!date) {
    return null;
  }
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** The amount field is free text so it can refuse letters; blank means "not stated". */
function amount(value: number | string | null): number | undefined {
  return value === null || value === '' ? undefined : Number(value);
}

/** "2026-01-01" as local midnight, for the date picker's min and max. */
function localDate(iso: string | undefined): Date | null {
  return iso ? new Date(iso + 'T00:00:00') : null;
}
