import { DatePipe, DecimalPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';

import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusChipComponent } from '../../shared/ui/status-chip.component';
import { ImportFacade } from './import.facade';

const POLL_INTERVAL_MS = 2000;

@Component({
  selector: 'app-import-detail',
  imports: [
    DatePipe,
    DecimalPipe,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatTableModule,
    PageHeaderComponent,
    StatusChipComponent,
    EmptyStateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './import-detail.component.html',
  styleUrl: './import-detail.component.scss',
})
export class ImportDetailComponent {
  readonly id = input.required<string>();

  protected readonly facade = inject(ImportFacade);
  private readonly destroyRef = inject(DestroyRef);
  private timer: ReturnType<typeof setTimeout> | null = null;

  protected readonly job = this.facade.detail;
  protected readonly errorColumns = ['row', 'code', 'message', 'affected'];
  protected readonly polling = signal(false);

  protected readonly running = computed(() => {
    const status = this.job()?.status;
    return status === 'PENDING' || status === 'RUNNING';
  });

  /** Interrupted is not the same as failed validation, and the screen must not blur the two. */
  protected readonly interrupted = computed(
    () => this.job()?.failureCode === 'INTERRUPTED_BY_SHUTDOWN',
  );

  protected readonly progress = computed(() => {
    const job = this.job();
    if (!job || job.submittedRows === 0) {
      return 0;
    }
    return Math.min(100, Math.round((job.processedRows / job.submittedRows) * 100));
  });

  constructor() {
    queueMicrotask(() => void this.refresh());
    this.destroyRef.onDestroy(() => this.stopPolling());
  }

  private async refresh(): Promise<void> {
    const job = await this.facade.loadDetail(this.id());
    // Polling stops the moment the job reaches a terminal state: there is nothing left to
    // learn, and a timer that never stops is a page that never goes idle.
    if (job && (job.status === 'PENDING' || job.status === 'RUNNING')) {
      this.polling.set(true);
      this.timer = setTimeout(() => void this.refresh(), POLL_INTERVAL_MS);
    } else {
      this.stopPolling();
    }
  }

  private stopPolling(): void {
    this.polling.set(false);
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
  }
}
