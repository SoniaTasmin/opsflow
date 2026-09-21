import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { AuthService } from '../auth/auth.service';
import { ApiError } from '../error/api-error';

export const errorInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);

  return next(request).pipe(
    catchError((response: HttpErrorResponse) => {
      if (response.status === 401) {
        auth.login();
      }
      // Callers deal in ApiError; HttpErrorResponse stops here.
      return throwError(() => ApiError.from(response.status, response.error));
    }),
  );
};
