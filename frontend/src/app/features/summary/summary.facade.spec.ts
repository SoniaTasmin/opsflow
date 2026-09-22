import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import {
  PeriodResponse,
  PeriodSummaryResponse,
  ReportingPeriodsService,
} from '../../core/api/generated';
import { ApiError } from '../../core/error/api-error';
import { ErrorNotifier } from '../../core/error/error-notifier.service';
import { SummaryFacade } from './summary.facade';

function period(id: string, status: string): PeriodResponse {
  return {
    id,
    code: id.toUpperCase(),
    periodType: 'QUARTER',
    startsOn: '2026-01-01',
    endsOn: '2026-03-31',
    submissionDeadline: '2026-04-15',
    status,
    metricCount: 1,
    participantCount: 4,
    version: 1,
  };
}

const SUMMARY: PeriodSummaryResponse = {
  periodId: 'open-period',
  periodCode: 'OPEN-PERIOD',
  periodStatus: 'OPEN',
  scope: 'ALL',
  participatingOrganizations: 2,
  notStarted: 1,
  draft: 0,
  submitted: 0,
  underReview: 0,
  rejected: 0,
  approved: 1,
  completionRatio: 0.5,
};

class StubApi {
  periods: PeriodResponse[] = [];
  summaryError: unknown = null;

  listPeriods = () =>
    of({
      content: this.periods,
      page: 0,
      size: 100,
      totalElements: this.periods.length,
      totalPages: 1,
    });
  getPeriodSummary = () => (this.summaryError ? throwError(() => this.summaryError) : of(SUMMARY));
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

describe('SummaryFacade', () => {
  let api: StubApi;
  let notifier: StubNotifier;
  let facade: SummaryFacade;

  beforeEach(() => {
    api = new StubApi();
    notifier = new StubNotifier();
    TestBed.configureTestingModule({
      providers: [
        { provide: ReportingPeriodsService, useValue: api },
        { provide: ErrorNotifier, useValue: notifier },
      ],
    });
    facade = TestBed.inject(SummaryFacade);
  });

  // The period being reported on now is the one worth opening on.
  it('prefers the open period over a closed one', async () => {
    api.periods = [period('closed-period', 'CLOSED'), period('open-period', 'OPEN')];

    expect(await facade.loadPeriods()).toBe('open-period');
  });

  it('falls back to the most recent period when none is open', async () => {
    api.periods = [period('closed-period', 'CLOSED'), period('draft-period', 'DRAFT')];

    expect(await facade.loadPeriods()).toBe('closed-period');
  });

  it('has nothing to show when there are no periods at all', async () => {
    api.periods = [];

    expect(await facade.loadPeriods()).toBeNull();
  });

  it('keeps the figures it loaded', async () => {
    await facade.load('open-period');

    expect(facade.summary()?.participatingOrganizations).toBe(2);
    expect(facade.failed()).toBe(false);
  });

  // Leaving the previous period's numbers under a new heading would be worse than showing none.
  it('clears stale figures when a load fails', async () => {
    await facade.load('open-period');
    api.summaryError = new ApiError(404, 'RESOURCE_NOT_FOUND', 'No such period');

    await facade.load('missing-period');

    expect(facade.summary()).toBeNull();
    expect(facade.failed()).toBe(true);
    expect(notifier.errors.at(0)?.code).toBe('RESOURCE_NOT_FOUND');
  });
});
