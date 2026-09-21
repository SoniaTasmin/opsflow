import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';

import { CreatePeriodRequest } from '../../../core/api/generated';
import { EmptyStateComponent } from '../../../shared/ui/empty-state.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { StatusChipComponent } from '../../../shared/ui/status-chip.component';
import { PeriodFacade } from './period.facade';
import { PeriodFormDialog } from './period-form.dialog';

type StatusFilter = 'ALL' | 'DRAFT' | 'OPEN' | 'CLOSED';

@Component({
  selector: 'app-period-list',
  imports: [
    FormsModule,
    RouterLink,
    MatTableModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    MatPaginatorModule,
    PageHeaderComponent,
    EmptyStateComponent,
    StatusChipComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './period-list.component.html',
  styleUrl: './period-list.component.scss',
})
export class PeriodListComponent {
  private readonly dialog = inject(MatDialog);
  protected readonly facade = inject(PeriodFacade);

  protected readonly columns = ['code', 'type', 'dates', 'deadline', 'scope', 'status', 'actions'];
  protected readonly statusFilter = signal<StatusFilter>('ALL');
  protected readonly page = signal(0);
  protected readonly size = signal(20);

  constructor() {
    void this.facade.load();
  }

  protected reload(): Promise<void> {
    const status = this.statusFilter();
    return this.facade.load(this.page(), this.size(), status === 'ALL' ? undefined : status);
  }

  protected applyFilter(): void {
    this.page.set(0);
    void this.reload();
  }

  protected changePage(event: PageEvent): void {
    this.page.set(event.pageIndex);
    this.size.set(event.pageSize);
    void this.reload();
  }

  protected openCreate(): void {
    this.dialog
      .open(PeriodFormDialog)
      .afterClosed()
      .subscribe(async (result?: CreatePeriodRequest) => {
        if (result && (await this.facade.create(result))) {
          await this.reload();
        }
      });
  }
}
