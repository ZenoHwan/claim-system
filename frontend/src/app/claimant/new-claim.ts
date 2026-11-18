import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatStepper, MatStepperModule } from '@angular/material/stepper';
import { concat, last, of, switchMap } from 'rxjs';
import { LossCause, Policy, SubmitClaimRequest } from '../api/types';
import { ClaimsApi, documentProblem } from '../core/claims-api';
import { applyFieldErrors, problemMessage } from '../core/http';

/** Report an incident in five steps: policy, incident, line-specific details, documents, review. */
@Component({
  selector: 'app-new-claim',
  imports: [
    CurrencyPipe,
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCheckboxModule,
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
        <a matButton routerLink="/claims">Cancel</a>
      </div>

      <mat-stepper linear #stepper [formGroup]="form">
        <mat-step label="Policy" [stepControl]="form.controls.policyId">
          <div class="stack step">
            <mat-form-field>
              <mat-label>Which policy is this claim against?</mat-label>
              <mat-select formControlName="policyId">
                @for (p of policies(); track p.id) {
                  <mat-option [value]="p.id">
                    {{ p.policyNumber }} · {{ p.lineOfBusiness === 'MOTOR' ? 'Motor' : 'Property' }}
                  </mat-option>
                }
              </mat-select>
            </mat-form-field>
            @if (policy(); as p) {
              <p class="muted">
                Cover {{ p.effectiveFrom | date: 'd MMM y' }} to
                {{ p.effectiveTo | date: 'd MMM y' }}, sum insured
                {{ p.sumInsured | currency: p.currency : 'code' }}
              </p>
            }
            <div class="row">
              <button matButton="filled" matStepperNext type="button">Next</button>
            </div>
          </div>
        </mat-step>

        <mat-step label="Incident" [stepControl]="form.controls.incident">
          <div class="stack step" formGroupName="incident">
            <mat-form-field>
              <mat-label>Date of incident</mat-label>
              <input matInput type="date" formControlName="incidentDate" [max]="today" />
              @if (form.controls.incident.controls.incidentDate.errors?.['server']; as message) {
                <mat-error>{{ message }}</mat-error>
              }
            </mat-form-field>
            <mat-form-field>
              <mat-label>What happened?</mat-label>
              <textarea matInput rows="4" formControlName="incidentDescription"></textarea>
            </mat-form-field>
            <mat-form-field>
              <mat-label>Amount claimed ({{ policy()?.currency }})</mat-label>
              <input
                matInput
                type="number"
                min="0.01"
                step="0.01"
                formControlName="claimedAmount"
              />
            </mat-form-field>
            <div class="row">
              <button matButton matStepperPrevious type="button">Back</button>
              <button matButton="filled" matStepperNext type="button">Next</button>
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
                <input matInput formControlName="vehicleRegistration" />
              </mat-form-field>
              <mat-form-field>
                <mat-label>Driver name</mat-label>
                <input matInput formControlName="driverName" />
              </mat-form-field>
              <mat-form-field>
                <mat-label>Accident location</mat-label>
                <input matInput formControlName="accidentLocation" />
              </mat-form-field>
              <mat-checkbox formControlName="thirdPartyInvolved"
                >A third party was involved</mat-checkbox
              >
            </div>
          } @else {
            <div class="stack step" formGroupName="property">
              <mat-form-field>
                <mat-label>Property address</mat-label>
                <input matInput formControlName="propertyAddress" />
              </mat-form-field>
              <mat-form-field>
                <mat-label>Cause of loss</mat-label>
                <mat-select formControlName="lossCause">
                  @for (cause of lossCauses; track cause) {
                    <mat-option [value]="cause">{{ cause }}</mat-option>
                  }
                </mat-select>
              </mat-form-field>
            </div>
          }
          <div class="row">
            <button matButton matStepperPrevious type="button">Back</button>
            <button matButton="filled" matStepperNext type="button">Next</button>
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
                <dt>Amount claimed</dt>
                <dd>{{ incident.claimedAmount | currency: p.currency : 'code' }}</dd>
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
    </main>
  `,
  styles: `
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

  protected readonly today = new Date().toISOString().slice(0, 10);
  protected readonly lossCauses: LossCause[] = ['FIRE', 'WATER', 'THEFT', 'STORM', 'OTHER'];

  protected readonly form = this.fb.group({
    policyId: ['', Validators.required],
    incident: this.fb.group({
      incidentDate: ['', Validators.required],
      incidentDescription: ['', Validators.required],
      claimedAmount: [null as number | null, [Validators.required, Validators.min(0.01)]],
    }),
    motor: this.fb.group({
      vehicleRegistration: ['', Validators.required],
      driverName: ['', Validators.required],
      accidentLocation: ['', Validators.required],
      thirdPartyInvolved: [false],
    }),
    property: this.fb.group({
      propertyAddress: ['', Validators.required],
      lossCause: ['' as LossCause | '', Validators.required],
    }),
  });

  protected readonly policies = signal<Policy[]>([]);
  private readonly policyId = toSignal(this.form.controls.policyId.valueChanges, {
    initialValue: '',
  });
  protected readonly policy = computed(() => this.policies().find((p) => p.id === this.policyId()));
  protected readonly isMotor = computed(() => this.policy()?.lineOfBusiness !== 'PROPERTY');

  protected readonly files = signal<File[]>([]);
  protected readonly fileError = signal('');
  protected readonly error = signal('');
  protected readonly busy = signal(false);

  constructor() {
    this.api.policies().subscribe((policies) => this.policies.set(policies));
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
      incidentDate: incident.incidentDate,
      incidentDescription: incident.incidentDescription,
      claimedAmount: incident.claimedAmount!,
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
        next: (claim) => void this.router.navigate(['/claims', claim.id]),
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
