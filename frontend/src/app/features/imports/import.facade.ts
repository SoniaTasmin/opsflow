import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import {
  ImportJobResponse,
  ImportRowErrorResponse,
  ImportsService,
} from '../../core/api/generated';
import { ApiError } from '../../core/error/api-error';
import { ErrorNotifier } from '../../core/error/error-notifier.service';

export type ImportStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';

export interface StartImportRequest {
  file: File;
  reportingPeriodId: string;
  organizationId?: string;
}

@Injectable({ providedIn: 'root' })
export class ImportFacade {
  private readonly api = inject(ImportsService);
  private readonly notifier = inject(ErrorNotifier);

  readonly imports = signal<ImportJobResponse[]>([]);
  readonly total = signal(0);
  readonly loading = signal(false);
  readonly detail = signal<ImportJobResponse | null>(null);
  readonly errors = signal<ImportRowErrorResponse[]>([]);

  async load(page = 0, size = 20, status?: ImportStatus): Promise<void> {
    this.loading.set(true);
    try {
      const result = await firstValueFrom(this.api.listImports(undefined, status, page, size));
      this.imports.set(result.content);
      this.total.set(result.totalElements);
    } catch (error) {
      this.report(error);
    } finally {
      this.loading.set(false);
    }
  }

  async loadDetail(id: string): Promise<ImportJobResponse | null> {
    try {
      const job = await firstValueFrom(this.api.getImport(id));
      this.detail.set(job);
      if (job.status === 'COMPLETED' || job.status === 'FAILED') {
        this.errors.set(await firstValueFrom(this.api.getImportErrors(id)));
      }
      return job;
    } catch (error) {
      this.report(error);
      return null;
    }
  }

  async start(request: StartImportRequest): Promise<string | null> {
    try {
      // One key per upload the user asked for: pressing upload twice is two imports, while
      // retrying the same call is not.
      const job = await firstValueFrom(
        this.api.startImport(
          request.reportingPeriodId,
          request.file,
          request.organizationId,
          crypto.randomUUID(),
        ),
      );
      this.notifier.success('Import started');
      return job.id;
    } catch (error) {
      this.report(error);
      return null;
    }
  }

  private report(error: unknown): void {
    if (error instanceof ApiError) {
      this.notifier.notify(error);
    }
  }
}
