import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { firstValueFrom } from 'rxjs';

import { PeriodResponse, ReportingPeriodsService } from '../../core/api/generated';
import { AuthService } from '../../core/auth/auth.service';

export interface StartSubmissionResult {
  organizationId: string;
  reportingPeriodId: string;
}

@Component({
  selector: 'app-start-submission-dialog',
  imports: [FormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatSelectModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Start a submission</h2>
    <mat-dialog-content>
      <mat-form-field appearance="outline" class="full">
        <mat-label>Organization</mat-label>
        <mat-select
          [ngModel]="organizationId()"
          (ngModelChange)="organizationId.set($event)"
          name="organization"
        >
          @for (membership of organizations(); track membership.id) {
            <mat-option [value]="membership.id">
              {{ membership.code }} — {{ membership.name }}
            </mat-option>
          }
        </mat-select>
      </mat-form-field>

      <mat-form-field appearance="outline" class="full">
        <mat-label>Reporting period</mat-label>
        <mat-select [ngModel]="periodId()" (ngModelChange)="periodId.set($event)" name="period">
          @for (period of openPeriods(); track period.id) {
            <mat-option [value]="period.id">
              {{ period.code }} (due {{ period.submissionDeadline }})
            </mat-option>
          }
        </mat-select>
      </mat-form-field>

      @if (openPeriods().length === 0) {
        <p class="hint">No reporting period is open at the moment.</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button matButton mat-dialog-close>Cancel</button>
      <button matButton="filled" [disabled]="!ready()" (click)="confirm()">Start</button>
    </mat-dialog-actions>
  `,
  styles: `
    .full {
      width: 100%;
    }
    .hint {
      opacity: 0.7;
    }
  `,
})
export class StartSubmissionDialog {
  private readonly dialogRef = inject(MatDialogRef<StartSubmissionDialog, StartSubmissionResult>);
  private readonly periodsApi = inject(ReportingPeriodsService);
  private readonly auth = inject(AuthService);

  protected readonly organizations = this.auth.organizations;
  protected readonly openPeriods = signal<PeriodResponse[]>([]);
  protected readonly organizationId = signal('');
  protected readonly periodId = signal('');

  constructor() {
    queueMicrotask(async () => {
      // Memberships are granted server-side, so the copy loaded at sign-in can already be out
      // of date here -- and an empty picker would read as a broken screen rather than a stale one.
      const [page] = await Promise.all([
        firstValueFrom(this.periodsApi.listPeriods('OPEN', undefined, 0, 100)),
        firstValueFrom(this.auth.loadCurrentUser()),
      ]);
      this.openPeriods.set(page.content);
    });
  }

  protected ready(): boolean {
    return this.organizationId() !== '' && this.periodId() !== '';
  }

  protected confirm(): void {
    this.dialogRef.close({
      organizationId: this.organizationId(),
      reportingPeriodId: this.periodId(),
    });
  }
}
