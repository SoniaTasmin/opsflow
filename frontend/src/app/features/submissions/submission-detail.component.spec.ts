import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import {
  SubmissionDetailResponse,
  SubmissionMetricResponse,
  TransitionResponse,
} from '../../core/api/generated';
import { AuthService, Role } from '../../core/auth/auth.service';
import { ConfirmService } from '../../shared/ui/confirm.service';
import { SubmissionDetailComponent } from './submission-detail.component';
import { SubmissionFacade, ValueInput } from './submission.facade';

const HEADCOUNT: SubmissionMetricResponse = {
  metricId: 'metric-headcount',
  code: 'HEADCOUNT',
  name: 'Headcount',
  unit: 'people',
  valueType: 'INTEGER',
  required: true,
  displayOrder: 0,
};

const REVENUE: SubmissionMetricResponse = {
  metricId: 'metric-revenue',
  code: 'REVENUE',
  name: 'Revenue',
  unit: 'EUR',
  valueType: 'DECIMAL',
  required: false,
  displayOrder: 1,
};

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
    metrics: [HEADCOUNT, REVENUE],
    values: [{ metricId: HEADCOUNT.metricId, value: 42, source: 'MANUAL' }],
    version: 3,
    ...overrides,
  };
}

class StubFacade {
  readonly detail = signal<SubmissionDetailResponse | null>(null);
  readonly history = signal<TransitionResponse[]>([]);
  readonly loading = signal(false);
  readonly calls: string[] = [];
  savedValues: ValueInput[] = [];
  rejectedWith = '';

  loadDetail = async () => {
    this.calls.push('loadDetail');
  };
  saveValues = async (_id: string, values: ValueInput[]) => {
    this.calls.push('saveValues');
    this.savedValues = values;
    return true;
  };
  submit = async () => {
    this.calls.push('submit');
    return true;
  };
  withdraw = async () => {
    this.calls.push('withdraw');
    return true;
  };
  claim = async () => {
    this.calls.push('claim');
    return true;
  };
  release = async () => {
    this.calls.push('release');
    return true;
  };
  approve = async () => {
    this.calls.push('approve');
    return true;
  };
  reject = async (_id: string, reason: string) => {
    this.calls.push('reject');
    this.rejectedWith = reason;
    return true;
  };
}

class StubAuth {
  granted: Role[] = ['USER'];
  hasAnyRole(roles: readonly Role[]): boolean {
    return roles.some((role) => this.granted.includes(role));
  }
}

describe('SubmissionDetailComponent', () => {
  let facade: StubFacade;
  let auth: StubAuth;
  let confirmAnswer: boolean;
  let dialogAnswer: string | undefined;

  async function render(
    current: SubmissionDetailResponse,
  ): Promise<ComponentFixture<SubmissionDetailComponent>> {
    facade.detail.set(current);
    const fixture = TestBed.createComponent(SubmissionDetailComponent);
    fixture.componentRef.setInput('id', current.id);
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  function click(fixture: ComponentFixture<SubmissionDetailComponent>, action: string): void {
    const button: HTMLButtonElement | null = fixture.nativeElement.querySelector(
      `[data-action="${action}"]`,
    );
    expect(button).not.toBeNull();
    button?.click();
  }

  beforeEach(() => {
    facade = new StubFacade();
    auth = new StubAuth();
    confirmAnswer = true;
    dialogAnswer = undefined;

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: SubmissionFacade, useValue: facade },
        { provide: AuthService, useValue: auth },
        { provide: ConfirmService, useValue: { ask: () => of(confirmAnswer) } },
        { provide: MatDialog, useValue: { open: () => ({ afterClosed: () => of(dialogAnswer) }) } },
      ],
    });
  });

  describe('as the author', () => {
    it('renders one input per metric in the period scope', async () => {
      const fixture = await render(detail());

      expect(fixture.nativeElement.querySelectorAll('[data-metric]').length).toBe(2);
    });

    it('posts the values that are filled in and omits the blank ones', async () => {
      const fixture = await render(detail());

      click(fixture, 'save');
      await fixture.whenStable();

      expect(facade.calls).toContain('saveValues');
      expect(facade.savedValues).toEqual([
        { metricId: HEADCOUNT.metricId, value: 42, note: undefined },
      ]);
    });

    // A number input emits a number, not a string: a save path that assumes text breaks the
    // moment anyone actually types, which no fixture-only test would notice.
    it('posts a value the author typed', async () => {
      const fixture = await render(detail({ values: [] }));

      const input: HTMLInputElement = fixture.nativeElement.querySelector(
        `[data-metric="${HEADCOUNT.code}"]`,
      );
      input.value = '37';
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();

      click(fixture, 'save');
      await fixture.whenStable();

      expect(facade.savedValues).toEqual([
        { metricId: HEADCOUNT.metricId, value: 37, note: undefined },
      ]);
    });

    it('renders the accepted range in the hint', async () => {
      const fixture = await render(
        detail({ metrics: [{ ...HEADCOUNT, minValue: 0, maxValue: 100 }] }),
      );

      expect(fixture.nativeElement.textContent).toContain('people · 0 – 100');
      expect(fixture.nativeElement.textContent).not.toContain('null');
    });

    it('sends the submission for review', async () => {
      const fixture = await render(detail());

      click(fixture, 'submit');
      await fixture.whenStable();

      expect(facade.calls).toContain('submit');
    });

    it('offers correction and resubmission after a rejection', async () => {
      const fixture = await render(
        detail({ status: 'REJECTED', editable: true, rejectionReason: 'Headcount is wrong' }),
      );

      expect(fixture.nativeElement.textContent).toContain('Headcount is wrong');
      click(fixture, 'submit');
      await fixture.whenStable();

      expect(facade.calls).toContain('submit');
    });

    it('shows no editing once the submission is approved', async () => {
      const fixture = await render(detail({ status: 'APPROVED', editable: false }));

      expect(fixture.nativeElement.querySelector('[data-action="save"]')).toBeNull();
      expect(fixture.nativeElement.querySelector('[data-action="submit"]')).toBeNull();
    });

    it('offers no review actions to a plain reporter', async () => {
      const fixture = await render(detail({ status: 'SUBMITTED', editable: false }));

      expect(fixture.nativeElement.querySelector('[data-action="claim"]')).toBeNull();
    });
  });

  describe('as the reviewer', () => {
    beforeEach(() => {
      auth.granted = ['REVIEWER'];
    });

    it('claims a submission waiting for review', async () => {
      const fixture = await render(detail({ status: 'SUBMITTED', editable: false }));

      click(fixture, 'claim');
      await fixture.whenStable();

      expect(facade.calls).toContain('claim');
    });

    it('releases, approves and rejects a claimed submission', async () => {
      const fixture = await render(detail({ status: 'UNDER_REVIEW', editable: false }));

      click(fixture, 'release');
      click(fixture, 'approve');
      await fixture.whenStable();

      expect(facade.calls).toContain('release');
      expect(facade.calls).toContain('approve');
    });

    it('rejects with the reason the dialog collected', async () => {
      dialogAnswer = 'Headcount does not match payroll';
      const fixture = await render(detail({ status: 'UNDER_REVIEW', editable: false }));

      click(fixture, 'reject');
      await fixture.whenStable();

      expect(facade.rejectedWith).toBe('Headcount does not match payroll');
    });

    it('does not reject when the reviewer cancels the dialog', async () => {
      dialogAnswer = undefined;
      const fixture = await render(detail({ status: 'UNDER_REVIEW', editable: false }));

      click(fixture, 'reject');
      await fixture.whenStable();

      expect(facade.calls).not.toContain('reject');
    });
  });
});
