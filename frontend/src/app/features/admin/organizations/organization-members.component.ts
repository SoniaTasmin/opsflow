import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatListModule } from '@angular/material/list';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, filter } from 'rxjs';

import { OrganizationResponse, UserResponse } from '../../../core/api/generated';
import { ConfirmService } from '../../../shared/ui/confirm.service';
import { EmptyStateComponent } from '../../../shared/ui/empty-state.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { OrganizationFacade } from './organization.facade';

@Component({
  selector: 'app-organization-members',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatAutocompleteModule,
    MatListModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    PageHeaderComponent,
    EmptyStateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './organization-members.component.html',
  styleUrl: './organization-members.component.scss',
})
export class OrganizationMembersComponent {
  readonly id = input.required<string>();

  private readonly confirm = inject(ConfirmService);
  protected readonly facade = inject(OrganizationFacade);

  protected readonly organization = signal<OrganizationResponse | null>(null);
  protected readonly searchControl = new FormControl('', { nonNullable: true });
  protected readonly candidates = signal<UserResponse[]>([]);

  constructor() {
    this.searchControl.valueChanges
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        filter((term) => term.length >= 2),
      )
      .subscribe(async (term) => this.candidates.set(await this.facade.searchUsers(term)));

    queueMicrotask(async () => {
      this.organization.set(await this.facade.findOne(this.id()));
      await this.facade.loadMembers(this.id());
    });
  }

  protected async add(user: UserResponse): Promise<void> {
    this.searchControl.setValue('');
    this.candidates.set([]);
    await this.facade.addMember(this.id(), user.id);
  }

  protected remove(userId: string, displayName: string): void {
    this.confirm
      .ask({
        title: 'Remove member?',
        message: `${displayName} will lose access to this organization's data.`,
        confirmLabel: 'Remove',
        destructive: true,
      })
      .subscribe(async (confirmed) => {
        if (confirmed) {
          await this.facade.removeMember(this.id(), userId);
        }
      });
  }
}
