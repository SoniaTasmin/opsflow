import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { provideRouter } from '@angular/router';

import { AuthService } from './auth.service';
import { roleGuard } from './role.guard';

class StubAuthService {
  granted: string[] = [];
  hasAnyRole(roles: readonly string[]): boolean {
    return roles.some((role) => this.granted.includes(role));
  }
}

function runGuard(roles: string[]) {
  const route = { data: { roles } } as unknown as ActivatedRouteSnapshot;
  return TestBed.runInInjectionContext(() => roleGuard(route, {} as RouterStateSnapshot));
}

describe('roleGuard', () => {
  let auth: StubAuthService;

  beforeEach(() => {
    auth = new StubAuthService();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    });
  });

  it('allows a user holding a required role', () => {
    auth.granted = ['ADMIN'];

    expect(runGuard(['ADMIN'])).toBe(true);
  });

  it('redirects a user without the role', () => {
    auth.granted = ['USER'];

    const result = runGuard(['ADMIN']);

    expect(result).not.toBe(true);
    expect(String(result)).toContain('forbidden');
  });

  it('allows a route that requires nothing', () => {
    expect(runGuard([])).toBe(true);
  });

  it('does not treat a reviewer as an administrator', () => {
    auth.granted = ['USER', 'REVIEWER'];

    expect(runGuard(['ADMIN'])).not.toBe(true);
  });
});
