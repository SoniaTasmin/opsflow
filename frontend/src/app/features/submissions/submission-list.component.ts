import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { Router, RouterLink } from '@angular/router';

import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusChipComponent } from '../../shared/ui/status-chip.component';
import { SubmissionFacade, SubmissionStatus } from './submission.facade';
import { StartSubmissionDialog, StartSubmissionResult } from './start-submission.dialog';

type StatusFilter = SubmissionStatus | 'ALL';

@Component({
  selector: 'app-submission-list',
  imports: [
    DatePipe,
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
  templateUrl: './submission-list.component.html',
  styleUrl: './submission-list.component.scss',
})
export class SubmissionListComponent {
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  protected readonly facade = inject(SubmissionFacade);

  protected readonly columns = ['period', 'organization', 'status', 'values', 'updated', 'actions'];
  protected readonly statusFilter = signal<StatusFilter>('ALL');
  protected readonly page = signal(0);
  protected readonly size = signal(20);

  constructor() {
    void this.reload();
  }

  protected reload(): Promise<void> {
    const status = this.statusFilter();
    return this.facade.load({
      status: status === 'ALL' ? undefined : status,
      page: this.page(),
      size: this.size(),
    });
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

  protected start(): void {
    this.dialog
      .open(StartSubmissionDialog, { width: '28rem' })
      .afterClosed()
      .subscribe(async (result: StartSubmissionResult | undefined) => {
        if (!result) {
          return;
        }
        const id = await this.facade.create(result.organizationId, result.reportingPeriodId);
        if (id) {
          await this.router.navigate(['/submissions', id]);
        }
      });
  }
}
