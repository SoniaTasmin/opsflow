import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { switchMap, take } from 'rxjs';

import { AuthService } from '../auth/auth.service';
import { environment } from '../../../environments/environment';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith(environment.apiBaseUrl)) {
    return next(request);
  }

  return inject(AuthService)
    .accessToken()
    .pipe(
      take(1),
      switchMap((token) =>
        next(token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request),
      ),
    );
};
