import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { SubmissionDetailResponse, SubmissionsService } from '../../core/api/generated';
import { ApiError } from '../../core/error/api-error';
import { ErrorNotifier } from '../../core/error/error-notifier.service';
import { SubmissionFacade } from './submission.facade';

function detail(overrides: Partial<SubmissionDetailResponse> = {}): SubmissionDetailResponse {
  return {
    id: 'submission-1',
    organizationId: 'org-1',
    organizationCode: 'ALPHA',
    organizationName: 'Alpha unit',
    reportingPeriodId: 'period-1',
    periodCode: '2026-Q1',
    periodStatus: 'OPEN',
    submissionDeadline: '2026-04-15',
    status: 'DRAFT',
    editable: true,
    metrics: [],
    values: [],
    version: 7,
    ...overrides,
  };
}

class StubApi {
  readonly calls: string[] = [];
  submitError: unknown = null;

  getSubmission = () => {
    this.calls.push('getSubmission');
    return of(detail({ status: 'SUBMITTED', editable: false, version: 9 }));
  };
  getSubmissionHistory = () => of([]);
  submitSubmission = () => {
    this.calls.push('submitSubmission');
    return this.submitError ? throwError(() => this.submitError) : of(detail());
  };
}

class StubNotifier {
  readonly errors: ApiError[] = [];
  readonly successes: string[] = [];
  notify(error: ApiError): void {
    this.errors.push(error);
  }
  success(message: string): void {
    this.successes.push(message);
  }
}

describe('SubmissionFacade', () => {
  let api: StubApi;
  let notifier: StubNotifier;
  let facade: SubmissionFacade;

  beforeEach(() => {
    api = new StubApi();
    notifier = new StubNotifier();
    TestBed.configureTestingModule({
      providers: [
        { provide: SubmissionsService, useValue: api },
        { provide: ErrorNotifier, useValue: notifier },
      ],
    });
    facade = TestBed.inject(SubmissionFacade);
  });

  it('keeps the returned submission after a successful command', async () => {
    expect(await facade.submit('submission-1', 0)).toBe(true);
    expect(facade.detail()?.id).toBe('submission-1');
    expect(notifier.successes).toContain('Sent for review');
  });

  // Retrying with the version the screen held would overwrite a change nobody has seen.
  it('reloads rather than retrying when the version is stale', async () => {
    api.submitError = new ApiError(409, 'VERSION_CONFLICT', 'Someone else changed it');

    expect(await facade.submit('submission-1', 0)).toBe(false);
    expect(notifier.errors.at(0)?.code).toBe('VERSION_CONFLICT');
    expect(api.calls).toContain('getSubmission');
    expect(facade.detail()?.version).toBe(9);
  });

  it('does not reload for an ordinary rule failure', async () => {
    api.submitError = new ApiError(422, 'REQUIRED_METRICS_MISSING', 'Fill in the required metrics');

    expect(await facade.submit('submission-1', 0)).toBe(false);
    expect(api.calls).not.toContain('getSubmission');
  });
});
