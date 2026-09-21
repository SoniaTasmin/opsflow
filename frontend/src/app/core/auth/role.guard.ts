import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService, Role } from './auth.service';

/** UX only: hides routes a user could not use. The backend still enforces access. */
export const roleGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const required = (route.data['roles'] ?? []) as Role[];

  if (required.length === 0 || auth.hasAnyRole(required)) {
    return true;
  }
  return router.createUrlTree(['/forbidden']);
};
