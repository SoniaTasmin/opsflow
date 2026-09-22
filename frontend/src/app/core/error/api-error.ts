export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  code?: string;
  correlationId?: string;
  timestamp?: string;
  errors?: FieldError[];
  [extension: string]: unknown;
}

export interface FieldError {
  field: string;
  code: string;
  message: string;
}

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    readonly detail: string,
    readonly correlationId?: string,
    readonly fieldErrors: FieldError[] = [],
  ) {
    super(detail);
    this.name = 'ApiError';
  }

  get isVersionConflict(): boolean {
    return this.code === 'VERSION_CONFLICT';
  }

  get isValidation(): boolean {
    return this.status === 400 || this.status === 422;
  }

  static from(status: number, body: unknown): ApiError {
    const problem = (body ?? {}) as ProblemDetail;
    return new ApiError(
      status,
      problem.code ?? 'UNKNOWN',
      problem.detail ?? problem.title ?? 'Something went wrong.',
      problem.correlationId,
      problem.errors ?? [],
    );
  }
}
