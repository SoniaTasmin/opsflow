import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PeriodResponse, PeriodSummaryResponse } from '../../core/api/generated';
import { PeriodSummaryComponent } from './period-summary.component';
import { SummaryFacade } from './summary.facade';

function summary(overrides: Partial<PeriodSummaryResponse> = {}): PeriodSummaryResponse {
  return {
    periodId: 'period-1',
    periodCode: '2026-Q1',
    periodStatus: 'OPEN',
    scope: 'ALL',
    participatingOrganizations: 4,
    notStarted: 1,
    draft: 1,
    submitted: 0,
    underReview: 1,
    rejected: 0,
    approved: 1,
    completionRatio: 0.25,
    ...overrides,
  };
}

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

class StubFacade {
  readonly periods = signal<PeriodResponse[]>([period('period-1', 'OPEN')]);
  readonly summary = signal<PeriodSummaryResponse | null>(null);
  readonly loading = signal(false);
  readonly failed = signal(false);
  readonly loaded: string[] = [];

  loadPeriods = async () => this.periods()[0]?.id ?? null;
  load = async (id: string) => {
    this.loaded.push(id);
  };
}

describe('PeriodSummaryComponent', () => {
  let facade: StubFacade;

  async function render(): Promise<ComponentFixture<PeriodSummaryComponent>> {
    const fixture = TestBed.createComponent(PeriodSummaryComponent);
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    facade = new StubFacade();
    TestBed.configureTestingModule({
      providers: [{ provide: SummaryFacade, useValue: facade }],
    });
  });

  it('shows every status count and the completion figure', async () => {
    facade.summary.set(summary());
    const fixture = await render();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Not started');
    expect(text).toContain('Under review');
    expect(text).toContain('Approved');
    expect(fixture.nativeElement.querySelector('[data-testid="completion"]').textContent).toContain(
      '25%',
    );
  });

  // The denominator is the period's frozen participants, so the screen says so plainly.
  it('reports the approved count against the participating organizations', async () => {
    facade.summary.set(summary());
    const fixture = await render();

    expect(
      fixture.nativeElement.querySelector('[data-testid="participants"]').textContent,
    ).toContain('1 of 4 participating organizations');
  });

  it('says whose organizations the figures cover', async () => {
    facade.summary.set(summary({ scope: 'MY_ORGANIZATIONS' }));
    const fixture = await render();

    expect(fixture.nativeElement.querySelector('[data-testid="scope"]').textContent).toContain(
      'Your organizations',
    );
  });

  it('explains an empty participant set rather than showing zeroes', async () => {
    facade.summary.set(
      summary({
        participatingOrganizations: 0,
        notStarted: 0,
        draft: 0,
        underReview: 0,
        approved: 0,
        completionRatio: 0,
      }),
    );
    const fixture = await render();

    expect(fixture.nativeElement.textContent).toContain('no participating organizations');
    expect(fixture.nativeElement.querySelector('[data-testid="completion"]')).toBeNull();
  });

  it('offers a retry instead of stale figures when the summary fails', async () => {
    facade.failed.set(true);
    const fixture = await render();

    expect(fixture.nativeElement.textContent).toContain('could not be loaded');
    fixture.nativeElement.querySelector('[data-action="retry"]').click();
    await fixture.whenStable();

    expect(facade.loaded.length).toBeGreaterThan(0);
  });
});
