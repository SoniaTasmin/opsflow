import { Injectable, inject, signal } from '@angular/core';
import { Observable, firstValueFrom } from 'rxjs';

import {
  SubmissionDetailResponse,
  SubmissionResponse,
  SubmissionsService,
  TransitionResponse,
} from '../../core/api/generated';
import { ApiError } from '../../core/error/api-error';
import { ErrorNotifier } from '../../core/error/error-notifier.service';

export type SubmissionStatus = 'DRAFT' | 'SUBMITTED' | 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED';

export interface ValueInput {
  metricId: string;
  value: number;
  note?: string;
}

@Injectable({ providedIn: 'root' })
export class SubmissionFacade {
  private readonly api = inject(SubmissionsService);
  private readonly notifier = inject(ErrorNotifier);

  readonly submissions = signal<SubmissionResponse[]>([]);
  readonly total = signal(0);
  readonly loading = signal(false);
  readonly detail = signal<SubmissionDetailResponse | null>(null);
  readonly history = signal<TransitionResponse[]>([]);

  async load(options: ListOptions = {}): Promise<void> {
    this.loading.set(true);
    try {
      const page = await firstValueFrom(
        this.api.listSubmissions(
          options.organizationId,
          options.reportingPeriodId,
          options.status,
          options.page ?? 0,
          options.size ?? 20,
        ),
      );
      this.submissions.set(page.content);
      this.total.set(page.totalElements);
    } catch (error) {
      this.report(error);
    } finally {
      this.loading.set(false);
    }
  }

  async loadDetail(id: string): Promise<void> {
    this.loading.set(true);
    try {
      const [detail, history] = await Promise.all([
        firstValueFrom(this.api.getSubmission(id)),
        firstValueFrom(this.api.getSubmissionHistory(id)),
      ]);
      this.detail.set(detail);
      this.history.set(history);
    } catch (error) {
      this.report(error);
    } finally {
      this.loading.set(false);
    }
  }

  async create(organizationId: string, reportingPeriodId: string): Promise<string | null> {
    try {
      const created = await firstValueFrom(
        this.api.createSubmission({ organizationId, reportingPeriodId }),
      );
      this.notifier.success('Submission started');
      return created.id;
    } catch (error) {
      this.report(error);
      return null;
    }
  }

  saveValues(id: string, values: ValueInput[], version: number): Promise<boolean> {
    return this.run(id, this.api.saveSubmissionValues(id, { values, version }), 'Values saved');
  }

  submit(id: string, version: number): Promise<boolean> {
    return this.run(id, this.api.submitSubmission(id, { version }), 'Sent for review');
  }

  withdraw(id: string, version: number): Promise<boolean> {
    return this.run(id, this.api.withdrawSubmission(id, { version }), 'Pulled back into draft');
  }

  claim(id: string, version: number): Promise<boolean> {
    return this.run(id, this.api.claimSubmission(id, { version }), 'Review claimed');
  }

  release(id: string, version: number): Promise<boolean> {
    return this.run(id, this.api.releaseSubmission(id, { version }), 'Returned to the queue');
  }

  approve(id: string, version: number): Promise<boolean> {
    return this.run(id, this.api.approveSubmission(id, { version }), 'Approved');
  }

  reject(id: string, reason: string, version: number): Promise<boolean> {
    return this.run(
      id,
      this.api.rejectSubmission(id, { reason, version }),
      'Returned for correction',
    );
  }

  private async run(
    id: string,
    call: Observable<SubmissionDetailResponse>,
    success: string,
  ): Promise<boolean> {
    try {
      const updated = await firstValueFrom(call);
      this.detail.set(updated);
      this.history.set(await firstValueFrom(this.api.getSubmissionHistory(id)));
      this.notifier.success(success);
      return true;
    } catch (error) {
      await this.reportAndReload(id, error);
      return false;
    }
  }

  /**
   * A stale version means someone else moved the submission while this screen was open. The
   * only safe answer is to show them what is actually there -- retrying with the version they
   * had would overwrite a change they have never seen.
   */
  private async reportAndReload(id: string, error: unknown): Promise<void> {
    this.report(error);
    if (error instanceof ApiError && (error.isVersionConflict || error.status === 409)) {
      await this.loadDetail(id);
    }
  }

  private report(error: unknown): void {
    if (error instanceof ApiError) {
      this.notifier.notify(error);
    }
  }
}

export interface ListOptions {
  organizationId?: string;
  reportingPeriodId?: string;
  status?: SubmissionStatus;
  page?: number;
  size?: number;
}
