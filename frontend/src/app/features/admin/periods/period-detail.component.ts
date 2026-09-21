import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import {
  MetricResponse,
  MetricsService,
  OrganizationResponse,
  OrganizationsService,
} from '../../../core/api/generated';
import { ConfirmService } from '../../../shared/ui/confirm.service';
import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { StatusChipComponent } from '../../../shared/ui/status-chip.component';
import { PeriodFacade } from './period.facade';

@Component({
  selector: 'app-period-detail',
  imports: [
    FormsModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatListModule,
    MatCheckboxModule,
    MatTooltipModule,
    PageHeaderComponent,
    StatusChipComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './period-detail.component.html',
  styleUrl: './period-detail.component.scss',
})
export class PeriodDetailComponent {
  readonly id = input.required<string>();

  private readonly metricsApi = inject(MetricsService);
  private readonly organizationsApi = inject(OrganizationsService);
  private readonly confirm = inject(ConfirmService);
  protected readonly facade = inject(PeriodFacade);

  protected readonly availableMetrics = signal<MetricResponse[]>([]);
  protected readonly availableOrganizations = signal<OrganizationResponse[]>([]);
  protected readonly selectedMetricIds = signal<Set<string>>(new Set());
  protected readonly requiredMetricIds = signal<Set<string>>(new Set());
  protected readonly selectedOrganizationIds = signal<Set<string>>(new Set());

  protected readonly period = computed(() => this.facade.detail()?.period ?? null);
  protected readonly isDraft = computed(() => this.period()?.status === 'DRAFT');
  protected readonly canOpen = computed(
    () =>
      this.isDraft() &&
      this.selectedMetricIds().size > 0 &&
      this.selectedOrganizationIds().size > 0,
  );

  constructor() {
    queueMicrotask(async () => {
      await this.refresh();
      // Only active entries can be added to a scope, so the pickers offer only those.
      const [metrics, organizations] = await Promise.all([
        firstValueFrom(this.metricsApi.listMetrics(undefined, true, 0, 100)),
        firstValueFrom(this.organizationsApi.listOrganizations(undefined, 'ACTIVE', 0, 100)),
      ]);
      this.availableMetrics.set(metrics.content ?? []);
      this.availableOrganizations.set(organizations.content ?? []);
    });
  }

  private async refresh(): Promise<void> {
    await this.facade.loadDetail(this.id());
    const detail = this.facade.detail();
    this.selectedMetricIds.set(new Set(detail?.metrics?.map((metric) => metric.metricId) ?? []));
    this.requiredMetricIds.set(
      new Set(
        detail?.metrics?.filter((metric) => metric.required).map((metric) => metric.metricId) ?? [],
      ),
    );
    this.selectedOrganizationIds.set(
      new Set(detail?.organizations?.map((organization) => organization.organizationId) ?? []),
    );
  }

  protected toggle(
    set: ReturnType<typeof signal<Set<string>>>,
    id: string,
    checked: boolean,
  ): void {
    const next = new Set(set());
    if (checked) {
      next.add(id);
    } else {
      next.delete(id);
    }
    set.set(next);
  }

  protected toggleMetric(id: string, checked: boolean): void {
    this.toggle(this.selectedMetricIds, id, checked);
    if (!checked) {
      this.toggle(this.requiredMetricIds, id, false);
    }
  }

  protected async saveMetricScope(): Promise<void> {
    const period = this.period();
    if (!period) {
      return;
    }
    const metrics = [...this.selectedMetricIds()].map((metricId, index) => ({
      metricId,
      required: this.requiredMetricIds().has(metricId),
      displayOrder: index,
    }));
    if (await this.facade.setMetricScope(period.id, metrics, period.version)) {
      await this.refresh();
    }
  }

  protected async saveParticipants(): Promise<void> {
    const period = this.period();
    if (!period) {
      return;
    }
    if (
      await this.facade.setParticipants(
        period.id,
        [...this.selectedOrganizationIds()],
        period.version,
      )
    ) {
      await this.refresh();
    }
  }

  protected open(): void {
    const period = this.period();
    if (!period) {
      return;
    }
    this.confirm
      .ask({
        title: `Open ${period.code}?`,
        message:
          'Opening freezes both the metric scope and the participating organizations. Neither can be changed afterwards.',
        confirmLabel: 'Open period',
      })
      .subscribe(async (confirmed) => {
        if (confirmed && (await this.facade.open(period.id, period.version))) {
          await this.refresh();
        }
      });
  }

  protected close(): void {
    const period = this.period();
    if (!period) {
      return;
    }
    this.confirm
      .ask({
        title: `Close ${period.code}?`,
        message: 'Closing ends the reporting window for this period.',
        confirmLabel: 'Close period',
        destructive: true,
      })
      .subscribe(async (confirmed) => {
        if (confirmed && (await this.facade.close(period.id, period.version))) {
          await this.refresh();
        }
      });
  }
}
