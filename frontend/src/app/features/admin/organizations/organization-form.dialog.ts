import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { OrganizationResponse } from '../../../core/api/generated';

export interface OrganizationFormResult {
  code: string;
  name: string;
}

@Component({
  selector: 'app-organization-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ existing ? 'Rename organization' : 'New organization' }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="form">
        @if (!existing) {
          <mat-form-field>
            <mat-label>Code</mat-label>
            <input matInput formControlName="code" placeholder="BERLIN_OPS" />
            <mat-hint
              >Uppercase letters, digits, underscore or hyphen. Cannot be changed later.</mat-hint
            >
            @if (form.controls.code.hasError('pattern')) {
              <mat-error>2–32 characters of A–Z, 0–9, underscore or hyphen</mat-error>
            }
          </mat-form-field>
        }
        <mat-form-field>
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" />
          @if (
            form.controls.name.hasError('minlength') || form.controls.name.hasError('required')
          ) {
            <mat-error>2–120 characters</mat-error>
          }
        </mat-form-field>
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
      min-width: 22rem;
    }
  `,
})
export class OrganizationFormDialog {
  readonly dialogRef = inject(MatDialogRef<OrganizationFormDialog, OrganizationFormResult>);
  readonly existing = inject<OrganizationResponse | null>(MAT_DIALOG_DATA, { optional: true });

  private readonly fb = inject(FormBuilder);
  protected readonly form = this.fb.nonNullable.group({
    code: [
      this.existing?.code ?? '',
      [Validators.required, Validators.pattern(/^[A-Z0-9_-]{2,32}$/)],
    ],
    name: [
      this.existing?.name ?? '',
      [Validators.required, Validators.minLength(2), Validators.maxLength(120)],
    ],
  });

  protected submit(): void {
    if (this.form.valid) {
      this.dialogRef.close(this.form.getRawValue());
    }
  }
}
