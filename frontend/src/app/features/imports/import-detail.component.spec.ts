import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ImportJobResponse, ImportRowErrorResponse } from '../../core/api/generated';
import { ImportDetailComponent } from './import-detail.component';
import { ImportFacade } from './import.facade';

function job(overrides: Partial<ImportJobResponse> = {}): ImportJobResponse {
  return {
    id: 'import-1',
    reportingPeriodId: 'period-1',
    status: 'COMPLETED',
    originalFilename: 'values.csv',
    fileSizeBytes: 120,
    submittedRows: 10,
    processedRows: 10,
    validRows: 8,
    invalidRows: 2,
    createdAt: '2026-04-01T09:00:00Z',
    finishedAt: '2026-04-01T09:01:00Z',
    ...overrides,
  };
}

class StubFacade {
  readonly detail = signal<ImportJobResponse | null>(null);
  readonly errors = signal<ImportRowErrorResponse[]>([]);
  readonly imports = signal<ImportJobResponse[]>([]);
  readonly total = signal(0);
  readonly loading = signal(false);
  loads = 0;

  loadDetail = async () => {
    this.loads++;
    return this.detail();
  };
}

describe('ImportDetailComponent', () => {
  let facade: StubFacade;

  async function render(
    current: ImportJobResponse,
  ): Promise<ComponentFixture<ImportDetailComponent>> {
    facade.detail.set(current);
    const fixture = TestBed.createComponent(ImportDetailComponent);
    fixture.componentRef.setInput('id', current.id);
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    facade = new StubFacade();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: ImportFacade, useValue: facade }],
    });
  });

  it('shows what the import applied and what it rejected', async () => {
    const fixture = await render(job());

    expect(fixture.nativeElement.querySelector('[data-testid="valid-rows"]').textContent).toContain(
      '8',
    );
    expect(
      fixture.nativeElement.querySelector('[data-testid="invalid-rows"]').textContent,
    ).toContain('2');
  });

  it('lists the rows that could not be applied', async () => {
    facade.errors.set([
      {
        rowNumber: 3,
        code: 'ORG_UNKNOWN',
        message: 'No organization has code NOWHERE',
        affectedRows: 1,
      },
      {
        rowNumber: 5,
        code: 'SUBMISSION_NOT_EDITABLE',
        message: 'Already approved',
        affectedRows: 40,
      },
    ]);
    const fixture = await render(job());

    const table = fixture.nativeElement.querySelector('[data-testid="import-errors"]');
    expect(table.textContent).toContain('ORG_UNKNOWN');
    expect(table.textContent).toContain('No organization has code NOWHERE');
    // A group failure reports how many rows it cost rather than one row per line.
    expect(table.textContent).toContain('40');
  });

  it('says plainly when an import was interrupted rather than rejected', async () => {
    const fixture = await render(
      job({
        status: 'FAILED',
        failureCode: 'INTERRUPTED_BY_SHUTDOWN',
        failureMessage: 'The import was interrupted by an application shutdown',
      }),
    );

    const banner = fixture.nativeElement.querySelector('[data-testid="import-failure"]');
    expect(banner.textContent).toContain('Interrupted, not finished');
    expect(banner.textContent).toContain('Upload the file again');
  });

  it('distinguishes a failure from an interruption', async () => {
    const fixture = await render(
      job({ status: 'FAILED', failureCode: 'HEADER_MISMATCH', failureMessage: 'Wrong header' }),
    );

    const banner = fixture.nativeElement.querySelector('[data-testid="import-failure"]');
    expect(banner.textContent).toContain('This import did not finish');
    expect(banner.textContent).not.toContain('Upload the file again');
  });

  it('keeps refreshing while the import is running', async () => {
    const fixture = await render(job({ status: 'RUNNING', finishedAt: undefined }));
    const afterFirstLoad = facade.loads;

    expect(fixture.nativeElement.textContent).toContain('Refreshing while it runs');
    await new Promise((resolve) => setTimeout(resolve, 2500));

    expect(facade.loads).toBeGreaterThan(afterFirstLoad);
    fixture.destroy();
  });

  it('stops refreshing once the import is finished', async () => {
    const fixture = await render(job({ status: 'COMPLETED' }));
    const afterFirstLoad = facade.loads;

    expect(fixture.nativeElement.textContent).not.toContain('Refreshing while it runs');
    await new Promise((resolve) => setTimeout(resolve, 2500));

    expect(facade.loads).toBe(afterFirstLoad);
  });
});
