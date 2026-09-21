import { Injectable, inject, signal } from '@angular/core';
import { Observable, firstValueFrom } from 'rxjs';

import {
  CreatePeriodRequest,
  PeriodDetailResponse,
  PeriodResponse,
  ReportingPeriodsService,
} from '../../../core/api/generated';
import { ApiError } from '../../../core/error/api-error';
import { ErrorNotifier } from '../../../core/error/error-notifier.service';

@Injectable({ providedIn: 'root' })
export class PeriodFacade {
  private readonly api = inject(ReportingPeriodsService);
  private readonly notifier = inject(ErrorNotifier);

  readonly periods = signal<PeriodResponse[]>([]);
  readonly total = signal(0);
  readonly loading = signal(false);
  readonly detail = signal<PeriodDetailResponse | null>(null);

  async load(page = 0, size = 20, status?: 'DRAFT' | 'OPEN' | 'CLOSED'): Promise<void> {
    this.loading.set(true);
    try {
      const result = await firstValueFrom(this.api.listPeriods(status, undefined, page, size));
      this.periods.set(result.content ?? []);
      this.total.set(result.totalElements ?? 0);
    } catch (error) {
      this.report(error);
    } finally {
      this.loading.set(false);
    }
  }

  async loadDetail(id: string): Promise<void> {
    this.loading.set(true);
    try {
      this.detail.set(await firstValueFrom(this.api.getPeriod(id)));
    } catch (error) {
      this.report(error);
    } finally {
      this.loading.set(false);
    }
  }

  create(request: CreatePeriodRequest): Promise<boolean> {
    return this.run(this.api.createPeriod(request), 'Period created');
  }

  setMetricScope(
    id: string,
    metrics: { metricId: string; required: boolean; displayOrder: number }[],
    version: number,
  ): Promise<boolean> {
    return this.run(this.api.setPeriodMetricScope(id, { metrics, version }), 'Metric scope saved');
  }

  setParticipants(id: string, organizationIds: string[], version: number): Promise<boolean> {
    return this.run(
      this.api.setPeriodParticipants(id, { organizationIds, version }),
      'Participating organizations saved',
    );
  }

  open(id: string, version: number): Promise<boolean> {
    return this.run(this.api.openPeriod(id, { version }), 'Period opened');
  }

  close(id: string, version: number): Promise<boolean> {
    return this.run(this.api.closePeriod(id, { version }), 'Period closed');
  }

  private async run(call: Observable<unknown>, success: string): Promise<boolean> {
    try {
      await firstValueFrom(call);
      this.notifier.success(success);
      return true;
    } catch (error) {
      this.report(error);
      return false;
    }
  }

  private report(error: unknown): void {
    if (error instanceof ApiError) {
      this.notifier.notify(error);
    }
  }
}
