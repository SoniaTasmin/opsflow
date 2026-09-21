import { HttpInterceptorFn } from '@angular/common/http';

import { environment } from '../../../environments/environment';

/** Lets one identifier tie a user's report to the server logs for that request. */
export const correlationIdInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith(environment.apiBaseUrl)) {
    return next(request);
  }
  return next(request.clone({ setHeaders: { 'X-Correlation-Id': crypto.randomUUID() } }));
};
