import { ApiError } from './api-error';

describe('ApiError', () => {
  it('reads the problem detail contract', () => {
    const error = ApiError.from(409, {
      code: 'VERSION_CONFLICT',
      detail: 'Someone else changed it',
      correlationId: 'abc-123',
    });

    expect(error.status).toBe(409);
    expect(error.code).toBe('VERSION_CONFLICT');
    expect(error.detail).toBe('Someone else changed it');
    expect(error.correlationId).toBe('abc-123');
    expect(error.isVersionConflict).toBe(true);
  });

  it('carries field errors for a validation failure', () => {
    const error = ApiError.from(400, {
      code: 'VALIDATION_FAILED',
      detail: 'Invalid fields',
      errors: [{ field: 'code', code: 'PATTERN', message: 'bad' }],
    });

    expect(error.isValidation).toBe(true);
    expect(error.fieldErrors).toHaveLength(1);
    expect(error.fieldErrors[0]?.field).toBe('code');
  });

  it('treats 422 as a validation failure', () => {
    expect(ApiError.from(422, { code: 'PERIOD_SCOPE_FROZEN' }).isValidation).toBe(true);
  });

  it('falls back when the body is not a problem detail', () => {
    const error = ApiError.from(502, null);

    expect(error.code).toBe('UNKNOWN');
    expect(error.detail).toBe('Something went wrong.');
  });
});
