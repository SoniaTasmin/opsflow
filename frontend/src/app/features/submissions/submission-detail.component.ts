import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';

import { SubmissionMetricResponse } from '../../core/api/generated';
import { AuthService } from '../../core/auth/auth.service';
import { ConfirmService } from '../../shared/ui/confirm.service';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusChipComponent } from '../../shared/ui/status-chip.component';
import { RejectDialog } from './reject.dialog';
import { SubmissionFacade } from './submission.facade';
import { SubmissionTimelineComponent } from './submission-timeline.component';

interface ValueField {
  // A number input's value accessor emits a number, or null when it is cleared -- never a string.
  value: number | null;
  note: string;
}

@Component({
  selector: 'app-submission-detail',
  imports: [
    FormsModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatTooltipModule,
    PageHeaderComponent,
    StatusChipComponent,
    SubmissionTimelineComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './submission-detail.component.html',
  styleUrl: './submission-detail.component.scss',
})
export class SubmissionDetailComponent {
  readonly id = input.required<string>();

  private readonly dialog = inject(MatDialog);
  private readonly confirm = inject(ConfirmService);
  private readonly auth = inject(AuthService);
  protected readonly facade = inject(SubmissionFacade);

  protected readonly fields = signal<Record<string, ValueField>>({});

  protected readonly submission = this.facade.detail;
  protected readonly canReview = computed(() => this.auth.hasAnyRole(['REVIEWER', 'ADMIN']));
  protected readonly status = computed(() => this.submission()?.status ?? '');
  protected readonly editable = computed(() => this.submission()?.editable ?? false);

  constructor() {
    queueMicrotask(() => void this.refresh());
  }

  private async refresh(): Promise<void> {
    await this.facade.loadDetail(this.id());
    const detail = this.submission();
    if (!detail) {
      return;
    }
    const byMetric = new Map(detail.values.map((value) => [value.metricId, value]));
    const fields: Record<string, ValueField> = {};
    for (const metric of detail.metrics) {
      const existing = byMetric.get(metric.metricId);
      fields[metric.metricId] = {
        value: existing ? existing.value : null,
        note: existing?.note ?? '',
      };
    }
    this.fields.set(fields);
  }

  protected field(metricId: string): ValueField {
    return this.fields()[metricId] ?? { value: null, note: '' };
  }

  protected setValue(metricId: string, value: number | null): void {
    this.fields.set({ ...this.fields(), [metricId]: { ...this.field(metricId), value } });
  }

  protected setNote(metricId: string, note: string): void {
    this.fields.set({ ...this.fields(), [metricId]: { ...this.field(metricId), note } });
  }

  protected hint(metric: SubmissionMetricResponse): string {
    // Absent bounds arrive as null rather than undefined, so == is deliberate here.
    const min = metric.minValue ?? null;
    const max = metric.maxValue ?? null;
    const bounds =
      min !== null && max !== null
        ? `${min} – ${max}`
        : min !== null
          ? `at least ${min}`
          : max !== null
            ? `at most ${max}`
            : '';
    return bounds ? `${metric.unit} · ${bounds}` : metric.unit;
  }

  protected async save(): Promise<void> {
    const detail = this.submission();
    if (!detail) {
      return;
    }
    // A metric left blank is cleared, which is what the endpoint's replace semantics mean.
    const values = detail.metrics
      .map((metric) => ({ metric, field: this.field(metric.metricId) }))
      .filter((entry) => entry.field.value !== null && !Number.isNaN(entry.field.value))
      .map((entry) => ({
        metricId: entry.metric.metricId,
        value: entry.field.value as number,
        note: entry.field.note.trim() || undefined,
      }));

    if (await this.facade.saveValues(detail.id, values, detail.version)) {
      await this.refresh();
    }
  }

  protected async submit(): Promise<void> {
    await this.run((id, version) => this.facade.submit(id, version));
  }

  protected withdraw(): void {
    this.ask(
      'Pull this submission back?',
      'It returns to draft and leaves the review queue.',
      'Withdraw',
      (id, version) => this.facade.withdraw(id, version),
    );
  }

  protected async claim(): Promise<void> {
    await this.run((id, version) => this.facade.claim(id, version));
  }

  protected async release(): Promise<void> {
    await this.run((id, version) => this.facade.release(id, version));
  }

  protected approve(): void {
    this.ask(
      'Approve this submission?',
      'Approved submissions can no longer be edited by anyone.',
      'Approve',
      (id, version) => this.facade.approve(id, version),
    );
  }

  protected reject(): void {
    const detail = this.submission();
    if (!detail) {
      return;
    }
    this.dialog
      .open(RejectDialog, { width: '30rem' })
      .afterClosed()
      .subscribe(async (reason: string | undefined) => {
        if (reason && (await this.facade.reject(detail.id, reason, detail.version))) {
          await this.refresh();
        }
      });
  }

  private ask(
    title: string,
    message: string,
    confirmLabel: string,
    command: (id: string, version: number) => Promise<boolean>,
  ): void {
    this.confirm.ask({ title, message, confirmLabel }).subscribe(async (confirmed) => {
      if (confirmed) {
        await this.run(command);
      }
    });
  }

  private async run(command: (id: string, version: number) => Promise<boolean>): Promise<void> {
    const detail = this.submission();
    if (detail && (await command(detail.id, detail.version))) {
      await this.refresh();
    }
  }
}
