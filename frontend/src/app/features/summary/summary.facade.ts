import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import {
  PeriodResponse,
  PeriodSummaryResponse,
  ReportingPeriodsService,
} from '../../core/api/generated';
import { ApiError } from '../../core/error/api-error';
import { ErrorNotifier } from '../../core/error/error-notifier.service';

@Injectable({ providedIn: 'root' })
export class SummaryFacade {
  private readonly api = inject(ReportingPeriodsService);
  private readonly notifier = inject(ErrorNotifier);

  readonly periods = signal<PeriodResponse[]>([]);
  readonly summary = signal<PeriodSummaryResponse | null>(null);
  readonly loading = signal(false);
  readonly failed = signal(false);

  /** Returns the period to show first: the one being reported on now, else the most recent. */
  async loadPeriods(): Promise<string | null> {
    this.loading.set(true);
    try {
      const page = await firstValueFrom(this.api.listPeriods(undefined, undefined, 0, 100));
      this.periods.set(page.content);
      const open = page.content.find((period) => period.status === 'OPEN');
      return (open ?? page.content[0])?.id ?? null;
    } catch (error) {
      this.report(error);
      return null;
    } finally {
      this.loading.set(false);
    }
  }

  async load(periodId: string): Promise<void> {
    this.loading.set(true);
    this.failed.set(false);
    try {
      this.summary.set(await firstValueFrom(this.api.getPeriodSummary(periodId)));
    } catch (error) {
      // The previous period's figures must not sit under a new period's heading.
      this.summary.set(null);
      this.failed.set(true);
      this.report(error);
    } finally {
      this.loading.set(false);
    }
  }

  private report(error: unknown): void {
    if (error instanceof ApiError) {
      this.notifier.notify(error);
    }
  }
}
