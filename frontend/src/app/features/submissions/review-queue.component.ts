import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';

import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusChipComponent } from '../../shared/ui/status-chip.component';
import { SubmissionFacade, SubmissionStatus } from './submission.facade';

@Component({
  selector: 'app-review-queue',
  imports: [
    DatePipe,
    FormsModule,
    RouterLink,
    MatTableModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatPaginatorModule,
    PageHeaderComponent,
    EmptyStateComponent,
    StatusChipComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './review-queue.component.html',
  styleUrl: './submission-list.component.scss',
})
export class ReviewQueueComponent {
  protected readonly facade = inject(SubmissionFacade);

  protected readonly columns = ['period', 'organization', 'status', 'submitted', 'actions'];
  protected readonly statusFilter = signal<SubmissionStatus>('SUBMITTED');
  protected readonly page = signal(0);
  protected readonly size = signal(20);

  constructor() {
    void this.reload();
  }

  protected reload(): Promise<void> {
    return this.facade.load({
      status: this.statusFilter(),
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
}
