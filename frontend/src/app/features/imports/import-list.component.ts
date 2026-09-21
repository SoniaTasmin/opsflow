import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { Router, RouterLink } from '@angular/router';

import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusChipComponent } from '../../shared/ui/status-chip.component';
import { ImportFacade, StartImportRequest } from './import.facade';
import { StartImportDialog } from './start-import.dialog';

@Component({
  selector: 'app-import-list',
  imports: [
    DatePipe,
    RouterLink,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatPaginatorModule,
    PageHeaderComponent,
    EmptyStateComponent,
    StatusChipComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './import-list.component.html',
  styleUrl: './import-list.component.scss',
})
export class ImportListComponent {
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  protected readonly facade = inject(ImportFacade);

  protected readonly columns = ['file', 'status', 'rows', 'started', 'actions'];
  protected readonly page = signal(0);
  protected readonly size = signal(20);

  constructor() {
    void this.facade.load();
  }

  protected changePage(event: PageEvent): void {
    this.page.set(event.pageIndex);
    this.size.set(event.pageSize);
    void this.facade.load(this.page(), this.size());
  }

  protected start(): void {
    this.dialog
      .open(StartImportDialog, { width: '32rem' })
      .afterClosed()
      .subscribe(async (request: StartImportRequest | undefined) => {
        if (!request) {
          return;
        }
        const id = await this.facade.start(request);
        if (id) {
          await this.router.navigate(['/imports', id]);
        }
      });
  }
}
