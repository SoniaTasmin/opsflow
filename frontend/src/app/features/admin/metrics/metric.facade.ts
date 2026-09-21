import { Injectable, inject, signal } from '@angular/core';
import { Observable, firstValueFrom } from 'rxjs';

import { CreateMetricRequest, MetricResponse, MetricsService } from '../../../core/api/generated';
import { ApiError } from '../../../core/error/api-error';
import { ErrorNotifier } from '../../../core/error/error-notifier.service';

@Injectable({ providedIn: 'root' })
export class MetricFacade {
  private readonly api = inject(MetricsService);
  private readonly notifier = inject(ErrorNotifier);

  readonly metrics = signal<MetricResponse[]>([]);
  readonly total = signal(0);
  readonly loading = signal(false);

  async load(page = 0, size = 20, q?: string, active?: boolean): Promise<void> {
    this.loading.set(true);
    try {
      const result = await firstValueFrom(this.api.listMetrics(q || undefined, active, page, size));
      this.metrics.set(result.content ?? []);
      this.total.set(result.totalElements ?? 0);
    } catch (error) {
      this.report(error);
    } finally {
      this.loading.set(false);
    }
  }

  create(request: CreateMetricRequest): Promise<boolean> {
    return this.run(this.api.createMetric(request), 'Metric created');
  }

  update(
    metric: MetricResponse,
    changes: {
      name: string;
      description?: string;
      unit: string;
      minValue?: number;
      maxValue?: number;
    },
  ): Promise<boolean> {
    return this.run(
      this.api.updateMetric(metric.id, { ...changes, version: metric.version }),
      'Metric updated',
    );
  }

  archive(metric: MetricResponse): Promise<boolean> {
    return this.run(
      this.api.archiveMetric(metric.id, { version: metric.version }),
      'Metric archived',
    );
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
