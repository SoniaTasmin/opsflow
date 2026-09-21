import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';

import { AuthService } from './auth.service';

/** UX only: the backend is the security boundary. */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (auth.isAuthenticated()) {
    return true;
  }
  auth.login();
  return false;
};
