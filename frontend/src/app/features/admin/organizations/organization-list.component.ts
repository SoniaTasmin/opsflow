import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';

import { OrganizationResponse } from '../../../core/api/generated';
import { ConfirmService } from '../../../shared/ui/confirm.service';
import { EmptyStateComponent } from '../../../shared/ui/empty-state.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { StatusChipComponent } from '../../../shared/ui/status-chip.component';
import { OrganizationFacade } from './organization.facade';
import { OrganizationFormDialog, OrganizationFormResult } from './organization-form.dialog';

@Component({
  selector: 'app-organization-list',
  imports: [
    FormsModule,
    RouterLink,
    MatTableModule,
    MatButtonModule,
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
  templateUrl: './organization-list.component.html',
  styleUrl: './organization-list.component.scss',
})
export class OrganizationListComponent {
  private readonly dialog = inject(MatDialog);
  private readonly confirm = inject(ConfirmService);
  protected readonly facade = inject(OrganizationFacade);

  protected readonly columns = ['code', 'name', 'status', 'members', 'actions'];
  protected readonly search = signal('');
  protected readonly page = signal(0);
  protected readonly size = signal(20);

  constructor() {
    void this.facade.load();
  }

  protected applySearch(): void {
    this.page.set(0);
    void this.facade.load(0, this.size(), this.search());
  }

  protected changePage(event: PageEvent): void {
    this.page.set(event.pageIndex);
    this.size.set(event.pageSize);
    void this.facade.load(event.pageIndex, event.pageSize, this.search());
  }

  protected openCreate(): void {
    this.dialog
      .open(OrganizationFormDialog)
      .afterClosed()
      .subscribe(async (result?: OrganizationFormResult) => {
        if (result && (await this.facade.create(result.code, result.name))) {
          await this.reload();
        }
      });
  }

  protected openRename(organization: OrganizationResponse): void {
    this.dialog
      .open(OrganizationFormDialog, { data: organization })
      .afterClosed()
      .subscribe(async (result?: OrganizationFormResult) => {
        if (result && (await this.facade.rename(organization, result.name))) {
          await this.reload();
        }
      });
  }

  protected archive(organization: OrganizationResponse): void {
    this.confirm
      .ask({
        title: `Archive ${organization.code}?`,
        message:
          'Archiving stops future participation and keeps history intact. It cannot be undone from this screen.',
        confirmLabel: 'Archive',
        destructive: true,
      })
      .subscribe(async (confirmed) => {
        if (confirmed && (await this.facade.archive(organization))) {
          await this.reload();
        }
      });
  }

  private reload(): Promise<void> {
    return this.facade.load(this.page(), this.size(), this.search());
  }
}
