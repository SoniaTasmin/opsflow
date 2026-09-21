import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { ImportJobResponse } from '../../core/api/generated';
import { ImportsService } from '../../core/api/generated';
import { ApiError } from '../../core/error/api-error';
import { ErrorNotifier } from '../../core/error/error-notifier.service';
import { ImportFacade } from './import.facade';

function job(overrides: Partial<ImportJobResponse> = {}): ImportJobResponse {
  return {
    id: 'import-1',
    reportingPeriodId: 'period-1',
    status: 'RUNNING',
    originalFilename: 'values.csv',
    fileSizeBytes: 120,
    submittedRows: 10,
    processedRows: 4,
    validRows: 4,
    invalidRows: 0,
    createdAt: '2026-04-01T09:00:00Z',
    ...overrides,
  };
}

class StubApi {
  readonly calls: string[] = [];
  readonly keys: (string | undefined)[] = [];
  current: ImportJobResponse = job();
  startError: unknown = null;

  getImport = () => {
    this.calls.push('getImport');
    return of(this.current);
  };
  getImportErrors = () => {
    this.calls.push('getImportErrors');
    return of([{ rowNumber: 2, code: 'ORG_UNKNOWN', message: 'No organization', affectedRows: 1 }]);
  };
  startImport = (_period: string, _file: Blob, _organizationId?: string, key?: string) => {
    this.keys.push(key);
    return this.startError ? throwError(() => this.startError) : of(job({ status: 'PENDING' }));
  };
}

class StubNotifier {
  readonly errors: ApiError[] = [];
  notify(error: ApiError): void {
    this.errors.push(error);
  }
  success(): void {
    // The facade reports success through the snack bar; the tests only care that it does not throw.
  }
}

describe('ImportFacade', () => {
  let api: StubApi;
  let notifier: StubNotifier;
  let facade: ImportFacade;

  beforeEach(() => {
    api = new StubApi();
    notifier = new StubNotifier();
    TestBed.configureTestingModule({
      providers: [
        { provide: ImportsService, useValue: api },
        { provide: ErrorNotifier, useValue: notifier },
      ],
    });
    facade = TestBed.inject(ImportFacade);
  });

  it('gives every upload its own idempotency key', async () => {
    const file = new File(['organization_code\n'], 'values.csv', { type: 'text/csv' });

    await facade.start({ file, reportingPeriodId: 'period-1' });
    await facade.start({ file, reportingPeriodId: 'period-1' });

    expect(api.keys).toHaveLength(2);
    expect(api.keys[0]).toBeTruthy();
    expect(api.keys[0]).not.toEqual(api.keys[1]);
  });

  // Errors are only complete once the job stops, so asking for them while it runs is noise.
  it('fetches row errors only once the job has finished', async () => {
    await facade.loadDetail('import-1');
    expect(api.calls).not.toContain('getImportErrors');

    api.current = job({ status: 'COMPLETED', invalidRows: 1 });
    await facade.loadDetail('import-1');

    expect(api.calls).toContain('getImportErrors');
    expect(facade.errors()).toHaveLength(1);
  });

  it('reports a refused upload rather than pretending it started', async () => {
    api.startError = new ApiError(409, 'DUPLICATE_RESOURCE', 'This key was already used');
    const file = new File(['x'], 'values.csv', { type: 'text/csv' });

    expect(await facade.start({ file, reportingPeriodId: 'period-1' })).toBeNull();
    expect(notifier.errors.at(0)?.code).toBe('DUPLICATE_RESOURCE');
  });
});
