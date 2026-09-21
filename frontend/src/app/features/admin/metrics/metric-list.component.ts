import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';

import { MetricResponse } from '../../../core/api/generated';
import { ConfirmService } from '../../../shared/ui/confirm.service';
import { EmptyStateComponent } from '../../../shared/ui/empty-state.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { StatusChipComponent } from '../../../shared/ui/status-chip.component';
import { MetricFacade } from './metric.facade';
import { MetricFormDialog, MetricFormResult } from './metric-form.dialog';

@Component({
  selector: 'app-metric-list',
  imports: [
    FormsModule,
    MatTableModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatTooltipModule,
    PageHeaderComponent,
    EmptyStateComponent,
    StatusChipComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './metric-list.component.html',
  styleUrl: './metric-list.component.scss',
})
export class MetricListComponent {
  private readonly dialog = inject(MatDialog);
  private readonly confirm = inject(ConfirmService);
  protected readonly facade = inject(MetricFacade);

  protected readonly columns = ['code', 'name', 'unit', 'valueType', 'range', 'status', 'actions'];
  protected readonly search = signal('');
  protected readonly activeFilter = signal<'all' | 'active' | 'archived'>('all');
  protected readonly page = signal(0);
  protected readonly size = signal(20);

  constructor() {
    void this.facade.load();
  }

  protected reload(): Promise<void> {
    const filter = this.activeFilter();
    const active = filter === 'all' ? undefined : filter === 'active';
    return this.facade.load(this.page(), this.size(), this.search(), active);
  }

  protected applyFilters(): void {
    this.page.set(0);
    void this.reload();
  }

  protected changePage(event: PageEvent): void {
    this.page.set(event.pageIndex);
    this.size.set(event.pageSize);
    void this.reload();
  }

  protected rangeOf(metric: MetricResponse): string {
    if (metric.minValue == null && metric.maxValue == null) {
      return '—';
    }
    return `${metric.minValue ?? '−∞'} … ${metric.maxValue ?? '∞'}`;
  }

  protected openCreate(): void {
    this.dialog
      .open(MetricFormDialog)
      .afterClosed()
      .subscribe(async (result?: MetricFormResult) => {
        if (result && (await this.facade.create(result))) {
          await this.reload();
        }
      });
  }

  protected openEdit(metric: MetricResponse): void {
    this.dialog
      .open(MetricFormDialog, { data: metric })
      .afterClosed()
      .subscribe(async (result?: MetricFormResult) => {
        if (result && (await this.facade.update(metric, result))) {
          await this.reload();
        }
      });
  }

  protected archive(metric: MetricResponse): void {
    this.confirm
      .ask({
        title: `Archive ${metric.code}?`,
        message:
          'Archived metrics cannot be added to new period scopes. This is refused while an open period collects the metric.',
        confirmLabel: 'Archive',
        destructive: true,
      })
      .subscribe(async (confirmed) => {
        if (confirmed && (await this.facade.archive(metric))) {
          await this.reload();
        }
      });
  }
}
