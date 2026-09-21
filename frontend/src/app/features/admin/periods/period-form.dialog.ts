import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatNativeDateModule } from '@angular/material/core';
import { MatSelectModule } from '@angular/material/select';

import { CreatePeriodRequest } from '../../../core/api/generated';

@Component({
  selector: 'app-period-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatNativeDateModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>New reporting period</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="form">
        <mat-form-field>
          <mat-label>Code</mat-label>
          <input matInput formControlName="code" placeholder="2026-Q1" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Type</mat-label>
          <mat-select formControlName="periodType">
            <mat-option value="MONTH">Month</mat-option>
            <mat-option value="QUARTER">Quarter</mat-option>
            <mat-option value="YEAR">Year</mat-option>
          </mat-select>
          <mat-hint>Periods of the same type may not overlap.</mat-hint>
        </mat-form-field>
        <mat-form-field>
          <mat-label>Starts on</mat-label>
          <input matInput [matDatepicker]="startPicker" formControlName="startsOn" />
          <mat-datepicker-toggle matIconSuffix [for]="startPicker" />
          <mat-datepicker #startPicker />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Ends on</mat-label>
          <input matInput [matDatepicker]="endPicker" formControlName="endsOn" />
          <mat-datepicker-toggle matIconSuffix [for]="endPicker" />
          <mat-datepicker #endPicker />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Submission deadline</mat-label>
          <input matInput [matDatepicker]="deadlinePicker" formControlName="submissionDeadline" />
          <mat-datepicker-toggle matIconSuffix [for]="deadlinePicker" />
          <mat-datepicker #deadlinePicker />
          <mat-hint>Cannot be earlier than the end date.</mat-hint>
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button matButton (click)="dialogRef.close()">Cancel</button>
      <button matButton="filled" [disabled]="form.invalid" (click)="submit()">Create</button>
    </mat-dialog-actions>
  `,
  styles: `
    .form {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      min-width: 24rem;
    }
  `,
})
export class PeriodFormDialog {
  readonly dialogRef = inject(MatDialogRef<PeriodFormDialog, CreatePeriodRequest>);

  private readonly fb = inject(FormBuilder);
  protected readonly form = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^[A-Z0-9-]{4,20}$/)]],
    periodType: ['QUARTER' as CreatePeriodRequest['periodType'], Validators.required],
    startsOn: [null as Date | null, Validators.required],
    endsOn: [null as Date | null, Validators.required],
    submissionDeadline: [null as Date | null, Validators.required],
  });

  protected submit(): void {
    if (this.form.invalid) {
      return;
    }
    const value = this.form.getRawValue();
    this.dialogRef.close({
      code: value.code,
      periodType: value.periodType,
      startsOn: toIsoDate(value.startsOn!),
      endsOn: toIsoDate(value.endsOn!),
      submissionDeadline: toIsoDate(value.submissionDeadline!),
    });
  }
}

/** Local calendar date, not an instant: toISOString() would shift it across time zones. */
function toIsoDate(date: Date): string {
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}
