import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

@Component({
  selector: 'app-reject-dialog',
  imports: [FormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Return for correction</h2>
    <mat-dialog-content>
      <p>The author sees this reason, so it has to say what needs changing.</p>
      <mat-form-field appearance="outline" class="full">
        <mat-label>Reason</mat-label>
        <textarea
          matInput
          rows="4"
          maxlength="500"
          [ngModel]="reason()"
          (ngModelChange)="reason.set($event)"
        ></textarea>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button matButton mat-dialog-close>Cancel</button>
      <button matButton="filled" [disabled]="!reason().trim()" (click)="confirm()">Reject</button>
    </mat-dialog-actions>
  `,
  styles: `
    .full {
      width: 100%;
    }
  `,
})
export class RejectDialog {
  private readonly dialogRef = inject(MatDialogRef<RejectDialog, string>);

  protected readonly reason = signal('');

  protected confirm(): void {
    this.dialogRef.close(this.reason().trim());
  }
}
