import { Injectable, inject, signal } from '@angular/core';
import { Observable, firstValueFrom } from 'rxjs';

import {
  MemberResponse,
  OrganizationResponse,
  OrganizationsService,
  UserResponse,
  UsersService,
} from '../../../core/api/generated';
import { ApiError } from '../../../core/error/api-error';
import { ErrorNotifier } from '../../../core/error/error-notifier.service';

@Injectable({ providedIn: 'root' })
export class OrganizationFacade {
  private readonly api = inject(OrganizationsService);
  private readonly usersApi = inject(UsersService);
  private readonly notifier = inject(ErrorNotifier);

  readonly organizations = signal<OrganizationResponse[]>([]);
  readonly total = signal(0);
  readonly loading = signal(false);
  readonly members = signal<MemberResponse[]>([]);

  async load(page = 0, size = 20, q?: string): Promise<void> {
    this.loading.set(true);
    try {
      const result = await firstValueFrom(
        this.api.listOrganizations(q || undefined, undefined, page, size),
      );
      this.organizations.set(result.content ?? []);
      this.total.set(result.totalElements ?? 0);
    } catch (error) {
      this.report(error);
    } finally {
      this.loading.set(false);
    }
  }

  create(code: string, name: string): Promise<boolean> {
    return this.run(this.api.createOrganization({ code, name }), 'Organization created');
  }

  rename(organization: OrganizationResponse, name: string): Promise<boolean> {
    return this.run(
      this.api.renameOrganization(organization.id, { name, version: organization.version }),
      'Organization renamed',
    );
  }

  archive(organization: OrganizationResponse): Promise<boolean> {
    return this.run(
      this.api.archiveOrganization(organization.id, { version: organization.version }),
      'Organization archived',
    );
  }

  async loadMembers(organizationId: string): Promise<void> {
    try {
      this.members.set(await firstValueFrom(this.api.listMembers(organizationId)));
    } catch (error) {
      this.report(error);
    }
  }

  async addMember(organizationId: string, userId: string): Promise<boolean> {
    const added = await this.run(this.api.addMember(organizationId, userId), 'Member added');
    if (added) {
      await this.loadMembers(organizationId);
    }
    return added;
  }

  async removeMember(organizationId: string, userId: string): Promise<boolean> {
    const removed = await this.run(this.api.removeMember(organizationId, userId), 'Member removed');
    if (removed) {
      await this.loadMembers(organizationId);
    }
    return removed;
  }

  async findOne(organizationId: string): Promise<OrganizationResponse | null> {
    try {
      return await firstValueFrom(this.api.getOrganization(organizationId));
    } catch (error) {
      this.report(error);
      return null;
    }
  }

  async searchUsers(q: string): Promise<UserResponse[]> {
    try {
      const result = await firstValueFrom(this.usersApi.searchUsers(q || undefined, 0, 10));
      return result.content ?? [];
    } catch (error) {
      this.report(error);
      return [];
    }
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
