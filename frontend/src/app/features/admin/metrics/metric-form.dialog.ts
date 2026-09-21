import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { MetricResponse } from '../../../core/api/generated';

export interface MetricFormResult {
  code: string;
  name: string;
  description?: string;
  unit: string;
  valueType: 'INTEGER' | 'DECIMAL' | 'PERCENTAGE';
  minValue?: number;
  maxValue?: number;
}

@Component({
  selector: 'app-metric-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ existing ? 'Edit metric' : 'New metric' }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="form">
        @if (!existing) {
          <mat-form-field>
            <mat-label>Code</mat-label>
            <input matInput formControlName="code" placeholder="HEADCOUNT" />
            <mat-hint>Cannot be changed later: imports reference it.</mat-hint>
          </mat-form-field>
          <mat-form-field>
            <mat-label>Value type</mat-label>
            <mat-select formControlName="valueType">
              <mat-option value="INTEGER">Integer</mat-option>
              <mat-option value="DECIMAL">Decimal</mat-option>
              <mat-option value="PERCENTAGE">Percentage</mat-option>
            </mat-select>
            <mat-hint>Fixed once created.</mat-hint>
          </mat-form-field>
        }
        <mat-form-field>
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Unit</mat-label>
          <input matInput formControlName="unit" placeholder="people" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Description</mat-label>
          <textarea matInput formControlName="description" rows="2"></textarea>
        </mat-form-field>
        <div class="range">
          <mat-form-field>
            <mat-label>Minimum</mat-label>
            <input matInput type="number" formControlName="minValue" />
          </mat-form-field>
          <mat-form-field>
            <mat-label>Maximum</mat-label>
            <input matInput type="number" formControlName="maxValue" />
          </mat-form-field>
        </div>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button matButton (click)="dialogRef.close()">Cancel</button>
      <button matButton="filled" [disabled]="form.invalid" (click)="submit()">Save</button>
    </mat-dialog-actions>
  `,
  styles: `
    .form {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      min-width: 24rem;
    }
    .range {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 0.5rem;
    }
  `,
})
export class MetricFormDialog {
  readonly dialogRef = inject(MatDialogRef<MetricFormDialog, MetricFormResult>);
  readonly existing = inject<MetricResponse | null>(MAT_DIALOG_DATA, { optional: true });

  private readonly fb = inject(FormBuilder);
  protected readonly form = this.fb.nonNullable.group({
    code: [
      this.existing?.code ?? '',
      [Validators.required, Validators.pattern(/^[A-Z0-9_]{2,40}$/)],
    ],
    name: [
      this.existing?.name ?? '',
      [Validators.required, Validators.minLength(2), Validators.maxLength(120)],
    ],
    description: [this.existing?.description ?? ''],
    unit: [this.existing?.unit ?? '', [Validators.required, Validators.maxLength(32)]],
    valueType: [
      (this.existing?.valueType ?? 'DECIMAL') as MetricFormResult['valueType'],
      Validators.required,
    ],
    minValue: [this.existing?.minValue ?? (null as number | null)],
    maxValue: [this.existing?.maxValue ?? (null as number | null)],
  });

  protected submit(): void {
    if (this.form.invalid) {
      return;
    }
    const value = this.form.getRawValue();
    this.dialogRef.close({
      code: value.code,
      name: value.name,
      description: value.description || undefined,
      unit: value.unit,
      valueType: value.valueType,
      minValue: value.minValue ?? undefined,
      maxValue: value.maxValue ?? undefined,
    });
  }
}
