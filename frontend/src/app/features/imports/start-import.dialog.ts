import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { firstValueFrom } from 'rxjs';

import { PeriodResponse, ReportingPeriodsService } from '../../core/api/generated';
import { AuthService } from '../../core/auth/auth.service';
import { StartImportRequest } from './import.facade';

@Component({
  selector: 'app-start-import-dialog',
  imports: [
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatSelectModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Import values from CSV</h2>
    <mat-dialog-content>
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

      <mat-form-field appearance="outline" class="full">
        <mat-label>Organization</mat-label>
        <mat-select
          [ngModel]="organizationId()"
          (ngModelChange)="organizationId.set($event)"
          name="organization"
        >
          <mat-option [value]="''">Any in the file</mat-option>
          @for (membership of organizations(); track membership.id) {
            <mat-option [value]="membership.id">
              {{ membership.code }} — {{ membership.name }}
            </mat-option>
          }
        </mat-select>
        <mat-hint>Restricting the scope refuses rows for any other organization.</mat-hint>
      </mat-form-field>

      <input type="file" accept=".csv,text/csv" data-testid="import-file" (change)="pick($event)" />
      @if (file(); as chosen) {
        <p class="file">{{ chosen.name }} ({{ chosen.size }} bytes)</p>
      }

      <p class="format">
        Columns: <code>organization_code,metric_code,value,note</code>. The period comes from this
        dialog, not from the file.
      </p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button matButton mat-dialog-close>Cancel</button>
      <button matButton="filled" data-action="upload" [disabled]="!ready()" (click)="confirm()">
        Upload
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .full {
      width: 100%;
    }
    .file {
      font-weight: 600;
    }
    .format {
      opacity: 0.75;
      font-size: 0.85rem;
    }
  `,
})
export class StartImportDialog {
  private readonly dialogRef = inject(MatDialogRef<StartImportDialog, StartImportRequest>);
  private readonly periodsApi = inject(ReportingPeriodsService);
  private readonly auth = inject(AuthService);

  protected readonly organizations = this.auth.organizations;
  protected readonly openPeriods = signal<PeriodResponse[]>([]);
  protected readonly periodId = signal('');
  protected readonly organizationId = signal('');
  protected readonly file = signal<File | null>(null);

  constructor() {
    queueMicrotask(async () => {
      const [periods] = await Promise.all([
        firstValueFrom(this.periodsApi.listPeriods('OPEN', undefined, 0, 100)),
        firstValueFrom(this.auth.loadCurrentUser()),
      ]);
      this.openPeriods.set(periods.content);
    });
  }

  protected pick(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.file.set(input.files?.[0] ?? null);
  }

  protected ready(): boolean {
    return this.periodId() !== '' && this.file() !== null;
  }

  protected confirm(): void {
    const chosen = this.file();
    if (!chosen) {
      return;
    }
    this.dialogRef.close({
      file: chosen,
      reportingPeriodId: this.periodId(),
      organizationId: this.organizationId() || undefined,
    });
  }
}
